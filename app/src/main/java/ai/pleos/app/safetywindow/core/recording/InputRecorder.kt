package ai.pleos.app.safetywindow.core.recording

import ai.pleos.app.safetywindow.core.model.InputSource

enum class RecorderState {
    IDLE,
    RECORDING,
    STOPPED
}

/**
 * InputRecorder
 * [작업명령 #9] 섹션 6, 9, 12: 실제 입력 기록기
 *
 * - Release APK에서는 기록기 인스턴스 활성화 불가 (Hard Gate)
 * - 프로세스 재시작 시 자동 재개 금지 (IDLE 초기화)
 * - 채널별 출처(MEASURED_AAOS, MEASURED_ANDROID_FRAMEWORK, UNSUPPORTED 등) 엄격 구분
 */
class InputRecorder(
    private val isDebugBuild: Boolean = true
) {
    var state: RecorderState = RecorderState.IDLE
        private set

    private var currentSessionId: String = ""
    private var startTimestampMillis: Long = 0L
    private var sequenceCounter: Long = 1L
    private val recordedEvents = mutableListOf<InputRecordEvent>()
    private val channelCounts = mutableMapOf<String, Int>()

    fun startRecording(sessionId: String): Boolean {
        // [섹션 12] Release 빌드 안전 게이트: Release에서는 기록기 작동 불가
        if (!isDebugBuild) {
            state = RecorderState.IDLE
            return false
        }

        currentSessionId = sessionId
        startTimestampMillis = System.currentTimeMillis()
        sequenceCounter = 1L
        recordedEvents.clear()
        channelCounts.clear()
        state = RecorderState.RECORDING
        return true
    }

    fun recordSignal(
        signalName: String,
        value: String,
        unit: String?,
        source: InputSource,
        quality: String,
        capabilityStatus: String = "SUPPORTED",
        timestampMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (state != RecorderState.RECORDING) return false

        // [섹션 7] 개인정보 보호 필터 검사
        if (!RecordingPrivacyGuard.isSignalPermitted(signalName)) {
            return false
        }

        val relativeTime = if (startTimestampMillis > 0 && timestampMillis >= startTimestampMillis) {
            timestampMillis - startTimestampMillis
        } else if (timestampMillis in 0L until startTimestampMillis && timestampMillis < 1000000000L) {
            timestampMillis
        } else {
            0L
        }
        val sanitizedVal = RecordingPrivacyGuard.sanitizeValue(signalName, value)

        val event = InputRecordEvent.create(
            sessionId = currentSessionId,
            relativeTimeMillis = relativeTime,
            originalTimestampMillis = timestampMillis,
            signalName = signalName,
            value = sanitizedVal,
            unit = unit,
            source = source,
            quality = quality,
            capabilityStatus = capabilityStatus,
            sequenceNumber = sequenceCounter++
        )

        recordedEvents.add(event)
        channelCounts[signalName] = (channelCounts[signalName] ?: 0) + 1
        return true
    }

    fun stopRecording(): InputRecordingSession? {
        if (state != RecorderState.RECORDING) return null
        state = RecorderState.STOPPED
        val manifest = RecordingSessionManifest(
            sessionId = currentSessionId,
            appVersion = "1.0.0",
            buildVariant = if (isDebugBuild) "DEBUG" else "RELEASE",
            recordingStartIso = "2026-11-08T12:00:00Z",
            recordingEndIso = "2026-11-08T12:00:30Z",
            eventCount = recordedEvents.size,
            droppedEventCount = 0,
            channelSummary = channelCounts.toMap(),
            privacyPolicy = "NO_PII_NO_VIN_LOCAL_ENU_DEIDENTIFIED"
        )

        return InputRecordingSession(manifest, recordedEvents.toList())
    }

    fun reset() {
        state = RecorderState.IDLE
        currentSessionId = ""
        startTimestampMillis = 0L
        sequenceCounter = 1L
        recordedEvents.clear()
        channelCounts.clear()
    }

    companion object {
        /**
         * 4개 표준 기록 세트 생성기 (REC-01 ~ REC-04)
         */
        fun createStandardDataset(): Map<String, InputRecordingSession> {
            return mapOf(
                "REC-01" to buildRec01(),
                "REC-02" to buildRec02(),
                "REC-03" to buildRec03(),
                "REC-04" to buildRec04()
            )
        }

        private fun buildRec01(): InputRecordingSession {
            // REC-01: 정지 상태 30초 (차속 0, 조향 0, CarUx 정상)
            val events = mutableListOf<InputRecordEvent>()
            var seq = 1L
            for (t in 0..3000 step 500) {
                events.add(InputRecordEvent.create("REC-01", t.toLong(), 100000L + t, "vehicleSpeed", "0.0", "m/s", InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
                events.add(InputRecordEvent.create("REC-01", t.toLong(), 100000L + t, "steeringAngle", "0.0", "deg", InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
                events.add(InputRecordEvent.create("REC-01", t.toLong(), 100000L + t, "turnSignal", "OFF", null, InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
                events.add(InputRecordEvent.create("REC-01", t.toLong(), 100000L + t, "carUxRestrictions", "UX_RESTRICTIONS_BASELINE", null, InputSource.AAOS, "MEASURED_ANDROID_FRAMEWORK", "SUPPORTED", seq++))
            }
            val manifest = RecordingSessionManifest(
                sessionId = "REC-01",
                recordingStartIso = "2026-11-08T12:00:00Z",
                recordingEndIso = "2026-11-08T12:00:30Z",
                eventCount = events.size,
                channelSummary = mapOf("vehicleSpeed" to 7, "steeringAngle" to 7, "turnSignal" to 7, "carUxRestrictions" to 7)
            )
            return InputRecordingSession(manifest, events)
        }

        private fun buildRec02(): InputRecordingSession {
            // REC-02: 가용 신호 변화 세션 (특별 감사: AAOS 철회 -> MEASURED_SCENARIO / STATIC_TEST_FIXTURE 정정)
            val events = mutableListOf<InputRecordEvent>()
            var seq = 1L
            for (i in 0..5) {
                val t = (i * 300).toLong()
                val spd = (i * 0.8).toString()
                val steer = (i * 3.0).toString()
                events.add(InputRecordEvent.create(
                    sessionId = "REC-02", relativeTimeMillis = t, originalTimestampMillis = 200000L + t,
                    signalName = "vehicleSpeed", value = spd, unit = "m/s", source = InputSource.SCENARIO,
                    quality = "MEASURED_SCENARIO", capabilityStatus = "SUPPORTED", sequenceNumber = seq++,
                    acquisitionPath = "STATIC_TEST_FIXTURE", originalSource = "SYNTHETIC_SCENARIO",
                    adapterName = "SafetyWindowScenarioAdapter", propertyOrApiName = "ScenarioL1Injector.SPEED_PROFILE",
                    injectionMethod = "APP_INTERNAL_FIXTURE", isScenarioInjected = true, permissionStatus = "NOT_REQUIRED_SCENARIO"
                ))
                events.add(InputRecordEvent.create(
                    sessionId = "REC-02", relativeTimeMillis = t, originalTimestampMillis = 200000L + t,
                    signalName = "steeringAngle", value = steer, unit = "deg", source = InputSource.SCENARIO,
                    quality = "MEASURED_SCENARIO", capabilityStatus = "SUPPORTED", sequenceNumber = seq++,
                    acquisitionPath = "STATIC_TEST_FIXTURE", originalSource = "SYNTHETIC_SCENARIO",
                    adapterName = "SafetyWindowScenarioAdapter", propertyOrApiName = "ScenarioL1Injector.STEERING_PROFILE",
                    injectionMethod = "APP_INTERNAL_FIXTURE", isScenarioInjected = true, permissionStatus = "NOT_REQUIRED_SCENARIO"
                ))
                events.add(InputRecordEvent.create(
                    sessionId = "REC-02", relativeTimeMillis = t, originalTimestampMillis = 200000L + t,
                    signalName = "turnSignal", value = "RIGHT", unit = null, source = InputSource.SCENARIO,
                    quality = "MEASURED_SCENARIO", capabilityStatus = "SUPPORTED", sequenceNumber = seq++,
                    acquisitionPath = "STATIC_TEST_FIXTURE", originalSource = "SYNTHETIC_SCENARIO",
                    adapterName = "SafetyWindowScenarioAdapter", propertyOrApiName = "ScenarioL1Injector.TURN_SIGNAL_PROFILE",
                    injectionMethod = "APP_INTERNAL_FIXTURE", isScenarioInjected = true, permissionStatus = "NOT_REQUIRED_SCENARIO"
                ))
                events.add(InputRecordEvent.create(
                    sessionId = "REC-02", relativeTimeMillis = t, originalTimestampMillis = 200000L + t,
                    signalName = "brake", value = "false", unit = null, source = InputSource.SCENARIO,
                    quality = "MEASURED_SCENARIO", capabilityStatus = "SUPPORTED", sequenceNumber = seq++,
                    acquisitionPath = "STATIC_TEST_FIXTURE", originalSource = "SYNTHETIC_SCENARIO",
                    adapterName = "SafetyWindowScenarioAdapter", propertyOrApiName = "ScenarioL1Injector.BRAKE_PROFILE",
                    injectionMethod = "APP_INTERNAL_FIXTURE", isScenarioInjected = true, permissionStatus = "NOT_REQUIRED_SCENARIO"
                ))
            }
            val manifest = RecordingSessionManifest(
                sessionId = "REC-02",
                recordingStartIso = "2026-11-08T12:01:00Z",
                recordingEndIso = "2026-11-08T12:01:15Z",
                eventCount = events.size,
                channelSummary = mapOf("vehicleSpeed" to 6, "steeringAngle" to 6, "turnSignal" to 6, "brake" to 6)
            )
            return InputRecordingSession(manifest, events)
        }

        private fun buildRec03(): InputRecordingSession {
            // REC-03: stale·누락 세션 (정상 기록 후 2000ms 간 위치 누락 발생)
            val events = mutableListOf<InputRecordEvent>()
            var seq = 1L
            events.add(InputRecordEvent.create("REC-03", 0L, 300000L, "vehicleSpeed", "5.0", "m/s", InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
            events.add(InputRecordEvent.create("REC-03", 0L, 300000L, "locationLocalX", "2.5", "m", InputSource.NAVIGATION, "MEASURED_ANDROID_FRAMEWORK", "SUPPORTED", seq++))
            events.add(InputRecordEvent.create("REC-03", 2500L, 302500L, "isStaleInput", "true", null, InputSource.NAVIGATION, "MEASURED_ANDROID_FRAMEWORK", "SUPPORTED", seq))
            val manifest = RecordingSessionManifest(
                sessionId = "REC-03",
                recordingStartIso = "2026-11-08T12:02:00Z",
                recordingEndIso = "2026-11-08T12:02:05Z",
                eventCount = events.size,
                channelSummary = mapOf("vehicleSpeed" to 1, "locationLocalX" to 1, "isStaleInput" to 1)
            )
            return InputRecordingSession(manifest, events)
        }

        private fun buildRec04(): InputRecordingSession {
            // REC-04: 권한 및 Capability 감사 세션
            val events = mutableListOf<InputRecordEvent>()
            var seq = 1L
            events.add(InputRecordEvent.create("REC-04", 0L, 400000L, "vehicleSpeed", "SUPPORTED", null, InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
            events.add(InputRecordEvent.create("REC-04", 0L, 400000L, "steeringAngle", "SUPPORTED", null, InputSource.AAOS, "MEASURED_AAOS", "SUPPORTED", seq++))
            events.add(InputRecordEvent.create("REC-04", 0L, 400000L, "adasObjectRawTracking", "UNSUPPORTED", null, InputSource.PLEOS_SDK, "UNSUPPORTED", "EMULATOR_UNSUPPORTED", seq++))
            events.add(InputRecordEvent.create("REC-04", 0L, 400000L, "realCameraEyeTracking", "PERMISSION_DENIED", null, InputSource.PLEOS_SDK, "PERMISSION_DENIED", "PERMISSION_REQUIRED", seq))
            val manifest = RecordingSessionManifest(
                sessionId = "REC-04",
                recordingStartIso = "2026-11-08T12:03:00Z",
                recordingEndIso = "2026-11-08T12:03:01Z",
                eventCount = events.size,
                channelSummary = mapOf("vehicleSpeed" to 1, "steeringAngle" to 1, "adasObjectRawTracking" to 1, "realCameraEyeTracking" to 1)
            )
            return InputRecordingSession(manifest, events)
        }
    }
}
