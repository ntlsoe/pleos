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
import ai.pleos.app.safetywindow.ui.hud.SafetyAlertLevel
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

class MainActivity : ComponentActivity() {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var carUxRestrictionsManager: CarUxRestrictionsManager? = null

    private lateinit var sensorRepository: VehicleSensorRepository
    private lateinit var spatialEngine: SpatialContextEngine
    private lateinit var adasDataSource: ScenarioSimulationAdasDataSource
    private lateinit var alertManager: MultimodalAlertManager
    private lateinit var viewModel: SafetyViewModel
    private val isDemoModeState = mutableStateOf(false)
    private lateinit var variantIntentController: VariantIntentController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "Location permission denied - operating in RESTRICTED_MODE")
            viewModel.setCarUxRestricted(true)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorRepository = VehicleSensorRepository(this)
        spatialEngine = SpatialContextEngine(this)
        adasDataSource = ScenarioSimulationAdasDataSource(sensorRepository, spatialEngine)
        alertManager = MultimodalAlertManager(this, sensorRepository)
        val settingsRepository = ProductSettingsRepository(this)
        viewModel = SafetyViewModel(
            sensorRepository = sensorRepository,
            spatialEngine = spatialEngine,
            adasProvider = adasDataSource,
            alertManager = alertManager,
            settingsRepository = settingsRepository
        )

        variantIntentController = VariantIntentController(this, viewModel, sensorRepository) { isDemoModeState.value = it }

        initCarServices()

        sensorRepository.startListening(carPropertyManager)

        CapabilityChecker.checkAllCapabilities(this, carPropertyManager)

        requestRequiredPermissions()

        variantIntentController.register()
        variantIntentController.handleExternalIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isDemo by isDemoModeState

            if (isDemo) {
                FourViewDemoInteractiveHost(
                    onClose = { isDemoModeState.value = false }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    SafetyWindowVectorHud(uiState = uiState)

                    if (BuildConfig.DEBUG) {
                        // 우측 상단 3D 4분할 시연 실행 런처 버튼 (Debug Test Console 전용)
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.90f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clickable { isDemoModeState.value = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "3D 4분할 시연 실행 ▶",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 제품 화면 오른쪽 하단 설정 아이콘 및 패널
                    ProductSettingsAnchor(
                        isDrivingRestricted = uiState.isDrivingRestrictionsActive,
                        onOpenPanel = { viewModel.openSettingsPanel() },
                        onShowDrivingNotice = { viewModel.showSettingsNotice("정차 후 설정 가능") },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )

                    uiState.settingsNoticeMessage?.let { notice ->
                        ProductSettingsDrivingNotice(
                            message = notice,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }

                    ProductSettingsDialog(
                        isOpen = uiState.isSettingsPanelOpen,
                        settings = uiState.productSettings,
                        isDrivingRestricted = uiState.isDrivingRestrictionsActive,
                        onToggleUserEnabled = { viewModel.toggleUserEnabled(it) },
                        onToggleVoiceMuted = { viewModel.toggleVoiceMuted(it) },
                        onClose = { viewModel.closeSettingsPanel() },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        variantIntentController.handleExternalIntent(intent)
    }

    private fun initCarServices() {
        try {
            car = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { carInstance, ready ->
                if (ready) {
                    carPropertyManager = carInstance.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    carUxRestrictionsManager = carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    sensorRepository.startListening(carPropertyManager)
                    listenUxRestrictions()
                    Log.i(TAG, "AAOS Car service connected successfully")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AAOS Car service unavailable on this host: ${e.message}")
        }
    }

    private fun listenUxRestrictions() {
        try {
            carUxRestrictionsManager?.registerListener { restrictions ->
                val noVideo = (restrictions.activeRestrictions and
                        android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0
                val restricted = noVideo
                viewModel.setCarUxRestricted(restricted)
                viewModel.setDrivingRestrictionsActive(restricted)
                alertManager.isCarUxRestricted = restricted
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register UX restrictions listener: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        variantIntentController.unregister()
        sensorRepository.stopListening()
        adasDataSource.stop()
        alertManager.destroy()
        try {
            carUxRestrictionsManager?.unregisterListener()
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting Car service: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
