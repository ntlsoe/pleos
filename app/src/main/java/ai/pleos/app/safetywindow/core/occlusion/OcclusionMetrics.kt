package ai.pleos.app.safetywindow.core.occlusion

data class PointOcclusionEvaluation(
    val positionVeh: Point3,
    val coverage: Double,
    val isUniversalOccluded: Boolean,
    val isPossibleOccluded: Boolean
)

data class TimestepOcclusionResult(
    val timeSec: Double,
    val pose: VehiclePose,
    val sampleCount: Int,
    val universalOccludedCount: Int,
    val possibleOccludedCount: Int,
    val universalWeightedRatio: Double,
    val possibleWeightedRatio: Double,
    val pointStates: List<PointOcclusionEvaluation> = emptyList()
)

data class SimulationTrajectoryMetrics(
    val scenarioId: String,
    val profileId: String,
    val eyeModelId: String,
    val totalDurationSec: Double,
    val timestepSec: Double,
    val peakPossibleRatio: Double,
    val peakUniversalRatio: Double,
    val maxPerPointContinuousDuration: Double,
    val anyPointContinuousDuration: Double,
    val exposureIntegralPossible: Double,
    val exposureIntegralUniversal: Double,
    val wasDurationTruncated: Boolean,
    val simulationEndTime: Double,
    val trajectoryEndReason: String,
    val timesteps: List<TimestepOcclusionResult>
) {
    companion object {
        fun calculateFromTimesteps(
            scenarioId: String,
            profileId: String,
            eyeModelId: String,
            timestepSec: Double,
            timesteps: List<TimestepOcclusionResult>
        ): SimulationTrajectoryMetrics {
            val totalDuration = if (timesteps.size > 1) {
                timesteps.last().timeSec - timesteps.first().timeSec
            } else 0.0

            val peakPossible = timesteps.maxOfOrNull { it.possibleWeightedRatio } ?: 0.0
            val peakUniversal = timesteps.maxOfOrNull { it.universalWeightedRatio } ?: 0.0

            val simEndTime = timesteps.lastOrNull()?.timeSec ?: 0.0
            val wasTruncated = (timesteps.lastOrNull()?.possibleOccludedCount ?: 0) > 0
            val endReason = if (wasTruncated) "SIMULATION_TIME_LIMIT" else "TARGET_PASSED_OR_OUT_OF_BOUNDS"

            // Max per-point continuous duration
            var maxPerPoint = 0.0
            if (timesteps.isNotEmpty() && timesteps[0].pointStates.isNotEmpty()) {
                val numPoints = timesteps[0].pointStates.size
                for (ptIdx in 0 until numPoints) {
                    var streak = 0.0
                    var maxPtStreak = 0.0
                    for (t in timesteps) {
                        if (t.pointStates[ptIdx].isPossibleOccluded) {
                            streak += timestepSec
                            if (streak > maxPtStreak) maxPtStreak = streak
                        } else {
                            streak = 0.0
                        }
                    }
                    if (maxPtStreak > maxPerPoint) maxPerPoint = maxPtStreak
                }
            }

            // Any point continuous duration
            var streakAny = 0.0
            var maxStreakAny = 0.0
            for (t in timesteps) {
                if (t.possibleOccludedCount > 0) {
                    streakAny += timestepSec
                    if (streakAny > maxStreakAny) maxStreakAny = streakAny
                } else {
                    streakAny = 0.0
                }
            }

            // Trapezoidal exposure integrals
            var integralPoss = 0.0
            var integralUniv = 0.0
            for (i in 0 until timesteps.size - 1) {
                val dt = timesteps[i + 1].timeSec - timesteps[i].timeSec
                integralPoss += 0.5 * (timesteps[i].possibleWeightedRatio + timesteps[i + 1].possibleWeightedRatio) * dt
                integralUniv += 0.5 * (timesteps[i].universalWeightedRatio + timesteps[i + 1].universalWeightedRatio) * dt
            }

            return SimulationTrajectoryMetrics(
                scenarioId = scenarioId,
                profileId = profileId,
                eyeModelId = eyeModelId,
                totalDurationSec = totalDuration,
                timestepSec = timestepSec,
                peakPossibleRatio = peakPossible,
                peakUniversalRatio = peakUniversal,
                maxPerPointContinuousDuration = maxPerPoint,
                anyPointContinuousDuration = maxStreakAny,
                exposureIntegralPossible = integralPoss,
                exposureIntegralUniversal = integralUniv,
                wasDurationTruncated = wasTruncated,
                simulationEndTime = simEndTime,
                trajectoryEndReason = endReason,
                timesteps = timesteps
            )
        }
    }
}
