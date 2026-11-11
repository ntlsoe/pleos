import json
from typing import List, Dict, Any, Optional
from geometry import Point3, Triangle, Ray, ray_triangle_intersect_segment

class PillarVolume:
    __slots__ = ('side', 'vertices', 'triangles', 'source')
    def __init__(self, side: str, vertices: List[Point3], source: str):
        self.side = side
        self.vertices = vertices
        self.source = source
        self.triangles = self._build_triangles(vertices)

    @staticmethod
    def _build_triangles(v: List[Point3]) -> List[Triangle]:
        """
        Builds 12 triangles covering the 6 quadrilateral faces of an 8-vertex prism.
        v[0..3] = base (frontOuter, frontInner, rearInner, rearOuter)
        v[4..7] = top  (frontOuter, frontInner, rearInner, rearOuter)
        """
        assert len(v) == 8, f"Expected 8 vertices for prism, got {len(v)}"
        triangles = [
            # Bottom cap
            Triangle(v[0], v[1], v[2]),
            Triangle(v[0], v[2], v[3]),
            # Top cap
            Triangle(v[4], v[6], v[5]),
            Triangle(v[4], v[7], v[6]),
            # Front face (v0, v1, v5, v4)
            Triangle(v[0], v[4], v[5]),
            Triangle(v[0], v[5], v[1]),
            # Inner face (v1, v2, v6, v5)
            Triangle(v[1], v[5], v[6]),
            Triangle(v[1], v[6], v[2]),
            # Rear face (v2, v3, v7, v6)
            Triangle(v[2], v[6], v[7]),
            Triangle(v[2], v[7], v[3]),
            # Outer face (v3, v0, v4, v7)
            Triangle(v[3], v[7], v[4]),
            Triangle(v[3], v[4], v[0]),
        ]
        return triangles

    def is_occluded(self, eye: Point3, target: Point3) -> bool:
        ray = Ray(origin=eye, target=target)
        for tri in self.triangles:
            t = ray_triangle_intersect_segment(ray, tri)
            if t is not None:
                return True
        return False

class VehiclePillarGeometry:
    def __init__(self, profile_id: str, source: str, left_pillar: PillarVolume, right_pillar: PillarVolume, driver_eye_nominal: Point3, wheelbase: float, track_width: float):
        self.profile_id = profile_id
        self.source = source
        self.left_pillar = left_pillar
        self.right_pillar = right_pillar
        self.driver_eye_nominal = driver_eye_nominal
        self.wheelbase = wheelbase
        self.track_width = track_width

    def is_occluded_by_either_pillar(self, eye: Point3, target: Point3) -> bool:
        return self.left_pillar.is_occluded(eye, target) or self.right_pillar.is_occluded(eye, target)

def load_vehicle_pillars(config_path: str) -> VehiclePillarGeometry:
    with open(config_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    profile_id = data["profileId"]
    source = data.get("parameterClass", data.get("source", "SYNTHETIC_TEST_ONLY"))
    wb = data.get("wheelbaseMeters", 2.85)
    tw = data.get("trackWidthMeters", 1.62)
    eye_dict = data["driverEyeNominal"]
    nominal_eye = Point3(eye_dict["x"], eye_dict["y"], eye_dict["z"])

    def parse_pillar(p_dict: Dict[str, Any], side: str) -> PillarVolume:
        base = p_dict["base"]
        top = p_dict["top"]
        verts = [
            Point3(*base["frontOuter"]),
            Point3(*base["frontInner"]),
            Point3(*base["rearInner"]),
            Point3(*base["rearOuter"]),
            Point3(*top["frontOuter"]),
            Point3(*top["frontInner"]),
            Point3(*top["rearInner"]),
            Point3(*top["rearOuter"]),
        ]
        return PillarVolume(side=side, vertices=verts, source=source)

    left_p = parse_pillar(data["leftPillar"], "LEFT")
    right_p = parse_pillar(data["rightPillar"], "RIGHT")

    return VehiclePillarGeometry(
        profile_id=profile_id,
        source=source,
        left_pillar=left_p,
        right_pillar=right_p,
        driver_eye_nominal=nominal_eye,
        wheelbase=wb,
        track_width=tw
    )
