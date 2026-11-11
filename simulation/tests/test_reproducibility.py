import pytest
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from geometry import Point3
from eye_model import load_eye_model
from pillar_model import load_vehicle_pillars
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from uncertainty import UncertaintyConfig, apply_uncertainty
from occlusion_engine import BinocularOcclusionEngine

def test_identical_seed_produces_identical_results():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    config_dir = os.path.join(base_dir, 'config')

    veh_geom = load_vehicle_pillars(os.path.join(config_dir, 'representative_sedan_v0.json'))
    eye_model = load_eye_model(os.path.join(config_dir, 'synthetic_eye_model_v0.json'), 'SyntheticCenterProfile', veh_geom.driver_eye_nominal)
    engine = BinocularOcclusionEngine(veh_geom, eye_model)

    surf = RiskSurface("crosswalk_s8", "CROSSWALK", Point3(8.0, -3.0, 0.0), length_x=4.0, width_y=4.0)
    samples = surf.sample_points(spacing=0.5)
    base_poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=8.0, speed_kmh=12.0, is_right=True, duration_sec=3.0, dt=0.05)

    u_cfg1 = UncertaintyConfig(pos_sigma_meters=0.3, heading_sigma_degrees=1.5, timestamp_delay_millis=50, dropout_probability=0.05, seed=12345)
    u_cfg2 = UncertaintyConfig(pos_sigma_meters=0.3, heading_sigma_degrees=1.5, timestamp_delay_millis=50, dropout_probability=0.05, seed=12345)

    poses1 = apply_uncertainty(base_poses, u_cfg1)
    poses2 = apply_uncertainty(base_poses, u_cfg2)

    m1 = engine.evaluate_trajectory("RUN_1", samples, poses1, dt=0.05)
    m2 = engine.evaluate_trajectory("RUN_2", samples, poses2, dt=0.05)

    assert m1.peak_possible_ratio == m2.peak_possible_ratio
    assert m1.exposure_integral_possible == m2.exposure_integral_possible
    assert m1.max_per_point_continuous_duration == m2.max_per_point_continuous_duration
    assert m1.any_point_continuous_duration == m2.any_point_continuous_duration
