package ai.pleos.app.safetywindow.core.advisory

/**
 * Feature flags controlling live integration scope (Section 6.1 & Section 12).
 * Release default is strictly all false.
 * In Work Order #6, only [enableScenarioL1Visual] may be set to true in Debug/Scenario testing.
 */
data class LiveIntegrationFlags(
    val enableScenarioL1Visual: Boolean = false,
    val enableRealInputL1Visual: Boolean = false,
    val enableL2Visual: Boolean = false,
    val enableL2Tts: Boolean = false,
    val enableL3: Boolean = false
) {
    val enableScenarioL2Visual: Boolean get() = enableL2Visual
    val enableRealInputEngineIntegration: Boolean get() = enableRealInputL1Visual
    val enableEngineTtsIntegration: Boolean get() = enableL2Tts

    fun isReleaseSafe(): Boolean =
        !enableScenarioL1Visual && !enableRealInputL1Visual && !enableL2Visual && !enableL2Tts && !enableL3

    fun isScenarioL1OnlySafe(): Boolean =
        enableScenarioL1Visual && !enableRealInputL1Visual && !enableL2Visual && !enableL2Tts && !enableL3

    companion object {
        val RELEASE_DEFAULTS = LiveIntegrationFlags(
            enableScenarioL1Visual = false,
            enableRealInputL1Visual = false,
            enableL2Visual = false,
            enableL2Tts = false,
            enableL3 = false
        )

        val DEBUG_SCENARIO_L1_ONLY = LiveIntegrationFlags(
            enableScenarioL1Visual = true,
            enableRealInputL1Visual = false,
            enableL2Visual = false,
            enableL2Tts = false,
            enableL3 = false
        )

        val SCENARIO_TEST_L1_ONLY = DEBUG_SCENARIO_L1_ONLY
    }
}

/**
 * Experimental stability parameters to prevent rapid L0 <-> L1 oscillation (Section 8.1).
 * Not finalized product safety thresholds.
 */
data class AdvisoryStabilityPolicy(
    val enterConsecutiveCount: Int = 2,
    val exitConsecutiveCount: Int = 2,
    val minimumVisibleMillis: Long = 1500L,
    val cooldownMillis: Long = 1000L,
    val parameterClass: String = "EXPERIMENTAL_COMPARISON_ONLY"
)
