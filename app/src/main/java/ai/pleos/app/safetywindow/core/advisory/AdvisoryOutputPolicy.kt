package ai.pleos.app.safetywindow.core.advisory

import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.ui.hud.SafetyAlertLevel
import ai.pleos.app.safetywindow.ui.hud.ScenarioL1OverlayState

/**
 * OutputResolutionPriority
 * [작업명령 #7] 섹션 9: 출력 우선순위
 *
 * 1. POLICY_RESTRICTED (CarUxRestrictions 정책 제한)
 * 2. DATA_INVALID_OR_STALE (입력 센서 지연/타임아웃)
 * 3. MODE_EXIT (시나리오 모드 종료/복귀)
 * 4. SCENARIO_L1_OVERLAY (안정화 검증된 Scenario 전용 L1)
 * 5. L0_IDLE (기본 대기 상태)
 *
 * 주의: LEGACY_HEURISTIC 경로는 사용자 출력 우선순위에서 완전히 제외된다.
 */
enum class OutputResolutionPriority {
    POLICY_RESTRICTED,
    DATA_INVALID_OR_STALE,
    MODE_EXIT,
    SCENARIO_L1_OVERLAY,
    L0_IDLE
}

data class ResolvedAdvisoryOutput(
    val priority: OutputResolutionPriority,
    val visualAlertLevel: SafetyAlertLevel,
    val scenarioL1OverlayState: ScenarioL1OverlayState,
    val ttsPermitted: Boolean,
    val activePipeline: AdvisoryPipeline,
    val suppressionReason: String = ""
)

/**
 * AdvisoryOutputPolicy
 * [작업명령 #7] 섹션 4, 6, 9: 전역 출력 결정 정책 게이트
 */
class AdvisoryOutputPolicy(
    var flags: AdvisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
) {
    fun resolve(
        executionMode: ExecutionMode,
        isCarUxRestricted: Boolean,
        isStaleInput: Boolean,
        candidateOverlayState: ScenarioL1OverlayState,
        legacyDiagnostic: LegacyAdvisoryDiagnostic,
        isParkedValidation: Boolean = false
    ): ResolvedAdvisoryOutput {
        // 정차 중 검증 모드
        if (isParkedValidation) {
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.L0_IDLE,
                visualAlertLevel = SafetyAlertLevel.PARKED_VALIDATION,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                ttsPermitted = false,
                activePipeline = AdvisoryPipeline.PARKED_VALIDATION,
                suppressionReason = "PARKED_VALIDATION_DIAGNOSTIC_ONLY"
            )
        }

        // 우선순위 1: POLICY_RESTRICTED
        if (isCarUxRestricted) {
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.POLICY_RESTRICTED,
                visualAlertLevel = SafetyAlertLevel.RESTRICTED_MODE,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                ttsPermitted = false,
                activePipeline = AdvisoryPipeline.UNKNOWN,
                suppressionReason = "BLOCKED_BY_CAR_UX_RESTRICTIONS"
            )
        }

        // 우선순위 2: DATA_INVALID_OR_STALE
        if (isStaleInput) {
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.DATA_INVALID_OR_STALE,
                visualAlertLevel = SafetyAlertLevel.L0_IDLE,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                ttsPermitted = false,
                activePipeline = AdvisoryPipeline.UNKNOWN,
                suppressionReason = "BLOCKED_BY_STALE_OR_INVALID_DATA"
            )
        }

        // 우선순위 3: MODE_EXIT 및 모드 격리
        if (executionMode == ExecutionMode.REPLAY_INPUT) {
            // Replay 모드: Debug Replay 플래그 허용 시에만 정적 L1 오버레이 허용
            if (flags.enableReplayL1Visual && candidateOverlayState is ScenarioL1OverlayState.Visible) {
                return ResolvedAdvisoryOutput(
                    priority = OutputResolutionPriority.SCENARIO_L1_OVERLAY,
                    visualAlertLevel = SafetyAlertLevel.L0_IDLE,
                    scenarioL1OverlayState = candidateOverlayState,
                    ttsPermitted = false, // Replay TTS는 전면 차단
                    activePipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    suppressionReason = "NONE_REPLAY_L1_ACTIVE"
                )
            }
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.L0_IDLE,
                visualAlertLevel = SafetyAlertLevel.L0_IDLE,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                ttsPermitted = false,
                activePipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                suppressionReason = "REPLAY_OUTPUT_SUPPRESSED_OR_IDLE"
            )
        }

        if (executionMode != ExecutionMode.SCENARIO_INPUT) {
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.MODE_EXIT,
                visualAlertLevel = SafetyAlertLevel.L0_IDLE,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                ttsPermitted = false,
                activePipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                suppressionReason = "REAL_INPUT_MODE_SCENARIO_OVERLAY_BLOCKED"
            )
        }

        // 우선순위 4: SCENARIO_L1_OVERLAY (Debug 시나리오 플래그 허용 시)
        if (flags.enableScenarioL1Overlay && candidateOverlayState is ScenarioL1OverlayState.Visible) {
            return ResolvedAdvisoryOutput(
                priority = OutputResolutionPriority.SCENARIO_L1_OVERLAY,
                visualAlertLevel = SafetyAlertLevel.L0_IDLE, // 기본 HUD 레벨은 L0_IDLE 유지
                scenarioL1OverlayState = candidateOverlayState,
                ttsPermitted = false, // Scenario L1 시험에서는 TTS 차단 (TTS 0회)
                activePipeline = AdvisoryPipeline.SCENARIO_L1_OVERLAY,
                suppressionReason = "NONE_SCENARIO_L1_ACTIVE"
            )
        }

        // 우선순위 5: L0_IDLE
        return ResolvedAdvisoryOutput(
            priority = OutputResolutionPriority.L0_IDLE,
            visualAlertLevel = SafetyAlertLevel.L0_IDLE,
            scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
            ttsPermitted = false,
            activePipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
            suppressionReason = "L0_IDLE_DEFAULT"
        )
    }
}
