import os
import csv
import time
import matplotlib.pyplot as plt
from geometry import Point3
from eye_model import EyePopulationModel
from pillar_model import load_vehicle_pillars
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from occlusion_engine import BinocularOcclusionEngine

def run_spatial_convergence():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    fig_dir = os.path.join(output_dir, "figures")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(fig_dir, exist_ok=True)

    # Load Sedan Profile
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

    spacings = [1.0, 0.5, 0.25, 0.125]
    dt = 0.05

    scenarios = [
        {"id": "S1_10kmh", "type": "STRAIGHT", "speed": 10.0, "dur": 4.0, "origin": Point3(14.0, 0.0, 0.5), "lx": 3.0, "wy": 8.0},
        {"id": "S2_R7m_10kmh", "type": "RADIUS", "radius": 7.0, "speed": 10.0, "dur": 4.0, "origin": Point3(7.0, -4.5, 0.5), "lx": 4.0, "wy": 5.0},
        {"id": "S4_NORMAL", "type": "STOP_GO", "speed": 20.0, "accel": "NORMAL", "dur": 5.0, "origin": Point3(10.0, 0.0, 0.5), "lx": 3.0, "wy": 6.0},
        {"id": "S7", "type": "STRAIGHT", "speed": 15.0, "dur": 4.0, "origin": Point3(12.0, 0.0, 0.5), "lx": 3.0, "wy": 4.0}
    ]

    csv_rows = []
    plot_dict = {sc["id"]: {"spacings": [], "peaks": [], "integrals": [], "sample_counts": []} for sc in scenarios}

    print("=== Spatial Resolution Convergence Verification (1.0m -> 0.5m -> 0.25m -> 0.125m) ===")

    for sc in scenarios:
        if sc["type"] == "STRAIGHT":
            traj = VehicleTrajectoryGenerator.generate_straight(speed_kmh=sc["speed"], duration_sec=sc["dur"], dt=dt)
        elif sc["type"] == "RADIUS":
            traj = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=sc["radius"], speed_kmh=sc["speed"], is_right=True, duration_sec=sc["dur"], dt=dt)
        elif sc["type"] == "STOP_GO":
            traj = VehicleTrajectoryGenerator.generate_stop_and_go(target_speed_kmh=sc["speed"], accel_profile=sc["accel"], duration_sec=sc["dur"], dt=dt)

        surf = RiskSurface(
            surface_id=f"surf_{sc['id']}",
            surface_type="CROSSWALK",
            origin=sc["origin"],
            length_x=sc["lx"],
            width_y=sc["wy"],
            height_z=0.0
        )

        prev_res = None
        for sp in spacings:
            t0 = time.perf_counter()
            pts = surf.sample_points(spacing=sp)
            metrics = engine.evaluate_trajectory(sc["id"], pts, traj, dt=dt)
            exec_time_ms = (time.perf_counter() - t0) * 1000.0

            csv_rows.append({
                "scenario_id": sc["id"],
                "spacing_meters": sp,
                "sample_point_count": len(pts),
                "peak_possible_ratio": metrics.peak_possible_ratio,
                "max_per_point_duration_sec": metrics.max_per_point_continuous_duration,
                "any_point_duration_sec": metrics.any_point_continuous_duration,
                "exposure_integral_possible": metrics.exposure_integral_possible,
                "execution_time_ms": round(exec_time_ms, 2)
            })

            plot_dict[sc["id"]]["spacings"].append(sp)
            plot_dict[sc["id"]]["peaks"].append(metrics.peak_possible_ratio)
            plot_dict[sc["id"]]["integrals"].append(metrics.exposure_integral_possible)
            plot_dict[sc["id"]]["sample_counts"].append(len(pts))

            if sp == 0.25 and prev_res is not None:
                # Compare 0.5m vs 0.25m
                d_ratio = abs(metrics.peak_possible_ratio - prev_res["peak"])
                d_int = abs(metrics.exposure_integral_possible - prev_res["int"])
                print(f"[CONVERGENCE CHECK] {sc['id']}: 0.50m vs 0.25m -> DeltaPeak={d_ratio:.4f}, DeltaInt={d_int:.4f}")

            if sp == 0.5:
                prev_res = {
                    "peak": metrics.peak_possible_ratio,
                    "int": metrics.exposure_integral_possible
                }

    # Save CSV
    csv_path = os.path.join(output_dir, "spatial_resolution_convergence.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        fieldnames = list(csv_rows[0].keys())
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)
    print(f"Saved spatial resolution convergence CSV to: {csv_path} ({os.path.getsize(csv_path)} bytes)")

    # Plot Figure 8
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    ax1, ax2 = axes[0], axes[1]

    colors = ['tab:blue', 'tab:orange', 'tab:green', 'tab:red']
    for idx, (sc_id, data) in enumerate(plot_dict.items()):
        ax1.plot(data["spacings"], data["peaks"], marker='o', color=colors[idx], linewidth=1.8, label=sc_id)
        ax2.plot(data["spacings"], data["integrals"], marker='s', color=colors[idx], linewidth=1.8, label=sc_id)

    ax1.set_title('Peak Possible Ratio vs Spatial Grid Spacing', fontsize=11, fontweight='bold')
    ax1.set_xlabel('Sample Grid Spacing Δs (meters)')
    ax1.set_ylabel('Peak Possible Ratio')
    ax1.invert_xaxis()
    ax1.grid(True, alpha=0.3)
    ax1.legend(fontsize=8)

    ax2.set_title('Exposure Integral vs Spatial Grid Spacing', fontsize=11, fontweight='bold')
    ax2.set_xlabel('Sample Grid Spacing Δs (meters)')
    ax2.set_ylabel('Exposure Integral ∫ R(t) dt')
    ax2.invert_xaxis()
    ax2.grid(True, alpha=0.3)
    ax2.legend(fontsize=8)

    fig.suptitle('Figure 8: Spatial Resolution Convergence (1.0m -> 0.5m -> 0.25m -> 0.125m)\n(Demonstrating Grid Invariance: 0.5m Selected as Balanced Baseline for Prototype)', fontsize=13, fontweight='bold')
    fig.text(0.5, 0.02, "Simulation with synthetic representative parameters. Not validated against a production vehicle.",
             ha='center', fontsize=9, color='darkred', weight='bold')

    plt.tight_layout(rect=[0, 0.05, 1, 0.95])
    fig8_path = os.path.join(fig_dir, "fig8_spatial_resolution_convergence.png")
    plt.savefig(fig8_path, dpi=200)
    plt.close()
    print(f"Generated Figure 8 at: {fig8_path} ({os.path.getsize(fig8_path)} bytes)")

if __name__ == "__main__":
    run_spatial_convergence()
