package ai.pleos.app.safetywindow.core.shadow

/**
 * Eligibility of the geometric occlusion assessment to become an output advisory (Stage 2 of Shadow Decision).
 */
enum class AdvisoryEligibility {
    ELIGIBLE,
    ELIGIBLE_INFORMATIONAL_PREARM,
    INELIGIBLE_INSUFFICIENT_LEAD_TIME,
    INELIGIBLE_STALE_INPUT,
    INELIGIBLE_LOW_SPATIAL_QUALITY,
    INELIGIBLE_UNKNOWN_DIRECTION,
    INELIGIBLE_OPPOSITE_SIDE,
    INELIGIBLE_UNSUPPORTED_CONTEXT,
    INELIGIBLE_POLICY_RESTRICTION,
    INELIGIBLE_USER_DISABLED
}
