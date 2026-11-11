import os
import csv
from geometry import Point3
from eye_model import EyePopulationModel, WeightedEyePair, EyePair
from pillar_model import PillarVolume, VehiclePillarGeometry
from occlusion_engine import BinocularOcclusionEngine
from vehicle_trajectory import VehiclePose

def make_box_pillar(side: str, x_min: float, x_max: float, y_min: float, y_max: float, z_min: float, z_max: float) -> PillarVolume:
    verts = [
        Point3(x_min, y_max, z_min), Point3(x_min, y_min, z_min),
        Point3(x_max, y_min, z_min), Point3(x_max, y_max, z_min),
        Point3(x_min, y_max, z_max), Point3(x_min, y_min, z_max),
        Point3(x_max, y_min, z_max), Point3(x_max, y_max, z_max)
    ]
    return PillarVolume(side=side, vertices=verts, source="SYNTHETIC_TEST_ONLY")

def run_universal_validation():
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    os.makedirs(output_dir, exist_ok=True)

    # 9-sample population model
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
        nominal_eye=Point3(1.30, 0.0, 1.20)
    )

    pose = VehiclePose(x=0.0, y=0.0, heading=0.0, speed_mps=0.0, time_sec=0.0)
    target_pt = Point3(8.0, 0.0, 1.20)

    results = []
    print("=== Universal / Possible / Monocular Occlusion Verification ===")

    # Case U0: No Occlusion (Pillar far away)
    p_u0_left = make_box_pillar("LEFT", 2.0, 2.2, 3.0, 3.2, 0.5, 2.0)
    p_u0_right = make_box_pillar("RIGHT", 2.0, 2.2, -3.2, -3.0, 0.5, 2.0)
    v_u0 = VehiclePillarGeometry("U0_Profile", "SYNTHETIC", p_u0_left, p_u0_right, Point3(1.30, 0.0, 1.20), 2.8, 1.6)
    eng_u0 = BinocularOcclusionEngine(v_u0, eye_model)
    res_u0 = eng_u0.evaluate_risk_point_at_pose(target_pt, pose)

    pass_u0 = (res_u0["isUniversalOccluded"] == False and res_u0["isPossibleOccluded"] == False)
    print(f"[{'PASS' if pass_u0 else 'FAIL'}] U0 (No Occlusion): Universal={res_u0['isUniversalOccluded']}, Possible={res_u0['isPossibleOccluded']}, Coverage={res_u0['coverage']}")
    assert pass_u0
    results.append({"case_id": "U0_NO_OCCLUSION", "description": "Pillars outside ray paths", "is_universal": res_u0["isUniversalOccluded"], "is_possible": res_u0["isPossibleOccluded"], "coverage": res_u0["coverage"], "passed": pass_u0})

    # Case U1: Universal Forced (Massive central pillar blocking all rays)
    p_u1_left = make_box_pillar("LEFT", 2.0, 2.4, -0.4, 0.4, 0.5, 2.0)
    p_u1_right = make_box_pillar("RIGHT", 2.0, 2.4, -0.4, 0.4, 0.5, 2.0)
    v_u1 = VehiclePillarGeometry("U1_Profile", "SYNTHETIC", p_u1_left, p_u1_right, Point3(1.30, 0.0, 1.20), 2.8, 1.6)
    eng_u1 = BinocularOcclusionEngine(v_u1, eye_model)
    res_u1 = eng_u1.evaluate_risk_point_at_pose(target_pt, pose)

    pass_u1 = (res_u1["isUniversalOccluded"] == True and res_u1["isPossibleOccluded"] == True and res_u1["coverage"] == 1.0)
    print(f"[{'PASS' if pass_u1 else 'FAIL'}] U1 (Universal Forced): Universal={res_u1['isUniversalOccluded']}, Possible={res_u1['isPossibleOccluded']}, Coverage={res_u1['coverage']}")
    assert pass_u1
    results.append({"case_id": "U1_UNIVERSAL_FORCED", "description": "Wide pillar blocking all 9 eye pairs", "is_universal": res_u1["isUniversalOccluded"], "is_possible": res_u1["isPossibleOccluded"], "coverage": res_u1["coverage"], "passed": pass_u1})

    # Case U2: Possible Only (Pillar blocks central eye, but displaced eyes peek around)
    p_u2_left = make_box_pillar("LEFT", 2.0, 2.2, -0.045, 0.045, 0.5, 2.0)
    p_u2_right = make_box_pillar("RIGHT", 2.0, 2.2, 2.0, 2.2, 0.5, 2.0)
    v_u2 = VehiclePillarGeometry("U2_Profile", "SYNTHETIC", p_u2_left, p_u2_right, Point3(1.30, 0.0, 1.20), 2.8, 1.6)
    eng_u2 = BinocularOcclusionEngine(v_u2, eye_model)
    res_u2 = eng_u2.evaluate_risk_point_at_pose(target_pt, pose)

    pass_u2 = (res_u2["isUniversalOccluded"] == False and res_u2["isPossibleOccluded"] == True and 0.0 < res_u2["coverage"] < 1.0)
    print(f"[{'PASS' if pass_u2 else 'FAIL'}] U2 (Possible Only): Universal={res_u2['isUniversalOccluded']}, Possible={res_u2['isPossibleOccluded']}, Coverage={res_u2['coverage']}")
    assert pass_u2
    results.append({"case_id": "U2_POSSIBLE_ONLY", "description": "Narrow pillar blocking some eyes but not all", "is_universal": res_u2["isUniversalOccluded"], "is_possible": res_u2["isPossibleOccluded"], "coverage": res_u2["coverage"], "passed": pass_u2})

    # Case U3: Monocular Only (Pillar blocks ONLY left eye, right eye has clear line of sight)
    # Left eye is at Y ~ +0.0325, Right eye is at Y ~ -0.0325
    p_u3_left = make_box_pillar("LEFT", 2.0, 2.2, 0.01, 0.06, 0.5, 2.0)
    p_u3_right = make_box_pillar("RIGHT", 2.0, 2.2, 2.0, 2.2, 0.5, 2.0)
    v_u3 = VehiclePillarGeometry("U3_Profile", "SYNTHETIC", p_u3_left, p_u3_right, Point3(1.30, 0.0, 1.20), 2.8, 1.6)
    eng_u3 = BinocularOcclusionEngine(v_u3, eye_model)
    res_u3 = eng_u3.evaluate_risk_point_at_pose(target_pt, pose)

    pass_u3 = (res_u3["isPossibleOccluded"] == False and res_u3["leftMonocularOccluded"] == True and res_u3["coverage"] == 0.0)
    print(f"[{'PASS' if pass_u3 else 'FAIL'}] U3 (Monocular Only): BinocularPossible={res_u3['isPossibleOccluded']}, LeftMonocular={res_u3['leftMonocularOccluded']}, Coverage={res_u3['coverage']}")
    assert pass_u3
    results.append({"case_id": "U3_MONOCULAR_ONLY", "description": "Blocks left eye only, right eye clear", "is_universal": res_u3["isUniversalOccluded"], "is_possible": res_u3["isPossibleOccluded"], "coverage": res_u3["coverage"], "passed": pass_u3})

    # Save CSV
    csv_path = os.path.join(output_dir, "universal_forced_cases.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=["case_id", "description", "is_universal", "is_possible", "coverage", "passed"])
        writer.writeheader()
        writer.writerows(results)
    print(f"Saved universal forced cases CSV to: {csv_path} ({os.path.getsize(csv_path)} bytes)")

if __name__ == "__main__":
    run_universal_validation()
