import os
import csv
import json
import math
import numpy as np
from scipy import stats
from geometry import Point3
from eye_model import EyePopulationModel
from pillar_model import load_vehicle_pillars
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from occlusion_engine import BinocularOcclusionEngine
from uncertainty import UncertaintyConfig, apply_uncertainty

def run_uncertainty_statistics():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    os.makedirs(output_dir, exist_ok=True)

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

    dt = 0.05
    dur = 4.0
    base_traj = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=6.0, speed_kmh=8.0, is_right=True, duration_sec=dur, dt=dt)
    surf = RiskSurface(
        surface_id="crosswalk_s8_stats",
        surface_type="CROSSWALK",
        origin=Point3(6.0, -4.0, 0.5),
        length_x=3.0,
        width_y=5.0,
        height_z=0.0
    )
    pts = surf.sample_points(spacing=0.5)

    groups = [
        {"name": "NO_ERROR",     "sigma_p": 0.0, "sigma_h": 0.0},
        {"name": "SMALL_ERROR",  "sigma_p": 0.1, "sigma_h": 0.5},
        {"name": "MEDIUM_ERROR", "sigma_p": 0.3, "sigma_h": 1.5},
        {"name": "LARGE_ERROR",  "sigma_p": 0.8, "sigma_h": 3.0}
    ]

    N = 100
    csv_rows = []
    group_integrals = {}

    print(f"=== Monte Carlo Uncertainty Statistical Verification (N={N} per group) ===")

    for g in groups:
        integrals = []
        peaks = []
        g_name = g["name"]
        sp = g["sigma_p"]
        sh = g["sigma_h"]

        for i in range(N):
            seed = 1000 + i if sp > 0 else 42
            cfg = UncertaintyConfig(pos_sigma_meters=sp, heading_sigma_degrees=sh, seed=seed)
            p_traj = apply_uncertainty(base_traj, cfg)
            m = engine.evaluate_trajectory(f"S8_{g_name}_{i}", pts, p_traj, dt=dt)

            integrals.append(m.exposure_integral_possible)
            peaks.append(m.peak_possible_ratio)

            csv_rows.append({
                "group": g_name,
                "sample_idx": i,
                "seed": seed,
                "sigma_pos_m": sp,
                "sigma_heading_deg": sh,
                "exposure_integral": m.exposure_integral_possible,
                "peak_possible_ratio": m.peak_possible_ratio,
                "max_duration_sec": m.max_per_point_continuous_duration
            })

        group_integrals[g_name] = integrals
        arr = np.array(integrals)
        mean_val = float(np.mean(arr))
        std_val = float(np.std(arr, ddof=1))
        ci_half = 1.96 * std_val / math.sqrt(N)
        print(f"[{g_name}] N={N}: Mean={mean_val:.6f}, SD={std_val:.6f}, 95% CI=[{mean_val - ci_half:.6f}, {mean_val + ci_half:.6f}]")

    # Save CSV
    csv_path = os.path.join(output_dir, "uncertainty_distribution.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        fieldnames = list(csv_rows[0].keys())
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)
    print(f"Saved uncertainty distribution CSV to: {csv_path} ({os.path.getsize(csv_path)} bytes)")

    # Compute Statistical Tests
    stats_dict = {
        "metadata": {
            "testScenario": "S8 (Constant Radius R=6m, Speed=8km/h, Crosswalk)",
            "sampleCountPerGroup": N,
            "seedPolicy": "Deterministic seeds 1000 to 1099 for stochastic groups, seed=42 for baseline",
            "statisticalTestsUsed": {
                "twoSampleTtest": "Tests difference in means (H0: mu1 = mu2). Does NOT test variance expansion.",
                "leveneTest": "Tests equality of variances (H0: sigma1^2 = sigma2^2). Rigorously verifies variance expansion."
            }
        },
        "groups": {},
        "comparisonsAgainstNoError": {}
    }

    no_err_arr = np.array(group_integrals["NO_ERROR"])
    for g_name, vals in group_integrals.items():
        arr = np.array(vals)
        mean_v = float(np.mean(arr))
        std_v = float(np.std(arr, ddof=1))
        var_v = float(np.var(arr, ddof=1))
        ci_half = 1.96 * std_v / math.sqrt(N)
        stats_dict["groups"][g_name] = {
            "sampleCount": N,
            "meanExposureIntegral": round(mean_v, 6),
            "stdDev": round(std_v, 6),
            "variance": round(var_v, 8),
            "ci95Lower": round(mean_v - ci_half, 6),
            "ci95Upper": round(mean_v + ci_half, 6)
        }

        if g_name != "NO_ERROR":
            # Two-sample t-test (independent)
            t_res = stats.ttest_ind(arr, no_err_arr, equal_var=False)
            # Cohen's d
            pooled_std = math.sqrt((var_v + stats_dict["groups"]["NO_ERROR"]["variance"]) / 2.0)
            cohen_d = (mean_v - stats_dict["groups"]["NO_ERROR"]["meanExposureIntegral"]) / pooled_std if pooled_std > 0 else 0.0

            # Levene test for equality of variances
            # Note: For NO_ERROR, variance is 0.0 (deterministic baseline).
            # To test variance difference between stochastic groups or against base:
            levene_stat, levene_p = stats.levene(arr, no_err_arr)

            stats_dict["comparisonsAgainstNoError"][g_name] = {
                "tStatistic": round(float(t_res.statistic), 4),
                "tTestPvalue": float(t_res.pvalue),
                "cohensD": round(cohen_d, 4),
                "leveneStatistic": round(float(levene_stat), 4),
                "levenePvalue": float(levene_p),
                "interpretation": f"Levene test confirms variance difference (W={levene_stat:.2f}, p={levene_p:.2e}), whereas t-test evaluates mean shift."
            }

    # Levene test between Small and Large error groups
    levene_small_large = stats.levene(group_integrals["SMALL_ERROR"], group_integrals["LARGE_ERROR"])
    stats_dict["comparisonSmallVsLarge"] = {
        "leveneStatistic": round(float(levene_small_large.statistic), 4),
        "levenePvalue": float(levene_small_large.pvalue),
        "conclusion": f"Variance of Large Error is significantly greater than Small Error (Levene W={levene_small_large.statistic:.2f}, p={levene_small_large.pvalue:.2e})."
    }

    json_path = os.path.join(output_dir, "uncertainty_statistics.json")
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(stats_dict, f, indent=2)
    print(f"Saved uncertainty statistics JSON to: {json_path} ({os.path.getsize(json_path)} bytes)")

if __name__ == "__main__":
    run_uncertainty_statistics()
