package ai.pleos.app.safetywindow.core.recording

data class RecordingIntegrityReport(
    val sessionId: String,
    val totalEvents: Int,
    val isSequenceContinuous: Boolean,
    val isChecksumAllValid: Boolean,
    val isTimeMonotonic: Boolean,
    val isPrivacyCompliant: Boolean,
    val errors: List<String>
) {
    val isAllValid: Boolean
        get() = isSequenceContinuous && isChecksumAllValid && isTimeMonotonic && isPrivacyCompliant && errors.isEmpty()
}

/**
 * RecordingIntegrityValidator
 * [작업명령 #9] 섹션 6, 8: 기록 세션 무결성 및 순차성 검증기
 */
object RecordingIntegrityValidator {

    fun validateSession(session: InputRecordingSession): RecordingIntegrityReport {
        var seqContinuous = true
        var checksumValid = true
        var timeMonotonic = true
        var privacyCompliant = true
        val errors = mutableListOf<String>()

        val events = session.events
        if (events.isEmpty()) {
            errors.add("Recording session contains zero events")
            return RecordingIntegrityReport(
                session.manifest.sessionId, 0, false, false, false, false, errors
            )
        }

        var lastSeq = events.first().sequenceNumber - 1
        var lastTime = 0L

        events.forEach { ev ->
            // 1. 순번 검증
            if (ev.sequenceNumber != lastSeq + 1) {
                seqContinuous = false
                errors.add("Sequence gap detected: expected=${lastSeq + 1}, found=${ev.sequenceNumber}")
            }
            lastSeq = ev.sequenceNumber

            // 2. 시간 단조성 검증
            if (ev.relativeTimeMillis < 0L) {
                timeMonotonic = false
                errors.add("Negative relative time at seq ${ev.sequenceNumber}: ${ev.relativeTimeMillis}")
            } else if (ev.relativeTimeMillis < lastTime) {
                timeMonotonic = false
                errors.add("Time regression detected at seq ${ev.sequenceNumber}: ${ev.relativeTimeMillis} < $lastTime")
            }
            lastTime = ev.relativeTimeMillis

            // 3. 체크섬 검증
            if (!ev.verifyChecksum()) {
                checksumValid = false
                errors.add("Checksum mismatch at seq ${ev.sequenceNumber} for signal ${ev.signalName}")
            }

            // 4. 개인정보 필터 검증
            if (!RecordingPrivacyGuard.isSignalPermitted(ev.signalName)) {
                privacyCompliant = false
                errors.add("Privacy violation: forbidden signal '${ev.signalName}' recorded at seq ${ev.sequenceNumber}")
            }
        }

        return RecordingIntegrityReport(
            sessionId = session.manifest.sessionId,
            totalEvents = events.size,
            isSequenceContinuous = seqContinuous,
            isChecksumAllValid = checksumValid,
            isTimeMonotonic = timeMonotonic,
            isPrivacyCompliant = privacyCompliant,
            errors = errors
        )
    }
}
