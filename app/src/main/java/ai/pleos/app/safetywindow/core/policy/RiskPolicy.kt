package ai.pleos.app.safetywindow.core.policy

/**
 * ParameterMeta
 *
 * 정책 파라미터의 근거 및 검증 상태를 보증하기 위한 메타데이터.
 */
data class ParameterMeta(
    val source: String,
    val evidenceLevel: String, // "STANDARD", "EMPIRICAL", "HEURISTIC", "EXPERIMENTAL"
    val isExperimental: Boolean,
    val notes: String
)

data class TimeRange(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    fun contains(hour: Int, minute: Int): Boolean {
        val totalMinutes = hour * 60 + minute
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        return totalMinutes in startTotal..endTotal
    }
}

/**
 * RiskPolicy
 *
 * 코드 내 하드코딩 상수를 완전히 외부화하고 실험적 파라미터 메타데이터를 유지하는 정책 객체.
 */
data class RiskPolicy(
    val schoolZoneTimeWeightEnabled: Boolean = true,
    val schoolZoneTimeRanges: List<TimeRange> = listOf(
        TimeRange(14, 0, 17, 59) // 14:00 ~ 17:59 하교 시간대
    ),
    val preActivationDistanceMeters: Double = 70.0,
    val defaultCautionDistanceMeters: Double = 150.0,
    val lowSpeedThresholdMps: Double = 8.33, // 30 km/h
    val stationaryThresholdMps: Double = 0.05, // 0.18 km/h (정차 판정 기준)
    val stationaryHoldMillis: Long = 1000L,
    val alertCooldownMillis: Long = 5000L,
    val steeringAdvisoryThresholdDeg: Double = 15.0,
    val steeringWarningThresholdDeg: Double = 25.0,

    val parameterMetadata: Map<String, ParameterMeta> = mapOf(
        "schoolZoneTimeRanges" to ParameterMeta(
            source = "행정안전부 어린이보호구역 보행사고 통계 (14~18시 집중)",
            evidenceLevel = "EMPIRICAL",
            isExperimental = false,
            notes = "하교 시간대 집중 가중치 적용"
        ),
        "preActivationDistanceMeters" to ParameterMeta(
            source = "도심지 30km/h 주행 시 반응시간(2.5초)+제동거리 기준 조기 예고 마진",
            evidenceLevel = "HEURISTIC",
            isExperimental = true,
            notes = "70m 조기 확장 파라미터는 실험적 기본값임"
        ),
        "stationaryThresholdMps" to ParameterMeta(
            source = "AAOS PERF_VEHICLE_SPEED 0.05m/s 노이즈 마진",
            evidenceLevel = "EXPERIMENTAL",
            isExperimental = true,
            notes = "법규 준수 완료 판정이 아닌 차량 정지 상태 감지 판단용"
        )
    )
)
