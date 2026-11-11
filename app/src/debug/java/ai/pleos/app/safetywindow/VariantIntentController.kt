package ai.pleos.app.safetywindow

import ai.pleos.app.safetywindow.core.alert.MultimodalAlertManager
import ai.pleos.app.safetywindow.core.benchmark.AndroidRuntimeBenchmarkRunner
import ai.pleos.app.safetywindow.core.benchmark.ScenarioL1E2EBenchmarkRunner
import ai.pleos.app.safetywindow.core.capability.CapabilityChecker
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.settings.ProductSettingsRepository
import ai.pleos.app.safetywindow.demo.scenario.ScenarioSimulationAdasDataSource
import ai.pleos.app.safetywindow.demo.ui.FourViewDemoInteractiveHost
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.app.safetywindow.data.sensor.VehicleSensorRepository
import ai.pleos.app.safetywindow.data.spatial.CautionType
import ai.pleos.app.safetywindow.data.spatial.SpatialContextEngine
import ai.pleos.app.safetywindow.ui.hud.SafetyViewModel
import ai.pleos.app.safetywindow.ui.hud.SafetyWindowVectorHud
import ai.pleos.app.safetywindow.ui.settings.ProductSettingsAnchor
import ai.pleos.app.safetywindow.ui.settings.ProductSettingsDialog
import ai.pleos.app.safetywindow.ui.settings.ProductSettingsDrivingNotice
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.app.safetywindow.demo.ui.FourViewDemoInteractiveHost


