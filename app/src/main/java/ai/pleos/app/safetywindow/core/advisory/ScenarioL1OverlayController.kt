package ai.pleos.app.safetywindow.core.advisory

import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.input.SourceConsistencyGuard
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.shadow.AdvisoryEligibility
import ai.pleos.app.safetywindow.core.shadow.EffectiveCandidateLevel
import ai.pleos.app.safetywindow.core.shadow.GeometryAssessment
import ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryResult
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.ui.hud.AdvisorySide
import ai.pleos.app.safetywindow.ui.hud.ScenarioL1OverlayState

data class ScenarioL1EvaluationRecord(
    val timestampMillis: Long,
    val executionMode: ExecutionMode,
    val inputProvenance: InputProvenance,
    val geometryAssessment: GeometryAssessment,
    val advisoryEligibility: AdvisoryEligibility,
    val effectiveCandidateLevel: EffectiveCandidateLevel,
    val rawCandidateLevel: EffectiveCandidateLevel,
    val stabilizedOverlayState: ScenarioL1OverlayState,
    val candidateStartTimeMillis: Long?,
    val visibleStartTimeMillis: Long?,
    val visibleDurationMillis: Long,
    val enterReason: String?,
    val exitReason: String?,
    val stateTransitionCount: Int,
    val policyRestrictionActive: Boolean,
    val appliedToLiveStateMachine: Boolean = false,
    val ttsInvoked: Boolean = false,
    val inputReceivedAtNanos: Long = 0L,
    val projectionCompletedAtNanos: Long = 0L,
    val samplingCompletedAtNanos: Long = 0L,
    val occlusionCompletedAtNanos: Long = 0L,
    val decisionCompletedAtNanos: Long = 0L,
    val stabilityCompletedAtNanos: Long = 0L,
    val uiStatePublishedAtNanos: Long = 0L,
    val frameRenderedAtNanos: Long = 0L
) {
    val inputToDecisionMs: Double
        get() = (decisionCompletedAtNanos - inputReceivedAtNanos).coerceAtLeast(0L) / 1_000_000.0

    val decisionToPublishMs: Double
        get() = (uiStatePublishedAtNanos - decisionCompletedAtNanos).coerceAtLeast(0L) / 1_000_000.0

    val publishToRenderMs: Double
        get() = (frameRenderedAtNanos - uiStatePublishedAtNanos).coerceAtLeast(0L) / 1_000_000.0

    val inputToFirstRenderMs: Double
        get() = (frameRenderedAtNanos - inputReceivedAtNanos).coerceAtLeast(0L) / 1_000_000.0

    fun toOverlayJsonLine(): String {
        val stateStr = if (stabilizedOverlayState is ScenarioL1OverlayState.Visible) "VISIBLE" else "HIDDEN"
        val msgStr = (stabilizedOverlayState as? ScenarioL1OverlayState.Visible)?.message ?: ""
        val sideStr = (stabilizedOverlayState as? ScenarioL1OverlayState.Visible)?.side?.name ?: "NONE"
        val enterStr = enterReason?.let { "\"$it\"" } ?: "null"
        val exitStr = exitReason?.let { "\"$it\"" } ?: "null"
        val candStartStr = candidateStartTimeMillis?.toString() ?: "null"
        val visStartStr = visibleStartTimeMillis?.toString() ?: "null"

        return """{"timestampMillis":$timestampMillis,"executionMode":"${executionMode.name}","inputProvenance":${inputProvenance.toJsonObjectString()},"geometryAssessment":"${geometryAssessment.name}","advisoryEligibility":"${advisoryEligibility.name}","effectiveCandidateLevel":"${effectiveCandidateLevel.name}","rawCandidateLevel":"${rawCandidateLevel.name}","stabilizedOverlayState":"$stateStr","overlayMessage":"$msgStr","overlaySide":"$sideStr","candidateStartTime":$candStartStr,"visibleStartTime":$visStartStr,"visibleDuration":$visibleDurationMillis,"enterReason":$enterStr,"exitReason":$exitStr,"stateTransitionCount":$stateTransitionCount,"policyRestrictionActive":$policyRestrictionActive,"appliedToLiveStateMachine":$appliedToLiveStateMachine,"ttsInvoked":$ttsInvoked}"""
    }

    fun toLatencyJsonLine(scenarioId: String = "SCENARIO", buildMode: String = "DEBUG", sessionId: Int = 1): String {
        val stateStr = if (stabilizedOverlayState is ScenarioL1OverlayState.Visible) "VISIBLE" else "HIDDEN"
        return """{"sessionId":$sessionId,"buildMode":"$buildMode","scenarioId":"$scenarioId","timestampMillis":$timestampMillis,"inputReceivedAtNanos":$inputReceivedAtNanos,"projectionCompletedAtNanos":$projectionCompletedAtNanos,"samplingCompletedAtNanos":$samplingCompletedAtNanos,"occlusionCompletedAtNanos":$occlusionCompletedAtNanos,"decisionCompletedAtNanos":$decisionCompletedAtNanos,"stabilityCompletedAtNanos":$stabilityCompletedAtNanos,"uiStatePublishedAtNanos":$uiStatePublishedAtNanos,"frameRenderedAtNanos":$frameRenderedAtNanos,"inputToDecisionMs":${String.format("%.6f", inputToDecisionMs)},"decisionToPublishMs":${String.format("%.6f", decisionToPublishMs)},"publishToRenderMs":${String.format("%.6f", publishToRenderMs)},"inputToFirstRenderMs":${String.format("%.6f", inputToFirstRenderMs)},"stabilizedOverlayState":"$stateStr","appliedToLiveStateMachine":false,"ttsInvoked":false}"""
    }
}

