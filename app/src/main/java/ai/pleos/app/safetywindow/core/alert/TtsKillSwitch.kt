package ai.pleos.app.safetywindow.core.alert

import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline
import android.util.Log

/**
 * TtsKillSwitch
 * [작업명령 #7] 섹션 10: TTS Kill Switch
 *
 * 신규 및 기존 TTS의 최종 호출 직전에 위치하는 공통 Kill Switch.
 * Release 기본값, Scenario L1 시험 모드, Parked Validation 모드에서는 permitted=false로 고정되며,
 * 기존 코드가 TTS를 호출하더라도 오디오 덕킹 및 실제 발화를 차단하고 차단 사유를 남긴다.
 */
class TtsKillSwitch(
    var isMasterKillSwitchActive: Boolean = true,
    var isReleaseBuild: Boolean = false
) {
    private val decisionHistory = mutableListOf<TtsOutputDecision>()

    @Synchronized
    fun evaluate(
        pipeline: AdvisoryPipeline,
        message: String,
        isCarUxRestricted: Boolean = false,
        isScenarioMode: Boolean = false,
        isParkedValidation: Boolean = false,
        enableTtsFlag: Boolean = false,
        isVoiceMuted: Boolean = false
    ): TtsOutputDecision {
        val decision = when {
            isVoiceMuted -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_USER_MUTED,
                pipeline = pipeline,
                requestedMessage = message
            )
            pipeline == AdvisoryPipeline.UNKNOWN -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_UNKNOWN_PIPELINE,
                pipeline = pipeline,
                requestedMessage = message
            )
            isCarUxRestricted -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_POLICY_RESTRICTION,
                pipeline = pipeline,
                requestedMessage = message
            )
            isReleaseBuild || !enableTtsFlag -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_RELEASE_SAFE_DEFAULT,
                pipeline = pipeline,
                requestedMessage = message
            )
            isParkedValidation -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_PARKED_VALIDATION,
                pipeline = pipeline,
                requestedMessage = message
            )
            isScenarioMode -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_SCENARIO_L1_ONLY,
                pipeline = pipeline,
                requestedMessage = message
            )
            isMasterKillSwitchActive -> TtsOutputDecision(
                permitted = false,
                reason = TtsBlockReason.BLOCKED_KILL_SWITCH_ACTIVE,
                pipeline = pipeline,
                requestedMessage = message
            )
            else -> TtsOutputDecision(
                permitted = true,
                reason = TtsBlockReason.NONE,
                pipeline = pipeline,
                requestedMessage = message
            )
        }

        decisionHistory.add(decision)
        try {
            Log.d(TAG, "TTS Kill Switch Evaluation: permitted=${decision.permitted}, reason=${decision.reason}, pipeline=$pipeline, msg='$message'")
        } catch (_: Throwable) {}

        return decision
    }

    @Synchronized
    fun getHistory(): List<TtsOutputDecision> = decisionHistory.toList()

    @Synchronized
    fun clearHistory() {
        decisionHistory.clear()
    }

    companion object {
        private const val TAG = "TtsKillSwitch"
    }
}
