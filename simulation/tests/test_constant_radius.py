import pytest
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from geometry import Point3
from eye_model import load_eye_model
from pillar_model import load_vehicle_pillars
from risk_sampling import RiskSurface
from vehicle_trajectory import VehicleTrajectoryGenerator
from occlusion_engine import BinocularOcclusionEngine

def test_left_right_turn_symmetry_on_symmetric_center():
    """
    On a symmetric vehicle pillar configuration with center eye point,
    left turn across left crosswalk and right turn across mirrored right crosswalk
    must produce identical occlusion metrics within floating point tolerance.
    """
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    config_dir = os.path.join(base_dir, 'config')

    veh_geom = load_vehicle_pillars(os.path.join(config_dir, 'representative_sedan_v0.json'))
    # Set nominal eye to vehicle lateral center Y = 0 to test pure geometric symmetry
    center_eye = Point3(veh_geom.driver_eye_nominal.x, 0.0, veh_geom.driver_eye_nominal.z)
    eye_model = load_eye_model(os.path.join(config_dir, 'synthetic_eye_model_v0.json'), 'SyntheticCenterProfile', center_eye)
    engine = BinocularOcclusionEngine(veh_geom, eye_model)

    # Right crosswalk
    right_surf = RiskSurface("right_cw", "CROSSWALK", Point3(8.0, -3.5, 0.0), length_x=4.0, width_y=4.0)
    right_samples = right_surf.sample_points(spacing=0.5)
    right_poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=10.0, speed_kmh=10.0, is_right=True, duration_sec=4.0, dt=0.05)
    m_right = engine.evaluate_trajectory("RIGHT_TURN", right_samples, right_poses, dt=0.05)

    # Mirrored Left crosswalk
    left_surf = RiskSurface("left_cw", "CROSSWALK", Point3(8.0, 3.5, 0.0), length_x=4.0, width_y=4.0)
    left_samples = left_surf.sample_points(spacing=0.5)
    left_poses = VehicleTrajectoryGenerator.generate_constant_radius(radius_m=10.0, speed_kmh=10.0, is_right=False, duration_sec=4.0, dt=0.05)
    m_left = engine.evaluate_trajectory("LEFT_TURN", left_samples, left_poses, dt=0.05)

    assert abs(m_right.any_point_continuous_duration - m_left.any_point_continuous_duration) < 1e-4
    assert abs(m_right.peak_possible_ratio - m_left.peak_possible_ratio) < 1e-4
    assert abs(m_right.exposure_integral_possible - m_left.exposure_integral_possible) < 1e-4
