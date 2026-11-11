package ai.pleos.app.safetywindow.core.advisory

/**
 * AdvisoryOutputFlags
 * [작업명령 #7] 섹션 6: Release 안전 기본값
 *
 * 모든 플래그의 기본값은 false이며, Release APK 및 제품 기본값은 전부 false로 강제된다.
 */
data class AdvisoryOutputFlags(
    val enableLegacyVisualL1: Boolean = false,
    val enableLegacyVisualL2: Boolean = false,
    val enableLegacyTts: Boolean = false,
    val enableScenarioL1Overlay: Boolean = false,
    val enableReplayL1Visual: Boolean = false,
    val enableOcclusionRealInputL1: Boolean = false,
    val enableOcclusionL2Visual: Boolean = false,
    val enableOcclusionTts: Boolean = false
) {
    companion object {
        /**
         * Release 안전 기본값: 모든 시각/음성 출력 플래그 false (Fail-safe)
         */
        val RELEASE_SAFE_DEFAULT = AdvisoryOutputFlags(
            enableLegacyVisualL1 = false,
            enableLegacyVisualL2 = false,
            enableLegacyTts = false,
            enableScenarioL1Overlay = false,
            enableReplayL1Visual = false,
            enableOcclusionRealInputL1 = false,
            enableOcclusionL2Visual = false,
            enableOcclusionTts = false
        )

        /**
         * Debug Scenario 시연 모드: Scenario L1 Overlay만 허용 (TTS 및 기존 HUD 출력 전부 차단)
         */
        val DEBUG_SCENARIO_DEMO = AdvisoryOutputFlags(
            enableLegacyVisualL1 = false,
            enableLegacyVisualL2 = false,
            enableLegacyTts = false,
            enableScenarioL1Overlay = true,
            enableReplayL1Visual = false,
            enableOcclusionRealInputL1 = false,
            enableOcclusionL2Visual = false,
            enableOcclusionTts = false
        )

        /**
         * Debug Replay 검증 모드: Replay L1 Visual만 허용 (TTS 및 L2/L3 전면 차단)
         */
        val DEBUG_REPLAY_VALIDATION = AdvisoryOutputFlags(
            enableLegacyVisualL1 = false,
            enableLegacyVisualL2 = false,
            enableLegacyTts = false,
            enableScenarioL1Overlay = false,
            enableReplayL1Visual = true,
            enableOcclusionRealInputL1 = false,
            enableOcclusionL2Visual = false,
            enableOcclusionTts = false
        )

        /** Synthetic Work 48 fixture: geometry HUD visual only; voice stays off. */
        val DEBUG_GEOMETRY_FIXTURE = AdvisoryOutputFlags(
            enableLegacyVisualL1 = false,
            enableLegacyVisualL2 = false,
            enableLegacyTts = false,
            enableScenarioL1Overlay = false,
            enableReplayL1Visual = false,
            enableOcclusionRealInputL1 = false,
            enableOcclusionL2Visual = true,
            enableOcclusionTts = false
        )

        /**
         * 정차 검증 모드: 주행 HUD 안내 및 TTS 비활성화, 진단 패널만 허용
         */
        val PARKED_VALIDATION_DEFAULT = AdvisoryOutputFlags(
            enableLegacyVisualL1 = false,
            enableLegacyVisualL2 = false,
            enableLegacyTts = false,
            enableScenarioL1Overlay = false,
            enableReplayL1Visual = false,
            enableOcclusionRealInputL1 = false,
            enableOcclusionL2Visual = false,
            enableOcclusionTts = false
        )
    }

    val isAllSuppressed: Boolean
        get() = !enableLegacyVisualL1 &&
                !enableLegacyVisualL2 &&
                !enableLegacyTts &&
                !enableScenarioL1Overlay &&
                !enableReplayL1Visual &&
                !enableOcclusionRealInputL1 &&
                !enableOcclusionL2Visual &&
                !enableOcclusionTts
}