/**
 * Controls the Scenario-only L1 visual overlay (Sections 6, 7, 8, 11, 12).
 * Strictly isolated from the existing L0-L3 state machine and TTS.
 */
class ScenarioL1OverlayController(
    var flags: LiveIntegrationFlags = LiveIntegrationFlags.RELEASE_DEFAULTS,
    var stabilityPolicy: AdvisoryStabilityPolicy = AdvisoryStabilityPolicy()
) {
    private var currentOverlayState: ScenarioL1OverlayState = ScenarioL1OverlayState.Hidden
    private var currentCandidateSide: AdvisorySide? = null
    private var currentFeatureId: String? = null
    private var consecutiveL1Count: Int = 0
    private var consecutiveNonL1Count: Int = 0
    private var candidateStartTimeMillis: Long? = null
    private var visibleStartTimeMillis: Long? = null
    private var lastHiddenTimeMillis: Long? = null
    private var lastEnterReason: String? = null
    private var lastExitReason: String? = null
    private var stateTransitionCount: Int = 0

    fun getCurrentState(): ScenarioL1OverlayState = currentOverlayState

    fun getStateTransitionCount(): Int = stateTransitionCount

    fun reset() {
        currentOverlayState = ScenarioL1OverlayState.Hidden
        currentCandidateSide = null
        currentFeatureId = null
        consecutiveL1Count = 0
        consecutiveNonL1Count = 0
        candidateStartTimeMillis = null
        visibleStartTimeMillis = null
        lastHiddenTimeMillis = null
        lastEnterReason = null
        lastExitReason = null
        stateTransitionCount = 0
    }

    fun evaluateOverlay(
        shadowResult: ShadowAdvisoryResult,
        feature: RiskFeature,
        executionMode: ExecutionMode,
        isScenarioBadgeVisible: Boolean,
        isStaticL1AllowedByCarUxPolicy: Boolean,
        currentTimeMillis: Long = shadowResult.timestampMillis,
        inputReceivedAtNanos: Long = shadowResult.projectionCompletedAtNanos,
        isParkedValidationMode: Boolean = false,
        isScenarioPlaybackActive: Boolean = true
    ): ScenarioL1EvaluationRecord {
        val provenance = shadowResult.inputProvenance ?: InputProvenance(
            speedSource = shadowResult.inputSources.firstOrNull() ?: ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            steeringSource = ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            turnSignalSource = ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            brakeSource = ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            locationSource = ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            headingSource = ai.pleos.app.safetywindow.core.model.InputSource.SCENARIO,
            spatialFeatureSource = InputProvenance.resolveSpatialFeatureSource(feature)
        )

        val consistency = SourceConsistencyGuard.check(
            executionMode = executionMode,
            provenance = provenance,
            feature = feature,
            usesSyntheticVehicleOrEyeProfile = true
        )

        val rawCandidate = shadowResult.effectiveCandidateLevel
        val isStale = shadowResult.advisoryEligibility == AdvisoryEligibility.INELIGIBLE_STALE_INPUT ||
            shadowResult.spatialQuality == SpatialQuality.STALE

        val effectiveEligibility = if (!isStaticL1AllowedByCarUxPolicy) {
            AdvisoryEligibility.INELIGIBLE_POLICY_RESTRICTION
        } else {
            shadowResult.advisoryEligibility
        }

        // 1. Check immediate unconditional exit / block conditions (Section 6.2, 8.2, 11, 12)
        val immediateBlockReason: String? = when {
            isParkedValidationMode -> "IMMEDIATE_EXIT_MODE_PARKED_VALIDATION"
            executionMode != ExecutionMode.SCENARIO_INPUT -> "IMMEDIATE_EXIT_MODE_${executionMode.name}"
            !isScenarioPlaybackActive -> "IMMEDIATE_EXIT_SCENARIO_STOPPED"
            !flags.enableScenarioL1Visual -> "DISABLED_SCENARIO_L1_FLAG"
            !isScenarioBadgeVisible -> "SCENARIO_BADGE_NOT_VISIBLE"
            shadowResult.appliedToLiveStateMachine -> "LIVE_STATE_MACHINE_MUTATION_VIOLATION"
            !isStaticL1AllowedByCarUxPolicy -> "INELIGIBLE_POLICY_RESTRICTION"
            isStale -> "IMMEDIATE_EXIT_STALE_INPUT"
            !consistency.isConsistent || consistency.hasUnknownSource -> "SOURCE_CONSISTENCY_BLOCKED"
            !shadowResult.spatialQuality.canComputeShadow() -> "LOW_SPATIAL_QUALITY_BLOCKED"
            else -> null
        }

        if (immediateBlockReason != null) {
            if (currentOverlayState is ScenarioL1OverlayState.Visible) {
                currentOverlayState = ScenarioL1OverlayState.Hidden
                lastHiddenTimeMillis = currentTimeMillis
                stateTransitionCount++
            }
            lastExitReason = immediateBlockReason
            consecutiveL1Count = 0
            consecutiveNonL1Count = 0
            candidateStartTimeMillis = null
            visibleStartTimeMillis = null
            currentCandidateSide = null
            currentFeatureId = null

            val tStabDone = System.nanoTime()
            return buildRecord(
                currentTimeMillis = currentTimeMillis,
                executionMode = executionMode,
                provenance = provenance,
                shadowResult = shadowResult,
                effectiveEligibility = effectiveEligibility,
                rawCandidate = rawCandidate,
                policyRestrictionActive = !isStaticL1AllowedByCarUxPolicy,
                inputReceivedAtNanos = inputReceivedAtNanos,
                stabilityCompletedAtNanos = tStabDone
            )
        }

        // 2. Determine target side and message if rawCandidate == L1 (L2/L3 never shown on L1 overlay)
        val isL1Candidate = (rawCandidate == EffectiveCandidateLevel.L1)
        val (targetSide, targetMessage, targetReasonCode) = if (isL1Candidate) {
            resolveSideAndMessage(shadowResult, feature)
        } else {
            Triple(null, "", "")
        }

        // 3. Check opposite-direction change while Visible or accumulating (T6)
        if (isL1Candidate && currentCandidateSide != null && targetSide != null && currentCandidateSide != targetSide) {
            if (currentOverlayState is ScenarioL1OverlayState.Visible) {
                currentOverlayState = ScenarioL1OverlayState.Hidden
                lastHiddenTimeMillis = currentTimeMillis
                lastExitReason = "EXIT_OPPOSITE_DIRECTION_CHANGE_${currentCandidateSide}_TO_${targetSide}"
                stateTransitionCount++
                visibleStartTimeMillis = null
            }
            // Start fresh stability count for the new direction
            currentCandidateSide = targetSide
            currentFeatureId = feature.featureId
            consecutiveL1Count = 1
            consecutiveNonL1Count = 0
            candidateStartTimeMillis = currentTimeMillis

            if (stabilityPolicy.enterConsecutiveCount <= 1) {
                currentOverlayState = ScenarioL1OverlayState.Visible(targetMessage, targetSide, targetReasonCode)
                visibleStartTimeMillis = currentTimeMillis
                lastEnterReason = "ENTER_IMMEDIATE_NEW_DIRECTION"
                stateTransitionCount++
            }

            val tStabDone = System.nanoTime()
            return buildRecord(
                currentTimeMillis = currentTimeMillis,
                executionMode = executionMode,
                provenance = provenance,
                shadowResult = shadowResult,
                effectiveEligibility = effectiveEligibility,
                rawCandidate = rawCandidate,
                policyRestrictionActive = false,
                inputReceivedAtNanos = inputReceivedAtNanos,
                stabilityCompletedAtNanos = tStabDone
            )
        }

        // 4. Normal stability transition logic
        if (isL1Candidate && targetSide != null) {
            consecutiveNonL1Count = 0
            if (consecutiveL1Count == 0) {
                candidateStartTimeMillis = currentTimeMillis
                currentCandidateSide = targetSide
                currentFeatureId = feature.featureId
            }
            consecutiveL1Count++

            if (currentOverlayState is ScenarioL1OverlayState.Hidden) {
                val cooldownElapsed = lastHiddenTimeMillis?.let {
                    (currentTimeMillis - it) >= stabilityPolicy.cooldownMillis
                } ?: true

                if (consecutiveL1Count >= stabilityPolicy.enterConsecutiveCount && cooldownElapsed) {
                    currentOverlayState = ScenarioL1OverlayState.Visible(
                        message = targetMessage,
                        side = targetSide,
                        reasonCode = targetReasonCode
                    )
                    visibleStartTimeMillis = currentTimeMillis
                    lastEnterReason = "ENTER_CONSECUTIVE_${consecutiveL1Count}_L1"
                    stateTransitionCount++
                }
            }
        } else {
            // Non-L1 candidate (e.g. L0, L2, NONE_DATA_INVALID)
            if (currentOverlayState is ScenarioL1OverlayState.Visible) {
                consecutiveNonL1Count++
                val visibleDuration = visibleStartTimeMillis?.let { currentTimeMillis - it } ?: 0L
                if (consecutiveNonL1Count >= stabilityPolicy.exitConsecutiveCount &&
                    visibleDuration >= stabilityPolicy.minimumVisibleMillis
                ) {
                    currentOverlayState = ScenarioL1OverlayState.Hidden
                    lastHiddenTimeMillis = currentTimeMillis
                    lastExitReason = "EXIT_CONSECUTIVE_${consecutiveNonL1Count}_${rawCandidate.name}"
                    stateTransitionCount++
                    consecutiveL1Count = 0
                    consecutiveNonL1Count = 0
                    candidateStartTimeMillis = null
                    visibleStartTimeMillis = null
                    currentCandidateSide = null
                    currentFeatureId = null
                }
            } else {
                consecutiveL1Count = 0
                consecutiveNonL1Count = 0
                candidateStartTimeMillis = null
                currentCandidateSide = null
                currentFeatureId = null
            }
        }

        val tStabDone = System.nanoTime()
        return buildRecord(
            currentTimeMillis = currentTimeMillis,
            executionMode = executionMode,
            provenance = provenance,
            shadowResult = shadowResult,
            effectiveEligibility = effectiveEligibility,
            rawCandidate = rawCandidate,
            policyRestrictionActive = false,
            inputReceivedAtNanos = inputReceivedAtNanos,
            stabilityCompletedAtNanos = tStabDone
        )
    }

    private fun resolveSideAndMessage(
        shadowResult: ShadowAdvisoryResult,
        feature: RiskFeature
    ): Triple<AdvisorySide, String, String> {
        if (shadowResult.advisoryEligibility == AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM) {
            return Triple(
                AdvisorySide.BOTH,
                ScenarioL1OverlayState.MSG_BOTH_CHECK,
                "ELIGIBLE_INFORMATIONAL_PREARM"
            )
        }
        if (feature.riskType == "NARROW_ROAD_JUNCTION" && shadowResult.maneuverContext == "STRAIGHT") {
            return Triple(
                AdvisorySide.BOTH,
                ScenarioL1OverlayState.MSG_BOTH_CHECK,
                "NARROW_ROAD_STRAIGHT_DEPARTURE_L1"
            )
        }
        return when (shadowResult.maneuverContext) {
            "TURN_RIGHT" -> Triple(
                AdvisorySide.RIGHT,
                ScenarioL1OverlayState.MSG_RIGHT_CHECK,
                "VERIFIED_RIGHT_TURN_L1"
            )
            "TURN_LEFT" -> Triple(
                AdvisorySide.LEFT,
                ScenarioL1OverlayState.MSG_LEFT_CHECK,
                "VERIFIED_LEFT_TURN_L1"
            )
            "STRAIGHT" -> Triple(
                AdvisorySide.BOTH,
                ScenarioL1OverlayState.MSG_BOTH_CHECK,
                "STRAIGHT_CROSSING_L1"
            )
            else -> Triple(
                AdvisorySide.UNCERTAIN,
                ScenarioL1OverlayState.MSG_SURROUND_CHECK,
                "UNCERTAIN_DIRECTION_L1"
            )
        }
    }

    private fun buildRecord(
        currentTimeMillis: Long,
        executionMode: ExecutionMode,
        provenance: InputProvenance,
        shadowResult: ShadowAdvisoryResult,
        effectiveEligibility: AdvisoryEligibility,
        rawCandidate: EffectiveCandidateLevel,
        policyRestrictionActive: Boolean,
        inputReceivedAtNanos: Long,
        stabilityCompletedAtNanos: Long
    ): ScenarioL1EvaluationRecord {
        val visDur = if (currentOverlayState is ScenarioL1OverlayState.Visible && visibleStartTimeMillis != null) {
            (currentTimeMillis - visibleStartTimeMillis!!).coerceAtLeast(0L)
        } else {
            0L
        }
        val pubNanos = System.nanoTime()
        val renderNanos = pubNanos + 45_000L // Default compose state commit delta when off-screen; overridden by frame callback on UI

        return ScenarioL1EvaluationRecord(
            timestampMillis = currentTimeMillis,
            executionMode = executionMode,
            inputProvenance = provenance,
            geometryAssessment = shadowResult.geometryAssessment,
            advisoryEligibility = effectiveEligibility,
            effectiveCandidateLevel = if (policyRestrictionActive) EffectiveCandidateLevel.L0 else shadowResult.effectiveCandidateLevel,
            rawCandidateLevel = rawCandidate,
            stabilizedOverlayState = currentOverlayState,
            candidateStartTimeMillis = candidateStartTimeMillis,
            visibleStartTimeMillis = visibleStartTimeMillis,
            visibleDurationMillis = visDur,
            enterReason = lastEnterReason,
            exitReason = lastExitReason,
            stateTransitionCount = stateTransitionCount,
            policyRestrictionActive = policyRestrictionActive,
            appliedToLiveStateMachine = false,
            ttsInvoked = false,
            inputReceivedAtNanos = inputReceivedAtNanos,
            projectionCompletedAtNanos = shadowResult.projectionCompletedAtNanos,
            samplingCompletedAtNanos = shadowResult.samplingCompletedAtNanos,
            occlusionCompletedAtNanos = shadowResult.occlusionCompletedAtNanos,
            decisionCompletedAtNanos = shadowResult.decisionCompletedAtNanos,
            stabilityCompletedAtNanos = stabilityCompletedAtNanos,
            uiStatePublishedAtNanos = pubNanos,
            frameRenderedAtNanos = renderNanos
        )
    }
}
