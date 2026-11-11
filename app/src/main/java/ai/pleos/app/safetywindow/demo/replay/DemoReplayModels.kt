package ai.pleos.app.safetywindow.demo.replay

/**
 * DemoReplayModels
 * [작업명령 #24] 섹션 4: Demo Replay Registry 및 Raw 프레임 모델
 */
data class DemoReplayEntry(
    val replayId: String,
    val runId: String,
    val batchItemId: String,
    val zoneId: String,
    val facilityName: String,
    val selectedLinkId: String,
    val riskFeatureId: String,
    val riskFeatureType: String,
    val vehicleProfileId: String,
    val syntheticPillarProfileId: String,
    val scenarioId: String,
    val speedProfileId: String,
    val eyeModelId: String,
    val geometryEvidenceClass: String,
    val sourceCommit: String,
    val inputCanonicalJsonSha256: String,
    val intermediateDigest: String,
    val outputCanonicalJsonSha256: String,
    val rawArtifactPath: String,
    val maxLevel: String,
    val peakPossibleRatio: Double,
    val peakUniversalRatio: Double,
    val maxOcclusionMillis: Long,
    val exposureIntegral: Double,
    val ttsRequestedCount: Int,
    val ttsPermittedCount: Int,
    val ttsSpokenCount: Int,
    val replayEligible: Boolean,
    val exclusionReason: String? = null,
    val status: String = "CANDIDATE",
    val replayPurpose: String = "HAZARD_ADVISORY_DEMO",
    val preInjectionMaxLevel: String = "",
    val postInjectionLevel: String = "",
    val expectedUserOutput: String = "",
    val safetyContract: String = "",
    val eligibilityReason: String = ""
)

/** Raw replay input only; evaluated policy and output values are excluded. */
data class DemoRawInputFrame(
    val simulationTimeMillis: Long,
    val possibleRatio: Double,
    val universalRatio: Double,
    val exposureIntegral: Double,
    val maxContinuousOcclusionMillis: Long
)
