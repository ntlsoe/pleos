package ai.pleos.app.safetywindow.data.sensor

import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.model.SignalSample
import ai.pleos.app.safetywindow.core.policy.RiskPolicy
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TurnSignalState {
    OFF,
    LEFT,
    RIGHT,
    HAZARD
}

/**
 * VehicleSensorRepository
 *
 * AAOS CarPropertyManager(PERF_VEHICLE_SPEED) 및 Pleos Vehicle SDK 연동 관리.
 * 실제 센서 입력(AAOS/PLEOS_SDK)과 시나리오 입력(SCENARIO)을 엄격히 분리하여 처리한다.
 */
class VehicleSensorRepository(
    private val context: Context,
    private val policy: RiskPolicy = RiskPolicy()
) {

    var executionMode: ExecutionMode = ExecutionMode.REAL_INPUT

    // 신호별 출처 및 검증 메타데이터 포함 샘플
    private val _speedSample = MutableStateFlow(
        SignalSample("vehicleSpeed", 0f, "km/h", InputSource.UNKNOWN)
    )
    val speedSample: StateFlow<SignalSample<Float>> = _speedSample.asStateFlow()

    private val _steeringSample = MutableStateFlow(
        SignalSample("steeringAngle", 0f, "degree", InputSource.UNKNOWN)
    )
    val steeringSample: StateFlow<SignalSample<Float>> = _steeringSample.asStateFlow()

    private val _turnSignalSample = MutableStateFlow(
        SignalSample("turnSignal", TurnSignalState.OFF, "state", InputSource.UNKNOWN)
    )
    val turnSignalSample: StateFlow<SignalSample<TurnSignalState>> = _turnSignalSample.asStateFlow()

    private val _brakeSample = MutableStateFlow(
        SignalSample("brakePedal", false, "boolean", InputSource.UNKNOWN)
    )
    val brakeSample: StateFlow<SignalSample<Boolean>> = _brakeSample.asStateFlow()

    // 금지 문구 "일시정지 준수" 대체: 객관적 물리 상태 "차량 정지 상태 감지"
    private val _isVehicleStationaryDetected = MutableStateFlow(false)
    val isVehicleStationaryDetected: StateFlow<Boolean> = _isVehicleStationaryDetected.asStateFlow()

    // 앰비언트 라이트는 필수 경고 수단이 아닌 데모 확장 기능으로만 격리
    var ambientLightDemoEnabled: Boolean = false
    private val _ambientColorRgb = MutableStateFlow(COLOR_SAFE_NEUTRAL)
    val ambientColorRgb: StateFlow<Int> = _ambientColorRgb.asStateFlow()

    private var carPropertyManager: CarPropertyManager? = null

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (executionMode != ExecutionMode.REAL_INPUT) {
                Log.d(TAG, "Ignoring AAOS speed sample while $executionMode is active")
                return
            }
            if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                val speedMps = (value.value as? Float) ?: 0f
                val kmh = speedMps * 3.6f
                val sample = SignalSample(
                    signalName = "vehicleSpeed",
                    value = kmh,
                    unit = "km/h",
                    source = InputSource.AAOS,
                    sourceVersion = "API34",
                    timestampMillis = System.currentTimeMillis(),
                    isSimulated = false
                )
                _speedSample.value = sample
                evaluateStationaryState(speedMps, _brakeSample.value.value)
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "CarPropertyManager error on property: $propId, zone: $zone")
        }
    }

    fun startListening(manager: CarPropertyManager?) {
        this.carPropertyManager = manager
        manager?.let { mgr ->
            try {
                if (mgr.isPropertyAvailable(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)) {
                    val registered = mgr.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10.0f)
                    Log.i(TAG, "Registered AAOS speed callback at 10Hz: $registered")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Speed sensor registration failed: ${e.message}")
            }
            Unit
        }
        initPleosVehicleSdk()
    }

    fun stopListening() {
        carPropertyManager?.let { mgr ->
            try {
                mgr.unregisterCallback(speedCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Unregister speed sensor failed: ${e.message}")
            }
            Unit
        }
    }

    private fun initPleosVehicleSdk() {
        try {
            val vehicleClass = Class.forName("ai.pleos.playground.vehicle.Vehicle")
            Log.i(TAG, "Pleos Vehicle SDK runtime class available: $vehicleClass")
        } catch (e: Throwable) {
            Log.d(TAG, "Pleos Vehicle SDK not directly active, fallback: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // 시뮬레이션 주입 채널 (SCENARIO_INPUT 또는 HYBRID_DEBUG 모드에서만 수용)
    // -------------------------------------------------------------
    fun injectSimulatedSpeed(speedKmh: Float) {
        if (executionMode == ExecutionMode.REAL_INPUT) {
            Log.d(TAG, "Ignored simulated speed in REAL_INPUT mode")
            return
        }
        val sample = SignalSample(
            signalName = "vehicleSpeed",
            value = speedKmh,
            unit = "km/h",
            source = InputSource.SCENARIO,
            isSimulated = true
        )
        _speedSample.value = sample
        evaluateStationaryState(speedKmh / 3.6f, _brakeSample.value.value)
    }

    fun injectSimulatedSteering(angleDeg: Float) {
        if (executionMode == ExecutionMode.REAL_INPUT) return
        _steeringSample.value = SignalSample(
            signalName = "steeringAngle",
            value = angleDeg,
            unit = "degree",
            source = InputSource.SCENARIO,
            isSimulated = true
        )
    }

    fun injectSimulatedTurnSignal(state: TurnSignalState) {
        if (executionMode == ExecutionMode.REAL_INPUT) return
        _turnSignalSample.value = SignalSample(
            signalName = "turnSignal",
            value = state,
            unit = "state",
            source = InputSource.SCENARIO,
            isSimulated = true
        )
    }

    fun injectSimulatedBrake(pressed: Boolean) {
        if (executionMode == ExecutionMode.REAL_INPUT) return
        _brakeSample.value = SignalSample(
            signalName = "brakePedal",
            value = pressed,
            unit = "boolean",
            source = InputSource.SCENARIO,
            isSimulated = true
        )
        evaluateStationaryState(_speedSample.value.value / 3.6f, pressed)
    }

    private fun evaluateStationaryState(speedMps: Float, brakePressed: Boolean) {
        // 객관적 기준: 속도가 stationaryThresholdMps 이하이고 브레이크가 유지될 때 차량 정지 감지
        _isVehicleStationaryDetected.value = (speedMps <= policy.stationaryThresholdMps && brakePressed)
    }

    fun setAmbientWarningColor(colorRgb: Int) {
        if (!ambientLightDemoEnabled) return
        _ambientColorRgb.value = colorRgb
        Log.d(TAG, "[Demo Extension] Ambient light color set to 0x${Integer.toHexString(colorRgb)}")
    }

    companion object {
        private const val TAG = "VehicleSensorRepository"
        const val COLOR_SAFE_NEUTRAL = 0xFF2A364F.toInt() // 중립색
    }
}
