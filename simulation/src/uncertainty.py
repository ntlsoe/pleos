import math
import random
from typing import List, Optional
from vehicle_trajectory import VehiclePose

class UncertaintyConfig:
    def __init__(
        self,
        pos_sigma_meters: float = 0.0,
        heading_sigma_degrees: float = 0.0,
        timestamp_delay_millis: int = 0,
        dropout_probability: float = 0.0,
        fixed_bias_x: float = 0.0,
        fixed_bias_y: float = 0.0,
        seed: int = 42
    ):
        self.pos_sigma_meters = float(pos_sigma_meters)
        self.heading_sigma_degrees = float(heading_sigma_degrees)
        self.timestamp_delay_millis = int(timestamp_delay_millis)
        self.dropout_probability = float(dropout_probability)
        self.fixed_bias_x = float(fixed_bias_x)
        self.fixed_bias_y = float(fixed_bias_y)
        self.seed = int(seed)

    def to_dict(self):
        return {
            "posSigmaMeters": self.pos_sigma_meters,
            "headingSigmaDegrees": self.heading_sigma_degrees,
            "timestampDelayMillis": self.timestamp_delay_millis,
            "dropoutProbability": self.dropout_probability,
            "fixedBiasX": self.fixed_bias_x,
            "fixedBiasY": self.fixed_bias_y,
            "seed": self.seed
        }

def apply_uncertainty(poses: List[VehiclePose], config: UncertaintyConfig, base_noise_dt: float = 0.10) -> List[VehiclePose]:
    if (config.pos_sigma_meters == 0.0 and
        config.heading_sigma_degrees == 0.0 and
        config.fixed_bias_x == 0.0 and
        config.fixed_bias_y == 0.0 and
        config.dropout_probability == 0.0 and
        config.timestamp_delay_millis == 0):
        return poses

    if not poses:
        return poses

    total_duration = poses[-1].time_sec - poses[0].time_sec + 2.0
    num_noise_anchors = int(math.ceil(total_duration / base_noise_dt)) + 3

    rng = random.Random(config.seed)
    heading_sigma_rad = math.radians(config.heading_sigma_degrees)

    anchor_noise_x = [rng.gauss(0.0, config.pos_sigma_meters) if config.pos_sigma_meters > 0 else 0.0 for _ in range(num_noise_anchors)]
    anchor_noise_y = [rng.gauss(0.0, config.pos_sigma_meters) if config.pos_sigma_meters > 0 else 0.0 for _ in range(num_noise_anchors)]
    anchor_noise_h = [rng.gauss(0.0, heading_sigma_rad) if heading_sigma_rad > 0 else 0.0 for _ in range(num_noise_anchors)]

    delay_sec = config.timestamp_delay_millis / 1000.0
    perturbed = []
    last_valid_pose = poses[0]

    for p in poses:
        # Dropout check
        if config.dropout_probability > 0 and rng.random() < config.dropout_probability:
            perturbed.append(VehiclePose(
                x=last_valid_pose.x,
                y=last_valid_pose.y,
                heading=last_valid_pose.heading,
                speed_mps=last_valid_pose.speed_mps,
                time_sec=p.time_sec
            ))
            continue

        t = p.time_sec
        idx = max(0, int(t / base_noise_dt))
        frac = (t - idx * base_noise_dt) / base_noise_dt
        if idx >= len(anchor_noise_x) - 1:
            idx = len(anchor_noise_x) - 2
            frac = 1.0

        noise_x = (1.0 - frac) * anchor_noise_x[idx] + frac * anchor_noise_x[idx + 1]
        noise_y = (1.0 - frac) * anchor_noise_y[idx] + frac * anchor_noise_y[idx + 1]
        noise_h = (1.0 - frac) * anchor_noise_h[idx] + frac * anchor_noise_h[idx + 1]

        new_x = p.x + config.fixed_bias_x + noise_x
        new_y = p.y + config.fixed_bias_y + noise_y
        new_h = p.heading + noise_h
        new_t = p.time_sec + delay_sec

        new_pose = VehiclePose(x=new_x, y=new_y, heading=new_h, speed_mps=p.speed_mps, time_sec=new_t)
        perturbed.append(new_pose)
        last_valid_pose = new_pose

    return perturbed
