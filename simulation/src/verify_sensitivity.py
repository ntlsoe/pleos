import os
import csv
import math
import matplotlib.pyplot as plt
from scipy.stats import linregress
from geometry import Point3
from eye_model import EyePopulationModel
from pillar_model import load_vehicle_pillars, PillarVolume, VehiclePillarGeometry
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from occlusion_engine import BinocularOcclusionEngine

def make_scaled_pillar(base_pillar: PillarVolume, width_scale: float) -> PillarVolume:
    # Scale width around local centerline of the 4 base points and 4 top points
    v = base_pillar.vertices
    # base center
    bc_y = sum(p.y for p in v[:4]) / 4.0
    tc_y = sum(p.y for p in v[4:]) / 4.0

    new_v = []
    for i, p in enumerate(v):
        c_y = bc_y if i < 4 else tc_y
        new_y = c_y + (p.y - c_y) * width_scale
        new_v.append(Point3(p.x, new_y, p.z))

    return PillarVolume(side=base_pillar.side, vertices=new_v, source=base_pillar.source)

def make_angled_pillar(base_pillar: PillarVolume, target_rake_deg: float) -> PillarVolume:
    v = base_pillar.vertices
    # Current base and top average Z and X
    base_x = sum(p.x for p in v[:4]) / 4.0
    base_z = sum(p.z for p in v[:4]) / 4.0
    top_z = sum(p.z for p in v[4:]) / 4.0

    dz = top_z - base_z
    # Desired top X: base_x - dz * tan(rake)
    desired_top_x = base_x - dz * math.tan(math.radians(target_rake_deg))
    current_top_x = sum(p.x for p in v[4:]) / 4.0
    shift_x = desired_top_x - current_top_x

    new_v = []
    for i, p in enumerate(v):
        if i < 4:
            new_v.append(p)
        else:
            new_v.append(Point3(p.x + shift_x, p.y, p.z))

    return PillarVolume(side=base_pillar.side, vertices=new_v, source=base_pillar.source)

