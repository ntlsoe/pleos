package ai.pleos.app.safetywindow.core.alert

import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline

/**
 * TtsBlockReason
 * [작업명령 #7] 섹션 10: TTS Kill Switch 필수 차단 사유
 */
enum class TtsBlockReason {
    NONE,
    BLOCKED_RELEASE_SAFE_DEFAULT,
    BLOCKED_SCENARIO_L1_ONLY,
    BLOCKED_PARKED_VALIDATION,
    BLOCKED_POLICY_RESTRICTION,
    BLOCKED_UNKNOWN_PIPELINE,
    BLOCKED_KILL_SWITCH_ACTIVE,
    BLOCKED_USER_MUTED
}

/**
 * TtsOutputDecision
 * TTS 음성 발화 최종 판정 결과
 */
data class TtsOutputDecision(
    val permitted: Boolean,
    val reason: TtsBlockReason,
    val pipeline: AdvisoryPipeline,
    val requestedMessage: String,
    val timestampMs: Long = System.currentTimeMillis()
)
