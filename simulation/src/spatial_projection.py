import math
from typing import List, Tuple, Dict, Any, Optional
from geometry import Point3

class GeoPoint:
    def __init__(self, latitude: float, longitude: float, altitude: float = 0.0):
        self.latitude = latitude
        self.longitude = longitude
        self.altitude = altitude

    def to_dict(self) -> Dict[str, float]:
        return {
            "latitude": round(self.latitude, 7),
            "longitude": round(self.longitude, 7),
            "altitude": round(self.altitude, 2)
        }

class SpatialQuality:
    VERIFIED_DEMO = "VERIFIED_DEMO"
    PUBLIC_POINT_WITH_MANUAL_DIRECTION = "PUBLIC_POINT_WITH_MANUAL_DIRECTION"
    PUBLIC_POINT_DIRECTION_UNKNOWN = "PUBLIC_POINT_DIRECTION_UNKNOWN"
    SCENARIO = "SCENARIO"
    STALE = "STALE"
    INVALID = "INVALID"

    VALID_COMPUTATION_QUALITIES = {VERIFIED_DEMO, SCENARIO, PUBLIC_POINT_WITH_MANUAL_DIRECTION}

    @classmethod
    def can_compute_shadow(cls, quality: str) -> bool:
        return quality in cls.VALID_COMPUTATION_QUALITIES

class LocalCoordinateFrame:
    """
    Local Tangent Plane ENU (East-North-Up) using WGS84 Reference Ellipsoid.
    Standard geodesy:
      a = 6378137.0 m
      f = 1 / 298.257223563
    """
    WGS84_A = 6378137.0
    WGS84_F = 1.0 / 298.257223563
    WGS84_E2 = 2.0 * WGS84_F - WGS84_F * WGS84_F

    def __init__(self, origin: GeoPoint):
        self.origin = origin
        phi = math.radians(origin.latitude)
        sin_phi = math.sin(phi)
        cos_phi = math.cos(phi)
        self.sin_phi = sin_phi
        self.cos_phi = cos_phi

        self.n_rad = self.WGS84_A / math.sqrt(1.0 - self.WGS84_E2 * sin_phi * sin_phi)
        self.m_rad = self.WGS84_A * (1.0 - self.WGS84_E2) / math.pow(1.0 - self.WGS84_E2 * sin_phi * sin_phi, 1.5)

    def geodetic_to_enu(self, pt: GeoPoint) -> Tuple[float, float, float]:
        """
        Converts (lat, lon, alt) to local (East, North, Up) in meters relative to origin.
        Explicitly excludes antimeridian (+/-180 deg) and polar regions (|lat| > 85 deg).
        """
        if abs(pt.latitude) > 85.0:
            raise ValueError("Polar regions (|latitude| > 85 deg) excluded from MVP LocalCoordinateFrame")
        if abs(pt.longitude - self.origin.longitude) > 180.0:
            raise ValueError("Antimeridian crossing excluded from MVP LocalCoordinateFrame")

        d_lat_rad = math.radians(pt.latitude - self.origin.latitude)
        d_lon_rad = math.radians(pt.longitude - self.origin.longitude)

        north = d_lat_rad * (self.m_rad + self.origin.altitude)
        east = d_lon_rad * (self.n_rad + self.origin.altitude) * self.cos_phi
        up = pt.altitude - self.origin.altitude
        return east, north, up

    def enu_to_geodetic(self, east: float, north: float, up: float = 0.0) -> GeoPoint:
        d_lat_rad = north / (self.m_rad + self.origin.altitude)
        d_lon_rad = east / ((self.n_rad + self.origin.altitude) * self.cos_phi)
        lat = self.origin.latitude + math.degrees(d_lat_rad)
        lon = self.origin.longitude + math.degrees(d_lon_rad)
        alt = self.origin.altitude + up
        return GeoPoint(lat, lon, alt)

