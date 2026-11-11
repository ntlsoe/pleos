package ai.pleos.app.safetywindow.core.advisory

import ai.pleos.app.safetywindow.BuildConfig
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.shadow.AdvisoryEligibility
import ai.pleos.app.safetywindow.core.shadow.GeometryAssessment
import ai.pleos.app.safetywindow.core.shadow.EffectiveCandidateLevel
import ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryResult
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.model.InputSource

/** Immutable projection of a Geometry evaluation at the application's output boundary. */
data class AdvisoryDecision(
    val geometryCandidateLevel: EffectiveCandidateLevel,
    val advisoryEligibility: AdvisoryEligibility,
    val effectiveLevel: EffectiveCandidateLevel,
    val suppressionReason: String?,
    val visualAllowed: Boolean,
    val ttsAllowed: Boolean,
    val provenance: String,
    val inputMode: ExecutionMode,
    val evaluatedAt: Long
)

object AdvisoryDecisionResolver {
    /**
     * L2 requires the approved geometry rule. Release and REAL_INPUT remain fail closed
     * until a verified real spatial source and output flag are explicitly enabled.
     */
    fun resolve(
        geometry: ShadowAdvisoryResult,
        continuousOcclusionMs: Long,
        inputMode: ExecutionMode,
        userEnabled: Boolean,
        carUxRestricted: Boolean,
        visualFlag: Boolean,
        ttsFlag: Boolean,
        evaluatedAt: Long
    ): AdvisoryDecision {
        val eligibility = geometry.advisoryEligibility
        val geometryHasHits = geometry.binocularHits > 0 && geometry.geometryAssessment != GeometryAssessment.NO_OCCLUSION
        val l2GatePassed = geometry.binocularHits > 0 &&
            (geometry.peakPossibleRatio >= 0.25 || continuousOcclusionMs >= 800L)
        val candidate = when {
            !geometryHasHits -> EffectiveCandidateLevel.L0
            l2GatePassed -> EffectiveCandidateLevel.L2
            else -> EffectiveCandidateLevel.L1
        }
        val reason = when {
            carUxRestricted -> "CAR_UX_RESTRICTED"
            !userEnabled -> "USER_DISABLED"
            eligibility != AdvisoryEligibility.ELIGIBLE -> if (eligibility.name.startsWith("INELIGIBLE_")) eligibility.name else "INELIGIBLE_${eligibility.name}"
            inputMode != ExecutionMode.SCENARIO_INPUT -> "INPUT_MODE_BLOCKED"
            geometry.inputSources.contains(InputSource.UNKNOWN) ||
                !geometry.inputSources.contains(InputSource.SCENARIO) ||
                !geometry.inputSources.contains(InputSource.STATIC_PROFILE) -> "SOURCE_CONSISTENCY_BLOCKED"
            !BuildConfig.DEBUG -> "RELEASE_BUILD_OUTPUT_BLOCKED"
            geometry.spatialQuality == SpatialQuality.STALE || geometry.spatialQuality == SpatialQuality.INVALID -> "INVALID_INPUT"
            !visualFlag -> "VISUAL_OUTPUT_DISABLED"
            else -> null
        }
        val allowed = reason == null
        val level = if (allowed) candidate else EffectiveCandidateLevel.L0
        return AdvisoryDecision(
            geometryCandidateLevel = candidate,
            advisoryEligibility = eligibility,
            effectiveLevel = level,
            suppressionReason = reason,
            visualAllowed = allowed && level != EffectiveCandidateLevel.L0,
            ttsAllowed = allowed && ttsFlag && level >= EffectiveCandidateLevel.L2,
            provenance = geometry.inputSources.joinToString("+") { it.name },
            inputMode = inputMode,
            evaluatedAt = evaluatedAt
        )
    }
}
