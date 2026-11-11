package ai.pleos.app.safetywindow.core.model

/**
 * InputSource
 *
 * 모든 센서 및 공간 정보 데이터 입력원의 기원을 엄격히 구분하는 메타데이터 식별자.
 */
enum class InputSource {
    AAOS,
    PLEOS_SDK,
    NAVIGATION,
    SCENARIO,
    REPLAY,
    STATIC_PROFILE,
    EXTERNAL_GEOJSON,
    UNKNOWN
}

/**
 * ExecutionMode
 *
 * 실제 센서 입력과 시나리오 시뮬레이션 및 Replay 입력의 무의식적 혼재를 방지하기 위한 실행 격리 모드.
 */
enum class ExecutionMode {
    REAL_INPUT,      // 실제 센서/SDK 입력만 수용 (시나리오/Replay 브로드캐스트 전면 무시)
    SCENARIO_INPUT,  // 시뮬레이션 브로드캐스트 기반 시연 (실제 센서 판단 제외)
    HYBRID_DEBUG,    // 개발 및 감사 전용 모드 (UI 및 로그에 신호별 소스 표기)
    REPLAY_INPUT     // 저장된 시계열 신호 재생 검증 전용 모드 (Debug/Benchmark 전용)
}

/**
 * SignalSample
 *
 * 단일 센서/신호 샘플에 대한 출처 및 검증 메타데이터 래퍼.
 */
data class SignalSample<T>(
    val signalName: String,
    val value: T,
    val unit: String?,
    val source: InputSource,
    val sourceVersion: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = (source == InputSource.SCENARIO)
)