internal class VariantIntentController(
    private val activity: MainActivity,
    private val viewModel: SafetyViewModel,
    private val sensorRepository: VehicleSensorRepository,
    private val onDemoModeChanged: (Boolean) -> Unit
) {
    private val simulationHandler = Handler(Looper.getMainLooper())
    private val simulationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!BuildConfig.DEBUG) {
                Log.w(TAG, "Ignoring ACTION_SIMULATE outside debug builds")
                return
            }

            // 실행 모드 전환 요청 처리
            val modeStr = intent?.getStringExtra("mode")
            val screenStr = intent?.getStringExtra("screen")
            if (screenStr?.uppercase() == "DEMO" || screenStr?.uppercase() == "FOUR_VIEW" || modeStr?.uppercase() == "DEMO") {
                onDemoModeChanged(true)
                Log.i(TAG, "Switched screen to DEMO_3D_FOUR_VIEW")
            } else if (screenStr?.uppercase() == "HUD" || screenStr?.uppercase() == "2D") {
                onDemoModeChanged(false)
                Log.i(TAG, "Switched screen to HUD_2D")
            }
            modeStr?.let {
                val mode = when (it.uppercase()) {
                    "SCENARIO", "SCENARIO_INPUT" -> ExecutionMode.SCENARIO_INPUT
                    "HYBRID", "HYBRID_DEBUG" -> ExecutionMode.HYBRID_DEBUG
                    "REPLAY", "REPLAY_INPUT" -> {
                        if (BuildConfig.DEBUG) ExecutionMode.REPLAY_INPUT else ExecutionMode.REAL_INPUT
                    }
                    else -> ExecutionMode.REAL_INPUT
                }
                viewModel.setExecutionMode(mode)
                Log.i(TAG, "Switched executionMode to $mode")
            }

            if (intent?.getBooleanExtra("run_benchmark", false) == true) {
                val buildMode = intent.getStringExtra("build_mode") ?: "ANDROID_DEBUG"
                val sessionId = intent.getIntExtra("session_id", 1)
                Thread({
                    AndroidRuntimeBenchmarkRunner.runSingleSession(
                        context = activity.applicationContext,
                        sessionId = sessionId,
                        buildMode = buildMode
                    )
                }, "OcclusionBenchmarkWorker-$sessionId").start()
            }

            if (intent?.getBooleanExtra("run_e2e_benchmark", false) == true) {
                val buildMode = intent.getStringExtra("build_mode") ?: "ANDROID_DEBUG"
                val sessionId = intent.getIntExtra("session_id", 1)
                viewModel.setExecutionMode(ExecutionMode.SCENARIO_INPUT)
                Thread({
                    ScenarioL1E2EBenchmarkRunner.runSingleSession(
                        context = activity.applicationContext,
                        sessionId = sessionId,
                        buildMode = buildMode,
                        iterationsPerTransition = 30,
                        onPublishOverlayState = { overlayState ->
                            viewModel.updateScenarioL1OverlayDirect(overlayState)
                        }
                    )
                }, "ScenarioL1E2EBenchmarkWorker-$sessionId").start()
            }

            val scenarioL1Case = intent?.getStringExtra("scenario_l1_case") ?: intent?.getStringExtra("audit_case")
            scenarioL1Case?.let {
                viewModel.triggerScenarioL1AuditCase(it)
            }

            // 설정 관련 시뮬레이션 제어 (Work 43 감사 자동화 지원)
            val noticeStr = intent?.getStringExtra("settings_notice")
            if (noticeStr != null) {
                val displayMsg = when (noticeStr.uppercase()) {
                    "SPEED_GATED", "DRIVING_BLOCKED" -> "정차 후 설정 가능"
                    "PERSISTED_OK", "PERSISTENCE_VERIFIED" -> "설정 상태 복원 완료: SharedPreferences 정상 로드"
                    else -> noticeStr
                }
                viewModel.showSettingsNotice(displayMsg)
            }
            if (intent?.hasExtra("driving_restricted") == true) {
                val restricted = intent.getBooleanExtra("driving_restricted", false)
                viewModel.setDrivingRestrictionsActive(restricted)
            }
            if (intent?.hasExtra("settings_dialog") == true) {
                val open = intent.getBooleanExtra("settings_dialog", false)
                if (open) viewModel.openSettingsPanel() else viewModel.closeSettingsPanel()
            }
            if (intent?.hasExtra("user_enabled") == true) {
                val enabled = intent.getBooleanExtra("user_enabled", true)
                viewModel.toggleUserEnabled(enabled)
            }
            if (intent?.hasExtra("voice_muted") == true) {
                val muted = intent.getBooleanExtra("voice_muted", false)
                viewModel.toggleVoiceMuted(muted)
            }

            if (intent?.getBooleanExtra("run_work48_set07", false) == true) {
                viewModel.setExecutionMode(ExecutionMode.SCENARIO_INPUT)
                viewModel.simulateTurnSignal(TurnSignalState.OFF)
                viewModel.simulateSpeed(2.9f)
                simulationHandler.postDelayed({
                    viewModel.closeSettingsPanel()
                    Log.i(TAG, "Work48 SET-07 initial HUD=${viewModel.uiState.value.alertLevel}")
                    viewModel.openSettingsPanel()
                    Log.i(TAG, "Work48 SET-07 settingsOpen=${viewModel.uiState.value.isSettingsPanelOpen}")
                    Work48Set07RawFixtureProvider.play(activity.applicationContext, viewModel)
                }, 700L)
            }

            // 시나리오 입력이 허용된 모드일 때만 가상 신호 주입
            if (sensorRepository.executionMode != ExecutionMode.REAL_INPUT) {
                val signalStr = intent?.getStringExtra("signal")
                val steer = intent?.getFloatExtra("steer", 0f) ?: 0f
                val speed = intent?.getFloatExtra("speed", -1f) ?: -1f
                val brake = intent?.hasExtra("brake")?.let { intent.getBooleanExtra("brake", false) }
                val caution = intent?.getStringExtra("caution")

                signalStr?.let {
                    val state = try { TurnSignalState.valueOf(it.uppercase()) } catch (e: Exception) { TurnSignalState.OFF }
                    viewModel.simulateTurnSignal(state)
                }
                if (steer != 0f) viewModel.simulateSteering(steer)
                if (speed >= 0f) viewModel.simulateSpeed(speed)
                brake?.let { viewModel.simulateBrake(it) }
                caution?.let {
                    val type = try { CautionType.valueOf(it.uppercase()) } catch (e: Exception) { CautionType.SCHOOL_ZONE }
                    viewModel.simulateCaution(type, 80f)
                }
            } else {
                Log.d(TAG, "Simulation broadcast ignored because executionMode is REAL_INPUT")
            }
        }
    }


    fun register() {
        val filter = IntentFilter("ai.pleos.app.safetywindow.ACTION_SIMULATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(simulationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(simulationReceiver, filter)
        }
    }

    fun unregister() {
        try { activity.unregisterReceiver(simulationReceiver) }
        catch (e: Exception) { Log.w("MainActivity", "Unregister simulationReceiver failed: ${e.message}") }
    }

    fun handleExternalIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return

        val screenStr = intent?.getStringExtra("screen")
        val modeStr = intent?.getStringExtra("mode")
        if (screenStr?.uppercase() == "DEMO" || screenStr?.uppercase() == "FOUR_VIEW" || modeStr?.uppercase() == "DEMO") {
            onDemoModeChanged(true)
        } else if (screenStr?.uppercase() == "HUD" || screenStr?.uppercase() == "2D") {
            onDemoModeChanged(false)
        }
        val noticeStr = intent?.getStringExtra("settings_notice")
        if (noticeStr != null) {
            val displayMsg = when (noticeStr.uppercase()) {
                "SPEED_GATED", "DRIVING_BLOCKED" -> "정차 후 설정 가능"
                "PERSISTED_OK", "PERSISTENCE_VERIFIED" -> "설정 상태 복원 완료: SharedPreferences 정상 로드"
                else -> noticeStr
            }
            viewModel.showSettingsNotice(displayMsg)
        }
        if (intent?.hasExtra("driving_restricted") == true) {
            val restricted = intent.getBooleanExtra("driving_restricted", false)
            viewModel.setDrivingRestrictionsActive(restricted)
        }
        if (intent?.hasExtra("settings_dialog") == true) {
            val open = intent.getBooleanExtra("settings_dialog", false)
            if (open) viewModel.openSettingsPanel() else viewModel.closeSettingsPanel()
        }
        if (intent?.hasExtra("user_enabled") == true) {
            val enabled = intent.getBooleanExtra("user_enabled", true)
            viewModel.toggleUserEnabled(enabled)
        }
        if (intent?.hasExtra("voice_muted") == true) {
            val muted = intent.getBooleanExtra("voice_muted", false)
            viewModel.toggleVoiceMuted(muted)
        }
    }

    companion object { private const val TAG = "VariantIntentController" }
}
