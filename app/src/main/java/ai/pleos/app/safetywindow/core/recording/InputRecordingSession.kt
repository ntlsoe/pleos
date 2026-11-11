package ai.pleos.app.safetywindow.core.recording

import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.replay.ReplayEvent
import ai.pleos.app.safetywindow.core.replay.ReplaySession
import org.json.JSONObject

data class RecordingSessionManifest(
    val sessionId: String,
    val appVersion: String = "1.0.0",
    val buildVariant: String = "DEBUG",
    val platformTarget: String = "Pleos Connect AVD (API 34 AAOS)",
    val recordingStartIso: String,
    val recordingEndIso: String,
    val eventCount: Int,
    val droppedEventCount: Int = 0,
    val channelSummary: Map<String, Int>,
    val privacyPolicy: String = "NO_PII_NO_VIN_LOCAL_ENU_DEIDENTIFIED"
)

/**
 * InputRecordingSession
 * [작업명령 #9, #10] 실제 및 에뮬레이터 입력 기록 세션 및 Replay 변환기
 */
data class InputRecordingSession(
    val manifest: RecordingSessionManifest,
    val events: List<InputRecordEvent>
) {
    fun toJsonl(): String {
        return events.joinToString("\n") { ev ->
            val obj = JSONObject()
            obj.put("recordingSessionId", ev.recordingSessionId)
            obj.put("relativeTimeMillis", ev.relativeTimeMillis)
            obj.put("originalTimestampMillis", ev.originalTimestampMillis)
            obj.put("signalName", ev.signalName)
            obj.put("value", ev.value)
            if (ev.unit != null) obj.put("unit", ev.unit)
            obj.put("source", ev.source.name)
            obj.put("quality", ev.quality)
            obj.put("capabilityStatus", ev.capabilityStatus)
            obj.put("sequenceNumber", ev.sequenceNumber)
            obj.put("checksum", ev.checksum)
            obj.put("privacyTransform", ev.privacyTransform)
            // [작업명령 #10] 원천 감사 필드
            obj.put("acquisitionPath", ev.acquisitionPath)
            obj.put("originalSource", ev.originalSource)
            obj.put("adapterName", ev.adapterName)
            obj.put("propertyOrApiName", ev.propertyOrApiName)
            obj.put("rawValue", ev.rawValue)
            if (ev.rawUnit != null) obj.put("rawUnit", ev.rawUnit)
            obj.put("convertedValue", ev.convertedValue)
            if (ev.convertedUnit != null) obj.put("convertedUnit", ev.convertedUnit)
            obj.put("injectionMethod", ev.injectionMethod)
            obj.put("isScenarioInjected", ev.isScenarioInjected)
            obj.put("isMockLocation", ev.isMockLocation)
            obj.put("permissionStatus", ev.permissionStatus)
            obj.toString()
        }
    }

    /**
     * 기록 세션을 REPLAY_INPUT 포맷으로 변환
     */
    fun toReplaySession(description: String = "Actual Input Replay Session"): ReplaySession {
        val replayEvents = events.map { ev ->
            ReplayEvent.create(
                sessionId = ev.recordingSessionId,
                relativeTimeMillis = ev.relativeTimeMillis,
                originalTimestampMillis = ev.originalTimestampMillis,
                signalName = ev.signalName,
                value = ev.value,
                unit = ev.unit,
                originalSource = ev.source,
                quality = ev.quality,
                sequenceNumber = ev.sequenceNumber
            )
        }
        return ReplaySession(
            sessionId = manifest.sessionId,
            description = description,
            events = replayEvents
        )
    }

    companion object {
        fun fromJsonl(manifest: RecordingSessionManifest, jsonlString: String): InputRecordingSession {
            val list = mutableListOf<InputRecordEvent>()
            jsonlString.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val obj = JSONObject(trimmed)
                    val ev = InputRecordEvent(
                        recordingSessionId = obj.getString("recordingSessionId"),
                        relativeTimeMillis = obj.getLong("relativeTimeMillis"),
                        originalTimestampMillis = obj.getLong("originalTimestampMillis"),
                        signalName = obj.getString("signalName"),
                        value = obj.getString("value"),
                        unit = if (obj.has("unit")) obj.getString("unit") else null,
                        source = InputSource.valueOf(obj.getString("source")),
                        quality = obj.getString("quality"),
                        capabilityStatus = obj.getString("capabilityStatus"),
                        sequenceNumber = obj.getLong("sequenceNumber"),
                        checksum = obj.optString("checksum", ""),
                        privacyTransform = obj.optString("privacyTransform", "LOCAL_ENU_DEIDENTIFIED"),
                        acquisitionPath = obj.optString("acquisitionPath", "STATIC_TEST_FIXTURE"),
                        originalSource = obj.optString("originalSource", "STATIC_TEST_FIXTURE"),
                        adapterName = obj.optString("adapterName", "SafetyWindowTestAdapter"),
                        propertyOrApiName = obj.optString("propertyOrApiName", obj.getString("signalName")),
                        rawValue = obj.optString("rawValue", obj.getString("value")),
                        rawUnit = if (obj.has("rawUnit")) obj.getString("rawUnit") else null,
                        convertedValue = obj.optString("convertedValue", obj.getString("value")),
                        convertedUnit = if (obj.has("convertedUnit")) obj.getString("convertedUnit") else null,
                        injectionMethod = obj.optString("injectionMethod", "INTERNAL_FIXTURE"),
                        isScenarioInjected = obj.optBoolean("isScenarioInjected", false),
                        isMockLocation = obj.optBoolean("isMockLocation", false),
                        permissionStatus = obj.optString("permissionStatus", "GRANTED")
                    )
                    list.add(ev)
                }
            }
            return InputRecordingSession(manifest, list.sortedBy { it.sequenceNumber })
        }
    }
}
