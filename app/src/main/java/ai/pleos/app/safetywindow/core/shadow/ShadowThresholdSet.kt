package ai.pleos.app.safetywindow.core.shadow

/**
 * Configurable experimental parameters for Shadow Advisory Mode comparison.
 * Not product safety thresholds; response allowance is an experimental parameter.
 */
data class ShadowThresholdSet(
    val ttsStartupLatencySeconds: Double = 0.25,
    val utteranceDurationSeconds: Double = 1.25,
    val responseAllowanceSeconds: Double = 0.80,
    val minimumContinuousDurationSeconds: Double = 0.5,
    val minimumWeightedRatio: Double = 0.05,
    val strongWeightedRatio: Double = 0.30,
    val maximumSpatialAgeMillis: Long = 1500L,
    val maxSpeedAgeMillis: Long = 500L,
    val maxSteeringAgeMillis: Long = 500L,
    val maxTurnSignalAgeMillis: Long = 1000L,
    val maxSpatialFeatureAgeMillis: Long = 5000L,
    val parameterClass: String = "EXPERIMENTAL_COMPARISON_ONLY",
    val leadTimeParameterClass: String = "EXPERIMENTAL_COMPARISON_PARAMETER"
) {
    val requiredLeadTimeSeconds: Double
        get() = ttsStartupLatencySeconds + utteranceDurationSeconds + responseAllowanceSeconds
}
