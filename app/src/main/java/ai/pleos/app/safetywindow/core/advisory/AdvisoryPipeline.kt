package ai.pleos.app.safetywindow.core.advisory

/**
 * AdvisoryPipeline
 * [작업명령 #7] 섹션 4: 모든 안내 발생 경로 분류
 *
 * 1. LEGACY_HEURISTIC: 기존 조향각·방향지시등·속도 단순 임계값 상태기계 (L2/TTS 유발 경로)
 * 2. OCCLUSION_SHADOW: 3차원 양안 A필러 가림 기하 및 위험공간 투영 계산 엔진 (사용자 출력에 미연결된 내부 계산·로그)
 * 3. SCENARIO_L1_OVERLAY: 시나리오 입력 전용 L1 시각 안내 오버레이 (단일 시연 경로)
 * 4. PARKED_VALIDATION: 정차 중 가상 장애물/가림 시뮬레이션 진단 모드
 * 5. REPLAY_VALIDATION: [작업명령 #8] 저장된 시계열 신호 재현 검증 모드 (Debug L1 정적 오버레이 한정 허용)
 * 6. UNKNOWN: 미분류 경로 (Fail-closed: 발견 즉시 출력 차단)
 */
enum class AdvisoryPipeline {
    LEGACY_HEURISTIC,
    OCCLUSION_SHADOW,
    SCENARIO_L1_OVERLAY,
    PARKED_VALIDATION,
    REPLAY_VALIDATION,
    UNKNOWN;

    val isFailClosed: Boolean
        get() = this == UNKNOWN
}
