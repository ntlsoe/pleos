package ai.pleos.app.safetywindow.core.recording

import kotlin.math.cos

/**
 * RecordingPrivacyGuard
 * [작업명령 #9] 섹션 7: 기록 세션 개인정보 보호 필터
 *
 * - VIN, 사용자 이름, 단말 고유식별자, 목적지, 장기 위치이력, 음성, 병원/환자 정보 기록 전면 금지
 * - 실제 위·경도를 원본 저장하지 않고, 데모 기준점 대비 상대 Local ENU (X, Y in meters)로 비식별화 변환
 */
object RecordingPrivacyGuard {

    private val FORBIDDEN_SIGNALS = setOf(
        "vin", "vehicle_identification_number",
        "username", "user_name", "driver_name", "patient_id", "patient_name",
        "device_id", "imei", "serial_number", "mac_address",
        "destination", "route_history", "long_term_gps",
        "raw_audio", "microphone_input", "voice_recording",
        "hospital_id", "medical_record"
    )

    fun isSignalPermitted(signalName: String): Boolean {
        val normalized = signalName.lowercase().trim()
        for (forbidden in FORBIDDEN_SIGNALS) {
            if (normalized.contains(forbidden)) {
                return false
            }
        }
        return true
    }

    /**
     * WGS84 위경도를 데모 앵커 기준 상대 Local ENU 오프셋 (미터)으로 변환
     * (원본 위경도 노출 차단)
     */
    fun deidentifyCoordinatesToLocalEnu(
        latitude: Double,
        longitude: Double,
        anchorLat: Double = 37.4010,
        anchorLon: Double = 127.1060
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(anchorLat)
        val deltaLat = latitude - anchorLat
        val deltaLon = longitude - anchorLon

        // 평면 근사 변환 (미터 단위)
        val northMeters = deltaLat * 111132.92
        val eastMeters = deltaLon * (111412.84 * cos(latRad))

        return Pair(eastMeters, northMeters)
    }

    fun sanitizeValue(signalName: String, rawValue: String): String {
        require(isSignalPermitted(signalName)) {
            "개인정보 보호 위반: 금지된 신호('$signalName')는 기록할 수 없습니다."
        }
        return rawValue.trim()
    }
}
