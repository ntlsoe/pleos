import math
from typing import List, Dict, Any
from geometry import Point3

class RiskSamplePoint:
    __slots__ = ('position', 'weight', 'spacing_meters')
    def __init__(self, position: Point3, weight: float, spacing_meters: float):
        self.position = position
        self.weight = float(weight)
        self.spacing_meters = float(spacing_meters)

    def to_tuple(self):
        return (self.position.x, self.position.y, self.position.z, self.weight)

class RiskSurface:
    def __init__(self, surface_id: str, surface_type: str, origin: Point3, length_x: float, width_y: float, height_z: float = 0.0, source: str = "SYNTHETIC_TEST_ONLY"):
        self.id = surface_id
        self.type = surface_type
        self.origin = origin
        self.length_x = float(length_x)
        self.width_y = float(width_y)
        self.height_z = float(height_z)
        self.source = source

    def sample_points(self, spacing: float = 0.5) -> List[RiskSamplePoint]:
        """
        Generates uniform regular grid sample points across the surface area.
        Ensures uniform weight distribution summing to 1.0.
        """
        assert spacing > 0, "Spacing must be positive"
        nx = max(1, int(round(self.length_x / spacing)))
        ny = max(1, int(round(self.width_y / spacing)))

        dx = self.length_x / nx
        dy = self.width_y / ny

        points = []
        total_samples = nx * ny
        w = 1.0 / total_samples

        x_start = self.origin.x - self.length_x / 2.0
        y_start = self.origin.y - self.width_y / 2.0

        for ix in range(nx):
            px = x_start + (ix + 0.5) * dx
            for iy in range(ny):
                py = y_start + (iy + 0.5) * dy
                pz = self.origin.z + self.height_z
                points.append(RiskSamplePoint(Point3(px, py, pz), w, spacing))

        return points

def create_risk_surface_from_config(cfg: Dict[str, Any]) -> RiskSurface:
    sid = cfg.get("id", "risk_surface")
    stype = cfg.get("type", "CROSSWALK")
    orig_list = cfg.get("origin", [10.0, 0.0, 0.0])
    origin = Point3(orig_list[0], orig_list[1], orig_list[2])
    lx = cfg.get("lengthX", 4.0)
    wy = cfg.get("widthY", 4.0)
    hz = cfg.get("heightZ", 0.0)
    return RiskSurface(surface_id=sid, surface_type=stype, origin=origin, length_x=lx, width_y=wy, height_z=hz)
