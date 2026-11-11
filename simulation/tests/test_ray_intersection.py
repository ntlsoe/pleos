import pytest
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from geometry import Point3, Vector3, Triangle, Ray, ray_triangle_intersect_segment

def test_ray_triangle_direct_hit():
    # Triangle in Y-Z plane at X = 5.0
    v0 = Point3(5.0, -1.0, -1.0)
    v1 = Point3(5.0,  1.0, -1.0)
    v2 = Point3(5.0,  0.0,  1.0)
    tri = Triangle(v0, v1, v2)

    # Ray from origin (0,0,0) to target (10, 0, 0)
    ray = Ray(Point3(0.0, 0.0, 0.0), Point3(10.0, 0.0, 0.0))
    t = ray_triangle_intersect_segment(ray, tri)
    assert t is not None
    assert abs(t - 0.5) < 1e-5

def test_ray_triangle_miss_outside():
    v0 = Point3(5.0, 2.0, 2.0)
    v1 = Point3(5.0, 3.0, 2.0)
    v2 = Point3(5.0, 2.5, 3.0)
    tri = Triangle(v0, v1, v2)

    # Ray through (10, 0, 0) misses triangle at Y in [2, 3]
    ray = Ray(Point3(0.0, 0.0, 0.0), Point3(10.0, 0.0, 0.0))
    t = ray_triangle_intersect_segment(ray, tri)
    assert t is None

def test_ray_target_before_obstacle():
    # Triangle at X = 5.0
    v0 = Point3(5.0, -1.0, -1.0)
    v1 = Point3(5.0,  1.0, -1.0)
    v2 = Point3(5.0,  0.0,  1.0)
    tri = Triangle(v0, v1, v2)

    # Ray segment ends at X = 3.0 (target is in front of obstacle)
    ray = Ray(Point3(0.0, 0.0, 0.0), Point3(3.0, 0.0, 0.0))
    t = ray_triangle_intersect_segment(ray, tri)
    assert t is None, "Obstacle behind target must not occlude"

def test_ray_obstacle_behind_eye():
    # Triangle at X = -5.0
    v0 = Point3(-5.0, -1.0, -1.0)
    v1 = Point3(-5.0,  1.0, -1.0)
    v2 = Point3(-5.0,  0.0,  1.0)
    tri = Triangle(v0, v1, v2)

    # Ray from origin forward to X = +10.0
    ray = Ray(Point3(0.0, 0.0, 0.0), Point3(10.0, 0.0, 0.0))
    t = ray_triangle_intersect_segment(ray, tri)
    assert t is None, "Obstacle behind eye must not occlude"
