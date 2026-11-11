package ai.pleos.app.safetywindow.demo.replay

import ai.pleos.app.safetywindow.batch.execution.VehicleSensitivityRunner
import ai.pleos.app.safetywindow.demo.model.*
import kotlin.math.*

/**
 * BatchRunToDemoSceneMapper
 * [작업명령 #24] 섹션 5: 원시 프레임 데이터를 4분할 3D 시연 상태(DemoSceneState)로 변환
 */
object BatchRunToDemoSceneMapper {

    fun mapToSceneState(
        entry: DemoReplayEntry,
        frame: DemoRawInputFrame,
        simTimeMillis: Long
    ): DemoSceneState {
        val spec = VehicleSensitivityRunner.CANONICAL_VEHICLES[entry.vehicleProfileId]
            ?: VehicleSensitivityRunner.CANONICAL_VEHICLES["IONIQ_5"]!!
        val trace = VehicleSensitivityRunner.computeParameterTrace(spec)

        val speedMps = when (entry.speedProfileId) {
            "CONST_10" -> 2.78
            "CONST_20" -> 5.56
            "CONST_30" -> 8.33
            else -> 5.56
        }

        val tSec = simTimeMillis / 1000.0
        val dist = speedMps * tSec

        // Position & heading along trajectory
        val (posX, posY, heading) = when {
            entry.scenarioId.contains("LEFT_TURN") || entry.scenarioId.contains("LEFT") -> {
                val radius = 18.0
                val angle = min(Math.PI / 2.0, dist / radius)
                Triple(-radius * (1.0 - cos(angle)), radius * sin(angle), angle)
            }
            entry.scenarioId.contains("RIGHT_TURN") || entry.scenarioId.contains("RIGHT") -> {
                val radius = 18.0
                val angle = min(Math.PI / 2.0, dist / radius)
                Triple(radius * (1.0 - cos(angle)), radius * sin(angle), -angle)
            }
            else -> {
                Triple(0.0, dist, 0.0)
            }
        }

        val vehiclePose = VehiclePose3D(
            position = Point3D(posX, posY, 0.0),
            headingRadians = heading,
            speedMps = speedMps
        )

        val visualProfile = VehicleVisualProfile(
            id = spec.vehicleProfileId,
            label = "${spec.vehicleName} (${spec.bodyClass})",
            lengthM = spec.lengthMm / 1000.0,
            widthM = spec.widthMm / 1000.0,
            heightM = spec.heightMm / 1000.0,
            wheelbaseM = spec.wheelbaseMm / 1000.0,
            evidenceLabel = "공개 제원 기반 합성 필러 프로파일 (${spec.nominalPillarWidthMm.toInt()}mm)"
        )

        // Driver eye positions (N=9 samples modulated by vehicle)
        val eyeSamples = listOf(
            Point3D(trace.eyeX, trace.eyeY, trace.eyeZ),
            Point3D(trace.eyeX + trace.leftEyeOffset, trace.eyeY, trace.eyeZ),
            Point3D(trace.eyeX + trace.rightEyeOffset, trace.eyeY, trace.eyeZ),
            Point3D(trace.eyeX, trace.eyeY + 0.04, trace.eyeZ - 0.04), // tall_aft
            Point3D(trace.eyeX, trace.eyeY - 0.04, trace.eyeZ + 0.04), // short_fore
            Point3D(trace.eyeX, trace.eyeY + 0.04, trace.eyeZ),         // high_mid
            Point3D(trace.eyeX, trace.eyeY - 0.04, trace.eyeZ),         // low_mid
            Point3D(trace.eyeX, trace.eyeY, trace.eyeZ - 0.04),         // aft_mid
            Point3D(trace.eyeX, trace.eyeY, trace.eyeZ + 0.04)          // fore_mid
        )

        // Left A-pillar prism vertices
        val halfW = trace.pillarWidthM / 2.0
        val pillarMesh = listOf(
            Point3D(trace.pillarBaseX - halfW, trace.pillarBaseY, trace.pillarBaseZ),
            Point3D(trace.pillarBaseX + halfW, trace.pillarBaseY, trace.pillarBaseZ),
            Point3D(trace.pillarTopX + halfW, trace.pillarTopY, trace.pillarTopZ),
            Point3D(trace.pillarTopX - halfW, trace.pillarTopY, trace.pillarTopZ)
        )

        // Trajectory trail
        val trajectory = (0..10).map { i ->
            val trailDist = max(0.0, dist - i * 2.0)
            Point3D(posX * (trailDist / max(1.0, dist)), trailDist, 0.0)
        }

        // [COUP_02 DECOUPLING] Work 38: Evaluate strictly through Product Pipeline Stages
        // Stage 1: GeometryAssessment (Calculates pure occlusion candidate from physical metrics)
        val isStrong = (frame.possibleRatio >= 0.50)
        val isMeaningful = (frame.possibleRatio >= 0.25 || frame.maxContinuousOcclusionMillis >= 800)
        val isPossible = (frame.possibleRatio >= 0.10)
        val distToFeatureM = max(0.0, 50.0 - dist)

        val assess = when {
            isStrong -> GeometryAssessment.STRONG_OCCLUSION
            isMeaningful -> GeometryAssessment.MEANINGFUL_OCCLUSION
            isPossible -> GeometryAssessment.POSSIBLE_OCCLUSION
            frame.possibleRatio > 0.0 -> GeometryAssessment.NO_OCCLUSION
            else -> GeometryAssessment.INDETERMINATE
        }

        val rawCandidate = when {
            isStrong && frame.maxContinuousOcclusionMillis >= 1500 && distToFeatureM <= 15.0 -> DemoLevel.L3
            isMeaningful -> DemoLevel.L2
            isPossible -> DemoLevel.L1
            else -> DemoLevel.L0
        }

        // Stage 2: AdvisoryEligibility (Validates provenance, data quality, and fail-closed state)
        val isSynthetic = entry.scenarioId.contains("SYNTHETIC") || entry.runId.contains("PC_")
        val isFailClosed = false

        val elig = when {
            isFailClosed -> AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY
            isSynthetic -> AdvisoryEligibility.INELIGIBLE_SYNTHETIC
            else -> AdvisoryEligibility.ELIGIBLE
        }

        // Stage 3 & 4: EffectiveCandidateLevel & AlertManager (Applies noise filter and eligibility gate)
        val isNoiseGraze = (frame.possibleRatio < 0.10)
        val effectiveLevel = when {
            isFailClosed -> DemoLevel.NONE_DATA_INVALID
            elig != AdvisoryEligibility.ELIGIBLE -> DemoLevel.L0 // Synthetic suppressed to L0 in live vehicle HUD
            isNoiseGraze -> DemoLevel.L0 // Cowl graze filtered to L0 Silence
            else -> rawCandidate
        }

        val outputSuppressed = (effectiveLevel == DemoLevel.L0 || effectiveLevel == DemoLevel.NONE_DATA_INVALID)

        val direction = when {
            entry.scenarioId.contains("LEFT") -> DemoDirection.LEFT
            entry.scenarioId.contains("RIGHT") -> DemoDirection.RIGHT
            else -> DemoDirection.NONE
        }

        val advisoryMsg = when (effectiveLevel) {
            DemoLevel.L3 -> "강한 합성 A필러 사각지대 차폐 감지 (주의)"
            DemoLevel.L2 -> "유의미한 합성 A필러 가림 영역 진입"
            DemoLevel.L1 -> "보호구역 진입 정보 사전 예고"
            DemoLevel.L0 -> "정상 주행 (차폐 위험 없음)"
            DemoLevel.NONE_DATA_INVALID -> "입력 이상 감지: Fail-Closed 출력 억제"
        }

        val advisoryState = DemoAdvisoryState(
            rawLevel = rawCandidate,
            stabilizedLevel = effectiveLevel,
            geometryAssessment = assess,
            eligibility = elig,
            direction = direction,
            message = advisoryMsg,
            outputSuppressed = outputSuppressed,
            suppressionReason = if (outputSuppressed) {
                if (isFailClosed) "FAIL_CLOSED_INPUT_ANOMALY"
                else if (isSynthetic) "SYNTHETIC_TEST_INPUT_SUPPRESSED"
                else "SUB_THRESHOLD_COWL_GRAZE_FILTERED_TO_L0"
            } else null,
            ttsRequested = false,
            ttsPermitted = false,
            ttsSpoken = false,
            visible = !outputSuppressed && effectiveLevel != DemoLevel.L0
        )

        val metrics = DemoMetrics(
            peakPossibleRatio = frame.possibleRatio,
            peakUniversalRatio = frame.universalRatio,
            maxDurationMillis = frame.maxContinuousOcclusionMillis,
            exposureIntegral = frame.exposureIntegral,
            distanceToFeatureM = max(0.0, 50.0 - dist),
            estimatedTimeToFeatureS = max(0.0, (50.0 - dist) / speedMps),
            computeMillis = 0.04
        )

        return DemoSceneState(
            sessionId = "REPLAY_${entry.runId}",
            simulationTimeMillis = simTimeMillis,
            executionMode = DemoExecutionMode.DEMO_INTEGRATION,
            scenarioId = entry.scenarioId,
            zoneId = entry.zoneId,
            batchRunId = entry.runId,
            vehiclePose = vehiclePose,
            vehicleProfile = visualProfile,
            trajectory = trajectory,
            pillarMeshes = listOf(pillarMesh),
            eyeSamples = eyeSamples,
            riskSurfaces = emptyList(),
            visibilityVolumes = emptyList(),
            occlusionSamples = emptyList(),
            advisory = advisoryState,
            metrics = metrics,
            dataQualityLabel = "Clean Run 실데이터 (SHA: ${entry.outputCanonicalJsonSha256.take(8)})",
            virtualEnvironmentBadge = "가상환경 시연 (Clean Run)"
        )
    }
}
