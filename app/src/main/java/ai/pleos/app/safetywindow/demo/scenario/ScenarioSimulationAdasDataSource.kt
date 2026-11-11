package ai.pleos.app.safetywindow.demo.scenario

import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.data.adas.AdasObjectProvider
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.app.safetywindow.data.sensor.VehicleSensorRepository
import ai.pleos.app.safetywindow.data.spatial.CautionType
import ai.pleos.app.safetywindow.data.spatial.SpatialContextEngine
import ai.pleos.playground.adas.ObjectType
import ai.pleos.playground.adas.TrackedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ScenarioSimulationAdasDataSource
 *
 * [격리된 데모/시뮬레이션 전용 데이터 소스]
 * 실차량 ADAS 연동 데이터가 아니며, Virtual Console 시연 및 데모 평가 목적으로만 격리되어 사용된다.
 * 기본 주행 모드에서는 비활성화되며 SCENARIO_INPUT 또는 PARKED_VALIDATION 모드에서만 선택적으로 가상 객체를 생성한다.
 */
class ScenarioSimulationAdasDataSource(
    private val sensorRepository: VehicleSensorRepository,
    private val spatialContextEngine: SpatialContextEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : AdasObjectProvider {

    val source: InputSource = InputSource.SCENARIO
    val isSimulated: Boolean = true

    private val _detectedObjects = MutableStateFlow<List<TrackedObject>>(emptyList())
    override val detectedObjects: StateFlow<List<TrackedObject>> = _detectedObjects.asStateFlow()

    private var observationJob: Job? = null

    override fun start() {
        stop()
        observationJob = scope.launch {
            combine(
                sensorRepository.turnSignalSample,
                sensorRepository.steeringSample,
                spatialContextEngine.activeCautionPoint
            ) { turnSignalSample, steeringSample, cautionPoint ->
                generateObjectsForScenario(turnSignalSample.value, steeringSample.value, cautionPoint?.type)
            }.collect { objects ->
                _detectedObjects.value = objects
            }
        }
    }

    override fun stop() {
        observationJob?.cancel()
        observationJob = null
        _detectedObjects.value = emptyList()
    }

    private fun generateObjectsForScenario(
        turnSignal: TurnSignalState,
        steeringAngle: Float,
        cautionType: CautionType?
    ): List<TrackedObject> {
        val list = mutableListOf<TrackedObject>()

        // 우측 가상 객체 주입 (데모 전용)
        if (turnSignal == TurnSignalState.RIGHT || steeringAngle > 15f) {
            list.add(
                TrackedObject(
                    "DEMO_PED_R",
                    ObjectType.PEDESTRIAN,
                    7.5f,
                    38.0f,
                    3.5f
                )
            )
            if (cautionType == CautionType.NARROW_ROAD || cautionType == CautionType.SCHOOL_ZONE) {
                list.add(
                    TrackedObject(
                        "DEMO_TWO_WHEELER_R",
                        ObjectType.TWO_WHEELER,
                        11.0f,
                        42.0f,
                        18.0f
                    )
                )
            }
        }

        // 좌측 가상 객체 주입 (데모 전용)
        if (turnSignal == TurnSignalState.LEFT || steeringAngle < -15f) {
            list.add(
                TrackedObject(
                    "DEMO_TWO_WHEELER_L",
                    ObjectType.TWO_WHEELER,
                    9.0f,
                    -36.0f,
                    14.0f
                )
            )
        }

        return list
    }

    fun injectCustomObject(obj: TrackedObject) {
        val current = _detectedObjects.value.toMutableList()
        current.removeAll { it.id == obj.id }
        current.add(obj)
        _detectedObjects.value = current
    }

    fun clearInjectedObjects() {
        _detectedObjects.value = emptyList()
    }
}