class VehiclePoseSpatial:
    def __init__(
        self,
        local_x: float,
        local_y: float,
        heading_radians: float,
        timestamp_millis: int,
        source: str = "SCENARIO",
        quality: str = SpatialQuality.SCENARIO,
        speed_mps: float = 0.0
    ):
        self.local_x = local_x          # East in ENU
        self.local_y = local_y          # North in ENU
        self.heading_radians = heading_radians  # Clockwise azimuth from North (0 = North, pi/2 = East)
        self.timestamp_millis = timestamp_millis
        self.source = source
        self.quality = quality
        self.speed_mps = speed_mps

    def is_stale(self, current_time_millis: int, max_age_millis: int = 1500) -> bool:
        return (current_time_millis - self.timestamp_millis) > max_age_millis

    def enu_to_vehicle_body(self, east: float, north: float, up: float = 0.0) -> Point3:
        """
        Transforms ENU point to ISO 8855-aligned vehicle body coordinates:
          +X_veh: Forward
          +Y_veh: Lateral Left
          +Z_veh: Upward
        Heading is clockwise azimuth from North:
          Ahead unit vector in ENU = (sin(h), cos(h), 0)
          Left unit vector in ENU = (-cos(h), sin(h), 0)
        """
        dx = east - self.local_x
        dy = north - self.local_y
        sin_h = math.sin(self.heading_radians)
        cos_h = math.cos(self.heading_radians)

        x_veh = dx * sin_h + dy * cos_h
        y_veh = -dx * cos_h + dy * sin_h
        z_veh = up
        return Point3(round(x_veh, 6), round(y_veh, 6), round(z_veh, 6))

class RiskFeature:
    def __init__(
        self,
        feature_id: str,
        risk_type: str,
        source: str,
        geometry_method: str,
        manual_verified: bool,
        coordinate_quality: str,
        width_meters: float,
        length_meters: float,
        direction_degrees: float,
        center_enu: Tuple[float, float, float],
        source_date: str = "2026-11-07",
        notes: str = ""
    ):
        self.feature_id = feature_id
        self.risk_type = risk_type
        self.source = source
        self.geometry_method = geometry_method
        self.manual_verified = manual_verified
        self.coordinate_quality = coordinate_quality
        self.width_meters = width_meters
        self.length_meters = length_meters
        self.direction_degrees = direction_degrees
        self.center_enu = center_enu
        self.source_date = source_date
        self.notes = notes

class RiskFeatureSampler:
    @staticmethod
    def sample_feature_in_enu(
        feature: RiskFeature,
        spacing: float = 0.5
    ) -> List[Tuple[float, float, float]]:
        """
        Samples a grid of points on the corridor/rectangle in Local ENU.
        Rectangle orientation is rotated by direction_degrees relative to East/North.
        """
        cx, cy, cz = feature.center_enu
        ang_rad = math.radians(feature.direction_degrees)
        cos_a = math.cos(ang_rad)
        sin_a = math.sin(ang_rad)

        half_w = feature.width_meters / 2.0
        half_l = feature.length_meters / 2.0

        n_w = max(2, int(round(feature.width_meters / spacing)) + 1)
        n_l = max(2, int(round(feature.length_meters / spacing)) + 1)

        dw = (2.0 * half_w) / (n_w - 1)
        dl = (2.0 * half_l) / (n_l - 1)

        points_enu = []
        for i in range(n_w):
            w = -half_w + i * dw
            for j in range(n_l):
                l = -half_l + j * dl
                # Along width vector (transverse) and length vector (longitudinal)
                # direction_degrees = 90 means road along East (width along East)
                rx = cx + (w * cos_a - l * sin_a)
                ry = cy + (w * sin_a + l * cos_a)
                points_enu.append((round(rx, 4), round(ry, 4), round(cz, 4)))

        return points_enu

    @staticmethod
    def sample_feature_in_vehicle_frame(
        feature: RiskFeature,
        pose: VehiclePoseSpatial,
        spacing: float = 0.5,
        current_time_millis: Optional[int] = None,
        max_age_millis: int = 1500
    ) -> Optional[List[Point3]]:
        """
        Transforms sampled ENU points into vehicle body frame.
        Returns None if pose is stale or quality is invalid.
        """
        if current_time_millis is not None and pose.is_stale(current_time_millis, max_age_millis):
            return None

        if not SpatialQuality.can_compute_shadow(pose.quality):
            return None

        if not SpatialQuality.can_compute_shadow(feature.coordinate_quality):
            return None

        pts_enu = RiskFeatureSampler.sample_feature_in_enu(feature, spacing=spacing)
        pts_veh = [pose.enu_to_vehicle_body(e, n, u) for e, n, u in pts_enu]
        return pts_veh
