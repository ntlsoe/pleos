package ai.pleos.app.safetywindow.core.shadow

import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality

data class ShadowAdvisoryResult(
    val timestampMillis: Long,
    val zoneContext: String,
    val maneuverContext: String,

    val geometryAssessment: GeometryAssessment,
    val advisoryEligibility: AdvisoryEligibility,
    val effectiveCandidateLevel: EffectiveCandidateLevel,

    val peakPossibleRatio: Double,
    val binocularHits: Int = 0,
    val peakUniversalRatio: Double,
    val maxPerPointDurationSeconds: Double,
    val anyPointDurationSeconds: Double,
    val exposureIntegral: Double,

    val distanceToDecisionLineMeters: Double?,
    val estimatedTimeToDecisionLineSeconds: Double?,
    val availableLeadTimeSeconds: Double?,
    val requiredLeadTimeSeconds: Double?,

    val spatialQuality: SpatialQuality,
    val inputSources: Set<InputSource>,
    val reasonCodes: List<String>,
    val computationMillis: Double,

    val appliedToLiveStateMachine: Boolean = false,

    val currentSpeedMps: Double = 0.0,
    val ttsStartupLatencySeconds: Double = 0.25,
    val utteranceDurationSeconds: Double = 1.25,
    val responseAllowanceSeconds: Double = 0.80,
    val featureId: String = "UNKNOWN",
    val inputProvenance: InputProvenance? = null,
    val projectionCompletedAtNanos: Long = 0L,
    val samplingCompletedAtNanos: Long = 0L,
    val occlusionCompletedAtNanos: Long = 0L,
    val decisionCompletedAtNanos: Long = 0L
) {
    fun toJsonLine(): String {
        val sourcesStr = inputSources.joinToString(",") { "\"$it\"" }
        val reasonsStr = reasonCodes.joinToString(",") { "\"$it\"" }
        val distStr = distanceToDecisionLineMeters?.toString() ?: "null"
        val estTimeStr = estimatedTimeToDecisionLineSeconds?.toString() ?: "null"
        val availLeadStr = availableLeadTimeSeconds?.toString() ?: "null"
        val reqLeadStr = requiredLeadTimeSeconds?.toString() ?: "null"
        val provStr = inputProvenance?.toJsonObjectString() ?: "null"

        return """{"timestampMillis":$timestampMillis,"featureId":"$featureId","zoneContext":"$zoneContext","maneuverContext":"$maneuverContext","geometryAssessment":"$geometryAssessment","advisoryEligibility":"$advisoryEligibility","effectiveCandidateLevel":"$effectiveCandidateLevel","peakPossibleRatio":$peakPossibleRatio,"binocularHits":$binocularHits,"peakUniversalRatio":$peakUniversalRatio,"maxPerPointDurationSeconds":$maxPerPointDurationSeconds,"anyPointDurationSeconds":$anyPointDurationSeconds,"exposureIntegral":$exposureIntegral,"distanceToDecisionLineMeters":$distStr,"currentSpeedMps":$currentSpeedMps,"estimatedTimeToDecisionLineSeconds":$estTimeStr,"availableLeadTimeSeconds":$availLeadStr,"ttsStartupLatencySeconds":$ttsStartupLatencySeconds,"utteranceDurationSeconds":$utteranceDurationSeconds,"responseAllowanceSeconds":$responseAllowanceSeconds,"requiredLeadTimeSeconds":$reqLeadStr,"spatialQuality":"$spatialQuality","inputSources":[$sourcesStr],"inputProvenance":$provStr,"reasonCodes":[$reasonsStr],"computationMillis":$computationMillis,"appliedToLiveStateMachine":$appliedToLiveStateMachine}"""
    }
}
