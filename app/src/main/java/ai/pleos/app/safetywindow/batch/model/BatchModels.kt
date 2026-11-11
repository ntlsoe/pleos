package ai.pleos.app.safetywindow.batch.model

import java.time.Instant

enum class BatchExecutionMode { BATCH_SIMULATION }
enum class BatchRunStatus { PLANNED, RUNNING, PASSED, BLOCKED, FAILED, RETRYING }
enum class ZoneFinalStatus {
    SELECTED_CROSSWALK_VERIFIED,
    SELECTED_ZONE_CONTEXT_CROSSWALK_AMBIGUOUS,
    SELECTED_ZONE_CONTEXT_POLYGON_PENDING,
    SELECTED_VWORLD_LINK_CONFIRMED,
    SELECTED_VWORLD_LINK_MANUAL_CAUTION
}
enum class EvidenceClass {
    MANUAL_VERIFIED_CROSSWALK,
    SOURCE_CONFIRMED_NODE_LINK,
    SOURCE_CONFIRMED_NODE_LINK_MANUAL_REVIEW,
    PUBLIC_ZONE_POINT_PLUS_EXISTING_CANDIDATES,
    PUBLIC_ZONE_POINT_SINGLE_CANDIDATE
}
enum class RiskFeatureType {
    SCHOOL_ZONE_CONTEXT, CROSSWALK, FOUR_WAY_INTERSECTION, T_JUNCTION,
    CURVED_ROAD, SCHOOL_GATE_CANDIDATE, NARROW_ROAD_EXIT, ROAD_EDGE
}
enum class ScenarioFamily { BASE, CURVE, INTERSECTION, T_JUNCTION, SCHOOL_GATE, NARROW_ROAD, CROSSWALK }
enum class GeometryAssessment { NO_OCCLUSION, POSSIBLE_OCCLUSION, MEANINGFUL_OCCLUSION, STRONG_OCCLUSION, INDETERMINATE }
enum class AdvisoryEligibility {
    ELIGIBLE, ELIGIBLE_INFORMATIONAL_PREARM,
    INELIGIBLE_STALE_INPUT, INELIGIBLE_LOW_SPATIAL_QUALITY,
    INELIGIBLE_UNKNOWN_DIRECTION, INELIGIBLE_OPPOSITE_SIDE,
    INELIGIBLE_INSUFFICIENT_LEAD_TIME, INELIGIBLE_POLICY_RESTRICTION,
    INELIGIBLE_UNSUPPORTED_CONTEXT, INELIGIBLE_USER_DISABLED
}
enum class CandidateLevel { L0, L1, L2, L3, NONE_DATA_INVALID }
enum class ResultStatus {
    PASS_ALL_APPLICABLE_SCENARIOS, PASS_ZONE_CONTEXT_ONLY, PASS_WITH_CAUTION,
    BLOCKED_AMBIGUOUS_LINK, BLOCKED_UNVERIFIED_FEATURE,
    FAILED_ENGINE, FAILED_DECISION_SEMANTICS, FAILED_NONDETERMINISTIC_RESULT,
    FAILED_OUTPUT_POLICY, FAILED_ARTIFACT_WRITE
}

data class ZoneRecord(
    val zoneId: String,
    val facilityName: String,
    val latitude: Double,
    val longitude: Double,
    val finalStatus: ZoneFinalStatus,
    val evidenceClass: EvidenceClass,
    val selectedLinkId: String?,
    val sourceGeometrySha256: String?,
    val riskFeatures: List<RiskFeatureRecord>,
    val allowContextSimulation: Boolean,
    val allowPreciseOcclusion: Boolean,
    val cautionReason: String?
)

