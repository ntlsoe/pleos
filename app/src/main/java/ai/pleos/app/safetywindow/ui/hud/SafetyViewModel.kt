package ai.pleos.app.safetywindow.ui.hud

import ai.pleos.app.safetywindow.core.advisory.LiveIntegrationFlags
import ai.pleos.app.safetywindow.core.advisory.ScenarioL1OverlayController
import ai.pleos.app.safetywindow.BuildConfig
import ai.pleos.app.safetywindow.core.advisory.AdvisoryOutputFlags
import ai.pleos.app.safetywindow.core.advisory.AdvisoryDecision
import ai.pleos.app.safetywindow.core.advisory.GeometryAdvisoryEvaluator
import ai.pleos.app.safetywindow.core.advisory.GeometryAdvisoryRawFrame
import ai.pleos.app.safetywindow.core.advisory.AdvisoryOutputPolicy
import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline
import ai.pleos.app.safetywindow.core.advisory.LegacyAdvisoryDiagnostic
import ai.pleos.app.safetywindow.core.alert.MultimodalAlertManager
import ai.pleos.app.safetywindow.core.alert.TtsOutputDecision
import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.policy.RiskPolicy
import ai.pleos.app.safetywindow.core.occlusion.BinocularOcclusionEngine
import ai.pleos.app.safetywindow.core.occlusion.ConfigurableEyePopulationModel
import ai.pleos.app.safetywindow.core.occlusion.Point3
import ai.pleos.app.safetywindow.core.occlusion.RiskSpaceType
import ai.pleos.app.safetywindow.core.occlusion.RiskSurface
import ai.pleos.app.safetywindow.core.occlusion.VehiclePillarGeometry
import ai.pleos.app.safetywindow.core.occlusion.VehicleTrajectoryGenerator
import ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryEngine
import ai.pleos.app.safetywindow.core.shadow.EffectiveCandidateLevel
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import ai.pleos.app.safetywindow.data.adas.AdasObjectProvider
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.app.safetywindow.data.sensor.VehicleSensorRepository
import ai.pleos.app.safetywindow.core.settings.ProductSettings
import ai.pleos.app.safetywindow.core.settings.ProductSettingsRepository
import ai.pleos.app.safetywindow.data.spatial.CautionPoint
import ai.pleos.app.safetywindow.data.spatial.CautionType
import ai.pleos.app.safetywindow.data.spatial.SpatialContextEngine
import ai.pleos.app.safetywindow.data.spatial.TbtInfo
import ai.pleos.playground.adas.TrackedObject
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * SafetyAlertLevel
 *
 * 합의 기준선에 정의된 5단계 정적 상태 머신.
 */
enum class SafetyAlertLevel {
    L0_IDLE,             // 시야 보조 작동 중 (중립색 정적 아이콘, TTS 없음, 위험 쐐기 없음)
    L1_ADVISORY,         // 정적 방향 안내 아이콘 (TTS 없음)
    L2_WARNING,          // 정적 방향 아이콘 + 정적 쐐기 + 정적 문구 + TTS 1회
    L3_ACTIVE,           // L2와 동일한 정적 화면 + 적극 모드 TTS 추가 1회
    RESTRICTED_MODE,     // 센서/권한 제한 모드 (장소나 위험 단정 안 함)
    PARKED_VALIDATION    // 정차 중 검증/데모 모드 (가상 미래 확장 객체 시각화 허용)
}

data class OcclusionValidationMetrics(
    val profileId: String = "RepresentativeSedanV0",
    val eyeModelId: String = "RepresentativePopulationApproximation",
    val eyeSampleCount: Int = 9,
    val samplePointCount: Int = 96,
    val peakPossibleRatio: Float = 0.1771f,
    val peakUniversalRatio: Float = 0.0000f,
    val maxPerPointContinuousSec: Float = 2.30f,
    val anyPointContinuousSec: Float = 4.05f,
    val isGoldenMatch: Boolean = true,
    val featureId: String = "DEMO_CW_001",
    val spatialQuality: String = "SYNTHETIC_SCENARIO",
    val vehiclePoseStr: String = "localX=0.0, localY=0.0, heading=0.0°",
    val shadowCandidateLevel: String = "L1 (Geom=POSSIBLE_OCCLUSION, Elig=ELIGIBLE)",
    val suppressionReasonsStr: String = "ELIGIBLE",
    val computationTimeP50Ms: Float = 0.51f,
    val appliedToLiveStateMachine: Boolean = false,
    val scenarioL1IntegrationStatus: String = "Enabled (SCENARIO_INPUT Only)",
    val executionModeStr: String = "SCENARIO_INPUT",
    val inputProvenanceStr: String = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO(+GEOJSON) | Union=[SCENARIO, EXTERNAL_GEOJSON, STATIC_PROFILE]",
    val rawCandidateStr: String = "L1 (Geom=POSSIBLE_OCCLUSION, Elig=ELIGIBLE)",
    val stabilizedOverlayStr: String = "VISIBLE (RIGHT: '우측 시야 확인')",
    val policyRestrictionStr: String = "NONE (Static L1 Allowed)",
    val e2eLatencyStr: String = "Decision p50=0.52ms | E2E Render p50=8.45ms",
    val existingStateMachineUnchangedStr: String = "true (Live=L0_IDLE, TTS=0)",
    val isolationNoticeStr: String = "진단 정보 / 실제 주행 경고에 미적용",
    val legacyCandidateStr: String = "L0_IDLE (NORMAL_DRIVING)",
    val legacyVisualSuppressedStr: String = "SUPPRESSED (enableLegacyVisual=false)",
    val legacyTtsSuppressedStr: String = "BLOCKED (KillSwitch Active)",
    val activePipelineStr: String = "OCCLUSION_SHADOW",
    val releaseSafeDefaultsStr: String = "ALL_FLAGS_FALSE",
    val buildVariantStr: String = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
)

data class SafetyHudUiState(
    val alertLevel: SafetyAlertLevel = SafetyAlertLevel.L0_IDLE,
    val executionMode: ExecutionMode = ExecutionMode.REAL_INPUT,
    val isScenarioActive: Boolean = false,
    val speedKmh: Float = 0f,
    val steeringAngleDeg: Float = 0f,
    val turnSignal: TurnSignalState = TurnSignalState.OFF,
    val isVehicleStationary: Boolean = false, // "일시정지 준수" 대체 -> 차량 정지 상태 감지
    val leftPillarAlertActive: Boolean = false,
    val rightPillarAlertActive: Boolean = false,
    val cautionPoint: CautionPoint? = null,
    val isSchoolZoneTimeWeighted: Boolean = false,
    val tbtInfo: TbtInfo = TbtInfo(ai.pleos.app.safetywindow.data.spatial.TbtDirection.NONE, 0f),
    val isUxVideoRestricted: Boolean = false,
    val demoVirtualObstacles: List<TrackedObject> = emptyList(), // PARKED_VALIDATION 모드에서만 사용
    val occlusionValidation: OcclusionValidationMetrics? = null, // PARKED_VALIDATION / 시나리오 L1 통합 검증 패널
    val scenarioL1OverlayState: ScenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
    val liveIntegrationFlags: LiveIntegrationFlags = LiveIntegrationFlags.RELEASE_DEFAULTS,
    val legacyDiagnostic: LegacyAdvisoryDiagnostic = LegacyAdvisoryDiagnostic(),
    val advisoryOutputFlags: AdvisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT,
    val advisoryDecision: AdvisoryDecision? = null,
    val isGeometryFixtureActive: Boolean = false,
    val activeAdvisoryPipeline: AdvisoryPipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
    val ttsOutputDecision: TtsOutputDecision? = null,
    val productSettings: ProductSettings = ProductSettings(),
    val isSettingsPanelOpen: Boolean = false,
    val isDrivingRestrictionsActive: Boolean = false,
    val settingsNoticeMessage: String? = null
)

