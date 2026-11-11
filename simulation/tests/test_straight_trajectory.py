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

def test_speed_increase_reduces_occlusion_duration():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    config_dir = os.path.join(base_dir, 'config')

    veh_geom = load_vehicle_pillars(os.path.join(config_dir, 'representative_sedan_v0.json'))
    eye_model = load_eye_model(os.path.join(config_dir, 'synthetic_eye_model_v0.json'), 'SyntheticCenterProfile', veh_geom.driver_eye_nominal)
    engine = BinocularOcclusionEngine(veh_geom, eye_model)

    # Risk surface on the left where left pillar passes through
    surf = RiskSurface("crosswalk_s1", "CROSSWALK", Point3(12.0, 2.5, 0.0), length_x=4.0, width_y=4.0)
    samples = surf.sample_points(spacing=0.5)

    durations = []
    speeds = [5.0, 10.0, 30.0]

    for spd in speeds:
        poses = VehicleTrajectoryGenerator.generate_straight(speed_kmh=spd, duration_sec=5.0, dt=0.05)
        metrics = engine.evaluate_trajectory(f"S1_{spd}", samples, poses, dt=0.05)
        durations.append(metrics.any_point_continuous_duration)

    # Higher speed should lead to equal or lower continuous duration through fixed risk zone
    assert durations[0] >= durations[1], f"Duration at 5 km/h ({durations[0]}s) must be >= 10 km/h ({durations[1]}s)"
    assert durations[1] >= durations[2], f"Duration at 10 km/h ({durations[1]}s) must be >= 30 km/h ({durations[2]}s)"