data class RiskFeatureRecord(
    val featureId: String,
    val type: RiskFeatureType,
    val geometrySha256: String,
    val evidenceClass: String,
    val batchEligible: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

data class VehicleProfile(
    val vehicleProfileId: String,
    val publicDimensionSha256: String,
    val syntheticPillarProfileId: String,
    val pillarGeometrySha256: String,
    val evidenceLabel: String
)

data class EyeSample(val eyeSampleId: String, val weight: Double)
data class EyeModel(val eyeModelId: String, val sha256: String, val samples: List<EyeSample>) {
    init {
        require(samples.size == 9) { "Canonical batch eye model must contain N=9 samples" }
        require(kotlin.math.abs(samples.sumOf { it.weight } - 1.0) <= 1e-9) { "Eye weights must sum to 1.0" }
    }
}

data class SpeedPoint(val relativeTimeMillis: Long, val speedMps: Double)
data class SpeedProfile(val speedProfileId: String, val points: List<SpeedPoint>, val sha256: String)

data class ScenarioDefinition(
    val scenarioId: String,
    val family: ScenarioFamily,
    val requiredFeatureType: RiskFeatureType?,
    val speedProfileIds: List<String>,
    val expectedSilence: Boolean = false,
    val injectStaleAtMillis: Long? = null,
    val forceLowQuality: Boolean = false
)

data class BatchRunPlan(
    val runId: String,
    val zoneId: String,
    val featureId: String?,
    val riskFeatureType: RiskFeatureType,
    val vehicleProfileId: String,
    val pillarProfileId: String,
    val eyeModelId: String,
    val scenarioId: String,
    val speedProfileId: String,
    val inputManifestSha256: String,
    val engineVersion: String,
    val gitCommit: String,
    val plannedAt: Instant,
    val attemptLimit: Int = 3
)

data class EyeFrameResult(
    val eyeSampleId: String,
    val leftOccluded: Boolean,
    val rightOccluded: Boolean,
    val binocularOccluded: Boolean,
    val possibleRatio: Double,
    val universalRatio: Double,
    val exposureIntegral: Double
)

data class BatchFrameResult(
    val runId: String,
    val simulationTimeMillis: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val headingRadians: Double,
    val speedMps: Double,
    val distanceToFeatureMeters: Double?,
    val estimatedTimeToFeatureSeconds: Double?,
    val eyeResults: List<EyeFrameResult>,
    val weightedPossibleRatio: Double,
    val weightedUniversalRatio: Double,
    val maxContinuousOcclusionMillis: Long,
    val exposureIntegral: Double,
    val geometryAssessment: GeometryAssessment,
    val eligibility: AdvisoryEligibility,
    val rawCandidateLevel: CandidateLevel,
    val stabilizedLevel: CandidateLevel,
    val outputSuppressed: Boolean,
    val suppressionReason: String?,
    val ttsRequested: Boolean,
    val ttsPermitted: Boolean,
    val ttsActuallySpoken: Boolean = false,
    val engineComputeNanos: Long
)

data class BatchRunSummary(
    val runId: String,
    val zoneId: String,
    val featureId: String?,
    val vehicleProfileId: String,
    val scenarioId: String,
    val speedProfileId: String,
    val status: BatchRunStatus,
    val resultStatus: ResultStatus,
    val attemptCount: Int,
    val frameCount: Long,
    val maxLevel: CandidateLevel,
    val l0Millis: Long,
    val l1Millis: Long,
    val l2Millis: Long,
    val l3Millis: Long,
    val transitionCount: Int,
    val ttsRequestedCount: Int,
    val ttsPermittedCount: Int,
    val ttsSpokenCount: Int,
    val staleSuppressionCount: Int,
    val qualitySuppressionCount: Int,
    val peakPossibleRatio: Double,
    val peakUniversalRatio: Double,
    val peakExposureIntegral: Double,
    val maxContinuousOcclusionMillis: Long,
    val computeP50Millis: Double,
    val computeP95Millis: Double,
    val computeP99Millis: Double,
    val rawInputSha256: String,
    val rawOutputSha256: String,
    val failureCode: String?,
    val failureDetail: String?
)