def run_sensitivity_validation():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    fig_dir = os.path.join(output_dir, "figures")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(fig_dir, exist_ok=True)

    profile_path = os.path.join(config_dir, "representative_sedan_v0.json")
    base_geom = load_vehicle_pillars(profile_path)

    # Standard scenario: S1 10km/h
    dt = 0.05
    traj = VehicleTrajectoryGenerator.generate_straight(speed_kmh=10.0, duration_sec=4.0, dt=dt)
    surf = RiskSurface(
        surface_id="surf_s1_sens",
        surface_type="CROSSWALK",
        origin=Point3(14.0, 0.0, 0.5),
        length_x=3.0,
        width_y=8.0,
        height_z=0.0
    )
    pts = surf.sample_points(spacing=0.5)

    base_eye_model = EyePopulationModel(
        model_id="RepresentativePopulationApproximation",
        source="J941_CONCEPT_APPROXIMATION",
        nominal_eye=base_geom.driver_eye_nominal,
        samples_config=[
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
    )

    print("=== Systematic Sensitivity Analysis (4 Factors, >=5 levels each) ===")

    # 1. Pillar Width Sensitivity
    # Nominal width is 80mm
    widths_mm = [60.0, 70.0, 80.0, 90.0, 100.0, 110.0]
    width_rows = []
    width_peaks = []
    for w in widths_mm:
        scale = w / 80.0
        lp = make_scaled_pillar(base_geom.left_pillar, scale)
        rp = make_scaled_pillar(base_geom.right_pillar, scale)
        v = VehiclePillarGeometry("Sedan_VarWidth", "SYNTHETIC", lp, rp, base_geom.driver_eye_nominal, base_geom.wheelbase, base_geom.track_width)
        eng = BinocularOcclusionEngine(v, base_eye_model)
        m = eng.evaluate_trajectory("S1_sens_width", pts, traj, dt=dt)
        width_rows.append({
            "pillar_width_mm": w,
            "width_scale": round(scale, 4),
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration_sec": m.max_per_point_continuous_duration,
            "exposure_integral_possible": m.exposure_integral_possible
        })
        width_peaks.append(m.peak_possible_ratio)

    slope_w, intercept_w, r_val_w, p_val_w, std_err_w = linregress(widths_mm, width_peaks)
    for r in width_rows:
        pred = slope_w * r["pillar_width_mm"] + intercept_w
        r["linear_residual"] = round(r["peak_possible_ratio"] - pred, 6)

    csv_w_path = os.path.join(output_dir, "sensitivity_pillar_width.csv")
    with open(csv_w_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(width_rows[0].keys()))
        writer.writeheader()
        writer.writerows(width_rows)
    print(f"Pillar Width: Slope={slope_w*10.0*100.0:.2f}%p per 10mm, R2={r_val_w**2:.4f} -> {csv_w_path}")

    # 2. Pillar Rake Angle Sensitivity
    # Rake angles 24 to 40 deg
    angles_deg = [24.0, 28.0, 32.0, 36.0, 40.0]
    angle_rows = []
    angle_peaks = []
    for a in angles_deg:
        lp = make_angled_pillar(base_geom.left_pillar, a)
        rp = make_angled_pillar(base_geom.right_pillar, a)
        v = VehiclePillarGeometry("Sedan_VarAngle", "SYNTHETIC", lp, rp, base_geom.driver_eye_nominal, base_geom.wheelbase, base_geom.track_width)
        eng = BinocularOcclusionEngine(v, base_eye_model)
        m = eng.evaluate_trajectory("S1_sens_angle", pts, traj, dt=dt)
        angle_rows.append({
            "pillar_rake_angle_deg": a,
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration_sec": m.max_per_point_continuous_duration,
            "exposure_integral_possible": m.exposure_integral_possible
        })
        angle_peaks.append(m.peak_possible_ratio)

    slope_a, intercept_a, r_val_a, p_val_a, std_err_a = linregress(angles_deg, angle_peaks)
    for r in angle_rows:
        pred = slope_a * r["pillar_rake_angle_deg"] + intercept_a
        r["linear_residual"] = round(r["peak_possible_ratio"] - pred, 6)

    csv_a_path = os.path.join(output_dir, "sensitivity_pillar_angle.csv")
    with open(csv_a_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(angle_rows[0].keys()))
        writer.writeheader()
        writer.writerows(angle_rows)
    print(f"Pillar Angle: Slope={slope_a*100.0:.3f}%p per deg, R2={r_val_a**2:.4f} -> {csv_a_path}")

    # 3. IPD Sensitivity
    ipds_mm = [55.0, 60.0, 65.0, 70.0, 75.0]
    ipd_rows = []
    ipd_peaks = []
    for ipd in ipds_mm:
        ipd_m = ipd / 1000.0
        em = EyePopulationModel(
            model_id=f"Eye_IPD_{ipd}",
            source="J941_CONCEPT_APPROXIMATION",
            nominal_eye=base_geom.driver_eye_nominal,
            samples_config=[
                {"id": "center_mid", "weight": 1.0, "deltaX": 0.00, "deltaY": 0.00, "deltaZ": 0.00, "ipdMeters": ipd_m}
            ]
        )
        eng = BinocularOcclusionEngine(base_geom, em)
        m = eng.evaluate_trajectory("S1_sens_ipd", pts, traj, dt=dt)
        ipd_rows.append({
            "ipd_mm": ipd,
            "ipd_meters": ipd_m,
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration_sec": m.max_per_point_continuous_duration,
            "exposure_integral_possible": m.exposure_integral_possible
        })
        ipd_peaks.append(m.peak_possible_ratio)

    slope_i, intercept_i, r_val_i, p_val_i, std_err_i = linregress(ipds_mm, ipd_peaks)
    for r in ipd_rows:
        pred = slope_i * r["ipd_mm"] + intercept_i
        r["linear_residual"] = round(r["peak_possible_ratio"] - pred, 6)

    csv_i_path = os.path.join(output_dir, "sensitivity_ipd.csv")
    with open(csv_i_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(ipd_rows[0].keys()))
        writer.writeheader()
        writer.writerows(ipd_rows)
    print(f"IPD: Slope={slope_i*10.0*100.0:.3f}%p per 10mm, R2={r_val_i**2:.4f} -> {csv_i_path}")

    # 4. Eye-to-Pillar Longitudinal Distance Sensitivity
    # Cowl base is at X=2.10m. Eye nominal is at X=1.30m (distance = 800mm).
    # Eye X in [1.60, 1.50, 1.40, 1.30, 1.20]m -> Distances in [500, 600, 700, 800, 900]mm
    distances_mm = [500.0, 600.0, 700.0, 800.0, 900.0]
    dist_rows = []
    dist_peaks = []
    cowl_x = 2.10

    for d_mm in distances_mm:
        eye_x = cowl_x - (d_mm / 1000.0)
        em = EyePopulationModel(
            model_id=f"Eye_Dist_{d_mm}",
            source="J941_CONCEPT_APPROXIMATION",
            nominal_eye=Point3(eye_x, base_geom.driver_eye_nominal.y, base_geom.driver_eye_nominal.z),
            samples_config=[
                {"id": "center_mid", "weight": 1.0, "deltaX": 0.00, "deltaY": 0.00, "deltaZ": 0.00, "ipdMeters": 0.065}
            ]
        )
        eng = BinocularOcclusionEngine(base_geom, em)
        m = eng.evaluate_trajectory("S1_sens_dist", pts, traj, dt=dt)
        dist_rows.append({
            "eye_pillar_distance_mm": d_mm,
            "eye_x_meters": round(eye_x, 4),
            "peak_possible_ratio": m.peak_possible_ratio,
            "max_per_point_duration_sec": m.max_per_point_continuous_duration,
            "exposure_integral_possible": m.exposure_integral_possible
        })
        dist_peaks.append(m.peak_possible_ratio)

    slope_d, intercept_d, r_val_d, p_val_d, std_err_d = linregress(distances_mm, dist_peaks)
    for r in dist_rows:
        pred = slope_d * r["eye_pillar_distance_mm"] + intercept_d
        r["linear_residual"] = round(r["peak_possible_ratio"] - pred, 6)

    csv_d_path = os.path.join(output_dir, "sensitivity_eye_pillar_distance.csv")
    with open(csv_d_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(dist_rows[0].keys()))
        writer.writeheader()
        writer.writerows(dist_rows)
    print(f"Eye Distance: Slope={slope_d*100.0*100.0:.3f}%p per 100mm, R2={r_val_d**2:.4f} -> {csv_d_path}")

    # Plot Figure 10: 4 Subplots
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))

    # Subplot 1: Width
    ax = axes[0, 0]
    ax.scatter(widths_mm, width_peaks, color='tab:blue', s=60, zorder=3, label='Observed (N=6)')
    w_line = [slope_w * x + intercept_w for x in widths_mm]
    ax.plot(widths_mm, w_line, 'b--', label=f'Fit: slope={slope_w*1000:.2f}%/10mm (R²={r_val_w**2:.4f})')
    ax.set_title('(A) Pillar Width Sensitivity', fontsize=11, fontweight='bold')
    ax.set_xlabel('Pillar Width (mm)')
    ax.set_ylabel('Peak Possible Ratio')
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=9)

    # Subplot 2: Rake Angle
    ax = axes[0, 1]
    ax.scatter(angles_deg, angle_peaks, color='tab:orange', s=60, zorder=3, label='Observed (N=5)')
    a_line = [slope_a * x + intercept_a for x in angles_deg]
    ax.plot(angles_deg, a_line, 'r--', label=f'Fit: slope={slope_a*100:.3f}%/deg (R²={r_val_a**2:.4f})')
    ax.set_title('(B) Pillar Rake Angle Sensitivity', fontsize=11, fontweight='bold')
    ax.set_xlabel('Windshield Rake Angle (deg from vert)')
    ax.set_ylabel('Peak Possible Ratio')
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=9)

    # Subplot 3: IPD
    ax = axes[1, 0]
    ax.scatter(ipds_mm, ipd_peaks, color='tab:green', s=60, zorder=3, label='Observed (N=5)')
    i_line = [slope_i * x + intercept_i for x in ipds_mm]
    ax.plot(ipds_mm, i_line, 'g--', label=f'Fit: slope={slope_i*1000:.3f}%/10mm (R²={r_val_i**2:.4f})')
    ax.set_title('(C) Interpupillary Distance (IPD) Sensitivity', fontsize=11, fontweight='bold')
    ax.set_xlabel('Interpupillary Distance (mm)')
    ax.set_ylabel('Peak Possible Ratio')
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=9)

    # Subplot 4: Eye-Pillar Distance
    ax = axes[1, 1]
    ax.scatter(distances_mm, dist_peaks, color='tab:purple', s=60, zorder=3, label='Observed (N=5)')
    d_line = [slope_d * x + intercept_d for x in distances_mm]
    ax.plot(distances_mm, d_line, 'm--', label=f'Fit: slope={slope_d*10000:.3f}%/100mm (R²={r_val_d**2:.4f})')
    ax.set_title('(D) Eye-to-Pillar Distance Sensitivity', fontsize=11, fontweight='bold')
    ax.set_xlabel('Eye-to-Cowl Distance (mm)')
    ax.set_ylabel('Peak Possible Ratio')
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=9)

    fig.suptitle('Figure 10: Geometric Parameter Sensitivity Validation\n(Linear Regressions over 4 Primary Factors, N≥5 per Factor)', fontsize=13, fontweight='bold')
    fig.text(0.5, 0.02, "Simulation with synthetic representative parameters. Not validated against a production vehicle.",
             ha='center', fontsize=9, color='darkred', weight='bold')

    plt.tight_layout(rect=[0, 0.04, 1, 0.96])
    fig10_path = os.path.join(fig_dir, "fig10_sensitivity_validation.png")
    plt.savefig(fig10_path, dpi=200)
    plt.close()
    print(f"Generated Figure 10 at: {fig10_path} ({os.path.getsize(fig10_path)} bytes)")

if __name__ == "__main__":
    run_sensitivity_validation()
