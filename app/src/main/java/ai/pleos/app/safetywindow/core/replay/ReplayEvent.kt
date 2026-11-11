package ai.pleos.app.safetywindow.core.replay

import ai.pleos.app.safetywindow.core.model.InputSource
import java.security.MessageDigest

/**
 * ReplayEvent
 * [작업명령 #8] 섹션 5: Replay 데이터 포맷 (JSONL 기반 시계열 이벤트)
 *
 * 개인정보, VIN, 실제 목적지, 장기 위치이력은 일체 포함하지 않는다.
 */
data class ReplayEvent(
    val replaySessionId: String,
    val relativeTimeMillis: Long,
    val originalTimestampMillis: Long,
    val signalName: String,
    val value: String,
    val unit: String?,
    val originalSource: InputSource,
    val replaySource: InputSource = InputSource.REPLAY,
    val quality: String,
    val featureId: String? = null,
    val sequenceNumber: Long,
    val checksum: String = ""
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
            originalSource: InputSource,
            quality: String,
            featureId: String? = null,
            sequenceNumber: Long
        ): ReplayEvent {
            val cs = computeChecksum(sessionId, relativeTimeMillis, signalName, value, sequenceNumber)
            return ReplayEvent(
                replaySessionId = sessionId,
                relativeTimeMillis = relativeTimeMillis,
                originalTimestampMillis = originalTimestampMillis,
                signalName = signalName,
                value = value,
                unit = unit,
                originalSource = originalSource,
                replaySource = InputSource.REPLAY,
                quality = quality,
                featureId = featureId,
                sequenceNumber = sequenceNumber,
                checksum = cs
            )
        }
    }

    fun verifyChecksum(): Boolean {
        val expected = computeChecksum(replaySessionId, relativeTimeMillis, signalName, value, sequenceNumber)
        return checksum.equals(expected, ignoreCase = true)
    }
}
