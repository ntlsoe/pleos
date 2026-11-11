import pytest
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from geometry import Point3
from eye_model import EyePair, WeightedEyePair, EyePopulationModel
from pillar_model import PillarVolume, VehiclePillarGeometry
from occlusion_engine import BinocularOcclusionEngine
from vehicle_trajectory import VehiclePose

def create_synthetic_pillar(side: str, x_min: float, x_max: float, y_min: float, y_max: float, z_min: float, z_max: float) -> PillarVolume:
    verts = [
        Point3(x_max, y_max, z_min),
        Point3(x_max, y_min, z_min),
        Point3(x_min, y_min, z_min),
        Point3(x_min, y_max, z_min),
        Point3(x_max, y_max, z_max),
        Point3(x_max, y_min, z_max),
        Point3(x_min, y_min, z_max),
        Point3(x_min, y_max, z_max),
    ]
    return PillarVolume(side=side, vertices=verts, source="SYNTHETIC_TEST_ONLY")

def test_binocular_vs_monocular_occlusion():
    # Eye pair at X = 0: Left Eye at (0, +0.05, 1.0), Right Eye at (0, -0.05, 1.0)
    left_eye = Point3(0.0, 0.05, 1.0)
    right_eye = Point3(0.0, -0.05, 1.0)
    eye_pair = EyePair(left_eye, right_eye)
    sample = WeightedEyePair(eye_pair, 1.0, "SYNTHETIC_TEST_ONLY", "test_eye")

    eye_model = EyePopulationModel("TestEye", "SYNTHETIC_TEST_ONLY", [], Point3(0,0,1.0))
    eye_model.generate_samples = lambda: [sample]

    # Pillar 1 blocking ONLY Left eye ray to target (10, 0.05, 1.0)
    # Target 1: (10, 0.05, 1.0). Ray from Left eye goes along Y = 0.05
    # Target 2: (10, -0.05, 1.0). Ray from Right eye goes along Y = -0.05
    left_pillar = create_synthetic_pillar("LEFT", 4.0, 6.0, 0.02, 0.08, 0.5, 1.5)
    # Right pillar far away
    right_pillar = create_synthetic_pillar("RIGHT", 4.0, 6.0, -0.80, -0.70, 0.5, 1.5)

    veh_geom = VehiclePillarGeometry("TestVeh", "SYNTHETIC_TEST_ONLY", left_pillar, right_pillar, Point3(0,0,1.0), 2.8, 1.6)
    engine = BinocularOcclusionEngine(veh_geom, eye_model)

    zero_pose = VehiclePose(0.0, 0.0, 0.0, 0.0, 0.0)

    # 1. Target directly in line with left eye only:
    # Left eye ray is obstructed by left pillar. Right eye ray is unobstructed.
    res_partial = engine.evaluate_risk_point_at_pose(Point3(10.0, 0.05, 1.0), zero_pose)
    assert not res_partial["isPossibleOccluded"], "Partial monocular occlusion must NOT be reported as binocularly occluded"
    assert not res_partial["isUniversalOccluded"]

    # 2. Wide obstacle blocking BOTH eyes:
    wide_left_pillar = create_synthetic_pillar("LEFT", 4.0, 6.0, -0.20, 0.20, 0.5, 1.5)
    veh_geom_wide = VehiclePillarGeometry("TestVehWide", "SYNTHETIC_TEST_ONLY", wide_left_pillar, right_pillar, Point3(0,0,1.0), 2.8, 1.6)
    engine_wide = BinocularOcclusionEngine(veh_geom_wide, eye_model)

    res_binocular = engine_wide.evaluate_risk_point_at_pose(Point3(10.0, 0.0, 1.0), zero_pose)
    assert res_binocular["isPossibleOccluded"], "Both eyes occluded must result in binocular occlusion"
    assert res_binocular["isUniversalOccluded"]
