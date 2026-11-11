import math
from typing import Tuple, Optional

class Point3:
    __slots__ = ('x', 'y', 'z')
    def __init__(self, x: float, y: float, z: float):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)

    def to_tuple(self) -> Tuple[float, float, float]:
        return (self.x, self.y, self.z)

    def __repr__(self) -> str:
        return f"Point3({self.x:.4f}, {self.y:.4f}, {self.z:.4f})"

    def __eq__(self, other) -> bool:
        if not isinstance(other, Point3):
            return False
        return (math.isclose(self.x, other.x, abs_tol=1e-7) and
                math.isclose(self.y, other.y, abs_tol=1e-7) and
                math.isclose(self.z, other.z, abs_tol=1e-7))

class Vector3:
    __slots__ = ('x', 'y', 'z')
    def __init__(self, x: float, y: float, z: float):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)

    def length(self) -> float:
        return math.sqrt(self.x * self.x + self.y * self.y + self.z * self.z)

    def normalize(self) -> 'Vector3':
        l = self.length()
        if l < 1e-12:
            return Vector3(0.0, 0.0, 0.0)
        return Vector3(self.x / l, self.y / l, self.z / l)

    def dot(self, other: 'Vector3') -> float:
        return self.x * other.x + self.y * other.y + self.z * other.z

    def cross(self, other: 'Vector3') -> 'Vector3':
        return Vector3(
            self.y * other.z - self.z * other.y,
            self.z * other.x - self.x * other.z,
            self.x * other.y - self.y * other.x
        )

    def __sub__(self, other: 'Vector3') -> 'Vector3':
        return Vector3(self.x - other.x, self.y - other.y, self.z - other.z)

    def __add__(self, other: 'Vector3') -> 'Vector3':
        return Vector3(self.x + other.x, self.y + other.y, self.z + other.z)

    def __mul__(self, scalar: float) -> 'Vector3':
        return Vector3(self.x * scalar, self.y * scalar, self.z * scalar)

def point_diff(p1: Point3, p2: Point3) -> Vector3:
    return Vector3(p1.x - p2.x, p1.y - p2.y, p1.z - p2.z)

def point_add_vec(p: Point3, v: Vector3) -> Point3:
    return Point3(p.x + v.x, p.y + v.y, p.z + v.z)

class Ray:
    __slots__ = ('origin', 'direction', 'target')
    def __init__(self, origin: Point3, target: Point3):
        self.origin = origin
        self.target = target
        # direction vector from origin towards target
        self.direction = point_diff(target, origin)

class Triangle:
    __slots__ = ('v0', 'v1', 'v2')
    def __init__(self, v0: Point3, v1: Point3, v2: Point3):
        self.v0 = v0
        self.v1 = v1
        self.v2 = v2

    def normal(self) -> Vector3:
        edge1 = point_diff(self.v1, self.v0)
        edge2 = point_diff(self.v2, self.v0)
        return edge1.cross(edge2).normalize()

def ray_triangle_intersect_segment(ray: Ray, triangle: Triangle, epsilon: float = 1e-7) -> Optional[float]:
    """
    Möller–Trumbore ray-triangle intersection algorithm for segment [0, 1].
    Returns t in (epsilon, 1.0 - epsilon) if the segment intersects triangle interior, else None.
    t represents fractional distance from ray.origin to ray.target.
    If t in (0, 1), the triangle obstructs the view from origin to target.
    """
    v0, v1, v2 = triangle.v0, triangle.v1, triangle.v2
    edge1 = point_diff(v1, v0)
    edge2 = point_diff(v2, v0)
    dir_vec = ray.direction

    pvec = dir_vec.cross(edge2)
    det = edge1.dot(pvec)

    # Ray parallel to triangle plane
    if abs(det) < epsilon:
        return None

    inv_det = 1.0 / det
    tvec = point_diff(ray.origin, v0)
    u = tvec.dot(pvec) * inv_det
    if u < -epsilon or u > 1.0 + epsilon:
        return None

    qvec = tvec.cross(edge1)
    v = dir_vec.dot(qvec) * inv_det
    if v < -epsilon or u + v > 1.0 + epsilon:
        return None

    t = edge2.dot(qvec) * inv_det
    # Intersects strictly between eye (t=0) and target (t=1)
    if epsilon < t < (1.0 - epsilon):
        return t

    return None
