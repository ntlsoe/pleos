import os
import csv
import math
import time
from typing import List, Tuple, Set
from geometry import Point3
from eye_model import EyePopulationModel
from pillar_model import load_vehicle_pillars, VehiclePillarGeometry
from risk_sampling import RiskSurface, RiskSamplePoint
from vehicle_trajectory import VehicleTrajectoryGenerator, VehiclePose
from occlusion_engine import BinocularOcclusionEngine

class AdaptiveRiskSurfaceSampler:
    """
    Implements 0.5m Base Grid + 0.25m Boundary Refinement with strict area-weight conservation
    and duplicate sample prevention.
    """
    def __init__(
        self,
        vehicle: VehiclePillarGeometry,
        eye_model: EyePopulationModel,
        coverage_gradient_threshold: float = 0.05,
        pillar_margin_rad: float = 0.04,
        curvature_threshold_inv_m: float = 0.05,
        threshold_band: float = 0.03,
        candidate_threshold: float = 0.05
    ):
        self.vehicle = vehicle
        self.eye_model = eye_model
        self.coverage_gradient_threshold = coverage_gradient_threshold
        self.pillar_margin_rad = pillar_margin_rad
        self.curvature_threshold_inv_m = curvature_threshold_inv_m
        self.threshold_band = threshold_band
        self.candidate_threshold = candidate_threshold

        # Precompute angular bounds of Left and Right pillars from all eye positions in vehicle frame
        eye_samples = eye_model.generate_samples()
        eyes = []
        for s in eye_samples:
            eyes.append(s.eye_pair.left)
            eyes.append(s.eye_pair.right)

        def compute_pillar_az_bounds(verts: List[Point3]) -> Tuple[float, float]:
            angs = []
            for e in eyes:
                for v in verts:
                    angs.append(math.atan2(v.y - e.y, v.x - e.x))
            return (min(angs), max(angs))

        self.left_az_bounds = compute_pillar_az_bounds(vehicle.left_pillar.vertices)
        self.right_az_bounds = compute_pillar_az_bounds(vehicle.right_pillar.vertices)
        self.nominal_eye = vehicle.driver_eye_nominal

    def _is_near_pillar_frustum(self, pt_veh: Point3, cell_radius: float, extra_margin_rad: float) -> bool:
        dx = pt_veh.x - self.nominal_eye.x
        dy = pt_veh.y - self.nominal_eye.y
        dist = math.hypot(dx, dy)
        if dist < 0.2:
            return True
        az = math.atan2(dy, dx)
        ang_radius = math.atan2(cell_radius, max(0.5, dist)) + self.pillar_margin_rad + extra_margin_rad

        l_min, l_max = self.left_az_bounds
        r_min, r_max = self.right_az_bounds
        if (l_min - ang_radius) <= az <= (l_max + ang_radius):
            return True
        if (r_min - ang_radius) <= az <= (r_max + ang_radius):
            return True
        return False

    def _is_near_pillar_edge(self, pt_veh: Point3, cell_radius: float, extra_margin_rad: float) -> bool:
        if pt_veh.x <= 0.2 or pt_veh.x > 16.0:
            return False
        dx = pt_veh.x - self.nominal_eye.x
        dy = pt_veh.y - self.nominal_eye.y
        dist = math.hypot(dx, dy)
        if dist < 0.2:
            return True
        az = math.atan2(dy, dx)
        edge_tol = math.atan2(0.35 * cell_radius, max(1.0, dist)) + extra_margin_rad
        l_min, l_max = self.left_az_bounds
        r_min, r_max = self.right_az_bounds
        near_left = abs(az - l_min) <= edge_tol or abs(az - l_max) <= edge_tol
        near_right = abs(az - r_min) <= edge_tol or abs(az - r_max) <= edge_tol
        return near_left or near_right

    def sample_adaptive_surface(
        self,
        surface: RiskSurface,
        engine: BinocularOcclusionEngine,
        poses: List[VehiclePose],
        turn_curvature_inv_m: float = 0.0
    ) -> Tuple[List[RiskSamplePoint], dict]:
        base_spacing = 0.5
        nx = max(1, int(round(surface.length_x / base_spacing)))
        ny = max(1, int(round(surface.width_y / base_spacing)))

        dx = surface.length_x / nx
        dy = surface.width_y / ny
        cell_radius = 0.5 * math.hypot(dx, dy)
        base_w = 1.0 / (nx * ny)

        x_start = surface.origin.x - surface.length_x / 2.0
        y_start = surface.origin.y - surface.width_y / 2.0
        pz = surface.origin.z + surface.height_z

        # 1. Build base grid cells (ix, iy)
        base_centers = {}
        for ix in range(nx):
            px = x_start + (ix + 0.5) * dx
            for iy in range(ny):
                py = y_start + (iy + 0.5) * dy
                base_centers[(ix, iy)] = Point3(px, py, pz)

        # Curvature trigger expansion
        high_curvature = abs(turn_curvature_inv_m) >= self.curvature_threshold_inv_m
        extra_margin = 0.015 if high_curvature else 0.0

        # Sample key poses along trajectory (stride to keep trigger evaluation fast)
        stride = max(1, len(poses) // 20)
        check_poses = poses[::stride]
        if poses[-1] not in check_poses:
            check_poses.append(poses[-1])

        refine_flags: Set[Tuple[int, int]] = set()
        trigger_counts = {
            "neighbor_occlusion_diff": 0,
            "coverage_gradient": 0,
            "pillar_frustum_proximity": 0,
            "threshold_band": 0
        }

        for pose in check_poses:
            # Evaluate base cells at this pose for frustum proximity and coarse occlusion
            occ_map = {}
            cov_map = {}
            weighted_occ_sum = 0.0

            for (ix, iy), world_pt in base_centers.items():
                pt_veh = pose.world_to_vehicle(world_pt)
                near_frustum = self._is_near_pillar_frustum(pt_veh, cell_radius, extra_margin)
                if near_frustum:
                    res = engine.evaluate_risk_point_at_pose(world_pt, pose)
                    occ_map[(ix, iy)] = res["isPossibleOccluded"]
                    cov_map[(ix, iy)] = res["coverage"]
                    if res["isPossibleOccluded"]:
                        weighted_occ_sum += base_w
                    if 0.0 < res["coverage"] < 1.0:
                        refine_flags.add((ix, iy))
                        trigger_counts["coverage_gradient"] += 1
                    if self._is_near_pillar_edge(pt_veh, cell_radius, extra_margin):
                        refine_flags.add((ix, iy))
                        trigger_counts["pillar_frustum_proximity"] += 1
                else:
                    occ_map[(ix, iy)] = False
                    cov_map[(ix, iy)] = 0.0

            # Check neighbor differences (Trigger 1 & Trigger 2)
            for ix in range(nx):
                for iy in range(ny):
                    c_occ = occ_map[(ix, iy)]
                    c_cov = cov_map[(ix, iy)]
                    if c_occ:
                        refine_flags.add((ix, iy))
                    for dix in (-1, 0, 1):
                        for diy in (-1, 0, 1):
                            if dix == 0 and diy == 0:
                                continue
                            nix, niy = ix + dix, iy + diy
                            if 0 <= nix < nx and 0 <= niy < ny:
                                if occ_map[(nix, niy)] != c_occ:
                                    refine_flags.add((ix, iy))
                                    trigger_counts["neighbor_occlusion_diff"] += 1
                                elif abs(cov_map[(nix, niy)] - c_cov) >= self.coverage_gradient_threshold:
                                    refine_flags.add((ix, iy))
                                    trigger_counts["coverage_gradient"] += 1

            # Trigger 5: Base grid ratio near candidate threshold
            if abs(weighted_occ_sum - self.candidate_threshold) <= self.threshold_band:
                trigger_counts["threshold_band"] += 1

        # 2. Construct adaptive point list with strict area weight conservation and deduplication
        adaptive_points: List[RiskSamplePoint] = []
        seen_coords: Set[Tuple[int, int, int]] = set()
        sub_w = base_w / 4.0

        for ix in range(nx):
            for iy in range(ny):
                center = base_centers[(ix, iy)]
                if (ix, iy) in refine_flags:
                    for sx in (-0.25, 0.25):
                        for sy in (-0.25, 0.25):
                            sub_pt = Point3(center.x + sx * dx, center.y + sy * dy, center.z)
                            key = (int(round(sub_pt.x * 100000)), int(round(sub_pt.y * 100000)), int(round(sub_pt.z * 100000)))
                            assert key not in seen_coords, f"Duplicate sample point detected at {sub_pt}"
                            seen_coords.add(key)
                            adaptive_points.append(RiskSamplePoint(sub_pt, sub_w, 0.25))
                else:
                    key = (int(round(center.x * 100000)), int(round(center.y * 100000)), int(round(center.z * 100000)))
                    assert key not in seen_coords, f"Duplicate sample point detected at {center}"
                    seen_coords.add(key)
                    adaptive_points.append(RiskSamplePoint(center, base_w, 0.5))

        total_w = sum(p.weight for p in adaptive_points)
        assert abs(total_w - 1.0) < 1e-12, f"Area weight not conserved: {total_w}"

        stats = {
            "base_cell_count": nx * ny,
            "refined_cell_count": len(refine_flags),
            "unrefined_cell_count": (nx * ny) - len(refine_flags),
            "final_sample_count": len(adaptive_points),
            "fixed_025_sample_count": nx * ny * 4,
            "weight_sum": total_w
        }
        return adaptive_points, stats


def run_adaptive_validation():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    audit_dir = os.path.join(os.path.dirname(__file__), "..", "..", "audit", "results")
    os.makedirs(audit_dir, exist_ok=True)

    profile_path = os.path.join(config_dir, "representative_sedan_v0.json")
    vehicle = load_vehicle_pillars(profile_path)

    samples = [
        {"id": "center_mid", "weight": 0.28, "deltaX": 0.00, "deltaY": 0.00, "deltaZ": 0.00, "ipdMeters": 0.065},
        {"id": "tall_aft",   "weight": 0.12, "deltaX": -0.05, "deltaY": 0.01, "deltaZ": 0.05, "ipdMeters": 0.067},
        {"id": "short_fore", "weight": 0.12, "deltaX": 0.05, "deltaY": -0.01, "deltaZ": -0.05, "ipdMeters": 0.063},
        {"id": "high_mid",   "weight": 0.10, "deltaX": 0.00, "deltaY": 0.00, "deltaZ": 0.04, "ipdMeters": 0.065},
        {"id": "low_mid",    "weight": 0.10, "deltaX": 0.00, "deltaY": 0.00, "deltaZ": -0.04, "ipdMeters": 0.065},
        {"id": "aft_mid",    "weight": 0.08, "deltaX": -0.04, "deltaY": 0.00, "deltaZ": 0.00, "ipdMeters": 0.065},
        {"id": "fore_mid",   "weight": 0.08, "deltaX": 0.04, "deltaY": 0.00, "deltaZ": 0.00, "ipdMeters": 0.065},
        {"id": "left_mid",   "weight": 0.06, "deltaX": 0.00, "deltaY": 0.02, "deltaZ": 0.00, "ipdMeters": 0.065},
        {"id": "right_mid",  "weight": 0.06, "deltaX": 0.00, "deltaY": -0.02, "deltaZ": 0.00, "ipdMeters": 0.065}
    ]
    eye_model = EyePopulationModel(
        model_id="RepresentativePopulationApproximation",
        source="J941_CONCEPT_APPROXIMATION",
        samples_config=samples,
        nominal_eye=vehicle.driver_eye_nominal
    )
    engine = BinocularOcclusionEngine(vehicle, eye_model)
    sampler = AdaptiveRiskSurfaceSampler(vehicle, eye_model)

    dt = 0.05
    # 7 required target scenarios from Section 7.3:
    # S1, S2 R7, S4, S7, Shadow A, Shadow D, Shadow F
    scenarios = [
        {"id": "S1_10kmh", "type": "STRAIGHT", "speed": 10.0, "dur": 4.0, "origin": Point3(14.0, 0.0, 0.5), "lx": 3.0, "wy": 8.0, "curv": 0.0},
        {"id": "S2_R7m_10kmh", "type": "RADIUS", "radius": 7.0, "speed": 10.0, "dur": 4.0, "origin": Point3(7.0, -4.5, 0.5), "lx": 4.0, "wy": 5.0, "curv": 1.0/7.0},
        {"id": "S4_NORMAL", "type": "STOP_GO", "speed": 20.0, "accel": "NORMAL", "dur": 5.0, "origin": Point3(10.0, 0.0, 0.5), "lx": 3.0, "wy": 6.0, "curv": 0.0},
        {"id": "S7", "type": "STRAIGHT", "speed": 15.0, "dur": 4.0, "origin": Point3(12.0, 0.0, 0.5), "lx": 3.0, "wy": 4.0, "curv": 0.0},
        {"id": "SHADOW_A", "type": "SINGLE_POSE", "speed": 12.0, "dur": 0.9, "origin": Point3(5.0, -7.0, 0.5), "lx": 3.0, "wy": 6.0, "curv": 1.0/8.0},
        {"id": "SHADOW_D", "type": "SINGLE_POSE", "speed": 15.0, "dur": 0.72, "origin": Point3(8.0, -8.0, 0.5), "lx": 3.0, "wy": 6.0, "curv": 1.0/10.0},
        {"id": "SHADOW_F", "type": "SINGLE_POSE", "speed": 10.0, "dur": 1.44, "origin": Point3(6.0, 0.0, 0.5), "lx": 4.0, "wy": 16.0, "curv": 0.0}
    ]

    rows = []
    print("=== Adaptive Risk-Surface Sampling Verification (0.5m vs Adaptive vs 0.25m vs 0.125m) ===")

    for sc in scenarios:
        if sc["type"] == "STRAIGHT":
            traj = VehicleTrajectoryGenerator.generate_straight(speed_kmh=sc["speed"], duration_sec=sc["dur"], dt=dt)
        elif sc["type"] == "RADIUS":
            traj = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=sc["radius"], speed_kmh=sc["speed"], is_right=True, duration_sec=sc["dur"], dt=dt)
        elif sc["type"] == "STOP_GO":
            traj = VehicleTrajectoryGenerator.generate_stop_and_go(target_speed_kmh=sc["speed"], accel_profile=sc["accel"], duration_sec=sc["dur"], dt=dt)
        elif sc["type"] == "SINGLE_POSE":
            # Static snapshot trajectory over estimated passage duration
            steps = max(2, int(round(sc["dur"] / dt)))
            traj = [VehiclePose(0.0, 0.0, 0.0, sc["speed"] / 3.6, i * dt) for i in range(steps)]

        surf = RiskSurface(
            surface_id=f"surf_{sc['id']}",
            surface_type="CROSSWALK",
            origin=sc["origin"],
            length_x=sc["lx"],
            width_y=sc["wy"],
            height_z=0.0
        )

        # 1. Fixed 0.5m
        t0 = time.perf_counter()
        pts_050 = surf.sample_points(0.5)
        m_050 = engine.evaluate_trajectory(sc["id"], pts_050, traj, dt=dt)
        ms_050 = (time.perf_counter() - t0) * 1000.0

        # 2. Fixed 0.25m
        t0 = time.perf_counter()
        pts_025 = surf.sample_points(0.25)
        m_025 = engine.evaluate_trajectory(sc["id"], pts_025, traj, dt=dt)
        ms_025 = (time.perf_counter() - t0) * 1000.0

        # 3. Fixed 0.125m (Ground truth reference)
        t0 = time.perf_counter()
        pts_0125 = surf.sample_points(0.125)
        m_0125 = engine.evaluate_trajectory(sc["id"], pts_0125, traj, dt=dt)
        ms_0125 = (time.perf_counter() - t0) * 1000.0

        # 4. Adaptive (0.5m base + 0.25m boundary refinement)
        t0 = time.perf_counter()
        pts_adap, adap_stats = sampler.sample_adaptive_surface(surf, engine, traj, turn_curvature_inv_m=sc["curv"])
        m_adap = engine.evaluate_trajectory(sc["id"], pts_adap, traj, dt=dt)
        ms_adap = (time.perf_counter() - t0) * 1000.0

        def rel_err_pct(val: float, ref: float) -> float:
            if abs(ref) < 1e-12:
                return 0.0
            return ((val - ref) / ref) * 100.0

        err_peak_050 = rel_err_pct(m_050.peak_possible_ratio, m_0125.peak_possible_ratio)
        err_peak_025 = rel_err_pct(m_025.peak_possible_ratio, m_0125.peak_possible_ratio)
        err_peak_adap = rel_err_pct(m_adap.peak_possible_ratio, m_0125.peak_possible_ratio)

        err_int_050 = rel_err_pct(m_050.exposure_integral_possible, m_0125.exposure_integral_possible)
        err_int_025 = rel_err_pct(m_025.exposure_integral_possible, m_0125.exposure_integral_possible)
        err_int_adap = rel_err_pct(m_adap.exposure_integral_possible, m_0125.exposure_integral_possible)

        sample_reduction_vs_025_pct = (1.0 - (len(pts_adap) / len(pts_025))) * 100.0
        target_met = (abs(err_peak_adap) <= 5.0) and (abs(err_int_adap) <= 5.0) and (len(pts_adap) < len(pts_025))

        print(
            f"[{sc['id']}] Pts(0.5/Adap/0.25/0.125)={len(pts_050)}/{len(pts_adap)}/{len(pts_025)}/{len(pts_0125)} "
            f"(-{sample_reduction_vs_025_pct:.1f}% vs 0.25m) | "
            f"PeakErr(0.5/Adap)={err_peak_050:+.2f}%/{err_peak_adap:+.2f}% | "
            f"IntErr(0.5/Adap)={err_int_050:+.2f}%/{err_int_adap:+.2f}% | PASS={target_met}"
        )
        assert target_met, f"Target not met for {sc['id']}"

        rows.append({
            "scenario_id": sc["id"],
            "pts_050m": len(pts_050),
            "pts_adaptive": len(pts_adap),
            "pts_025m": len(pts_025),
            "pts_0125m": len(pts_0125),
            "refined_base_cells": adap_stats["refined_cell_count"],
            "total_base_cells": adap_stats["base_cell_count"],
            "sample_reduction_vs_025m_pct": f"{sample_reduction_vs_025_pct:.2f}%",
            "weight_sum_adaptive": f"{adap_stats['weight_sum']:.10f}",
            "peak_050m": f"{m_050.peak_possible_ratio:.6f}",
            "peak_adaptive": f"{m_adap.peak_possible_ratio:.6f}",
            "peak_025m": f"{m_025.peak_possible_ratio:.6f}",
            "peak_0125m": f"{m_0125.peak_possible_ratio:.6f}",
            "rel_err_peak_050_vs_0125_pct": f"{err_peak_050:+.2f}%",
            "rel_err_peak_adaptive_vs_0125_pct": f"{err_peak_adap:+.2f}%",
            "rel_err_peak_025_vs_0125_pct": f"{err_peak_025:+.2f}%",
            "integral_050m": f"{m_050.exposure_integral_possible:.6f}",
            "integral_adaptive": f"{m_adap.exposure_integral_possible:.6f}",
            "integral_025m": f"{m_025.exposure_integral_possible:.6f}",
            "integral_0125m": f"{m_0125.exposure_integral_possible:.6f}",
            "rel_err_integral_050_vs_0125_pct": f"{err_int_050:+.2f}%",
            "rel_err_integral_adaptive_vs_0125_pct": f"{err_int_adap:+.2f}%",
            "rel_err_integral_025_vs_0125_pct": f"{err_int_025:+.2f}%",
            "duration_050m_sec": f"{m_050.max_per_point_continuous_duration:.2f}",
            "duration_adaptive_sec": f"{m_adap.max_per_point_continuous_duration:.2f}",
            "duration_025m_sec": f"{m_025.max_per_point_continuous_duration:.2f}",
            "duration_0125m_sec": f"{m_0125.max_per_point_continuous_duration:.2f}",
            "runtime_050m_ms": f"{ms_050:.2f}",
            "runtime_adaptive_ms": f"{ms_adap:.2f}",
            "runtime_025m_ms": f"{ms_025:.2f}",
            "runtime_0125m_ms": f"{ms_0125:.2f}",
            "target_met": target_met
        })

    csv_path = os.path.join(audit_dir, "adaptive_sampling_validation.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Saved adaptive_sampling_validation.csv ({os.path.getsize(csv_path)} bytes)")

if __name__ == "__main__":
    run_adaptive_validation()
