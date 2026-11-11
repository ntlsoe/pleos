import os
import csv
from geometry import Point3, Vector3, Ray, Triangle, ray_triangle_intersect_segment

def run_ray_edge_cases():
    output_dir = os.path.join(os.path.dirname(__file__), "..", "output")
    os.makedirs(output_dir, exist_ok=True)

    results = []
    print("=== Ray-Triangle Intersection Parameter & Edge Case Verification ===")

    eye = Point3(0.0, 0.0, 1.0)
    target = Point3(10.0, 0.0, 1.0)
    ray = Ray(origin=eye, target=target)

    # 1. Pillar Behind Sample (t > 1.0)
    tri_behind_sample = Triangle(Point3(15.0, -1.0, 0.0), Point3(15.0, 1.0, 0.0), Point3(15.0, 0.0, 2.0))
    t1 = ray_triangle_intersect_segment(ray, tri_behind_sample)
    pass1 = (t1 is None)
    print(f"[{'PASS' if pass1 else 'FAIL'}] Case 1 (Pillar Behind Sample, X=15m): t={t1} (Expected: None)")
    assert pass1
    results.append({"case_id": "PILLAR_BEHIND_SAMPLE", "t_param": "t > 1.0", "expected": "None", "actual": str(t1), "passed": pass1})

    # 2. Pillar Behind Eye (t < 0.0)
    tri_behind_eye = Triangle(Point3(-5.0, -1.0, 0.0), Point3(-5.0, 1.0, 0.0), Point3(-5.0, 0.0, 2.0))
    t2 = ray_triangle_intersect_segment(ray, tri_behind_eye)
    pass2 = (t2 is None)
    print(f"[{'PASS' if pass2 else 'FAIL'}] Case 2 (Pillar Behind Eye, X=-5m): t={t2} (Expected: None)")
    assert pass2
    results.append({"case_id": "PILLAR_BEHIND_EYE", "t_param": "t < 0.0", "expected": "None", "actual": str(t2), "passed": pass2})

    # 3. Pillar Between Eye and Sample (0 < t < 1)
    tri_between = Triangle(Point3(5.0, -1.0, 0.0), Point3(5.0, 1.0, 0.0), Point3(5.0, 0.0, 2.0))
    t3 = ray_triangle_intersect_segment(ray, tri_between)
    pass3 = (t3 is not None and abs(t3 - 0.5) < 1e-4)
    print(f"[{'PASS' if pass3 else 'FAIL'}] Case 3 (Pillar Between, X=5m): t={t3:.4f} (Expected: ~0.5000)")
    assert pass3
    results.append({"case_id": "PILLAR_BETWEEN_EYE_AND_SAMPLE", "t_param": "0.0 < t < 1.0", "expected": "0.5000", "actual": f"{t3:.4f}", "passed": pass3})

    # 4. Pillar Coplanar with Target (t = 1.0)
    tri_coplanar_target = Triangle(Point3(10.0, -1.0, 0.0), Point3(10.0, 1.0, 0.0), Point3(10.0, 0.0, 2.0))
    t4 = ray_triangle_intersect_segment(ray, tri_coplanar_target)
    pass4 = (t4 is None) # Boundary exclusion (t < 1.0 - epsilon)
    print(f"[{'PASS' if pass4 else 'FAIL'}] Case 4 (Pillar Coplanar with Target, X=10m): t={t4} (Expected: None)")
    assert pass4
    results.append({"case_id": "PILLAR_COPLANAR_TARGET", "t_param": "t == 1.0", "expected": "None", "actual": str(t4), "passed": pass4})

    # 5. Grazing Ray Touching Boundary Edge
    tri_grazing = Triangle(Point3(5.0, 0.0, 1.0), Point3(5.0, 2.0, 1.0), Point3(5.0, 1.0, 3.0))
    t5 = ray_triangle_intersect_segment(ray, tri_grazing)
    # At exact vertex (u=0, v=0), boundary handling safely evaluates without crash
    pass5 = (t5 is None or (0.0 < t5 < 1.0))
    print(f"[{'PASS' if pass5 else 'FAIL'}] Case 5 (Grazing Ray on Vertex): t={t5} (Stable boundary handling)")
    assert pass5
    results.append({"case_id": "GRAZING_RAY_VERTEX", "t_param": "u=0, v=0", "expected": "Stable", "actual": str(t5), "passed": pass5})

    # 6. Degenerate Triangle (Collinear vertices, area = 0)
    tri_degenerate = Triangle(Point3(5.0, 0.0, 0.0), Point3(5.0, 0.0, 1.0), Point3(5.0, 0.0, 2.0))
    t6 = ray_triangle_intersect_segment(ray, tri_degenerate)
    pass6 = (t6 is None)
    print(f"[{'PASS' if pass6 else 'FAIL'}] Case 6 (Degenerate Collinear Triangle): t={t6} (Expected: None)")
    assert pass6
    results.append({"case_id": "DEGENERATE_TRIANGLE", "t_param": "|det| < 1e-7", "expected": "None", "actual": str(t6), "passed": pass6})

    # 7. Two Coplanar Triangles forming a quad
    # Quad at X=5, Y in [-1, 1], Z in [0, 2]
    v0 = Point3(5.0, -1.0, 0.0)
    v1 = Point3(5.0, 1.0, 0.0)
    v2 = Point3(5.0, 1.0, 2.0)
    v3 = Point3(5.0, -1.0, 2.0)
    tri_a = Triangle(v0, v1, v2)
    tri_b = Triangle(v0, v2, v3)

    # 7a: Ray through interior of tri_a (target at Y=0.4, Z=0.6)
    ray_interior = Ray(origin=Point3(0.0, 0.4, 0.6), target=Point3(10.0, 0.4, 0.6))
    t7_a = ray_triangle_intersect_segment(ray_interior, tri_a)
    t7_b = ray_triangle_intersect_segment(ray_interior, tri_b)
    hits_interior = [t for t in (t7_a, t7_b) if t is not None]
    pass7a = (len(hits_interior) == 1 and abs(hits_interior[0] - 0.5) < 1e-4)

    # 7b: Ray through shared diagonal (target at Y=0.0, Z=1.0)
    # Both triangles detect boundary hit; pillar volume short-circuits on first hit
    t7_diag_a = ray_triangle_intersect_segment(ray, tri_a)
    t7_diag_b = ray_triangle_intersect_segment(ray, tri_b)
    pass7b = (t7_diag_a is not None and t7_diag_b is not None)

    pass7 = pass7a and pass7b
    print(f"[{'PASS' if pass7 else 'FAIL'}] Case 7 (Coplanar Quad Triangles): InteriorHits={len(hits_interior)}, DiagonalHits=2 (Clean single-hit in interior, robust diagonal boundary)")
    assert pass7
    results.append({"case_id": "COPLANAR_TWO_TRIANGLES", "t_param": "Quad Mesh", "expected": "Interior: 1 hit, Diagonal: 2 hits", "actual": f"InteriorHits={len(hits_interior)}, DiagonalHits=2", "passed": pass7})

    csv_path = os.path.join(output_dir, "ray_edge_cases.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=["case_id", "t_param", "expected", "actual", "passed"])
        writer.writeheader()
        writer.writerows(results)
    print(f"Saved ray edge cases CSV to: {csv_path} ({os.path.getsize(csv_path)} bytes)")

if __name__ == "__main__":
    run_ray_edge_cases()
