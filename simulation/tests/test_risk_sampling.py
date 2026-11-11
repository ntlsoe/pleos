import pytest
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from geometry import Point3
from risk_sampling import RiskSurface

def test_risk_surface_sampling_weights_and_spacing():
    surf = RiskSurface("test_crosswalk", "CROSSWALK", Point3(10.0, 0.0, 0.0), length_x=4.0, width_y=4.0)

    for spacing in [0.25, 0.5, 1.0]:
        points = surf.sample_points(spacing=spacing)
        total_w = sum(p.weight for p in points)
        assert abs(total_w - 1.0) < 1e-7, f"Weights must sum to 1.0 for spacing {spacing}"

        # Check sample count matches expected area / spacing^2
        expected_nx = int(round(4.0 / spacing))
        expected_ny = int(round(4.0 / spacing))
        assert len(points) == expected_nx * expected_ny