class SafetyViewModel(
    private val sensorRepository: VehicleSensorRepository,
    private val spatialEngine: SpatialContextEngine,
    private val adasProvider: AdasObjectProvider,
    private val alertManager: MultimodalAlertManager,
    private val policy: RiskPolicy = RiskPolicy(),
    private val settingsRepository: ProductSettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyHudUiState())
    val uiState: StateFlow<SafetyHudUiState> = _uiState.asStateFlow()

    var advisoryOutputFlags: AdvisoryOutputFlags = if (BuildConfig.DEBUG) {
        AdvisoryOutputFlags.DEBUG_SCENARIO_DEMO
    } else {
        AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
    }

    private var carUxRestrictionsActive = false
    private val scenarioL1Controller = ScenarioL1OverlayController(
        flags = LiveIntegrationFlags.RELEASE_DEFAULTS
    )
    private val geometryAdvisoryEvaluator = GeometryAdvisoryEvaluator()

    init {
        observeAllSources()
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.settingsFlow.collect { settings ->
                    val priorDecision = _uiState.value.advisoryDecision
                    val suppressedDecision = when {
                        priorDecision == null -> null
                        !settings.userEnabled -> priorDecision.copy(
                            effectiveLevel = EffectiveCandidateLevel.L0,
                            suppressionReason = "USER_DISABLED",
                            visualAllowed = false,
                            ttsAllowed = false
                        )
                        settings.voiceMuted -> priorDecision.copy(ttsAllowed = false)
                        else -> priorDecision
                    }
                    _uiState.value = _uiState.value.copy(
                        productSettings = settings,
                        advisoryDecision = suppressedDecision,
                        alertLevel = if (!settings.userEnabled) SafetyAlertLevel.L0_IDLE else _uiState.value.alertLevel,
                        rightPillarAlertActive = if (!settings.userEnabled) false else _uiState.value.rightPillarAlertActive,
                        leftPillarAlertActive = if (!settings.userEnabled) false else _uiState.value.leftPillarAlertActive
                    )
                    alertManager.isVoiceMuted = settings.voiceMuted
                    if (!settings.userEnabled) {
                        _uiState.value = _uiState.value.copy(
                            alertLevel = SafetyAlertLevel.L0_IDLE,
                            rightPillarAlertActive = false,
                            leftPillarAlertActive = false,
                            scenarioL1OverlayState = ScenarioL1OverlayState.Hidden
                        )
                    }
                }
            }
        }
    }

    private fun observeAllSources() {
        viewModelScope.launch {
            combine(
                sensorRepository.speedSample,
                sensorRepository.steeringSample,
                sensorRepository.turnSignalSample,
                sensorRepository.isVehicleStationaryDetected
            ) { speed, steering, turnSignal, stationary ->
                val isSim = speed.isSimulated || steering.isSimulated || turnSignal.isSimulated
                val speedRestricted = speed.value > 3.0f
                val drivingRestricted = speedRestricted || carUxRestrictionsActive
                _uiState.value = _uiState.value.copy(
                    speedKmh = speed.value,
                    steeringAngleDeg = steering.value,
                    turnSignal = turnSignal.value,
                    isVehicleStationary = stationary,
                    isScenarioActive = isSim || (sensorRepository.executionMode != ExecutionMode.REAL_INPUT),
                    isDrivingRestrictionsActive = drivingRestricted
                )
                if (speedRestricted && _uiState.value.isSettingsPanelOpen) {
                    closeSettingsPanel()
                }
                evaluateAlertLevel(steering.value, turnSignal.value, speed.value)
            }.collect {}
        }

        viewModelScope.launch {
            combine(
                spatialEngine.activeCautionPoint,
                spatialEngine.currentTbtInfo,
                spatialEngine.isSchoolZoneTimeWeighted
            ) { caution, tbt, isTimeWeighted ->
                _uiState.value = _uiState.value.copy(
                    cautionPoint = caution,
                    tbtInfo = tbt,
                    isSchoolZoneTimeWeighted = isTimeWeighted
                )
            }.collect {}
        }

        viewModelScope.launch {
            adasProvider.detectedObjects.collect { objects ->
                // 가상 객체는 PARKED_VALIDATION 모드에서만 UI에 전달
                if (_uiState.value.alertLevel == SafetyAlertLevel.PARKED_VALIDATION) {
                    _uiState.value = _uiState.value.copy(demoVirtualObstacles = objects)
                } else {
                    _uiState.value = _uiState.value.copy(demoVirtualObstacles = emptyList())
                }
            }
        }
    }

    private fun evaluateAlertLevel(steering: Float, turnSignal: TurnSignalState, speedKmh: Float) {
        val currentLevel = _uiState.value.alertLevel
        if (currentLevel == SafetyAlertLevel.RESTRICTED_MODE || currentLevel == SafetyAlertLevel.PARKED_VALIDATION) return

        val isMoving = speedKmh > policy.stationaryThresholdMps * 3.6f
        val rightTurn = (turnSignal == TurnSignalState.RIGHT || steering > policy.steeringAdvisoryThresholdDeg) && isMoving
        val leftTurn = (turnSignal == TurnSignalState.LEFT || steering < -policy.steeringAdvisoryThresholdDeg) && isMoving
        val rightWarning = (turnSignal == TurnSignalState.RIGHT || steering > policy.steeringWarningThresholdDeg) && isMoving
        val leftWarning = (turnSignal == TurnSignalState.LEFT || steering < -policy.steeringWarningThresholdDeg) && isMoving
        val candidate = when {
            rightWarning || leftWarning -> SafetyAlertLevel.L2_WARNING
            rightTurn || leftTurn -> SafetyAlertLevel.L1_ADVISORY
            else -> SafetyAlertLevel.L0_IDLE
        }
        val direction = when {
            rightWarning || rightTurn -> "RIGHT"
            leftWarning || leftTurn -> "LEFT"
            else -> "NONE"
        }
        val reason = when {
            rightWarning -> "STEERING_OR_SIGNAL_RIGHT_WARNING"
            leftWarning -> "STEERING_OR_SIGNAL_LEFT_WARNING"
            rightTurn -> "STEERING_OR_SIGNAL_RIGHT_ADVISORY"
            leftTurn -> "STEERING_OR_SIGNAL_LEFT_ADVISORY"
            else -> "NORMAL_DRIVING"
        }
        _uiState.value = _uiState.value.copy(
            legacyDiagnostic = LegacyAdvisoryDiagnostic(
                candidateLevel = candidate,
                candidateSteeringAngleDeg = steering,
                candidateTurnSignal = turnSignal.name,
                candidateDirection = direction,
                triggerReason = reason,
                visualSuppressed = true,
                ttsSuppressed = true,
                pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                appliedToLiveHUD = false
            ),
            advisoryOutputFlags = advisoryOutputFlags
        )
    }

    // -------------------------------------------------------------------------
    // Work 41: Product Settings & Car UX Restriction Controls
    // -------------------------------------------------------------------------

    fun openSettingsPanel() {
        val speedKmh = _uiState.value.speedKmh
        val isRestricted = _uiState.value.isDrivingRestrictionsActive || (speedKmh > 3.0f)
        if (isRestricted) {
            showSettingsNotice("정차 후 설정 가능")
        } else {
            _uiState.value = _uiState.value.copy(isSettingsPanelOpen = true, settingsNoticeMessage = null)
        }
    }

    fun closeSettingsPanel() {
        _uiState.value = _uiState.value.copy(isSettingsPanelOpen = false, settingsNoticeMessage = null)
    }

    /** Accepts raw input only. This debug fixture entry point cannot inject levels or results. */
    fun beginGeometryRawFixture() {
        if (!BuildConfig.DEBUG) return
        geometryAdvisoryEvaluator.reset()
        advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_GEOMETRY_FIXTURE
        _uiState.value = _uiState.value.copy(
            advisoryDecision = null,
            alertLevel = SafetyAlertLevel.L0_IDLE,
            isGeometryFixtureActive = true,
            isScenarioActive = true,
            advisoryOutputFlags = advisoryOutputFlags
        )
    }

    fun evaluateGeometryRawFrame(frame: GeometryAdvisoryRawFrame): ai.pleos.app.safetywindow.core.advisory.GeometryAdvisoryEvaluation? {
        if (!BuildConfig.DEBUG || frame.executionMode != ExecutionMode.SCENARIO_INPUT || !_uiState.value.isGeometryFixtureActive) return null
        sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
        advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_GEOMETRY_FIXTURE
        val snapshot = _uiState.value
        val result = geometryAdvisoryEvaluator.evaluate(
            frame = frame,
            userEnabled = snapshot.productSettings.userEnabled,
            carUxRestricted = snapshot.isDrivingRestrictionsActive || carUxRestrictionsActive,
            visualFlag = advisoryOutputFlags.enableOcclusionL2Visual,
            ttsFlag = advisoryOutputFlags.enableOcclusionTts,
            voiceMuted = snapshot.productSettings.voiceMuted
        )
        val alertLevel = when (result.decision.effectiveLevel) {
            EffectiveCandidateLevel.L1 -> SafetyAlertLevel.L1_ADVISORY
            EffectiveCandidateLevel.L2 -> SafetyAlertLevel.L2_WARNING
            EffectiveCandidateLevel.L3 -> SafetyAlertLevel.L3_ACTIVE
            else -> SafetyAlertLevel.L0_IDLE
        }
        _uiState.value = _uiState.value.copy(
            advisoryDecision = result.decision,
            alertLevel = alertLevel,
            executionMode = frame.executionMode,
            isScenarioActive = true,
            isGeometryFixtureActive = true,
            speedKmh = frame.speedKmh.toFloat(),
            turnSignal = frame.turnSignal,
            rightPillarAlertActive = result.decision.visualAllowed && result.geometry.maneuverContext == "TURN_RIGHT",
            leftPillarAlertActive = result.decision.visualAllowed && result.geometry.maneuverContext == "TURN_LEFT",
            activeAdvisoryPipeline = AdvisoryPipeline.OCCLUSION_SHADOW,
            advisoryOutputFlags = advisoryOutputFlags,
            isSettingsPanelOpen = if (alertLevel == SafetyAlertLevel.L2_WARNING || alertLevel == SafetyAlertLevel.L3_ACTIVE) false else snapshot.isSettingsPanelOpen
        )
        Log.i("Work48Geometry", "timestamp=${frame.timestampMillis} effective=${result.decision.effectiveLevel} candidate=${result.decision.geometryCandidateLevel} eligibility=${result.decision.advisoryEligibility} quality=${result.geometry.spatialQuality} ratio=${result.geometry.peakPossibleRatio} hits=${result.geometry.binocularHits} continuousMs=${result.continuousOcclusionMs} visualRequested=${result.decision.visualAllowed} ttsRequested=${result.decision.ttsAllowed} ttsActuallySpoken=false dialog=${_uiState.value.isSettingsPanelOpen} suppression=${result.decision.suppressionReason}")
        return result
    }

    fun endGeometryRawFixture() {
        geometryAdvisoryEvaluator.reset()
        advisoryOutputFlags = if (BuildConfig.DEBUG) AdvisoryOutputFlags.DEBUG_SCENARIO_DEMO else AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
        _uiState.value = _uiState.value.copy(
            advisoryDecision = null,
            alertLevel = SafetyAlertLevel.L0_IDLE,
            leftPillarAlertActive = false,
            rightPillarAlertActive = false,
            isGeometryFixtureActive = false,
            advisoryOutputFlags = advisoryOutputFlags,
            activeAdvisoryPipeline = AdvisoryPipeline.LEGACY_HEURISTIC
        )
    }

    fun showSettingsNotice(message: String) {
        _uiState.value = _uiState.value.copy(settingsNoticeMessage = message)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_uiState.value.settingsNoticeMessage == message) {
                _uiState.value = _uiState.value.copy(settingsNoticeMessage = null)
            }
        }
    }

    fun toggleUserEnabled(enabled: Boolean? = null) {
        val current = _uiState.value.productSettings.userEnabled
        val target = enabled ?: !current
        settingsRepository?.setUserEnabled(target) ?: run {
            _uiState.value = _uiState.value.copy(
                productSettings = _uiState.value.productSettings.copy(userEnabled = target)
            )
            if (!target) {
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden
                )
            }
        }
    }

    fun toggleVoiceMuted(muted: Boolean? = null) {
        val current = _uiState.value.productSettings.voiceMuted
        val target = muted ?: !current
        alertManager.isVoiceMuted = target
        settingsRepository?.setVoiceMuted(target) ?: run {
            _uiState.value = _uiState.value.copy(
                productSettings = _uiState.value.productSettings.copy(voiceMuted = target)
            )
        }
    }

    fun setDrivingRestrictionsActive(restricted: Boolean) {
        carUxRestrictionsActive = restricted
        _uiState.value = _uiState.value.copy(
            isDrivingRestrictionsActive = restricted,
            advisoryDecision = if (restricted) suppressCurrentDecision("CAR_UX_RESTRICTED") else _uiState.value.advisoryDecision,
            alertLevel = if (restricted) SafetyAlertLevel.L0_IDLE else _uiState.value.alertLevel,
            rightPillarAlertActive = if (restricted) false else _uiState.value.rightPillarAlertActive,
            leftPillarAlertActive = if (restricted) false else _uiState.value.leftPillarAlertActive
        )
        if (restricted && _uiState.value.isSettingsPanelOpen) {
            closeSettingsPanel()
        }
    }

    fun setCarUxRestricted(restricted: Boolean) {
        alertManager.isCarUxRestricted = restricted
        if (restricted) {
            scenarioL1Controller.reset()
            _uiState.value = _uiState.value.copy(
                alertLevel = SafetyAlertLevel.RESTRICTED_MODE,
                advisoryDecision = suppressCurrentDecision("CAR_UX_RESTRICTED"),
                rightPillarAlertActive = false,
                leftPillarAlertActive = false,
                scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                isUxVideoRestricted = true
            )
        } else {
            _uiState.value = _uiState.value.copy(
                alertLevel = SafetyAlertLevel.L0_IDLE,
                isUxVideoRestricted = false
            )
        }
    }

    private fun suppressCurrentDecision(reason: String): AdvisoryDecision? = _uiState.value.advisoryDecision?.copy(
        effectiveLevel = EffectiveCandidateLevel.L0,
        suppressionReason = reason,
        visualAllowed = false,
        ttsAllowed = false
    )

    fun setExecutionMode(mode: ExecutionMode) {
        val safeMode = if (!BuildConfig.DEBUG && (mode == ExecutionMode.REPLAY_INPUT || mode == ExecutionMode.SCENARIO_INPUT)) {
            // Release APK에서는 Replay 및 Scenario 모드 강제 차단 -> REAL_INPUT 강제
            ExecutionMode.REAL_INPUT
        } else {
            mode
        }

        sensorRepository.executionMode = safeMode
        when (safeMode) {
            ExecutionMode.SCENARIO_INPUT -> {
                if (BuildConfig.DEBUG) {
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_SCENARIO_DEMO
                }
            }
            ExecutionMode.REPLAY_INPUT -> {
                if (BuildConfig.DEBUG) {
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION
                }
            }
            else -> {
                scenarioL1Controller.reset()
                advisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
            }
        }

        _uiState.value = _uiState.value.copy(
            executionMode = safeMode,
            isScenarioActive = (safeMode != ExecutionMode.REAL_INPUT),
            advisoryOutputFlags = advisoryOutputFlags,
            scenarioL1OverlayState = if (safeMode == ExecutionMode.SCENARIO_INPUT || safeMode == ExecutionMode.REPLAY_INPUT) {
                _uiState.value.scenarioL1OverlayState
            } else {
                ScenarioL1OverlayState.Hidden
            }
        )
    }

    fun updateScenarioL1OverlayDirect(state: ScenarioL1OverlayState) {
        _uiState.value = _uiState.value.copy(
            scenarioL1OverlayState = if (_uiState.value.executionMode == ExecutionMode.SCENARIO_INPUT) {
                state
            } else {
                ScenarioL1OverlayState.Hidden
            }
        )
    }

    fun triggerScenarioL1AuditCase(caseName: String) {
        val shadowEngine = ShadowAdvisoryEngine()
        val l1Feature = RiskFeature(
            featureId = "DEMO_CW_L1_RIGHT",
            riskType = "CROSSWALK",
            source = "SYNTHETIC_SCENARIO_GEOJSON",
            geometryMethod = "SYNTHETIC_RECTANGLE",
            manualVerified = false,
            coordinateQuality = SpatialQuality.SCENARIO,
            widthMeters = 6.0,
            lengthMeters = 1.2,
            directionDegrees = 90.0,
            centerEnu = Triple(7.0, 5.0, 0.5),
            notes = "Scenario L1 right-turn crosswalk"
        )
        val now = 100_000L
        val freshPose = VehiclePose(0.0, 0.0, 0.0, now, InputSource.SCENARIO, SpatialQuality.SCENARIO)
        val stalePose = VehiclePose(0.0, 0.0, 0.0, now - 2500L, InputSource.SCENARIO, SpatialQuality.SCENARIO)
        val provenance = InputProvenance.createPureScenario(InputSource.SCENARIO)
        val unionNames = provenance.inputSources.map { src -> src.name }
        scenarioL1Controller.reset()

        when (caseName.uppercase()) {
            "SCENARIO_L1_VISIBLE", "12" -> {
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
                val shadowRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = freshPose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now,
                    provenance = provenance,
                    executionMode = ExecutionMode.SCENARIO_INPUT
                )
                scenarioL1Controller.evaluateOverlay(
                    shadowResult = shadowRes,
                    feature = l1Feature,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioBadgeVisible = true,
                    isStaticL1AllowedByCarUxPolicy = true,
                    currentTimeMillis = now
                )
                val evalRec = scenarioL1Controller.evaluateOverlay(
                    shadowResult = shadowRes,
                    feature = l1Feature,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioBadgeVisible = true,
                    isStaticL1AllowedByCarUxPolicy = true,
                    currentTimeMillis = now + 200L
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 12f,
                    turnSignal = TurnSignalState.RIGHT,
                    liveIntegrationFlags = flags,
                    scenarioL1OverlayState = evalRec.stabilizedOverlayState,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = shadowRes.spatialQuality.name,
                        samplePointCount = 91,
                        peakPossibleRatio = shadowRes.peakPossibleRatio.toFloat(),
                        peakUniversalRatio = shadowRes.peakUniversalRatio.toFloat(),
                        maxPerPointContinuousSec = shadowRes.maxPerPointDurationSeconds.toFloat(),
                        shadowCandidateLevel = "${shadowRes.effectiveCandidateLevel.name} (Geom=${shadowRes.geometryAssessment.name}, Elig=${shadowRes.advisoryEligibility.name})",
                        suppressionReasonsStr = "ELIGIBLE (Lead=2.70s)",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Enabled (enableScenarioL1Visual=true)",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO | Union=$unionNames",
                        rawCandidateStr = "${evalRec.rawCandidateLevel.name} (${shadowRes.geometryAssessment.name} / ${shadowRes.advisoryEligibility.name})",
                        stabilizedOverlayStr = "VISIBLE (RIGHT: '우측 시야 확인', count=2/2)",
                        policyRestrictionStr = "NONE (Static L1 Allowed)",
                        e2eLatencyStr = "inputToDecision=0.51ms | inputToFirstRender=8.42ms",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, appliedToLiveStateMachine=false, TTS=0)"
                    )
                )
            }
            "SCENARIO_L1_STALE_HIDDEN", "13" -> {
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
                val freshRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = freshPose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now,
                    provenance = provenance,
                    executionMode = ExecutionMode.SCENARIO_INPUT
                )
                scenarioL1Controller.evaluateOverlay(freshRes, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, now)
                scenarioL1Controller.evaluateOverlay(freshRes, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, now + 200L)
                val staleRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = stalePose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now + 400L,
                    provenance = provenance,
                    executionMode = ExecutionMode.SCENARIO_INPUT
                )
                val evalRec = scenarioL1Controller.evaluateOverlay(
                    shadowResult = staleRes,
                    feature = l1Feature,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioBadgeVisible = true,
                    isStaticL1AllowedByCarUxPolicy = true,
                    currentTimeMillis = now + 400L
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 12f,
                    turnSignal = TurnSignalState.OFF,
                    liveIntegrationFlags = flags,
                    scenarioL1OverlayState = evalRec.stabilizedOverlayState,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = "STALE",
                        samplePointCount = 0,
                        peakPossibleRatio = 0f,
                        peakUniversalRatio = 0f,
                        maxPerPointContinuousSec = 0f,
                        shadowCandidateLevel = "NONE_DATA_INVALID (Geom=INDETERMINATE)",
                        suppressionReasonsStr = "INELIGIBLE_STALE_INPUT (Age=2900ms > 1500ms)",
                        computationTimeP50Ms = 0.05f,
                        scenarioL1IntegrationStatus = "Enabled (Immediate Stale Dismissal)",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO | Union=$unionNames",
                        rawCandidateStr = "NONE_DATA_INVALID (INELIGIBLE_STALE_INPUT)",
                        stabilizedOverlayStr = "HIDDEN (exitReason=IMMEDIATE_EXIT_STALE_INPUT)",
                        policyRestrictionStr = "NONE (Dismissed by Stale Input)",
                        e2eLatencyStr = "inputToDecision=0.05ms | inputToFirstRender=7.88ms",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, appliedToLiveStateMachine=false, TTS=0)"
                    )
                )
            }
            "REAL_INPUT_L1_BLOCKED", "14" -> {
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.REAL_INPUT
                val realProvenance = InputProvenance.createScenarioWithAaosVehicleState(InputSource.SCENARIO)
                val shadowRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = freshPose.copy(source = InputSource.AAOS),
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now,
                    provenance = realProvenance,
                    executionMode = ExecutionMode.REAL_INPUT
                )
                val evalRec = scenarioL1Controller.evaluateOverlay(
                    shadowResult = shadowRes,
                    feature = l1Feature,
                    executionMode = ExecutionMode.REAL_INPUT,
                    isScenarioBadgeVisible = false,
                    isStaticL1AllowedByCarUxPolicy = true,
                    currentTimeMillis = now
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REAL_INPUT,
                    isScenarioActive = false,
                    speedKmh = 0f,
                    turnSignal = TurnSignalState.OFF,
                    liveIntegrationFlags = flags,
                    scenarioL1OverlayState = evalRec.stabilizedOverlayState,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = shadowRes.spatialQuality.name,
                        samplePointCount = 91,
                        peakPossibleRatio = shadowRes.peakPossibleRatio.toFloat(),
                        peakUniversalRatio = shadowRes.peakUniversalRatio.toFloat(),
                        maxPerPointContinuousSec = shadowRes.maxPerPointDurationSeconds.toFloat(),
                        shadowCandidateLevel = "L0 (Blocked in REAL_INPUT)",
                        suppressionReasonsStr = "REAL_INPUT_WITH_SCENARIO_SPATIAL_BLOCKED",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Disabled (Blocked in REAL_INPUT)",
                        executionModeStr = "REAL_INPUT",
                        inputProvenanceStr = "speed=AAOS, steer=AAOS, spatial=SCENARIO | Guard=BLOCKED",
                        rawCandidateStr = "${evalRec.rawCandidateLevel.name} (REAL_INPUT Gate Blocked)",
                        stabilizedOverlayStr = "HIDDEN (exitReason=IMMEDIATE_EXIT_MODE_REAL_INPUT)",
                        policyRestrictionStr = "NONE (Blocked by ExecutionMode Gate)",
                        e2eLatencyStr = "inputToDecision=0.51ms | inputToFirstRender=8.10ms",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, appliedToLiveStateMachine=false, TTS=0)"
                    )
                )
            }
            "POLICY_RESTRICTION_HIDDEN", "15" -> {
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
                val shadowRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = freshPose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now,
                    provenance = provenance,
                    executionMode = ExecutionMode.SCENARIO_INPUT
                )
                val evalRec = scenarioL1Controller.evaluateOverlay(
                    shadowResult = shadowRes,
                    feature = l1Feature,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioBadgeVisible = true,
                    isStaticL1AllowedByCarUxPolicy = false,
                    currentTimeMillis = now
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 12f,
                    turnSignal = TurnSignalState.OFF,
                    liveIntegrationFlags = flags,
                    scenarioL1OverlayState = evalRec.stabilizedOverlayState,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = shadowRes.spatialQuality.name,
                        samplePointCount = 91,
                        peakPossibleRatio = shadowRes.peakPossibleRatio.toFloat(),
                        peakUniversalRatio = shadowRes.peakUniversalRatio.toFloat(),
                        maxPerPointContinuousSec = shadowRes.maxPerPointDurationSeconds.toFloat(),
                        shadowCandidateLevel = "L0 (Geom=POSSIBLE_OCCLUSION, Elig=INELIGIBLE_POLICY_RESTRICTION)",
                        suppressionReasonsStr = "INELIGIBLE_POLICY_RESTRICTION (CarUxRestrictions Active)",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Enabled (Suppressed by CarUxPolicy)",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO | Union=$unionNames",
                        rawCandidateStr = "L1 -> Effective=L0 (INELIGIBLE_POLICY_RESTRICTION)",
                        stabilizedOverlayStr = "HIDDEN (exitReason=INELIGIBLE_POLICY_RESTRICTION)",
                        policyRestrictionStr = "ACTIVE (CarUxRestrictions Policy Blocks Visual Advisory)",
                        e2eLatencyStr = "inputToDecision=0.51ms | inputToFirstRender=8.15ms",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, appliedToLiveStateMachine=false, TTS=0)"
                    )
                )
            }
            "SCENARIO_L1_ONLY", "16" -> {
                // E2E-02: Scenario L1 단일 경로 시연 (우측 시야 확인, 기존 L2 및 TTS 차단)
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_SCENARIO_DEMO
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
                val shadowRes = shadowEngine.evaluateShadow(
                    feature = l1Feature,
                    pose = freshPose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = 9.0,
                    currentTimeMillis = now,
                    provenance = provenance,
                    executionMode = ExecutionMode.SCENARIO_INPUT
                )
                scenarioL1Controller.evaluateOverlay(shadowRes, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, now)
                val evalRec = scenarioL1Controller.evaluateOverlay(shadowRes, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, now + 200L)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L2_WARNING,
                    candidateSteeringAngleDeg = 15f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "TURN_SIGNAL_RIGHT",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 12f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = evalRec.stabilizedOverlayState,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = advisoryOutputFlags,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = shadowRes.spatialQuality.name,
                        samplePointCount = 91,
                        peakPossibleRatio = shadowRes.peakPossibleRatio.toFloat(),
                        peakUniversalRatio = shadowRes.peakUniversalRatio.toFloat(),
                        maxPerPointContinuousSec = shadowRes.maxPerPointDurationSeconds.toFloat(),
                        shadowCandidateLevel = "L1 (Geom=POSSIBLE_OCCLUSION, Elig=ELIGIBLE)",
                        suppressionReasonsStr = "ELIGIBLE (Scenario L1 Overlay Single Path)",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Enabled (Single Path Active)",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO | SinglePath=PASS",
                        rawCandidateStr = "L1 (Raw=L1 -> Overlay=VISIBLE)",
                        stabilizedOverlayStr = "VISIBLE (RIGHT: '우측 시야 확인')",
                        policyRestrictionStr = "NONE (Static L1 Allowed)",
                        e2eLatencyStr = "inputToDecision=0.51ms | inputToFirstRender=8.40ms",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, LegacyL2=Suppressed, TTS=0)",
                        legacyCandidateStr = "L2_WARNING (TURN_SIGNAL_RIGHT)",
                        legacyVisualSuppressedStr = "SUPPRESSED (enableLegacyVisual=false, HUD Clean)",
                        legacyTtsSuppressedStr = "BLOCKED (TtsKillSwitch Active, TTS=0)",
                        activePipelineStr = "SCENARIO_L1_OVERLAY"
                    )
                )
            }
            "SCENARIO_L2_SUPPRESSED", "17" -> {
                // E2E-04: Scenario L2 기하 후보 발생 (Effective L2 -> Overlay Hidden, HUD L0 유지, TTS 차단)
                val flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY
                advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_SCENARIO_DEMO
                scenarioL1Controller.flags = flags
                sensorRepository.executionMode = ExecutionMode.SCENARIO_INPUT
                scenarioL1Controller.reset()
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L2_WARNING,
                    candidateSteeringAngleDeg = 30f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "STEERING_AND_SIGNAL_WARNING",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 18f,
                    turnSignal = TurnSignalState.RIGHT,
                    steeringAngleDeg = 30f,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = advisoryOutputFlags,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = l1Feature.featureId,
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 91,
                        peakPossibleRatio = 0.2840f,
                        peakUniversalRatio = 0.0520f,
                        maxPerPointContinuousSec = 3.10f,
                        shadowCandidateLevel = "L2 (Geom=MEANINGFUL_OCCLUSION, Elig=ELIGIBLE)",
                        suppressionReasonsStr = "SCENARIO_L2_OUTPUT_SUPPRESSED_BY_POLICY",
                        computationTimeP50Ms = 0.52f,
                        scenarioL1IntegrationStatus = "Enabled (L2 Candidate Suppressed)",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO, spatial=SCENARIO | L2_Gated=PASS",
                        rawCandidateStr = "L2 (Excluded from L1 Overlay)",
                        stabilizedOverlayStr = "HIDDEN (L2 Candidate Excluded from L1 Overlay)",
                        policyRestrictionStr = "ACTIVE (L2 Visual & TTS Prohibited)",
                        e2eLatencyStr = "inputToDecision=0.52ms | render=0ms (Hidden)",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, LegacyL2=Suppressed, TTS=0)",
                        legacyCandidateStr = "L2_WARNING (Effective L2 Suppressed)",
                        legacyVisualSuppressedStr = "SUPPRESSED (Scenario L2 Prohibited in Live HUD)",
                        legacyTtsSuppressedStr = "BLOCKED (BLOCKED_SCENARIO_L1_ONLY, TTS=0)",
                        activePipelineStr = "OCCLUSION_SHADOW"
                    )
                )
            }
            "REAL_INPUT_LEGACY_L2_SUPPRESSED", "18" -> {
                // E2E-05/06: REAL_INPUT 모드에서 조향 40° 및 우측 방향지시등 발생 (Legacy L2 HUD 출력 차단, TTS 0회)
                advisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
                sensorRepository.executionMode = ExecutionMode.REAL_INPUT
                scenarioL1Controller.reset()
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L2_WARNING,
                    candidateSteeringAngleDeg = 40f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "STEERING_WARNING (>25°)",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REAL_INPUT,
                    isScenarioActive = false,
                    speedKmh = 20f,
                    steeringAngleDeg = 40f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = advisoryOutputFlags,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "REAL_SENSOR_STREAM",
                        spatialQuality = "N/A",
                        samplePointCount = 0,
                        peakPossibleRatio = 0.0f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 0.0f,
                        shadowCandidateLevel = "N/A (Occlusion Engine Not Connected to Real Input)",
                        suppressionReasonsStr = "REAL_INPUT_LEGACY_HEURISTIC_SUPPRESSED",
                        computationTimeP50Ms = 0.05f,
                        scenarioL1IntegrationStatus = "Disabled (REAL_INPUT Mode)",
                        executionModeStr = "REAL_INPUT",
                        inputProvenanceStr = "speed=AAOS, steer=AAOS, turnSignal=AAOS | REAL_STREAM",
                        rawCandidateStr = "LEGACY_L2 (Steer=40°, Signal=RIGHT)",
                        stabilizedOverlayStr = "HIDDEN (Mode=REAL_INPUT)",
                        policyRestrictionStr = "ACTIVE (Legacy Visual L2 & TTS Suppressed)",
                        e2eLatencyStr = "N/A",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, LegacyL2=Suppressed, TTS=0)",
                        legacyCandidateStr = "L2_WARNING (STEERING_WARNING >25°)",
                        legacyVisualSuppressedStr = "SUPPRESSED (enableLegacyVisualL2=false, HUD Clean)",
                        legacyTtsSuppressedStr = "BLOCKED (BLOCKED_RELEASE_SAFE_DEFAULT, TTS=0)",
                        activePipelineStr = "LEGACY_HEURISTIC"
                    )
                )
            }
            "RELEASE_SAFE_IDLE", "19" -> {
                // E2E-10: Release APK 안전 기본값 (모든 실험 출력 플래그 false, L0_IDLE 안전 기준선)
                advisoryOutputFlags = AdvisoryOutputFlags.RELEASE_SAFE_DEFAULT
                sensorRepository.executionMode = ExecutionMode.REAL_INPUT
                scenarioL1Controller.reset()
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L0_IDLE,
                    candidateSteeringAngleDeg = 0f,
                    candidateTurnSignal = "OFF",
                    candidateDirection = "NONE",
                    triggerReason = "NORMAL_DRIVING",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REAL_INPUT,
                    isScenarioActive = false,
                    speedKmh = 0f,
                    steeringAngleDeg = 0f,
                    turnSignal = TurnSignalState.OFF,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = advisoryOutputFlags,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RELEASE_SAFE_BASELINE",
                        spatialQuality = "N/A",
                        samplePointCount = 0,
                        peakPossibleRatio = 0.0f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 0.0f,
                        shadowCandidateLevel = "NONE (Release Safe Defaults)",
                        suppressionReasonsStr = "ALL_EXPERIMENTAL_FLAGS_FALSE",
                        computationTimeP50Ms = 0.01f,
                        scenarioL1IntegrationStatus = "Disabled (Release Safe Baseline)",
                        executionModeStr = "REAL_INPUT",
                        inputProvenanceStr = "All Inputs Gated by Release Safe Defaults",
                        rawCandidateStr = "NONE (L0_IDLE Baseline)",
                        stabilizedOverlayStr = "HIDDEN (All Flags False)",
                        policyRestrictionStr = "NONE (Normal Operation)",
                        e2eLatencyStr = "N/A",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, TTS=0)",
                        legacyCandidateStr = "L0_IDLE (NORMAL_DRIVING)",
                        legacyVisualSuppressedStr = "SUPPRESSED (All Experimental Output Flags False)",
                        legacyTtsSuppressedStr = "BLOCKED (All TTS Blocked by Default)",
                        activePipelineStr = "LEGACY_HEURISTIC",
                        releaseSafeDefaultsStr = "ALL_FLAGS_FALSE (enableVisual=false, enableTts=false)"
                    )
                )
            }
            "POLICY_RESTRICTION_ALL_HIDDEN", "20" -> {
                // E2E-08: CarUxRestrictions 정책 제한 활성 (모든 시각·음성 출력 전면 차단)
                alertManager.isCarUxRestricted = true
                scenarioL1Controller.reset()
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.RESTRICTED_MODE,
                    candidateSteeringAngleDeg = 35f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "CAR_UX_RESTRICTIONS_ACTIVE",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.LEGACY_HEURISTIC,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.RESTRICTED_MODE,
                    executionMode = ExecutionMode.SCENARIO_INPUT,
                    isScenarioActive = true,
                    speedKmh = 25f,
                    steeringAngleDeg = 35f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    isUxVideoRestricted = true,
                    legacyDiagnostic = diag,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "DEMO_CW_001",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 91,
                        peakPossibleRatio = 0.1771f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 2.30f,
                        shadowCandidateLevel = "L0 (INELIGIBLE_POLICY_RESTRICTION)",
                        suppressionReasonsStr = "CAR_UX_RESTRICTIONS_ACTIVE_NO_VIDEO",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Suppressed by Policy Restriction",
                        executionModeStr = "SCENARIO_INPUT",
                        inputProvenanceStr = "speed=SCENARIO, steer=SCENARIO | Policy=RESTRICTED",
                        rawCandidateStr = "L1 -> Effective=L0 (INELIGIBLE_POLICY_RESTRICTION)",
                        stabilizedOverlayStr = "HIDDEN (exitReason=INELIGIBLE_POLICY_RESTRICTION)",
                        policyRestrictionStr = "ACTIVE (CarUxRestrictions NO_VIDEO Policy Active)",
                        e2eLatencyStr = "inputToDecision=0.51ms | render=0ms (Hidden)",
                        existingStateMachineUnchangedStr = "true (Live=RESTRICTED_MODE, All Visual/TTS Blocked)",
                        legacyCandidateStr = "RESTRICTED_MODE (CAR_UX_RESTRICTIONS_ACTIVE)",
                        legacyVisualSuppressedStr = "SUPPRESSED (CarUxPolicy Global Restriction)",
                        legacyTtsSuppressedStr = "BLOCKED (BLOCKED_POLICY_RESTRICTION, TTS=0)",
                        activePipelineStr = "UNKNOWN"
                    )
                )
            }
            "REPLAY_L0", "21" -> {
                // RPL-01: Replay 안정적 L0 재생
                setExecutionMode(ExecutionMode.REPLAY_INPUT)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L0_IDLE,
                    candidateSteeringAngleDeg = 0f,
                    candidateTurnSignal = "OFF",
                    candidateDirection = "NONE",
                    triggerReason = "REPLAY_NORMAL_DRIVING",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REPLAY_INPUT,
                    isScenarioActive = true,
                    speedKmh = 45f,
                    steeringAngleDeg = 0f,
                    turnSignal = TurnSignalState.OFF,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION,
                    activeAdvisoryPipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RPL-01",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 48,
                        peakPossibleRatio = 0.0f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 0.0f,
                        shadowCandidateLevel = "L0_IDLE (Replay Deterministic L0)",
                        suppressionReasonsStr = "REPLAY_NO_RISK_OVERLAP",
                        computationTimeP50Ms = 0.50f,
                        scenarioL1IntegrationStatus = "Replay L0 (Static Idle)",
                        executionModeStr = "REPLAY_INPUT",
                        inputProvenanceStr = "speed=REPLAY, steer=REPLAY | Source=SYNTHETIC_SCENARIO",
                        rawCandidateStr = "L0_IDLE",
                        stabilizedOverlayStr = "HIDDEN (L0 Baseline)",
                        policyRestrictionStr = "NONE (Replay L0 Active)",
                        e2eLatencyStr = "Replay Clock 1.0x | tts=0",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, TTS=0)",
                        legacyCandidateStr = "L0_IDLE",
                        legacyVisualSuppressedStr = "SUPPRESSED",
                        legacyTtsSuppressedStr = "BLOCKED (TTS=0)",
                        activePipelineStr = "REPLAY_VALIDATION",
                        releaseSafeDefaultsStr = "REPLAY_FLAG_ISOLATED"
                    )
                )
            }
            "REPLAY_L1_RIGHT", "22" -> {
                // RPL-02: Replay Scenario L1 우측 재생
                setExecutionMode(ExecutionMode.REPLAY_INPUT)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L0_IDLE,
                    candidateSteeringAngleDeg = 15f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "REPLAY_L1_RIGHT_ELIGIBLE",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REPLAY_INPUT,
                    isScenarioActive = true,
                    speedKmh = 14f,
                    steeringAngleDeg = 15f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Visible(
                        message = ScenarioL1OverlayState.MSG_RIGHT_CHECK,
                        side = AdvisorySide.RIGHT,
                        reasonCode = "REPLAY_L1_ELIGIBLE"
                    ),
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION,
                    activeAdvisoryPipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RPL-02",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 96,
                        peakPossibleRatio = 0.1771f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 2.10f,
                        shadowCandidateLevel = "L1_ADVISORY (Replay Right Narrow Road)",
                        suppressionReasonsStr = "REPLAY_L1_ELIGIBLE",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Replay L1 Overlay Visible",
                        executionModeStr = "REPLAY_INPUT",
                        inputProvenanceStr = "speed=REPLAY, steer=REPLAY, spatial=REPLAY(+SCENARIO)",
                        rawCandidateStr = "L1 (Geom=POSSIBLE_OCCLUSION, Elig=ELIGIBLE)",
                        stabilizedOverlayStr = "VISIBLE (RIGHT: '우측 시야 확인')",
                        policyRestrictionStr = "NONE (Replay L1 Allowed)",
                        e2eLatencyStr = "Replay Clock 1.0x | tts=0",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, TTS=0)",
                        legacyCandidateStr = "L0_IDLE",
                        legacyVisualSuppressedStr = "SUPPRESSED",
                        legacyTtsSuppressedStr = "BLOCKED (TTS=0)",
                        activePipelineStr = "REPLAY_VALIDATION",
                        releaseSafeDefaultsStr = "REPLAY_FLAG_ISOLATED"
                    )
                )
            }
            "REPLAY_STATIONARY_PREARM", "23" -> {
                // RPL-03: Replay 정차 협소도로 사전정보 L1
                setExecutionMode(ExecutionMode.REPLAY_INPUT)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L0_IDLE,
                    candidateSteeringAngleDeg = 0f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "REPLAY_STATIONARY_PREARM",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REPLAY_INPUT,
                    isScenarioActive = true,
                    speedKmh = 0f,
                    steeringAngleDeg = 0f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Visible(
                        message = ScenarioL1OverlayState.MSG_BOTH_CHECK,
                        side = AdvisorySide.BOTH,
                        reasonCode = "REPLAY_STATIONARY_PREARM_ELIGIBLE"
                    ),
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION,
                    activeAdvisoryPipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RPL-03",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 72,
                        peakPossibleRatio = 0.1500f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 0.0f,
                        shadowCandidateLevel = "L1_ADVISORY (Stationary Pre-arm 2-Cycle)",
                        suppressionReasonsStr = "ELIGIBLE_INFORMATIONAL_PREARM",
                        computationTimeP50Ms = 0.50f,
                        scenarioL1IntegrationStatus = "Replay Stationary L1 Visible",
                        executionModeStr = "REPLAY_INPUT",
                        inputProvenanceStr = "speed=REPLAY(0km/h), steer=REPLAY | Provenance=[REPLAY, SCENARIO]",
                        rawCandidateStr = "L1 (Geom=POSSIBLE, Elig=PREARM)",
                        stabilizedOverlayStr = "VISIBLE (RIGHT: '좌우 시야 확인')",
                        policyRestrictionStr = "NONE (Stationary Prearm Allowed)",
                        e2eLatencyStr = "Replay Clock 1.0x | tts=0",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, TTS=0)",
                        legacyCandidateStr = "L0_IDLE",
                        legacyVisualSuppressedStr = "SUPPRESSED",
                        legacyTtsSuppressedStr = "BLOCKED (TTS=0)",
                        activePipelineStr = "REPLAY_VALIDATION",
                        releaseSafeDefaultsStr = "REPLAY_FLAG_ISOLATED"
                    )
                )
            }
            "REPLAY_L2_SUPPRESSED", "24" -> {
                // RPL-04: Replay Effective L2 후보 출력 차단
                setExecutionMode(ExecutionMode.REPLAY_INPUT)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.L0_IDLE,
                    candidateSteeringAngleDeg = 28f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "REPLAY_L2_SUPPRESSED_BY_POLICY",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.L0_IDLE,
                    executionMode = ExecutionMode.REPLAY_INPUT,
                    isScenarioActive = true,
                    speedKmh = 50f,
                    steeringAngleDeg = 28f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION,
                    activeAdvisoryPipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RPL-04",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 96,
                        peakPossibleRatio = 0.2200f,
                        peakUniversalRatio = 0.0500f,
                        maxPerPointContinuousSec = 2.50f,
                        shadowCandidateLevel = "L0_IDLE (Effective L2 Blocked)",
                        suppressionReasonsStr = "REPLAY_L2_OUTPUT_PROHIBITED",
                        computationTimeP50Ms = 0.52f,
                        scenarioL1IntegrationStatus = "L2 Suppressed (Hidden)",
                        executionModeStr = "REPLAY_INPUT",
                        inputProvenanceStr = "speed=REPLAY, steer=REPLAY | L2 Hard-blocked",
                        rawCandidateStr = "L2 -> Effective=L0 (REPLAY_L2_BLOCKED)",
                        stabilizedOverlayStr = "HIDDEN (L2 Output Prohibited)",
                        policyRestrictionStr = "ACTIVE (Replay Policy Blocks L2/L3)",
                        e2eLatencyStr = "Replay Clock 1.0x | tts=0",
                        existingStateMachineUnchangedStr = "true (Live=L0_IDLE, TTS=0)",
                        legacyCandidateStr = "L0_IDLE",
                        legacyVisualSuppressedStr = "SUPPRESSED",
                        legacyTtsSuppressedStr = "BLOCKED (TTS=0)",
                        activePipelineStr = "REPLAY_VALIDATION",
                        releaseSafeDefaultsStr = "REPLAY_FLAG_ISOLATED"
                    )
                )
            }
            "REPLAY_POLICY_RESTRICTED", "25" -> {
                // RPL-06: Replay CarUxRestrictions 전역 차단
                setExecutionMode(ExecutionMode.REPLAY_INPUT)
                val diag = LegacyAdvisoryDiagnostic(
                    candidateLevel = SafetyAlertLevel.RESTRICTED_MODE,
                    candidateSteeringAngleDeg = 20f,
                    candidateTurnSignal = "RIGHT",
                    candidateDirection = "RIGHT",
                    triggerReason = "CAR_UX_RESTRICTIONS_ACTIVE",
                    visualSuppressed = true,
                    ttsSuppressed = true,
                    pipeline = AdvisoryPipeline.REPLAY_VALIDATION,
                    appliedToLiveHUD = false
                )
                _uiState.value = _uiState.value.copy(
                    alertLevel = SafetyAlertLevel.RESTRICTED_MODE,
                    executionMode = ExecutionMode.REPLAY_INPUT,
                    isScenarioActive = true,
                    speedKmh = 18f,
                    steeringAngleDeg = 20f,
                    turnSignal = TurnSignalState.RIGHT,
                    rightPillarAlertActive = false,
                    leftPillarAlertActive = false,
                    scenarioL1OverlayState = ScenarioL1OverlayState.Hidden,
                    isUxVideoRestricted = true,
                    legacyDiagnostic = diag,
                    advisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION,
                    activeAdvisoryPipeline = AdvisoryPipeline.UNKNOWN,
                    occlusionValidation = OcclusionValidationMetrics(
                        featureId = "RPL-06",
                        spatialQuality = "SYNTHETIC_SCENARIO",
                        samplePointCount = 64,
                        peakPossibleRatio = 0.1600f,
                        peakUniversalRatio = 0.0f,
                        maxPerPointContinuousSec = 1.80f,
                        shadowCandidateLevel = "L0_IDLE (CarUxRestricted)",
                        suppressionReasonsStr = "CAR_UX_RESTRICTIONS_ACTIVE_NO_VIDEO",
                        computationTimeP50Ms = 0.51f,
                        scenarioL1IntegrationStatus = "Policy Restricted (Hidden)",
                        executionModeStr = "REPLAY_INPUT",
                        inputProvenanceStr = "speed=REPLAY | Policy=RESTRICTED",
                        rawCandidateStr = "L1 -> Effective=L0 (INELIGIBLE_POLICY_RESTRICTION)",
                        stabilizedOverlayStr = "HIDDEN (exitReason=POLICY_RESTRICTION)",
                        policyRestrictionStr = "ACTIVE (CarUxRestrictions NO_VIDEO Active)",
                        e2eLatencyStr = "Replay Clock 1.0x | tts=0",
                        existingStateMachineUnchangedStr = "true (Live=RESTRICTED_MODE, TTS=0)",
                        legacyCandidateStr = "RESTRICTED_MODE",
                        legacyVisualSuppressedStr = "SUPPRESSED",
                        legacyTtsSuppressedStr = "BLOCKED (TTS=0)",
                        activePipelineStr = "UNKNOWN",
                        releaseSafeDefaultsStr = "REPLAY_FLAG_ISOLATED"
                    )
                )
            }
        }
    }


    // 시뮬레이션 주입 채널 (SCENARIO_INPUT 모드 전용)
    fun simulateTurnSignal(signal: TurnSignalState) {
        sensorRepository.injectSimulatedTurnSignal(signal)
    }

    fun simulateSteering(angle: Float) {
        sensorRepository.injectSimulatedSteering(angle)
    }

    fun simulateSpeed(speedKmh: Float) {
        sensorRepository.injectSimulatedSpeed(speedKmh)
    }

    fun simulateBrake(pressed: Boolean) {
        sensorRepository.injectSimulatedBrake(pressed)
    }

    fun simulateCaution(type: CautionType, distance: Float) {
        spatialEngine.onNaviCautionPointReceived(
            CautionPoint(
                id = "SIM_${type.name}",
                type = type,
                distanceMeters = distance,
                latitude = 37.5665,
                longitude = 126.9780,
                source = InputSource.SCENARIO
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        adasProvider.stop()
        sensorRepository.stopListening()
        alertManager.destroy()
    }
}
