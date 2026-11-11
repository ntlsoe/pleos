package ai.pleos.app.safetywindow.core.advisory

import ai.pleos.app.safetywindow.ui.hud.SafetyAlertLevel

/**
 * LegacyAdvisoryDiagnostic
 * [작업명령 #7] 섹션 7: Legacy Shadow 전환
 *
 * 기존 조향·방향지시등 휴리스틱 상태기계를 사용자 출력에서 차단하고 내부 진단용 상태로만 유지한다.
 * 계산된 후보값(candidate)은 진단 패널 및 감사 로그에만 기록되며, 주행 HUD 및 TTS에는 반영되지 않는다.
 */
data class LegacyAdvisoryDiagnostic(
    val candidateLevel: SafetyAlertLevel = SafetyAlertLevel.L0_IDLE,
    val candidateSteeringAngleDeg: Float = 0f,
    val candidateTurnSignal: String = "OFF",
    val candidateDirection: String = "NONE",
    val triggerReason: String = "NONE",
    val visualSuppressed: Boolean = true,
    val ttsSuppressed: Boolean = true,
    val pipeline: AdvisoryPipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
    val appliedToLiveHUD: Boolean = false,
    val diagnosticNotice: String = "진단 정보 / 실제 주행 경고에 미적용",
    val timestampMs: Long = System.currentTimeMillis()
)
