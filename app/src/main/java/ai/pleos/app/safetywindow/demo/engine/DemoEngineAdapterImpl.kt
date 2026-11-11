package ai.pleos.app.safetywindow.demo.engine

import ai.pleos.app.safetywindow.demo.model.*
import ai.pleos.app.safetywindow.demo.policy.DemoFeatureFlags
import ai.pleos.app.safetywindow.demo.policy.DemoOutputGate
import ai.pleos.app.safetywindow.demo.policy.DemoVisualSpec
import ai.pleos.app.safetywindow.demo.render.DemoEngineAdapter
import ai.pleos.app.safetywindow.demo.render.DemoScenarioConfig
import kotlin.math.cos
import kotlin.math.sin

class DemoEngineAdapterImpl(
    private val isReleaseBuild: Boolean = false,
    private val flags: DemoFeatureFlags = DemoFeatureFlags()
) : DemoEngineAdapter {

    private var currentConfig: DemoScenarioConfig? = null
    private var stepIndex = 0
    private var lastTtsTimeMillis = -1L
    private var ttsSpokenCount = 0

    // Vehicle profiles catalogue (5 standard vehicle profiles per Section 9)
    companion object {
        val VEHICLE_PROFILES = mapOf(
            "RepresentativeSedanV0" to VehicleVisualProfile(
                id = "RepresentativeSedanV0",
                label = "대표 세단 (Sedan)",
                lengthM = 4.85, widthM = 1.85, heightM = 1.45, wheelbaseM = 2.85,
                evidenceLabel = "공개 제원 기반 합성 필러 프로파일"
            ),
            "CompactSuvV0" to VehicleVisualProfile(
                id = "CompactSuvV0",
                label = "소형 SUV (Compact SUV)",
                lengthM = 4.30, widthM = 1.80, heightM = 1.60, wheelbaseM = 2.60,
                evidenceLabel = "공개 제원 기반 합성 필러 프로파일"
            ),
            "MidsizeSuvV0" to VehicleVisualProfile(
                id = "MidsizeSuvV0",
                label = "중형 SUV (Mid-size SUV)",
                lengthM = 4.80, widthM = 1.90, heightM = 1.70, wheelbaseM = 2.82,
                evidenceLabel = "공개 제원 기반 합성 필러 프로파일"
            ),
            "MinivanV0" to VehicleVisualProfile(
                id = "MinivanV0",
                label = "대형 미니밴 (Minivan)",
                lengthM = 5.15, widthM = 1.99, heightM = 1.77, wheelbaseM = 3.09,
                evidenceLabel = "공개 제원 기반 합성 필러 프로파일"
            ),
            "CommercialVanV0" to VehicleVisualProfile(
                id = "CommercialVanV0",
                label = "상용 밴 (Commercial Van)",
                lengthM = 5.30, widthM = 2.00, heightM = 1.95, wheelbaseM = 3.20,
                evidenceLabel = "공개 제원 기반 합성 필러 프로파일"
            )
        )
    }

    override suspend fun reset(config: DemoScenarioConfig): DemoSceneState {
        currentConfig = config
        stepIndex = 0
        lastTtsTimeMillis = -1L
        ttsSpokenCount = 0
        return computeState(0L)
    }

    override suspend fun step(simulationTimeMillis: Long): DemoSceneState {
        stepIndex++
        return computeState(simulationTimeMillis)
    }

    private fun computeState(simTime: Long): DemoSceneState {
        val cfg = currentConfig ?: DemoScenarioConfig(
            scenarioId = "D1_STRAIGHT_SILENCE",
            zoneId = "SZ_001",
            featureId = null,
            vehicleProfileId = "RepresentativeSedanV0",
            approachId = "APPROACH_STRAIGHT",
            initialSpeedMps = 8.33, // 30 km/h
            playbackRate = 1.0,
            ttsEnabled = true,
            dataQualityMode = "HIGH",
            eyeModelId = "POP_EYE_50TH"
        )

        val profile = VEHICLE_PROFILES[cfg.vehicleProfileId] ?: VEHICLE_PROFILES["RepresentativeSedanV0"]!!

        // Compute simulated vehicle path along centerline
        val tSec = simTime / 1000.0
        val distM = cfg.initialSpeedMps * tSec
        val heading = when (cfg.scenarioId) {
            "D3_INTERSECTION_TURN" -> Math.toRadians(minOf(90.0, tSec * 15.0))
            "D2_CURVE_START" -> Math.toRadians(sin(tSec * 0.5) * 20.0)
            else -> 0.0
        }
        val posX = distM * sin(heading)
        val posZ = distM * cos(heading)

        val pose = VehiclePose3D(
            position = Point3D(posX, 0.0, posZ),
            headingRadians = heading,
            speedMps = cfg.initialSpeedMps
        )

        // Trajectory
        val trajectory = mutableListOf<Point3D>()
        for (i in -10..20) {
            val d = distM + (i * 3.0)
            trajectory.add(Point3D(d * sin(heading), 0.0, d * cos(heading)))
        }

        // Scenario state logic per Section 8
        val (rawLevel, assessment, elig, dir, distanceToFeature, qualityLabel) = evaluateScenarioParameters(cfg.scenarioId, simTime)

        val mayRender = DemoOutputGate.mayRender(DemoExecutionMode.DEMO_INTEGRATION, isReleaseBuild, flags)
        val maySpeak = DemoOutputGate.maySpeak(DemoExecutionMode.DEMO_INTEGRATION, isReleaseBuild, rawLevel, flags)

        val ttsRequested = (rawLevel == DemoLevel.L2 || rawLevel == DemoLevel.L3) && cfg.ttsEnabled
        val ttsPermitted = ttsRequested && maySpeak && (ttsSpokenCount == 0 || (simTime - lastTtsTimeMillis > 3000L))
        val ttsSpoken = ttsPermitted

        if (ttsSpoken) {
            ttsSpokenCount++
            lastTtsTimeMillis = simTime
        }

        val isSuppressed = !mayRender || rawLevel == DemoLevel.NONE_DATA_INVALID || elig == AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY || elig == AdvisoryEligibility.INELIGIBLE_STALE_INPUT
        val isVisible = mayRender && !isSuppressed && rawLevel != DemoLevel.L0 && rawLevel != DemoLevel.NONE_DATA_INVALID
        val msg = if (isVisible) DemoVisualSpec.message(rawLevel, dir) else if (isSuppressed && rawLevel == DemoLevel.NONE_DATA_INVALID) "안내 억제됨 (저품질/신호지연)" else DemoVisualSpec.message(rawLevel, dir)

        val advisory = DemoAdvisoryState(
            rawLevel = rawLevel,
            stabilizedLevel = rawLevel,
            geometryAssessment = assessment,
            eligibility = elig,
            direction = dir,
            message = msg,
            outputSuppressed = isSuppressed,
            suppressionReason = if (isSuppressed) "FAIL_CLOSED_SAFETY_BOUNDARY" else null,
            ttsRequested = ttsRequested,
            ttsPermitted = ttsPermitted,
            ttsSpoken = ttsSpoken,
            visible = isVisible
        )

        val metrics = DemoMetrics(
            peakPossibleRatio = when (rawLevel) { DemoLevel.L3 -> 0.88; DemoLevel.L2 -> 0.62; DemoLevel.L1 -> 0.35; else -> 0.08 },
            peakUniversalRatio = when (rawLevel) { DemoLevel.L3 -> 0.75; DemoLevel.L2 -> 0.45; else -> 0.0 },
            maxDurationMillis = if (rawLevel != DemoLevel.L0) 1800L else 0L,
            exposureIntegral = if (rawLevel != DemoLevel.L0) 1.25 else 0.0,
            distanceToFeatureM = distanceToFeature,
            estimatedTimeToFeatureS = if (distanceToFeature != null && cfg.initialSpeedMps > 0) distanceToFeature / cfg.initialSpeedMps else null,
            computeMillis = 2.45
        )

        // Synthesize 3D visualization meshes
        val visibilityVolumes = listOf(
            VisibilityVolume(
                id = "vol_visible",
                vertices = listOf(Point3D(-8.0, 0.0, posZ + 5.0), Point3D(8.0, 0.0, posZ + 5.0), Point3D(0.0, 1.2, posZ)),
                visualClass = VisualClass.VISIBLE,
                alpha = 0.25f
            ),
            VisibilityVolume(
                id = "vol_occlusion",
                vertices = listOf(Point3D(3.0, 0.0, posZ + 12.0), Point3D(9.0, 0.0, posZ + 15.0), Point3D(5.0, 2.5, posZ + 10.0)),
                visualClass = when (rawLevel) {
                    DemoLevel.L3 -> VisualClass.STRONG
                    DemoLevel.L2 -> VisualClass.MEANINGFUL
                    DemoLevel.L1 -> VisualClass.POSSIBLE
                    else -> VisualClass.VISIBLE
                },
                alpha = 0.35f
            )
        )

        val occlusionSamples = listOf(
            ColoredSample(Point3D(posX + 4.0, 1.0, posZ + 12.0), VisualClass.MEANINGFUL, 0.8),
            ColoredSample(Point3D(posX + 5.0, 1.2, posZ + 14.0), when (rawLevel) { DemoLevel.L3 -> VisualClass.STRONG; else -> VisualClass.POSSIBLE }, 0.9),
            ColoredSample(Point3D(posX - 3.0, 0.8, posZ + 8.0), VisualClass.VISIBLE, 0.5)
        )

        return DemoSceneState(
            sessionId = "demo_session_${cfg.scenarioId}",
            simulationTimeMillis = simTime,
            executionMode = DemoExecutionMode.DEMO_INTEGRATION,
            scenarioId = cfg.scenarioId,
            zoneId = cfg.zoneId,
            batchRunId = "batch_demo_v21",
            vehiclePose = pose,
            vehicleProfile = profile,
            trajectory = trajectory,
            pillarMeshes = emptyList(),
            eyeSamples = listOf(Point3D(posX - 0.35, 1.2, posZ), Point3D(posX + 0.35, 1.2, posZ)),
            riskSurfaces = listOf(
                RiskSurfaceVisual(
                    id = "surf_main",
                    type = RiskFeatureType.FOUR_WAY_INTERSECTION,
                    vertices = listOf(
                        Point3D(-6.0, 0.0, posZ + 15.0), Point3D(6.0, 0.0, posZ + 15.0),
                        Point3D(6.0, 0.0, posZ + 25.0), Point3D(-6.0, 0.0, posZ + 25.0)
                    ),
                    quality = qualityLabel
                )
            ),
            visibilityVolumes = visibilityVolumes,
            occlusionSamples = occlusionSamples,
            advisory = advisory,
            metrics = metrics,
            dataQualityLabel = qualityLabel,
            virtualEnvironmentBadge = "가상환경 시연"
        )
    }

    private data class ScenarioParams(
        val level: DemoLevel,
        val assessment: GeometryAssessment,
        val eligibility: AdvisoryEligibility,
        val direction: DemoDirection,
        val distanceToFeatureM: Double?,
        val qualityLabel: String
    )

    private fun evaluateScenarioParameters(scenarioId: String, simTime: Long): ScenarioParams = when (scenarioId) {
        "D1_STRAIGHT_SILENCE" -> {
            // D1: Straight corridor silence -> L0
            ScenarioParams(DemoLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, DemoDirection.NONE, null, "HIGH")
        }
        "D2_CURVE_START" -> {
            // D2: Curve or school-gate departure -> L1
            ScenarioParams(DemoLevel.L1, GeometryAssessment.POSSIBLE_OCCLUSION, AdvisoryEligibility.ELIGIBLE, DemoDirection.RIGHT, 45.0, "HIGH")
        }
        "D3_INTERSECTION_TURN" -> {
            // D3: Intersection approach/turn -> L2 + 1 TTS
            val lvl = if (simTime > 1500L) DemoLevel.L2 else DemoLevel.L1
            ScenarioParams(lvl, GeometryAssessment.MEANINGFUL_OCCLUSION, AdvisoryEligibility.ELIGIBLE, DemoDirection.LEFT, 25.0, "HIGH")
        }
        "D4_STRONG_OCCLUSION" -> {
            // D4: Strong modeled occlusion + close proximity -> L3 + 1 TTS
            val lvl = if (simTime > 2000L) DemoLevel.L3 else if (simTime > 1000L) DemoLevel.L2 else DemoLevel.L1
            ScenarioParams(lvl, GeometryAssessment.STRONG_OCCLUSION, AdvisoryEligibility.ELIGIBLE, DemoDirection.RIGHT, 12.0, "HIGH")
        }
        "D5_L3_STALE" -> {
            // D5: Stale input during L3 -> immediate hidden, 0 extra TTS
            if (simTime > 2500L) {
                ScenarioParams(DemoLevel.NONE_DATA_INVALID, GeometryAssessment.INDETERMINATE, AdvisoryEligibility.INELIGIBLE_STALE_INPUT, DemoDirection.NONE, 8.0, "STALE_INPUT")
            } else {
                ScenarioParams(DemoLevel.L3, GeometryAssessment.STRONG_OCCLUSION, AdvisoryEligibility.ELIGIBLE, DemoDirection.RIGHT, 10.0, "HIGH")
            }
        }
        "D6_LOW_QUALITY_GEOMETRY" -> {
            // D6: Low-quality geometry -> fail-closed, no advisory
            ScenarioParams(DemoLevel.NONE_DATA_INVALID, GeometryAssessment.INDETERMINATE, AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY, DemoDirection.NONE, null, "LOW_QUALITY_REJECTED")
        }
        "D7_GENERAL_INTERSECTION" -> {
            // D7: General 4-way intersection -> L1 -> L2 -> L3 progression
            val lvl = when {
                simTime > 3000L -> DemoLevel.L3
                simTime > 1500L -> DemoLevel.L2
                simTime > 500L -> DemoLevel.L1
                else -> DemoLevel.L0
            }
            val assess = when (lvl) {
                DemoLevel.L3 -> GeometryAssessment.STRONG_OCCLUSION
                DemoLevel.L2 -> GeometryAssessment.MEANINGFUL_OCCLUSION
                DemoLevel.L1 -> GeometryAssessment.POSSIBLE_OCCLUSION
                else -> GeometryAssessment.NO_OCCLUSION
            }
            val dist = maxOf(5.0, 50.0 - (simTime / 100.0))
            ScenarioParams(lvl, assess, AdvisoryEligibility.ELIGIBLE, DemoDirection.BOTH, dist, "HIGH")
        }
        else -> {
            ScenarioParams(DemoLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, DemoDirection.NONE, null, "HIGH")
        }
    }
}
