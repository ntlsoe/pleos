import os
import csv
import math
import matplotlib.pyplot as plt
from geometry import Point3
from eye_model import EyePopulationModel
from pillar_model import load_vehicle_pillars
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from occlusion_engine import BinocularOcclusionEngine

def run_symmetry_validation():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    fig_dir = os.path.join(output_dir, "figures")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(fig_dir, exist_ok=True)

    # 1. Load Symmetric Vehicle Profile
    profile_path = os.path.join(config_dir, "symmetric_test_profile.json")
    vehicle = load_vehicle_pillars(profile_path)

    # Symmetric eye model centered at Y=0
    eye_model = EyePopulationModel(
        model_id="SymmetricEyePairModel",
        source="SYNTHETIC_TEST_ONLY",
        nominal_eye=vehicle.driver_eye_nominal,
        samples_config=[
            {"id": "center", "weight": 1.0, "deltaX": 0.0, "deltaY": 0.0, "deltaZ": 0.0, "ipdMeters": 0.065}
        ]
    )

    engine = BinocularOcclusionEngine(vehicle, eye_model)

    test_configs = [
        {"name": "R7m_10kmh", "radius": 7.0, "speed": 10.0, "cw_x": 7.0, "cw_y": 4.5, "dur": 4.0},
        {"name": "R10m_10kmh", "radius": 10.0, "speed": 10.0, "cw_x": 10.0, "cw_y": 5.5, "dur": 4.0}
    ]

    csv_rows = []
    plot_data = []

    print("=== Left-Right Symmetry Mathematical Verification ===")

    for cfg in test_configs:
        r = cfg["radius"]
        v = cfg["speed"]
        dur = cfg["dur"]
        cw_x = cfg["cw_x"]
        cw_y = cfg["cw_y"]
        dt = 0.05

        # Right Turn Setup
        traj_r = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=r, speed_kmh=v, is_right=True, duration_sec=dur, dt=dt)
        surf_r = RiskSurface(
            surface_id=f"crosswalk_right_{cfg['name']}",
            surface_type="CROSSWALK",
            origin=Point3(cw_x, -cw_y, 0.5),
            length_x=4.0,
            width_y=5.0,
            height_z=0.0
        )
        pts_r = surf_r.sample_points(spacing=0.5)
        metrics_r = engine.evaluate_trajectory(f"SYM_RIGHT_{cfg['name']}", pts_r, traj_r, dt=dt)

        # Left Turn Setup (Mirrored)
        traj_l = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=r, speed_kmh=v, is_right=False, duration_sec=dur, dt=dt)
        surf_l = RiskSurface(
            surface_id=f"crosswalk_left_{cfg['name']}",
            surface_type="CROSSWALK",
            origin=Point3(cw_x, cw_y, 0.5),
            length_x=4.0,
            width_y=5.0,
            height_z=0.0
        )
        pts_l = surf_l.sample_points(spacing=0.5)
        metrics_l = engine.evaluate_trajectory(f"SYM_LEFT_{cfg['name']}", pts_l, traj_l, dt=dt)

        # Evaluate symmetry differences
        diff_peak_poss = abs(metrics_r.peak_possible_ratio - metrics_l.peak_possible_ratio)
        diff_peak_univ = abs(metrics_r.peak_universal_ratio - metrics_l.peak_universal_ratio)
        diff_max_t = abs(metrics_r.max_per_point_continuous_duration - metrics_l.max_per_point_continuous_duration)
        diff_any_t = abs(metrics_r.any_point_continuous_duration - metrics_l.any_point_continuous_duration)
        diff_integral = abs(metrics_r.exposure_integral_possible - metrics_l.exposure_integral_possible)

        # Tolerance checks per Section 4:
        # Ratio <= 1e-6, Time <= 1 timestep (0.05), Integral <= 1e-5
        passed = (diff_peak_poss <= 1e-6 and diff_peak_univ <= 1e-6 and
                  diff_max_t <= dt and diff_any_t <= dt and diff_integral <= 1e-5)

        print(f"[{'PASS' if passed else 'FAIL'}] {cfg['name']}:")
        print(f"  Right Turn: PeakPoss={metrics_r.peak_possible_ratio:.6f}, MaxT={metrics_r.max_per_point_continuous_duration:.2f}s, AnyT={metrics_r.any_point_continuous_duration:.2f}s, Integral={metrics_r.exposure_integral_possible:.6f}")
        print(f"  Left  Turn: PeakPoss={metrics_l.peak_possible_ratio:.6f}, MaxT={metrics_l.max_per_point_continuous_duration:.2f}s, AnyT={metrics_l.any_point_continuous_duration:.2f}s, Integral={metrics_l.exposure_integral_possible:.6f}")
        print(f"  Delta: RatioDiff={diff_peak_poss:.2e}, TimeDiff={diff_max_t:.2e}s, IntDiff={diff_integral:.2e}")
        assert passed, f"Symmetry tolerance exceeded for {cfg['name']}"

        csv_rows.append({
            "test_case": cfg["name"],
            "radius_m": r,
            "speed_kmh": v,
            "right_peak_possible": metrics_r.peak_possible_ratio,
            "left_peak_possible": metrics_l.peak_possible_ratio,
            "peak_ratio_diff": diff_peak_poss,
            "right_max_duration_sec": metrics_r.max_per_point_continuous_duration,
            "left_max_duration_sec": metrics_l.max_per_point_continuous_duration,
            "max_duration_diff_sec": diff_max_t,
            "right_any_duration_sec": metrics_r.any_point_continuous_duration,
            "left_any_duration_sec": metrics_l.any_point_continuous_duration,
            "any_duration_diff_sec": diff_any_t,
            "right_exposure_integral": metrics_r.exposure_integral_possible,
            "left_exposure_integral": metrics_l.exposure_integral_possible,
            "exposure_integral_diff": diff_integral,
            "symmetry_verified": passed
        })

        plot_data.append({
            "name": cfg["name"],
            "r_times": [t.time_sec for t in metrics_r.timesteps],
            "r_ratios": [t.possible_weighted_ratio for t in metrics_r.timesteps],
            "l_times": [t.time_sec for t in metrics_l.timesteps],
            "l_ratios": [t.possible_weighted_ratio for t in metrics_l.timesteps]
        })

    # Write CSV
    csv_path = os.path.join(output_dir, "symmetry_validation.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        fieldnames = list(csv_rows[0].keys())
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)
    print(f"Saved symmetry validation CSV to: {csv_path} ({os.path.getsize(csv_path)} bytes)")

    # Plot Figure 6
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    for i, d in enumerate(plot_data):
        ax = axes[i]
        ax.plot(d["r_times"], d["r_ratios"], 'b-', linewidth=2.5, label='Right Turn (Mirrored -Y)')
        ax.plot(d["l_times"], d["l_ratios"], 'r--', linewidth=2.0, label='Left Turn (Mirrored +Y)')
        ax.set_title(f"Symmetry Test: {d['name']} (Radius & Speed Identical)", fontsize=11, fontweight='bold')
        ax.set_xlabel('Time (s)')
        ax.set_ylabel('Possible Occlusion Ratio')
        ax.grid(True, alpha=0.3)
        ax.legend()
        ax.set_ylim([-0.01, max(max(d["r_ratios"]), max(d["l_ratios"])) * 1.25 + 0.02])

    fig.suptitle('Figure 6: Left-Right Mirrored Trajectory Symmetry Verification\n(Symmetric Profile, Centerline Eye, Machine-Precision Equivalence)', fontsize=13, fontweight='bold')
    fig.text(0.5, 0.02, "Simulation with synthetic representative parameters. Not validated against a production vehicle.",
             ha='center', fontsize=9, color='darkred', weight='bold')

    plt.tight_layout(rect=[0, 0.05, 1, 0.95])
    fig6_path = os.path.join(fig_dir, "fig6_symmetry_validation.png")
    plt.savefig(fig6_path, dpi=200)
    plt.close()
    print(f"Generated Figure 6 at: {fig6_path} ({os.path.getsize(fig6_path)} bytes)")

if __name__ == "__main__":
    run_symmetry_validation()
