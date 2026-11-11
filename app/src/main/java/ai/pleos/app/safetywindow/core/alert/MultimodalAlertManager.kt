package ai.pleos.app.safetywindow.core.alert

import ai.pleos.app.safetywindow.BuildConfig
import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.data.sensor.VehicleSensorRepository
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * MultimodalAlertManager
 *
 * 1. AudioManager 오디오 포커스(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) 기반 인포테인먼트 사운드 덕킹
 * 2. TextToSpeech 음성 안내 출력
 * 3. [작업명령 #7] 공통 TtsKillSwitch 연동: Release 안전 기본값, 시나리오 L1 모드, 정책 제한 시 실제 발화 전면 차단
 */
class MultimodalAlertManager(
    private val context: Context,
    private val sensorRepository: VehicleSensorRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val killSwitch: TtsKillSwitch = TtsKillSwitch(
        isMasterKillSwitchActive = true,
        isReleaseBuild = !BuildConfig.DEBUG
    )
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var systemTts: android.speech.tts.TextToSpeech? = null
    var isTtsReady = false
        private set

    private var audioFocusRequest: AudioFocusRequest? = null

    // TTS 레이턴시 및 통계 계측용
    var lastTtsCallTimeMs = 0L
    var lastTtsStartTimeMs = 0L
    var actualSpokenCount = 0
        private set
    var blockedCount = 0
        private set

    var isCarUxRestricted: Boolean = false
    var isParkedValidationMode: Boolean = false
    var enableTtsFlag: Boolean = false
    var isVoiceMuted: Boolean = false

    init {
        initTts()
    }

    private fun initTts() {
        try {
            systemTts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    systemTts?.language = Locale.KOREAN
                    isTtsReady = true
                    Log.i(TAG, "Standard TTS engine initialized")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialize TTS on this platform: ${e.message}")
        }
    }

    /**
     * 안내 음성 출력 요청 (KillSwitch 최후 방어 게이트 통과 시에만 발화)
     */
    fun triggerAdvisory(
        message: String,
        pipeline: AdvisoryPipeline = AdvisoryPipeline.LEGACY_HEURISTIC
    ): TtsOutputDecision {
        val decision = killSwitch.evaluate(
            pipeline = pipeline,
            message = message,
            isCarUxRestricted = isCarUxRestricted,
            isScenarioMode = (sensorRepository.executionMode == ExecutionMode.SCENARIO_INPUT),
            isParkedValidation = isParkedValidationMode,
            enableTtsFlag = enableTtsFlag,
            isVoiceMuted = isVoiceMuted
        )

        if (!decision.permitted) {
            blockedCount++
            Log.i(TAG, "TTS output BLOCKED by KillSwitch: reason=${decision.reason}, pipeline=$pipeline, message='$message'")
            return decision
        }

        actualSpokenCount++
        scope.launch {
            requestAudioDucking()
            speak(message)
            delay(3500)
            restoreNormalState()
        }
        return decision
    }

    private fun requestAudioDucking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* handle ducking */ }
                .build()

            this.audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun speak(text: String) {
        lastTtsCallTimeMs = System.currentTimeMillis()
        if (isTtsReady && systemTts != null) {
            systemTts?.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "ADVISORY_${System.currentTimeMillis()}"
            )
        }
    }

    fun restoreNormalState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        systemTts?.stop()
        systemTts?.shutdown()
        restoreNormalState()
    }

    companion object {
        private const val TAG = "MultimodalAlertManager"
    }
}
