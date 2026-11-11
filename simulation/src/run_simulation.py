import os
import sys
import json
import csv
import math
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from geometry import Point3
from eye_model import load_eye_model
from pillar_model import load_vehicle_pillars, VehiclePillarGeometry, PillarVolume
from risk_sampling import create_risk_surface_from_config, RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator, VehiclePose
from uncertainty import UncertaintyConfig, apply_uncertainty
from occlusion_engine import BinocularOcclusionEngine, SimulationTrajectoryMetrics

WATERMARK_TEXT = "Simulation with synthetic representative parameters. Not validated against a production vehicle."

def add_watermark(fig):
    fig.text(
        0.5, 0.97, WATERMARK_TEXT,
        fontsize=9, color='red', alpha=0.8,
        ha='center', va='top',
        bbox=dict(boxstyle='round,pad=0.3', facecolor='white', edgecolor='red', alpha=0.7)
    )

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    config_dir = os.path.join(base_dir, 'config')
    output_dir = os.path.join(base_dir, 'output')
    fig_dir = os.path.join(output_dir, 'figures')
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(fig_dir, exist_ok=True)

    # 1. Load vehicle configurations
    sedan = load_vehicle_pillars(os.path.join(config_dir, 'representative_sedan_v0.json'))
    suv = load_vehicle_pillars(os.path.join(config_dir, 'representative_suv_v0.json'))
    mpv = load_vehicle_pillars(os.path.join(config_dir, 'representative_mpv_v0.json'))

    # 2. Load eye configurations
    eye_cfg_path = os.path.join(config_dir, 'synthetic_eye_model_v0.json')
    eye_center = load_eye_model(eye_cfg_path, 'SyntheticCenterProfile', sedan.driver_eye_nominal)
    eye_pop = load_eye_model(eye_cfg_path, 'RepresentativePopulationApproximation', sedan.driver_eye_nominal)

    # 3. Load scenarios
    with open(os.path.join(config_dir, 'scenarios.json'), 'r', encoding='utf-8') as f:
        scenarios_data = json.load(f)["scenarios"]

    engine_baseline = BinocularOcclusionEngine(sedan, eye_pop)

    baseline_runs = []
    golden_json_dict = {"scenarios": {}, "metadata": {"generator": "Python Reference Engine v1.0", "standard": "Pure Domain Math"}}

    print("--- Executing Baseline Scenarios S0..S8 ---")

    for sc in scenarios_data:
        sc_id = sc["id"]
        sc_name = sc["name"]
        t_type = sc["trajectoryType"]
        dt = sc.get("timeStepSeconds", 0.05)
        dur = sc.get("durationSeconds", 4.0)

        risk_surf = create_risk_surface_from_config(sc["riskSurface"])
        risk_samples = risk_surf.sample_points(spacing=0.5)

        if sc_id == "S0":
            poses = VehicleTrajectoryGenerator.generate_straight(speed_kmh=sc["speedKmh"], duration_sec=dur, dt=dt)
            metrics = engine_baseline.evaluate_trajectory(sc_id, risk_samples, poses, dt=dt)
            baseline_runs.append((sc_id, sc_name, metrics))

        elif sc_id == "S1":
            for spd in sc["speedsKmh"]:
                poses = VehicleTrajectoryGenerator.generate_straight(speed_kmh=spd, duration_sec=dur, dt=dt)
                m = engine_baseline.evaluate_trajectory(f"{sc_id}_{int(spd)}kmh", risk_samples, poses, dt=dt)
                baseline_runs.append((f"{sc_id}_{int(spd)}kmh", f"{sc_name} ({spd}km/h)", m))

        elif sc_id == "S2":
            for r in sc["radiiMeters"]:
                for spd in sc["speedsKmh"]:
                    run_id = f"{sc_id}_R{int(r)}m_{int(spd)}kmh"
                    poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=r, speed_kmh=spd, is_right=True, duration_sec=dur, dt=dt)
                    m = engine_baseline.evaluate_trajectory(run_id, risk_samples, poses, dt=dt)
                    baseline_runs.append((run_id, f"{sc_name} (R={r}m, {spd}km/h)", m))

        elif sc_id == "S3":
            r = sc["radiiMeters"][0]
            spd = sc["speedsKmh"][0]
            poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=r, speed_kmh=spd, is_right=False, duration_sec=dur, dt=dt)
            m = engine_baseline.evaluate_trajectory(sc_id, risk_samples, poses, dt=dt)
            baseline_runs.append((sc_id, sc_name, m))

        elif sc_id == "S4":
            for prof in sc["accelProfiles"]:
                run_id = f"{sc_id}_{prof}"
                poses = VehicleTrajectoryGenerator.generate_stop_and_go(target_speed_kmh=sc["targetSpeedKmh"], accel_profile=prof, duration_sec=dur, dt=dt)
                m = engine_baseline.evaluate_trajectory(run_id, risk_samples, poses, dt=dt)
                baseline_runs.append((run_id, f"{sc_name} ({prof})", m))

        elif sc_id == "S5":
            poses = VehicleTrajectoryGenerator.generate_stop_and_go(target_speed_kmh=sc["targetSpeedKmh"], accel_profile=sc["accelProfile"], duration_sec=dur, dt=dt, turn_radius_m=sc["radiusMeters"], is_right=True)
            m = engine_baseline.evaluate_trajectory(sc_id, risk_samples, poses, dt=dt)
            baseline_runs.append((sc_id, sc_name, m))

        elif sc_id == "S6":
            poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=sc["radiusMeters"], speed_kmh=sc["speedKmh"], is_right=True, duration_sec=dur, dt=dt)
            m = engine_baseline.evaluate_trajectory(sc_id, risk_samples, poses, dt=dt)
            baseline_runs.append((sc_id, sc_name, m))

        elif sc_id == "S7":
            poses = VehicleTrajectoryGenerator.generate_straight(speed_kmh=sc["speedKmh"], duration_sec=dur, dt=dt)
            m = engine_baseline.evaluate_trajectory(sc_id, risk_samples, poses, dt=dt)
            baseline_runs.append((sc_id, sc_name, m))

        elif sc_id == "S8":
            base_poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=sc["radiusMeters"], speed_kmh=sc["speedKmh"], is_right=True, duration_sec=dur, dt=dt)
            u_configs = [
                {"name": "NO_ERROR", "posSigma": 0.0, "headingSigma": 0.0, "delayMs": 0, "dropout": 0.0},
                {"name": "SMALL_ERROR", "posSigma": 0.1, "headingSigma": 0.5, "delayMs": 20, "dropout": 0.01},
                {"name": "MEDIUM_ERROR", "posSigma": 0.3, "headingSigma": 1.5, "delayMs": 50, "dropout": 0.03},
                {"name": "LARGE_ERROR", "posSigma": 0.8, "headingSigma": 3.0, "delayMs": 100, "dropout": 0.05}
            ]
            for u in u_configs:
                u_name = u["name"]
                run_id = f"{sc_id}_{u_name}"
                u_cfg = UncertaintyConfig(
                    pos_sigma_meters=u["posSigma"],
                    heading_sigma_degrees=u["headingSigma"],
                    timestamp_delay_millis=u["delayMs"],
                    dropout_probability=u["dropout"],
                    seed=2026
                )
                pert_poses = apply_uncertainty(base_poses, u_cfg)
                m = engine_baseline.evaluate_trajectory(run_id, risk_samples, pert_poses, dt=dt)
                baseline_runs.append((run_id, f"{sc_name} ({u_name})", m))

    # 4. Save baseline_results.csv and baseline_results.json
    baseline_csv_path = os.path.join(output_dir, "baseline_results.csv")
    with open(baseline_csv_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "run_id", "scenario_name", "profile_id", "eye_model_id",
            "total_duration_sec", "timestep_sec", "peak_possible_ratio",
            "peak_universal_ratio", "max_per_point_continuous_sec",
            "any_point_continuous_sec", "exposure_integral_possible",
            "exposure_integral_universal", "sample_count",
            "was_duration_truncated", "simulation_end_time", "trajectory_end_reason"
        ])
        for run_id, sc_name, m in baseline_runs:
            s_dict = m.to_summary_dict()
            writer.writerow([
                run_id, sc_name, s_dict["profileId"], s_dict["eyeModelId"],
                s_dict["totalDurationSec"], s_dict["timestepSec"],
                s_dict["peakPossibleRatio"], s_dict["peakUniversalRatio"],
                s_dict["maxPerPointContinuousDuration"], s_dict["anyPointContinuousDuration"],
                s_dict["exposureIntegralPossible"], s_dict["exposureIntegralUniversal"],
                m.timesteps[0].sample_count if m.timesteps else 0,
                s_dict["wasDurationTruncated"], s_dict["simulationEndTime"],
                s_dict["trajectoryEndReason"]
            ])
            golden_json_dict["scenarios"][run_id] = {
                "summary": s_dict,
                "timesteps": [t.to_dict() for t in m.timesteps]
            }

    golden_json_path = os.path.join(output_dir, "baseline_results.json")
    with open(golden_json_path, "w", encoding="utf-8") as f:
        json.dump(golden_json_dict, f, indent=2, ensure_ascii=False)

    print(f"Saved {baseline_csv_path} and {golden_json_path}")

    # 5. Sensitivity Analysis
    print("--- Running Sensitivity Analysis ---")
    sensitivity_rows = []
    sens_surf = RiskSurface("sens_cw", "CROSSWALK", Point3(7.0, -3.5, 0.0), length_x=4.0, width_y=5.0)

    # 5.1 Spacing Sensitivity (0.25m, 0.5m, 1.0m)
    for sp in [0.25, 0.5, 1.0]:
        pts = sens_surf.sample_points(spacing=sp)
        poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=8.0, speed_kmh=10.0, is_right=True, duration_sec=4.0, dt=0.05)
        m = engine_baseline.evaluate_trajectory(f"Sens_Spacing_{sp}m", pts, poses, dt=0.05)
        sensitivity_rows.append({
            "parameter_type": "SPACING_METERS",
            "parameter_value": str(sp),
            "sample_count": len(pts),
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration": m.max_per_point_continuous_duration,
            "exposure_integral": m.exposure_integral_possible
        })

    # 5.2 Vehicle Profile Sensitivity (Sedan vs SUV vs MPV)
    for v_prof in [sedan, suv, mpv]:
        pts = sens_surf.sample_points(spacing=0.5)
        eye_v = load_eye_model(eye_cfg_path, 'SyntheticCenterProfile', v_prof.driver_eye_nominal)
        eng_v = BinocularOcclusionEngine(v_prof, eye_v)
        poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=8.0, speed_kmh=10.0, is_right=True, duration_sec=4.0, dt=0.05)
        m = eng_v.evaluate_trajectory(f"Sens_Profile_{v_prof.profile_id}", pts, poses, dt=0.05)
        sensitivity_rows.append({
            "parameter_type": "VEHICLE_PROFILE",
            "parameter_value": v_prof.profile_id,
            "sample_count": len(pts),
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration": m.max_per_point_continuous_duration,
            "exposure_integral": m.exposure_integral_possible
        })

    # 5.3 Timestep Sensitivity (0.025s, 0.05s, 0.10s)
    for t_step in [0.025, 0.05, 0.10]:
        pts = sens_surf.sample_points(spacing=0.5)
        poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=8.0, speed_kmh=10.0, is_right=True, duration_sec=4.0, dt=t_step)
        m = engine_baseline.evaluate_trajectory(f"Sens_Timestep_{t_step}s", pts, poses, dt=t_step)
        sensitivity_rows.append({
            "parameter_type": "TIMESTEP_SECONDS",
            "parameter_value": str(t_step),
            "sample_count": len(pts),
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration": m.max_per_point_continuous_duration,
            "exposure_integral": m.exposure_integral_possible
        })

    sens_csv_path = os.path.join(output_dir, "sensitivity_results.csv")
    with open(sens_csv_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "parameter_type", "parameter_value", "sample_count",
            "peak_possible_ratio", "max_per_point_duration", "exposure_integral"
        ])
        writer.writeheader()
        writer.writerows(sensitivity_rows)

    print(f"Saved {sens_csv_path}")

    # 6. Generate Figures
    print("--- Generating Output Figures ---")

    # Figure 1: 3D Layout
    fig = plt.figure(figsize=(10, 7))
    ax = fig.add_subplot(111, projection='3d')
    # Plot sedan pillars
    for p, col, lbl in [(sedan.left_pillar, 'blue', 'Left Pillar'), (sedan.right_pillar, 'cyan', 'Right Pillar')]:
        vx = [v.x for v in p.vertices]
        vy = [v.y for v in p.vertices]
        vz = [v.z for v in p.vertices]
        ax.scatter(vx, vy, vz, color=col, s=30, label=lbl)
    # Plot eye position
    ax.scatter([sedan.driver_eye_nominal.x], [sedan.driver_eye_nominal.y], [sedan.driver_eye_nominal.z], color='green', s=80, marker='^', label='Driver Eye Nominal')
    # Plot sample risk surface
    cw_pts = sens_surf.sample_points(spacing=0.5)
    rx = [p.position.x for p in cw_pts]
    ry = [p.position.y for p in cw_pts]
    rz = [p.position.z for p in cw_pts]
    ax.scatter(rx, ry, rz, color='orange', s=15, alpha=0.6, label='Risk Surface Samples')
    ax.set_xlabel('X: Forward (m)')
    ax.set_ylabel('Y: Left (m)')
    ax.set_zlabel('Z: Upward (m)')
    ax.set_title('Representative 3D Binocular Geometry & Risk Surface Layout')
    ax.legend(loc='upper right')
    add_watermark(fig)
    fig.tight_layout()
    fig.savefig(os.path.join(fig_dir, "fig1_3d_geometry_layout.png"), dpi=150)
    plt.close(fig)

    # Figure 2: R(t) Time-series for S1, S2, S4, S6
    fig, ax = plt.subplots(figsize=(10, 5))
    selected_runs = ["S1_10kmh", "S2_R7m_10kmh", "S4_NORMAL", "S6"]
    for run_id in selected_runs:
        match = [m for rid, name, m in baseline_runs if rid == run_id]
        if match:
            m = match[0]
            times = [t.time_sec for t in m.timesteps]
            ratios = [t.possible_weighted_ratio for t in m.timesteps]
            ax.plot(times, ratios, label=f"{run_id} (R={m.peak_possible_ratio:.2f})", linewidth=1.8)
    ax.set_xlabel("Simulation Time (seconds)")
    ax.set_ylabel("Weighted Occluded Sample Ratio R(t)")
    ax.set_title("Time-series of A-Pillar Binocular Occlusion Ratio R(t)")
    ax.grid(True, linestyle='--', alpha=0.5)
    ax.legend()
    add_watermark(fig)
    fig.tight_layout()
    fig.savefig(os.path.join(fig_dir, "fig2_occlusion_ratio_timeseries.png"), dpi=150)
    plt.close(fig)

    # Figure 3: Speed comparison (5, 10, 30 km/h) on S1
    fig, ax = plt.subplots(figsize=(8, 4.5))
    s1_runs = ["S1_5kmh", "S1_10kmh", "S1_30kmh"]
    s1_speeds = [5, 10, 30]
    s1_durs = []
    for rid in s1_runs:
        match = [m for r, n, m in baseline_runs if r == rid]
        s1_durs.append(match[0].any_point_continuous_duration if match else 0.0)
    ax.bar([str(s) + " km/h" for s in s1_speeds], s1_durs, color=['steelblue', 'cornflowerblue', 'lightsteelblue'], edgecolor='navy', width=0.5)
    ax.set_ylabel("Any-Point Continuous Duration (seconds)")
    ax.set_xlabel("Vehicle Speed")
    ax.set_title("Effect of Approach Speed on Continuous Occlusion Duration (Scenario S1)")
    ax.grid(axis='y', linestyle='--', alpha=0.5)
    add_watermark(fig)
    fig.tight_layout()
    fig.savefig(os.path.join(fig_dir, "fig3_speed_vs_duration.png"), dpi=150)
    plt.close(fig)

    # Figure 4: Turn Radius comparison (R=5, 7, 10m at 10km/h) on S2
    fig, ax = plt.subplots(figsize=(8, 4.5))
    s2_radii = [5, 7, 10]
    s2_peaks = []
    for r in s2_radii:
        rid = f"S2_R{r}m_10kmh"
        match = [m for r_, n, m in baseline_runs if r_ == rid]
        s2_peaks.append(match[0].peak_possible_ratio if match else 0.0)
    ax.plot([f"R={r}m" for r in s2_radii], s2_peaks, marker='o', color='crimson', linewidth=2, markersize=8)
    ax.set_ylabel("Peak Possible Occlusion Ratio")
    ax.set_xlabel("Turn Radius")
    ax.set_title("Peak Occlusion Ratio across Turn Radii at 10 km/h (Scenario S2)")
    ax.grid(True, linestyle='--', alpha=0.5)
    add_watermark(fig)
    fig.tight_layout()
    fig.savefig(os.path.join(fig_dir, "fig4_turn_radius_vs_peak_ratio.png"), dpi=150)
    plt.close(fig)

    # Figure 5: Uncertainty Sensitivity (S8)
    fig, ax = plt.subplots(figsize=(8, 4.5))
    u_names = ["NO_ERROR", "SMALL_ERROR", "MEDIUM_ERROR", "LARGE_ERROR"]
    u_integrals = []
    for uname in u_names:
        rid = f"S8_{uname}"
        match = [m for r_, n, m in baseline_runs if r_ == rid]
        u_integrals.append(match[0].exposure_integral_possible if match else 0.0)
    ax.plot(u_names, u_integrals, marker='s', color='darkgreen', linewidth=2, markersize=8)
    ax.set_ylabel("Occlusion Exposure Integral E (s)")
    ax.set_xlabel("Sensor Uncertainty Level")
    ax.set_title("Exposure Integral Robustness under Location & Heading Uncertainty (Scenario S8)")
    ax.grid(True, linestyle='--', alpha=0.5)
    add_watermark(fig)
    fig.tight_layout()
    fig.savefig(os.path.join(fig_dir, "fig5_uncertainty_exposure_integral.png"), dpi=150)
    plt.close(fig)

    print("All figures generated successfully!")

if __name__ == "__main__":
    main()
