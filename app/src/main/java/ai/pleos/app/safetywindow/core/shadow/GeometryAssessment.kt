package ai.pleos.app.safetywindow.core.shadow

/**
 * Pure geometric occlusion assessment (Stage 1 of Shadow Decision).
 * Does not consider TTS latency, lead time, or driving eligibility policies.
 */
enum class GeometryAssessment {
    NO_OCCLUSION,
    POSSIBLE_OCCLUSION,
    MEANINGFUL_OCCLUSION,
    STRONG_OCCLUSION,
    INDETERMINATE
}
