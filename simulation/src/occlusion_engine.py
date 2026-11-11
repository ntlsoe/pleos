import math
from typing import List, Dict, Any, Tuple, Optional
from geometry import Point3
from eye_model import EyePopulationModel, WeightedEyePair
from pillar_model import VehiclePillarGeometry
from risk_sampling import RiskSamplePoint
from vehicle_trajectory import VehiclePose

class TimestepOcclusionResult:
    __slots__ = (
        'time_sec', 'pose', 'sample_count',
        'universal_occluded_count', 'possible_occluded_count',
        'universal_weighted_ratio', 'possible_weighted_ratio',
        'point_states'
    )
    def __init__(
        self,
        time_sec: float,
        pose: VehiclePose,
        sample_count: int,
        universal_occluded_count: int,
        possible_occluded_count: int,
        universal_weighted_ratio: float,
        possible_weighted_ratio: float,
        point_states: List[Dict[str, Any]]
    ):
        self.time_sec = time_sec
        self.pose = pose
        self.sample_count = sample_count
        self.universal_occluded_count = universal_occluded_count
        self.possible_occluded_count = possible_occluded_count
        self.universal_weighted_ratio = universal_weighted_ratio
        self.possible_weighted_ratio = possible_weighted_ratio
        self.point_states = point_states

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timeSec": round(self.time_sec, 4),
            "sampleCount": self.sample_count,
            "universalOccludedCount": self.universal_occluded_count,
            "possibleOccludedCount": self.possible_occluded_count,
            "universalWeightedRatio": round(self.universal_weighted_ratio, 6),
            "possibleWeightedRatio": round(self.possible_weighted_ratio, 6)
        }

class SimulationTrajectoryMetrics:
    def __init__(
        self,
        scenario_id: str,
        profile_id: str,
        eye_model_id: str,
        total_duration_sec: float,
        timestep_sec: float,
        timesteps: List[TimestepOcclusionResult]
    ):
        self.scenario_id = scenario_id
        self.profile_id = profile_id
        self.eye_model_id = eye_model_id
        self.total_duration_sec = total_duration_sec
        self.timestep_sec = timestep_sec
        self.timesteps = timesteps

        self.max_per_point_continuous_duration = self._calc_max_per_point_continuous_duration()
        self.any_point_continuous_duration = self._calc_any_point_continuous_duration()
        self.exposure_integral_universal = self._calc_exposure_integral(is_universal=True)
        self.exposure_integral_possible = self._calc_exposure_integral(is_universal=False)
        self.peak_possible_ratio = max((t.possible_weighted_ratio for t in timesteps), default=0.0)
        self.peak_universal_ratio = max((t.universal_weighted_ratio for t in timesteps), default=0.0)

        # Truncation tracking (Section 6)
        self.simulation_end_time = timesteps[-1].time_sec if timesteps else 0.0
        self.was_duration_truncated = self._check_duration_truncated()
        self.trajectory_end_reason = "SIMULATION_TIME_LIMIT" if self.was_duration_truncated else "TARGET_PASSED_OR_OUT_OF_BOUNDS"

    def _check_duration_truncated(self) -> bool:
        if not self.timesteps:
            return False
        # If any point is still occluded at the final timestep, duration was truncated by simulation end
        return self.timesteps[-1].possible_occluded_count > 0

    def _calc_max_per_point_continuous_duration(self) -> float:
        if not self.timesteps or not self.timesteps[0].point_states:
            return 0.0
        n_points = len(self.timesteps[0].point_states)
        max_duration_overall = 0.0

        for pt_idx in range(n_points):
            curr_streak = 0.0
            max_pt_streak = 0.0
            for t in self.timesteps:
                is_occ = t.point_states[pt_idx]["isPossibleOccluded"]
                if is_occ:
                    curr_streak += self.timestep_sec
                    if curr_streak > max_pt_streak:
                        max_pt_streak = curr_streak
                else:
                    curr_streak = 0.0
            if max_pt_streak > max_duration_overall:
                max_duration_overall = max_pt_streak

        return round(max_duration_overall, 4)

    def _calc_any_point_continuous_duration(self) -> float:
        curr_streak = 0.0
        max_streak = 0.0
        for t in self.timesteps:
            if t.possible_occluded_count > 0:
                curr_streak += self.timestep_sec
                if curr_streak > max_streak:
                    max_streak = curr_streak
            else:
                curr_streak = 0.0
        return round(max_streak, 4)

    def _calc_exposure_integral(self, is_universal: bool) -> float:
        if len(self.timesteps) < 2:
            return 0.0
        integral = 0.0
        for i in range(len(self.timesteps) - 1):
            r0 = self.timesteps[i].universal_weighted_ratio if is_universal else self.timesteps[i].possible_weighted_ratio
            r1 = self.timesteps[i+1].universal_weighted_ratio if is_universal else self.timesteps[i+1].possible_weighted_ratio
            dt = self.timesteps[i+1].time_sec - self.timesteps[i].time_sec
            integral += 0.5 * (r0 + r1) * dt
        return round(integral, 6)

    def to_summary_dict(self) -> Dict[str, Any]:
        return {
            "scenarioId": self.scenario_id,
            "profileId": self.profile_id,
            "eyeModelId": self.eye_model_id,
            "totalDurationSec": round(self.total_duration_sec, 4),
            "timestepSec": round(self.timestep_sec, 4),
            "peakPossibleRatio": round(self.peak_possible_ratio, 6),
            "peakUniversalRatio": round(self.peak_universal_ratio, 6),
            "maxPerPointContinuousDuration": self.max_per_point_continuous_duration,
            "anyPointContinuousDuration": self.any_point_continuous_duration,
            "exposureIntegralPossible": self.exposure_integral_possible,
            "exposureIntegralUniversal": self.exposure_integral_universal,
            "timestepCount": len(self.timesteps),
            "wasDurationTruncated": self.was_duration_truncated,
            "simulationEndTime": round(self.simulation_end_time, 4),
            "trajectoryEndReason": self.trajectory_end_reason
        }

