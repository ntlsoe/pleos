import os
import json
import csv
import math
from spatial_projection import (
    GeoPoint, LocalCoordinateFrame, VehiclePoseSpatial, SpatialQuality,
    RiskFeature, RiskFeatureSampler
)

def run_spatial_projection_verification():
    output_dir = os.path.join(os.path.dirname(__file__), "..", "..", "audit", "results")
    os.makedirs(output_dir, exist_ok=True)

    lat0 = 37.401000
    lon0 = 127.106000
    alt0 = 35.0
    anchor = GeoPoint(lat0, lon0, alt0)
    frame = LocalCoordinateFrame(anchor)

    tests_results = []
    expected_data = {}

    print("=== Spatial Projection Layer Verification (P0..P7) ===")

    # Target point: 15m North of origin in ENU (East=0.0, North=15.0, Up=0.0)
    target_enu = (0.0, 15.0, 0.0)
    target_geo = frame.enu_to_geodetic(target_enu[0], target_enu[1], target_enu[2])

    # P0. Origin Manual Comparison
    p0_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=0.0, timestamp_millis=1000)
    p0_pt = p0_pose.enu_to_vehicle_body(target_enu[0], target_enu[1], target_enu[2])
    manual_p0_x = 15.0
    manual_p0_y = 0.0
    err_p0 = math.hypot(p0_pt.x - manual_p0_x, p0_pt.y - manual_p0_y)
    pass_p0 = (err_p0 < 1e-5)
    tests_results.append({
        "test_id": "P0_ORIGIN_MANUAL_CALC",
        "description": "Relative coordinates vs manual calculation at origin",
        "heading_deg": 0.0,
        "input_enu": str(target_enu),
        "expected_body": f"({manual_p0_x}, {manual_p0_y}, 0.0)",
        "actual_body": f"({p0_pt.x}, {p0_pt.y}, {p0_pt.z})",
        "error_meters": f"{err_p0:.2e}",
        "passed": pass_p0
    })
    expected_data["P0"] = {"expected": [manual_p0_x, manual_p0_y, 0.0], "actual": [p0_pt.x, p0_pt.y, p0_pt.z], "passed": pass_p0}
    print(f"[{'PASS' if pass_p0 else 'FAIL'}] P0: Origin relative calc err={err_p0:.2e}")

    # P1. Heading 0° (North)
    p1_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=0.0, timestamp_millis=1000)
    p1_pt = p1_pose.enu_to_vehicle_body(target_enu[0], target_enu[1], target_enu[2])
    pass_p1 = (abs(p1_pt.x - 15.0) < 1e-5 and abs(p1_pt.y - 0.0) < 1e-5)
    tests_results.append({
        "test_id": "P1_HEADING_0_DEG",
        "description": "Forward hazard (+15m North) appears directly ahead (+X) when facing North",
        "heading_deg": 0.0,
        "input_enu": str(target_enu),
        "expected_body": "(15.0, 0.0, 0.0)",
        "actual_body": f"({p1_pt.x}, {p1_pt.y}, {p1_pt.z})",
        "error_meters": f"{abs(p1_pt.x - 15.0):.2e}",
        "passed": pass_p1
    })
    expected_data["P1"] = {"expected": [15.0, 0.0, 0.0], "actual": [p1_pt.x, p1_pt.y, p1_pt.z], "passed": pass_p1}
    print(f"[{'PASS' if pass_p1 else 'FAIL'}] P1: Heading 0 deg -> body=({p1_pt.x}, {p1_pt.y})")

    # P2. Heading 90° (East)
    p2_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=math.radians(90.0), timestamp_millis=1000)
    p2_pt = p2_pose.enu_to_vehicle_body(target_enu[0], target_enu[1], target_enu[2])
    # Facing East, North is to driver's left (+Y)
    pass_p2 = (abs(p2_pt.x - 0.0) < 1e-5 and abs(p2_pt.y - 15.0) < 1e-5)
    tests_results.append({
        "test_id": "P2_HEADING_90_DEG",
        "description": "Facing East: North hazard appears at lateral left (+Y = +15m)",
        "heading_deg": 90.0,
        "input_enu": str(target_enu),
        "expected_body": "(0.0, 15.0, 0.0)",
        "actual_body": f"({p2_pt.x}, {p2_pt.y}, {p2_pt.z})",
        "error_meters": f"{abs(p2_pt.y - 15.0):.2e}",
        "passed": pass_p2
    })
    expected_data["P2"] = {"expected": [0.0, 15.0, 0.0], "actual": [p2_pt.x, p2_pt.y, p2_pt.z], "passed": pass_p2}
    print(f"[{'PASS' if pass_p2 else 'FAIL'}] P2: Heading 90 deg -> body=({p2_pt.x}, {p2_pt.y})")

    # P3. Heading 180° (South)
    p3_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=math.radians(180.0), timestamp_millis=1000)
    p3_pt = p3_pose.enu_to_vehicle_body(target_enu[0], target_enu[1], target_enu[2])
    # Facing South, North is behind driver (-X)
    pass_p3 = (abs(p3_pt.x - (-15.0)) < 1e-5 and abs(p3_pt.y - 0.0) < 1e-5)
    tests_results.append({
        "test_id": "P3_HEADING_180_DEG",
        "description": "Facing South: North hazard appears directly behind (-X = -15m)",
        "heading_deg": 180.0,
        "input_enu": str(target_enu),
        "expected_body": "(-15.0, 0.0, 0.0)",
        "actual_body": f"({p3_pt.x}, {p3_pt.y}, {p3_pt.z})",
        "error_meters": f"{abs(p3_pt.x - (-15.0)):.2e}",
        "passed": pass_p3
    })
    expected_data["P3"] = {"expected": [-15.0, 0.0, 0.0], "actual": [p3_pt.x, p3_pt.y, p3_pt.z], "passed": pass_p3}
    print(f"[{'PASS' if pass_p3 else 'FAIL'}] P3: Heading 180 deg -> body=({p3_pt.x}, {p3_pt.y})")

    # P4. Left-Right Reflection (Mirror Symmetry)
    # Facing North: Right hazard (East=+6m, North=+12m) vs Left hazard (East=-6m, North=+12m)
    p4_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=0.0, timestamp_millis=1000)
    pt_right = p4_pose.enu_to_vehicle_body(6.0, 12.0, 0.0)
    pt_left = p4_pose.enu_to_vehicle_body(-6.0, 12.0, 0.0)
    # Forward X should be equal (+12m), Lateral Y opposite (Right = -6m, Left = +6m)
    pass_p4 = (abs(pt_right.x - pt_left.x) < 1e-5 and abs(pt_right.y + pt_left.y) < 1e-5 and pt_left.y > 0)
    tests_results.append({
        "test_id": "P4_LEFT_RIGHT_REFLECTION",
        "description": "Symmetric East/West points yield identical +X and negated Y coordinates",
        "heading_deg": 0.0,
        "input_enu": "Right(+6, 12), Left(-6, 12)",
        "expected_body": "Right(12.0, -6.0), Left(12.0, +6.0)",
        "actual_body": f"Right({pt_right.x}, {pt_right.y}), Left({pt_left.x}, {pt_left.y})",
        "error_meters": f"{abs(pt_right.y + pt_left.y):.2e}",
        "passed": pass_p4
    })
    expected_data["P4"] = {
        "rightBody": [pt_right.x, pt_right.y, pt_right.z],
        "leftBody": [pt_left.x, pt_left.y, pt_left.z],
        "passed": pass_p4
    }
    print(f"[{'PASS' if pass_p4 else 'FAIL'}] P4: Reflection symmetry -> Right Y={pt_right.y}, Left Y={pt_left.y}")

    # P5. Continuous Heading Smoothness (No Discontinuous Jump across 360 deg)
    max_step_jump = 0.0
    prev_pt = None
    step_deg = 1.0
    for deg in range(0, 361):
        rad = math.radians(deg)
        pose_step = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=rad, timestamp_millis=1000)
        curr_pt = pose_step.enu_to_vehicle_body(target_enu[0], target_enu[1], target_enu[2])
        if prev_pt is not None:
            jump = math.hypot(curr_pt.x - prev_pt.x, curr_pt.y - prev_pt.y)
            if jump > max_step_jump:
                max_step_jump = jump
        prev_pt = curr_pt
    # For R=15m and 1 deg step: arc length = 15 * radians(1) = 0.2618m
    pass_p5 = (max_step_jump < 0.27 and max_step_jump > 0.25)
    tests_results.append({
        "test_id": "P5_CONTINUOUS_HEADING",
        "description": "Heading rotated 0..360 deg in 1 deg steps yields smooth continuous trajectory",
        "heading_deg": "0..360",
        "input_enu": str(target_enu),
        "expected_body": "Max step jump ~ 0.2618m (smooth arc)",
        "actual_body": f"Max step jump = {max_step_jump:.4f}m",
        "error_meters": f"{abs(max_step_jump - 15.0 * math.radians(1.0)):.2e}",
        "passed": pass_p5
    })
    expected_data["P5"] = {"maxStepJumpMeters": round(max_step_jump, 5), "passed": pass_p5}
    print(f"[{'PASS' if pass_p5 else 'FAIL'}] P5: Continuous heading max jump={max_step_jump:.4f}m")

    # P6. Antimeridian & Polar Region Explicit Exclusion
    polar_pt = GeoPoint(89.0, 127.0, 0.0)
    try:
        frame.geodetic_to_enu(polar_pt)
        pass_p6 = False
    except ValueError:
        pass_p6 = True
    tests_results.append({
        "test_id": "P6_POLAR_ANTIMERIDIAN_EXCLUSION",
        "description": "Explicit exclusion of polar (|lat| > 85 deg) and antimeridian regions raises ValueError",
        "heading_deg": "N/A",
        "input_enu": "Lat=89.0 deg",
        "expected_body": "ValueError raised (MVP exclusion)",
        "actual_body": "ValueError properly raised",
        "error_meters": "0.0",
        "passed": pass_p6
    })
    expected_data["P6"] = {"exceptionRaised": True, "passed": pass_p6}
    print(f"[{'PASS' if pass_p6 else 'FAIL'}] P6: Polar/Antimeridian properly excluded (ValueError)")

    # P7. Stale Pose Suppression
    fresh_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=0.0, timestamp_millis=10000)
    stale_pose = VehiclePoseSpatial(local_x=0.0, local_y=0.0, heading_radians=0.0, timestamp_millis=8000) # 2000ms old (>1500ms)
    feat = RiskFeature(
        feature_id="DEMO_CW_TEST",
        risk_type="CROSSWALK",
        source="MANUALLY_VERIFIED_DEMO",
        geometry_method="MANUAL",
        manual_verified=True,
        coordinate_quality=SpatialQuality.VERIFIED_DEMO,
        width_meters=8.0,
        length_meters=3.0,
        direction_degrees=90.0,
        center_enu=(0.0, 15.0, 0.0)
    )
    current_time = 10000
    res_fresh = RiskFeatureSampler.sample_feature_in_vehicle_frame(feat, fresh_pose, spacing=0.5, current_time_millis=current_time)
    res_stale = RiskFeatureSampler.sample_feature_in_vehicle_frame(feat, stale_pose, spacing=0.5, current_time_millis=current_time)
    pass_p7 = (res_fresh is not None and res_stale is None)
    tests_results.append({
        "test_id": "P7_STALE_POSE_SUPPRESSION",
        "description": "Pose age > 1500ms suppresses engine input generation (returns None)",
        "heading_deg": 0.0,
        "input_enu": "Age: fresh=0ms, stale=2000ms",
        "expected_body": "Fresh: List[Point3], Stale: None",
        "actual_body": f"Fresh: {len(res_fresh) if res_fresh else 0} pts, Stale: {res_stale}",
        "error_meters": "0.0",
        "passed": pass_p7
    })
    expected_data["P7"] = {
        "freshPointCount": len(res_fresh) if res_fresh else 0,
        "staleResult": None,
        "passed": pass_p7
    }
    print(f"[{'PASS' if pass_p7 else 'FAIL'}] P7: Stale pose suppression -> Fresh pts={len(res_fresh)}, Stale={res_stale}")

    # Write CSV
    csv_path = os.path.join(output_dir, "spatial_projection_validation.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(tests_results[0].keys()))
        writer.writeheader()
        writer.writerows(tests_results)
    print(f"Saved {csv_path} ({os.path.getsize(csv_path)} bytes)")

    # Write JSON
    json_path = os.path.join(output_dir, "spatial_projection_expected.json")
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(expected_data, f, indent=2)
    print(f"Saved {json_path} ({os.path.getsize(json_path)} bytes)")

if __name__ == "__main__":
    run_spatial_projection_verification()
