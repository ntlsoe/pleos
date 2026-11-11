import math
from typing import List, Tuple
from geometry import Point3

class VehiclePose:
    __slots__ = ('x', 'y', 'heading', 'speed_mps', 'time_sec')
    def __init__(self, x: float, y: float, heading: float, speed_mps: float, time_sec: float):
        self.x = float(x)
        self.y = float(y)
        self.heading = float(heading) # radians (0 = facing +X)
        self.speed_mps = float(speed_mps)
        self.time_sec = float(time_sec)

    def world_to_vehicle(self, p_world: Point3) -> Point3:
        """
        Transforms a 3D point from World frame to Vehicle frame at this pose.
        Vehicle frame: +X forward along heading, +Y left, +Z up.
        """
        dx = p_world.x - self.x
        dy = p_world.y - self.y
        cos_h = math.cos(self.heading)
        sin_h = math.sin(self.heading)

        # Rotation by -heading
        x_veh = cos_h * dx + sin_h * dy
        y_veh = -sin_h * dx + cos_h * dy
        z_veh = p_world.z
        return Point3(x_veh, y_veh, z_veh)

class VehicleTrajectoryGenerator:
    ACCELERATION_PROFILES = {
        "GENTLE": 0.8,  # m/s^2
        "NORMAL": 1.5,  # m/s^2
        "BRISK":  2.5   # m/s^2
    }

    @staticmethod
    def generate_straight(speed_kmh: float, duration_sec: float, dt: float = 0.05) -> List[VehiclePose]:
        speed_mps = speed_kmh / 3.6
        num_steps = int(round(duration_sec / dt)) + 1
        poses = []
        for i in range(num_steps):
            t = i * dt
            x = speed_mps * t
            poses.append(VehiclePose(x=x, y=0.0, heading=0.0, speed_mps=speed_mps, time_sec=t))
        return poses

    @staticmethod
    def generate_constant_radius(radius_m: float, speed_kmh: float, is_right: bool, duration_sec: float, dt: float = 0.05) -> List[VehiclePose]:
        assert radius_m > 0, "Radius must be positive"
        speed_mps = speed_kmh / 3.6
        # Right turn: heading decreases (clockwise); Left turn: heading increases (counter-clockwise)
        omega = (speed_mps / radius_m) * (-1.0 if is_right else 1.0)

        num_steps = int(round(duration_sec / dt)) + 1
        poses = []
        curr_x = 0.0
        curr_y = 0.0
        curr_h = 0.0

        for i in range(num_steps):
            t = i * dt
            poses.append(VehiclePose(x=curr_x, y=curr_y, heading=curr_h, speed_mps=speed_mps, time_sec=t))
            # Kinematic step
            curr_h += omega * dt
            curr_x += speed_mps * math.cos(curr_h) * dt
            curr_y += speed_mps * math.sin(curr_h) * dt

        return poses

    @staticmethod
    def generate_stop_and_go(target_speed_kmh: float, accel_profile: str, duration_sec: float, dt: float = 0.05, turn_radius_m: float = 0.0, is_right: bool = False) -> List[VehiclePose]:
        target_v = target_speed_kmh / 3.6
        a = VehicleTrajectoryGenerator.ACCELERATION_PROFILES.get(accel_profile.upper(), 1.5)
        num_steps = int(round(duration_sec / dt)) + 1

        poses = []
        curr_x = 0.0
        curr_y = 0.0
        curr_h = 0.0
        curr_v = 0.0

        for i in range(num_steps):
            t = i * dt
            poses.append(VehiclePose(x=curr_x, y=curr_y, heading=curr_h, speed_mps=curr_v, time_sec=t))

            # Accelerate up to target speed
            if curr_v < target_v:
                curr_v = min(target_v, curr_v + a * dt)

            if turn_radius_m > 0.1:
                omega = (curr_v / turn_radius_m) * (-1.0 if is_right else 1.0)
                curr_h += omega * dt

            curr_x += curr_v * math.cos(curr_h) * dt
            curr_y += curr_v * math.sin(curr_h) * dt

        return poses