class BinocularOcclusionEngine:
    def __init__(self, vehicle_pillars: VehiclePillarGeometry, eye_population: EyePopulationModel):
        self.vehicle_pillars = vehicle_pillars
        self.eye_population = eye_population
        self.eye_samples = eye_population.generate_samples()
        self.total_eye_weight = sum(s.weight for s in self.eye_samples)
        assert self.total_eye_weight > 0, "Total eye population weight must be positive"

    def evaluate_risk_point_at_pose(
        self,
        world_pt: Point3,
        pose: VehiclePose
    ) -> Dict[str, Any]:
        """
        Evaluates a single risk sample point relative to vehicle pose across all eye samples.
        """
        # Transform world point to vehicle coordinate frame
        pt_veh = pose.world_to_vehicle(world_pt)

        # Check occlusion for each eye pair
        occluded_eye_weight = 0.0
        num_occluded_eyes = 0
        left_monocular_eyes = 0
        right_monocular_eyes = 0

        for eye_sample in self.eye_samples:
            left_eye = eye_sample.eye_pair.left
            right_eye = eye_sample.eye_pair.right

            left_occ = self.vehicle_pillars.is_occluded_by_either_pillar(left_eye, pt_veh)
            right_occ = self.vehicle_pillars.is_occluded_by_either_pillar(right_eye, pt_veh)

            if left_occ:
                left_monocular_eyes += 1
            if right_occ:
                right_monocular_eyes += 1

            # Binocular occlusion occurs ONLY if BOTH left and right rays are obstructed
            is_binocular = left_occ and right_occ
            if is_binocular:
                occluded_eye_weight += eye_sample.weight
                num_occluded_eyes += 1

        total_eyes = len(self.eye_samples)
        coverage = occluded_eye_weight / self.total_eye_weight
        is_universal = (num_occluded_eyes == total_eyes)
        is_possible = (num_occluded_eyes > 0)

        return {
            "positionVeh": (round(pt_veh.x, 4), round(pt_veh.y, 4), round(pt_veh.z, 4)),
            "coverage": round(coverage, 6),
            "isUniversalOccluded": is_universal,
            "isPossibleOccluded": is_possible,
            "leftMonocularOccluded": (left_monocular_eyes > 0),
            "rightMonocularOccluded": (right_monocular_eyes > 0),
            "leftMonocularOnly": (left_monocular_eyes > 0 and num_occluded_eyes == 0),
            "rightMonocularOnly": (right_monocular_eyes > 0 and num_occluded_eyes == 0)
        }

    def evaluate_trajectory(
        self,
        scenario_id: str,
        risk_points: List[RiskSamplePoint],
        trajectory_poses: List[VehiclePose],
        dt: float
    ) -> SimulationTrajectoryMetrics:
        timesteps_res = []
        total_risk_weight = sum(p.weight for p in risk_points)
        assert total_risk_weight > 0, "Risk point weights must sum to > 0"

        for pose in trajectory_poses:
            univ_count = 0
            poss_count = 0
            univ_weighted_sum = 0.0
            poss_weighted_sum = 0.0
            pt_states = []

            for pt in risk_points:
                res = self.evaluate_risk_point_at_pose(pt.position, pose)
                pt_states.append(res)
                if res["isUniversalOccluded"]:
                    univ_count += 1
                    univ_weighted_sum += pt.weight
                if res["isPossibleOccluded"]:
                    poss_count += 1
                    poss_weighted_sum += pt.weight

            univ_ratio = univ_weighted_sum / total_risk_weight
            poss_ratio = poss_weighted_sum / total_risk_weight

            step_res = TimestepOcclusionResult(
                time_sec=pose.time_sec,
                pose=pose,
                sample_count=len(risk_points),
                universal_occluded_count=univ_count,
                possible_occluded_count=poss_count,
                universal_weighted_ratio=univ_ratio,
                possible_weighted_ratio=poss_ratio,
                point_states=pt_states
            )
            timesteps_res.append(step_res)

        total_dur = trajectory_poses[-1].time_sec - trajectory_poses[0].time_sec if len(trajectory_poses) > 1 else 0.0

        return SimulationTrajectoryMetrics(
            scenario_id=scenario_id,
            profile_id=self.vehicle_pillars.profile_id,
            eye_model_id=self.eye_population.model_id,
            total_duration_sec=total_dur,
            timestep_sec=dt,
            timesteps=timesteps_res
        )
