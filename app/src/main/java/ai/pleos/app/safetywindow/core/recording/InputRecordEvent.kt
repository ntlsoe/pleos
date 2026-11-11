package ai.pleos.app.safetywindow.core.recording

import ai.pleos.app.safetywindow.core.model.InputSource
import java.security.MessageDigest

/**
 * AcquisitionPath
 * [작업명령 #10] 섹션 5: Recorder 이벤트 획득 경로
 */
enum class AcquisitionPath {
    AAOS_CAR_PROPERTY,
    PLEOS_SDK_CALLBACK,
    ANDROID_FRAMEWORK,
    VIRTUAL_CONSOLE_VHAL,
    ADB_SCENARIO_BROADCAST,
    MOCK_LOCATION,
    STATIC_TEST_FIXTURE,
    UNRESOLVED
}

/**
 * InputRecordEvent
 * [작업명령 #9, #10] 실제 및 시뮬레이션 입력 기록 이벤트
 *
 * 개인정보, VIN, 단말 고유값, 목적지, 환자 정보 일체 배제
 * [작업명령 #10] 13개 원천 감사 필드 전수 반영
 */
data class InputRecordEvent(
    val recordingSessionId: String,
    val relativeTimeMillis: Long,
    val originalTimestampMillis: Long,
    val signalName: String,
    val value: String,
    val unit: String?,
    val source: InputSource,
    val quality: String,
    val capabilityStatus: String,
    val sequenceNumber: Long,
    val checksum: String = "",
    val privacyTransform: String = "LOCAL_ENU_DEIDENTIFIED",
    // [작업명령 #10] 원천 감사 필드
    val acquisitionPath: String = "STATIC_TEST_FIXTURE",
    val originalSource: String = "STATIC_TEST_FIXTURE",
    val adapterName: String = "SafetyWindowTestAdapter",
    val propertyOrApiName: String = signalName,
    val rawValue: String = value,
    val rawUnit: String? = unit,
    val convertedValue: String = value,
    val convertedUnit: String? = unit,
    val injectionMethod: String = "INTERNAL_FIXTURE",
    val isScenarioInjected: Boolean = false,
    val isMockLocation: Boolean = false,
    val permissionStatus: String = "GRANTED"
) {
    companion object {
        fun computeChecksum(
            sessionId: String,
            relativeTime: Long,
            signal: String,
            valueStr: String,
            seq: Long
        ): String {
            val raw = "$sessionId:$relativeTime:$signal:$valueStr:$seq"
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            return hashBytes.take(8).joinToString("") { "%02X".format(it) }
        }

        fun create(
            sessionId: String,
            relativeTimeMillis: Long,
            originalTimestampMillis: Long,
            signalName: String,
            value: String,
            unit: String?,
            source: InputSource,
            quality: String,
            capabilityStatus: String,
            sequenceNumber: Long,
            privacyTransform: String = "LOCAL_ENU_DEIDENTIFIED",
            acquisitionPath: String = "STATIC_TEST_FIXTURE",
            originalSource: String = "STATIC_TEST_FIXTURE",
            adapterName: String = "SafetyWindowTestAdapter",
            propertyOrApiName: String = signalName,
            rawValue: String = value,
            rawUnit: String? = unit,
            convertedValue: String = value,
            convertedUnit: String? = unit,
            injectionMethod: String = "INTERNAL_FIXTURE",
            isScenarioInjected: Boolean = false,
            isMockLocation: Boolean = false,
            permissionStatus: String = "GRANTED"
        ): InputRecordEvent {
            val cs = computeChecksum(sessionId, relativeTimeMillis, signalName, value, sequenceNumber)
            return InputRecordEvent(
                recordingSessionId = sessionId,
                relativeTimeMillis = relativeTimeMillis,
                originalTimestampMillis = originalTimestampMillis,
                signalName = signalName,
                value = value,
                unit = unit,
                source = source,
                quality = quality,
                capabilityStatus = capabilityStatus,
                sequenceNumber = sequenceNumber,
                checksum = cs,
                privacyTransform = privacyTransform,
                acquisitionPath = acquisitionPath,
                originalSource = originalSource,
                adapterName = adapterName,
                propertyOrApiName = propertyOrApiName,
                rawValue = rawValue,
                rawUnit = rawUnit,
                convertedValue = convertedValue,
                convertedUnit = convertedUnit,
                injectionMethod = injectionMethod,
                isScenarioInjected = isScenarioInjected,
                isMockLocation = isMockLocation,
                permissionStatus = permissionStatus
            )
        }
    }

    fun verifyChecksum(): Boolean {
        val expected = computeChecksum(recordingSessionId, relativeTimeMillis, signalName, value, sequenceNumber)
        return checksum.equals(expected, ignoreCase = true)
    }
}
