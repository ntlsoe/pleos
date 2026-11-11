package ai.pleos.app.safetywindow.core.shadow

/**
 * Final effective candidate level combining GeometryAssessment and AdvisoryEligibility (Stage 3 of Shadow Decision).
 * When AdvisoryEligibility != ELIGIBLE, EffectiveCandidateLevel is strictly L0 or NONE_DATA_INVALID.
 */
enum class EffectiveCandidateLevel {
    L0,
    L1,
    L2,
    L3,
    NONE_DATA_INVALID
}
