package ai.pleos.app.safetywindow.core.replay

import ai.pleos.app.safetywindow.core.model.InputSource
import org.json.JSONObject

/**
 * ReplaySession
 * [작업명령 #8] 섹션 5, 6: Replay 세션 데이터 모델 및 파서
 */
data class ReplaySession(
    val sessionId: String,
    val description: String,
    val events: List<ReplayEvent>
) {
    val durationMillis: Long
        get() = events.maxOfOrNull { it.relativeTimeMillis } ?: 0L

    val totalEvents: Int
        get() = events.size

    val inputSources: Set<InputSource>
        get() = events.flatMap { listOf(it.originalSource, it.replaySource) }.toSet()

    fun toJsonl(): String {
        return events.joinToString("\n") { ev ->
            val obj = JSONObject()
            obj.put("replaySessionId", ev.replaySessionId)
            obj.put("relativeTimeMillis", ev.relativeTimeMillis)
            obj.put("originalTimestampMillis", ev.originalTimestampMillis)
            obj.put("signalName", ev.signalName)
            obj.put("value", ev.value)
            if (ev.unit != null) obj.put("unit", ev.unit)
            obj.put("originalSource", ev.originalSource.name)
            obj.put("replaySource", ev.replaySource.name)
            obj.put("quality", ev.quality)
            if (ev.featureId != null) obj.put("featureId", ev.featureId)
            obj.put("sequenceNumber", ev.sequenceNumber)
            obj.put("checksum", ev.checksum)
            obj.toString()
        }
    }

    companion object {
        fun fromJsonl(sessionId: String, description: String, jsonlString: String): ReplaySession {
            val list = mutableListOf<ReplayEvent>()
            jsonlString.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val obj = JSONObject(trimmed)
                    val ev = ReplayEvent(
                        replaySessionId = obj.getString("replaySessionId"),
                        relativeTimeMillis = obj.getLong("relativeTimeMillis"),
                        originalTimestampMillis = obj.getLong("originalTimestampMillis"),
                        signalName = obj.getString("signalName"),
                        value = obj.getString("value"),
                        unit = if (obj.has("unit")) obj.getString("unit") else null,
                        originalSource = InputSource.valueOf(obj.getString("originalSource")),
                        replaySource = InputSource.valueOf(obj.optString("replaySource", "REPLAY")),
                        quality = obj.getString("quality"),
                        featureId = if (obj.has("featureId")) obj.getString("featureId") else null,
                        sequenceNumber = obj.getLong("sequenceNumber"),
                        checksum = obj.optString("checksum", "")
                    )
                    list.add(ev)
                }
            }
            return ReplaySession(sessionId, description, list.sortedBy { it.sequenceNumber })
        }

        /**
         * 8개 표준 Replay 세트 생성기 (RPL-01 ~ RPL-08)
         */
        fun createStandardDataset(): Map<String, ReplaySession> {
            return mapOf(
                "RPL-01" to buildRpl01(),
                "RPL-02" to buildRpl02(),
                "RPL-03" to buildRpl03(),
                "RPL-04" to buildRpl04(),
                "RPL-05" to buildRpl05(),
                "RPL-06" to buildRpl06(),
                "RPL-07" to buildRpl07(),
                "RPL-08" to buildRpl08()
            )
        }

        private fun buildRpl01(): ReplaySession {
            // RPL-01: 안정적 L0 (직진 정속 주행, 장애물 없음)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            for (t in 0..1000 step 100) {
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "vehicleSpeed", "12.5", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "steeringAngle", "0.0", "deg", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "turnSignal", "OFF", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "locationLocalX", (t * 1.25).toString(), "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "locationLocalY", "0.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-01", t.toLong(), 10000L + t, "headingRadians", "0.0", "rad", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            }
            return ReplaySession("RPL-01", "안정적 L0 (직진 정속 주행)", events)
        }

        private fun buildRpl02(): ReplaySession {
            // RPL-02: Scenario L1 우측 (우회전 진입, 우측 A필러 위험공간 가림 L1 발생)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            for (t in 0..1000 step 100) {
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "vehicleSpeed", "4.0", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "steeringAngle", "15.0", "deg", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "turnSignal", "RIGHT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "locationLocalX", "5.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-02-RIGHT", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "locationLocalY", "-2.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-02-RIGHT", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-02", t.toLong(), 20000L + t, "headingRadians", "0.35", "rad", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            }
            return ReplaySession("RPL-02", "Scenario L1 우측 협소도로 안내", events)
        }

        private fun buildRpl03(): ReplaySession {
            // RPL-03: 정차 협소도로 사전정보 L1 (차속 0.0, 2사이클 안정화 후 Visible)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            for (cycle in 1..3) {
                val t = (cycle - 1) * 200L
                events.add(ReplayEvent.create("RPL-03", t, 30000L + t, "vehicleSpeed", "0.0", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-03", t, 30000L + t, "steeringAngle", "0.0", "deg", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-03", t, 30000L + t, "turnSignal", "RIGHT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-03", t, 30000L + t, "locationLocalX", "3.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-03-STOP", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-03", t, 30000L + t, "locationLocalY", "-1.5", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-03-STOP", sequenceNumber = seq++))
            }
            return ReplaySession("RPL-03", "정차 협소도로 사전정보 L1", events)
        }

        private fun buildRpl04(): ReplaySession {
            // RPL-04: Effective L2 후보 출력 차단 (기하학적 L2 중첩이나 L2 차단 정책으로 L0 유지, TTS 0)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            for (t in 0..600 step 100) {
                events.add(ReplayEvent.create("RPL-04", t.toLong(), 40000L + t, "vehicleSpeed", "14.0", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-04", t.toLong(), 40000L + t, "steeringAngle", "28.0", "deg", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-04", t.toLong(), 40000L + t, "locationLocalX", "10.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-04-OBSTACLE", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-04", t.toLong(), 40000L + t, "locationLocalY", "-3.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-04-OBSTACLE", sequenceNumber = seq++))
            }
            return ReplaySession("RPL-04", "Effective L2 후보 출력 차단 및 L0 유지", events)
        }

        private fun buildRpl05(): ReplaySession {
            // RPL-05: stale 전환 즉시 Hidden (활성 후 600ms 동안 위치 갱신 중단)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            // 0~200ms: 정상 활성
            for (t in 0..200 step 100) {
                events.add(ReplayEvent.create("RPL-05", t.toLong(), 50000L + t, "vehicleSpeed", "3.0", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
                events.add(ReplayEvent.create("RPL-05", t.toLong(), 50000L + t, "locationLocalX", "4.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-05", sequenceNumber = seq++))
            }
            // 300ms: stale 신호 발생 (relativeTime = 800ms)
            events.add(ReplayEvent.create("RPL-05", 800L, 50800L, "isStaleInput", "true", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            return ReplaySession("RPL-05", "stale 전환 즉시 Hidden", events)
        }

        private fun buildRpl06(): ReplaySession {
            // RPL-06: CarUxRestrictions 전역 차단 (주행 제한 신호 수신 즉시 Hidden/Restricted)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            events.add(ReplayEvent.create("RPL-06", 0L, 60000L, "vehicleSpeed", "5.0", "m/s", InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            events.add(ReplayEvent.create("RPL-06", 0L, 60000L, "locationLocalX", "4.0", "m", InputSource.SCENARIO, "SYNTHETIC_SCENARIO", featureId = "F-06", sequenceNumber = seq++))
            events.add(ReplayEvent.create("RPL-06", 100L, 60100L, "carUxRestriction", "UX_RESTRICTIONS_FULLY_RESTRICTED", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            return ReplaySession("RPL-06", "CarUxRestrictions 전역 차단", events)
        }

        private fun buildRpl07(): ReplaySession {
            // RPL-07: 방향전환 RIGHT→LEFT 안정화 (급격한 조향 변경 시 깜빡임 방지)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            events.add(ReplayEvent.create("RPL-07", 0L, 70000L, "turnSignal", "RIGHT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            events.add(ReplayEvent.create("RPL-07", 50L, 70050L, "turnSignal", "LEFT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            events.add(ReplayEvent.create("RPL-07", 100L, 70100L, "turnSignal", "RIGHT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            return ReplaySession("RPL-07", "방향전환 RIGHT->LEFT 안정화", events)
        }

        private fun buildRpl08(): ReplaySession {
            // RPL-08: 앱 재시작 후 안전 기본값 (콜드 스타트 이벤트 후 L0_IDLE 기본 복구)
            val events = mutableListOf<ReplayEvent>()
            var seq = 1L
            events.add(ReplayEvent.create("RPL-08", 0L, 80000L, "processRestartEvent", "COLD_BOOT", null, InputSource.AAOS, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            events.add(ReplayEvent.create("RPL-08", 50L, 80050L, "executionModeEvent", "REPLAY_INPUT", null, InputSource.REPLAY, "SYNTHETIC_SCENARIO", sequenceNumber = seq++))
            return ReplaySession("RPL-08", "앱 재시작 후 안전 기본값 복구", events)
        }
    }
}
