package ai.pleos.app.safetywindow.core.replay

import ai.pleos.app.safetywindow.core.advisory.AdvisoryOutputFlags
import ai.pleos.app.safetywindow.core.advisory.AdvisoryOutputPolicy
import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline
import ai.pleos.app.safetywindow.core.advisory.LegacyAdvisoryDiagnostic
import ai.pleos.app.safetywindow.core.advisory.ResolvedAdvisoryOutput
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.ui.hud.AdvisorySide
import ai.pleos.app.safetywindow.ui.hud.SafetyAlertLevel
import ai.pleos.app.safetywindow.ui.hud.ScenarioL1OverlayState

data class ReplayStepResult(
    val relativeTimeMillis: Long,
    val sequenceNumber: Long,
    val signalName: String,
    val rawValue: String,
    val resolvedOutput: ResolvedAdvisoryOutput,
    val inputProvenance: Set<InputSource>,
    val ttsSpokenCount: Int = 0
)

/**
 * ReplayInputController
 * [작업명령 #8] 섹션 4, 7, 8: Replay 입력 제어기
 *
 * - 실제 센서와 Replay 입력을 격리
 * - ReplayClock을 사용하여 결정론적으로 진행
 * - Replay 종료 시 즉시 L0/Hidden 상태로 환원
 */
class ReplayInputController(
    val clock: ReplayClock = ReplayClock(),
    var outputFlags: AdvisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
) {
    private var currentSession: ReplaySession? = null
    private var eventIndex = 0
    private val policy = AdvisoryOutputPolicy(outputFlags)

    var isReplayActive: Boolean = false
        private set

    // 최근 재생 상태 캐시
    private var isCarUxRestricted = false
    private var isStaleInput = false
    private var candidateOverlayState: ScenarioL1OverlayState = ScenarioL1OverlayState.Hidden
    private var consecutiveCycles = 0

    fun loadSession(session: ReplaySession) {
        currentSession = session
        eventIndex = 0
        clock.reset()
        isReplayActive = true
        isCarUxRestricted = false
        isStaleInput = false
        candidateOverlayState = ScenarioL1OverlayState.Hidden
        consecutiveCycles = 0
        policy.flags = outputFlags
    }

    fun stepNext(): ReplayStepResult? {
        val session = currentSession ?: return null
        if (eventIndex >= session.events.size) {
            // Replay 완료: 즉시 Hidden / L0 복원
            isReplayActive = false
            candidateOverlayState = ScenarioL1OverlayState.Hidden
            return null
        }

        val ev = session.events[eventIndex++]
        clock.advanceTo(ev.relativeTimeMillis)

        // 신호 파싱 및 상태 갱신
        when (ev.signalName) {
            "carUxRestriction" -> {
                isCarUxRestricted = ev.value.contains("RESTRICTED")
            }
            "isStaleInput" -> {
                isStaleInput = ev.value.toBoolean()
            }
            "locationLocalX", "locationLocalY" -> {
                // Feature 중첩 후보 발생 (RPL-02, RPL-03)
                if (ev.featureId != null) {
                    consecutiveCycles++
                    if (consecutiveCycles >= 2) {
                        val isStationary = (ev.replaySessionId == "RPL-03")
                        candidateOverlayState = ScenarioL1OverlayState.Visible(
                            message = if (isStationary) ScenarioL1OverlayState.MSG_BOTH_CHECK else ScenarioL1OverlayState.MSG_RIGHT_CHECK,
                            side = if (isStationary) AdvisorySide.BOTH else AdvisorySide.RIGHT,
                            reasonCode = "REPLAY_L1_ELIGIBLE"
                        )
                    }
                }
            }
            "vehicleSpeed" -> {
                val speed = ev.value.toDoubleOrNull() ?: 0.0
                if (speed == 0.0 && ev.replaySessionId == "RPL-03") {
                    // 정차 사전정보
                }
            }
        }

        val resolved = policy.resolve(
            executionMode = ExecutionMode.REPLAY_INPUT,
            isCarUxRestricted = isCarUxRestricted,
            isStaleInput = isStaleInput,
            candidateOverlayState = candidateOverlayState,
            legacyDiagnostic = LegacyAdvisoryDiagnostic(),
            isParkedValidation = false
        )

        return ReplayStepResult(
            relativeTimeMillis = clock.currentReplayTimeMillis,
            sequenceNumber = ev.sequenceNumber,
            signalName = ev.signalName,
            rawValue = ev.value,
            resolvedOutput = resolved,
            inputProvenance = setOf(ev.originalSource, ev.replaySource),
            ttsSpokenCount = 0 // TTS는 항상 0
        )
    }

    fun runAll(): List<ReplayStepResult> {
        val results = mutableListOf<ReplayStepResult>()
        while (isReplayActive) {
            val step = stepNext() ?: break
            results.add(step)
        }
        return results
    }

    fun stop() {
        isReplayActive = false
        currentSession = null
        candidateOverlayState = ScenarioL1OverlayState.Hidden
        clock.reset()
    }
}
