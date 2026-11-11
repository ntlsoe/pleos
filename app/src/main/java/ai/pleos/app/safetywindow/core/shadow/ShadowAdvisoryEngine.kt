package ai.pleos.app.safetywindow.core.shadow

import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.input.SourceConsistencyGuard
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.occlusion.BinocularOcclusionEngine
import ai.pleos.app.safetywindow.core.occlusion.ConfigurableEyePopulationModel
import ai.pleos.app.safetywindow.core.occlusion.VehiclePillarGeometry
import ai.pleos.app.safetywindow.core.spatial.AdaptiveRiskFeatureSampler
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.RiskFeatureSampler
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ShadowAdvisoryEngine(
    private val vehicleGeometry: VehiclePillarGeometry = VehiclePillarGeometry.createRepresentativeSedanV0(),
    val thresholds: ShadowThresholdSet = ShadowThresholdSet(),
    private val useAdaptiveSampling: Boolean = true
) {
    private val eyeModel = ConfigurableEyePopulationModel.createRepresentativePopulation(vehicleGeometry.driverEyeNominal)
    val occlusionEngine = BinocularOcclusionEngine(vehicleGeometry, eyeModel)
    val adaptiveSampler = AdaptiveRiskFeatureSampler(vehicleGeometry, eyeModel)

    fun evaluateShadow(
        feature: RiskFeature,
        pose: VehiclePose,
        speedKmh: Double,
        turnSignal: TurnSignalState,
        steeringAngleDeg: Double = 0.0,
        currentTimeMillis: Long = System.currentTimeMillis(),
        turnRadiusMeters: Double? = null,
        remainingPathDistanceMeters: Double? = null,
        provenance: InputProvenance? = null,
        executionMode: ExecutionMode = ExecutionMode.SCENARIO_INPUT
    ): ShadowAdvisoryResult {
        val t0 = System.nanoTime()
        val reasonCodes = mutableListOf<String>()

        val resolvedProvenance = provenance ?: InputProvenance.fromPoseAndFeature(
            pose = pose,
            feature = feature
        )
        val inputSources = resolvedProvenance.inputSources

        val consistencyCheck = SourceConsistencyGuard.check(
            executionMode = executionMode,
            provenance = resolvedProvenance,
            feature = feature,
            usesSyntheticVehicleOrEyeProfile = true
        )

        val speedMps = max(0.0, speedKmh / 3.6)
        val turnCurvatureInvM = if (turnRadiusMeters != null && abs(turnRadiusMeters) > 0.1) {
            1.0 / turnRadiusMeters
        } else {
            0.0
        }

        val maneuver = determineManeuver(turnSignal, turnCurvatureInvM, steeringAngleDeg)
        if (inputSources.contains(InputSource.SCENARIO)) {
            reasonCodes.add("SOURCE_SCENARIO_SIMULATION")
        }
        if (inputSources.contains(InputSource.STATIC_PROFILE)) {
            reasonCodes.add("SOURCE_STATIC_PROFILE")
        }
        if (consistencyCheck.hasUnknownSource) {
            reasonCodes.add("UNKNOWN_SOURCE_CAPPED_AT_L0")
        }

        val reqLeadSec = thresholds.requiredLeadTimeSeconds

        // 1. Stale Location Check -> Geometry=INDETERMINATE, Eligibility=INELIGIBLE_STALE_INPUT, Effective=NONE_DATA_INVALID
        if (pose.isStale(currentTimeMillis, thresholds.maximumSpatialAgeMillis) || pose.quality == SpatialQuality.STALE) {
            reasonCodes.add("INELIGIBLE_STALE_INPUT")
            val t1 = System.nanoTime()
            return ShadowAdvisoryResult(
                timestampMillis = currentTimeMillis,
                zoneContext = feature.riskType,
                maneuverContext = maneuver,
                geometryAssessment = GeometryAssessment.INDETERMINATE,
                advisoryEligibility = AdvisoryEligibility.INELIGIBLE_STALE_INPUT,
                effectiveCandidateLevel = EffectiveCandidateLevel.NONE_DATA_INVALID,
                peakPossibleRatio = 0.0,
                peakUniversalRatio = 0.0,
                maxPerPointDurationSeconds = 0.0,
                anyPointDurationSeconds = 0.0,
                exposureIntegral = 0.0,
                distanceToDecisionLineMeters = null,
                estimatedTimeToDecisionLineSeconds = null,
                availableLeadTimeSeconds = null,
                requiredLeadTimeSeconds = reqLeadSec,
                spatialQuality = SpatialQuality.STALE,
                inputSources = inputSources,
                reasonCodes = reasonCodes,
                computationMillis = (t1 - t0) / 1_000_000.0,
                appliedToLiveStateMachine = false,
                currentSpeedMps = speedMps,
                ttsStartupLatencySeconds = thresholds.ttsStartupLatencySeconds,
                utteranceDurationSeconds = thresholds.utteranceDurationSeconds,
                responseAllowanceSeconds = thresholds.responseAllowanceSeconds,
                featureId = feature.featureId,
                inputProvenance = resolvedProvenance,
                projectionCompletedAtNanos = t1,
                samplingCompletedAtNanos = t1,
                occlusionCompletedAtNanos = t1,
                decisionCompletedAtNanos = t1
            )
        }

        // 2. Unknown Direction Check
        if (feature.coordinateQuality == SpatialQuality.PUBLIC_POINT_DIRECTION_UNKNOWN ||
            pose.quality == SpatialQuality.PUBLIC_POINT_DIRECTION_UNKNOWN
        ) {
            reasonCodes.add("INELIGIBLE_UNKNOWN_DIRECTION")
            val t1 = System.nanoTime()
            return ShadowAdvisoryResult(
                timestampMillis = currentTimeMillis,
                zoneContext = feature.riskType,
                maneuverContext = maneuver,
                geometryAssessment = GeometryAssessment.INDETERMINATE,
                advisoryEligibility = AdvisoryEligibility.INELIGIBLE_UNKNOWN_DIRECTION,
                effectiveCandidateLevel = EffectiveCandidateLevel.NONE_DATA_INVALID,
                peakPossibleRatio = 0.0,
                peakUniversalRatio = 0.0,
                maxPerPointDurationSeconds = 0.0,
                anyPointDurationSeconds = 0.0,
                exposureIntegral = 0.0,
                distanceToDecisionLineMeters = null,
                estimatedTimeToDecisionLineSeconds = null,
                availableLeadTimeSeconds = null,
                requiredLeadTimeSeconds = reqLeadSec,
                spatialQuality = feature.coordinateQuality,
                inputSources = inputSources,
                reasonCodes = reasonCodes,
                computationMillis = (t1 - t0) / 1_000_000.0,
                appliedToLiveStateMachine = false,
                currentSpeedMps = speedMps,
                ttsStartupLatencySeconds = thresholds.ttsStartupLatencySeconds,
                utteranceDurationSeconds = thresholds.utteranceDurationSeconds,
                responseAllowanceSeconds = thresholds.responseAllowanceSeconds,
                featureId = feature.featureId,
                inputProvenance = resolvedProvenance,
                projectionCompletedAtNanos = t1,
                samplingCompletedAtNanos = t1,
                occlusionCompletedAtNanos = t1,
                decisionCompletedAtNanos = t1
            )
        }

        // 3. Spatial Quality Check
        if (!pose.quality.canComputeShadow() || !feature.coordinateQuality.canComputeShadow()) {
            reasonCodes.add("INELIGIBLE_LOW_SPATIAL_QUALITY")
            val t1 = System.nanoTime()
            return ShadowAdvisoryResult(
                timestampMillis = currentTimeMillis,
                zoneContext = feature.riskType,
                maneuverContext = maneuver,
                geometryAssessment = GeometryAssessment.INDETERMINATE,
                advisoryEligibility = AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY,
                effectiveCandidateLevel = EffectiveCandidateLevel.NONE_DATA_INVALID,
                peakPossibleRatio = 0.0,
                peakUniversalRatio = 0.0,
                maxPerPointDurationSeconds = 0.0,
                anyPointDurationSeconds = 0.0,
                exposureIntegral = 0.0,
                distanceToDecisionLineMeters = null,
                estimatedTimeToDecisionLineSeconds = null,
                availableLeadTimeSeconds = null,
                requiredLeadTimeSeconds = reqLeadSec,
                spatialQuality = pose.quality,
                inputSources = inputSources,
                reasonCodes = reasonCodes,
                computationMillis = (t1 - t0) / 1_000_000.0,
                appliedToLiveStateMachine = false,
                currentSpeedMps = speedMps,
                ttsStartupLatencySeconds = thresholds.ttsStartupLatencySeconds,
                utteranceDurationSeconds = thresholds.utteranceDurationSeconds,
                responseAllowanceSeconds = thresholds.responseAllowanceSeconds,
                featureId = feature.featureId,
                inputProvenance = resolvedProvenance,
                projectionCompletedAtNanos = t1,
                samplingCompletedAtNanos = t1,
                occlusionCompletedAtNanos = t1,
                decisionCompletedAtNanos = t1
            )
        }

        val tProjectionDone = System.nanoTime()

        // 4. Sample risk points in vehicle frame (Adaptive 0.5m -> 0.25m by default)
        val samplePoints = if (useAdaptiveSampling) {
            adaptiveSampler.sampleAdaptiveFeatureInVehicleFrame(
                feature = feature,
                pose = pose,
                engine = occlusionEngine,
                currentTimeMillis = currentTimeMillis,
                maxAgeMillis = thresholds.maximumSpatialAgeMillis,
                turnCurvatureInvM = turnCurvatureInvM
            )?.points
        } else {
            RiskFeatureSampler.sampleFeatureInVehicleFrame(
                feature = feature,
                pose = pose,
                spacing = 0.5,
                currentTimeMillis = currentTimeMillis,
                maxAgeMillis = thresholds.maximumSpatialAgeMillis
            )
        }
        val tSamplingDone = System.nanoTime()

        if (samplePoints.isNullOrEmpty()) {
            reasonCodes.add("INELIGIBLE_UNKNOWN_DIRECTION")
            val t1 = System.nanoTime()
            return ShadowAdvisoryResult(
                timestampMillis = currentTimeMillis,
                zoneContext = feature.riskType,
                maneuverContext = maneuver,
                geometryAssessment = GeometryAssessment.INDETERMINATE,
                advisoryEligibility = AdvisoryEligibility.INELIGIBLE_UNKNOWN_DIRECTION,
                effectiveCandidateLevel = EffectiveCandidateLevel.NONE_DATA_INVALID,
                peakPossibleRatio = 0.0,
                peakUniversalRatio = 0.0,
                maxPerPointDurationSeconds = 0.0,
                anyPointDurationSeconds = 0.0,
                exposureIntegral = 0.0,
                distanceToDecisionLineMeters = null,
                estimatedTimeToDecisionLineSeconds = null,
                availableLeadTimeSeconds = null,
                requiredLeadTimeSeconds = reqLeadSec,
                spatialQuality = pose.quality,
                inputSources = inputSources,
                reasonCodes = reasonCodes,
                computationMillis = (t1 - t0) / 1_000_000.0,
                appliedToLiveStateMachine = false,
                currentSpeedMps = speedMps,
                ttsStartupLatencySeconds = thresholds.ttsStartupLatencySeconds,
                utteranceDurationSeconds = thresholds.utteranceDurationSeconds,
                responseAllowanceSeconds = thresholds.responseAllowanceSeconds,
                featureId = feature.featureId,
                inputProvenance = resolvedProvenance,
                projectionCompletedAtNanos = tProjectionDone,
                samplingCompletedAtNanos = tSamplingDone,
                occlusionCompletedAtNanos = t1,
                decisionCompletedAtNanos = t1
            )
        }

        // 5. Evaluate pure geometric occlusion (Stage 1: GeometryAssessment)
        var occludedWeight = 0.0
        var universalWeight = 0.0
        var occludedCount = 0

        val dummyZeroPose = ai.pleos.app.safetywindow.core.occlusion.VehiclePose(0.0, 0.0, 0.0, speedMps, 0.0)
        for (pt in samplePoints) {
            val eval = occlusionEngine.evaluateRiskPointAtPose(pt.position, dummyZeroPose)
            if (eval.isPossibleOccluded) {
                occludedWeight += pt.weight
                occludedCount++
            }
            if (eval.isUniversalOccluded) {
                universalWeight += pt.weight
            }
        }
        val tOcclusionDone = System.nanoTime()

        val effectiveSpeedForDuration = if (speedMps > 0.05) speedMps else 2.0
        val estDurationSec = if (occludedWeight > 0.0) (feature.lengthMeters / effectiveSpeedForDuration) else 0.0
        val exposureInt = occludedWeight * estDurationSec

        val geometryAssessment = when {
            occludedCount == 0 || occludedWeight <= 1e-6 -> {
                reasonCodes.add("GEOMETRY_NO_OCCLUSION")
                GeometryAssessment.NO_OCCLUSION
            }
            occludedWeight >= thresholds.strongWeightedRatio && estDurationSec >= thresholds.minimumContinuousDurationSeconds -> {
                reasonCodes.add("GEOMETRY_STRONG_OCCLUSION_${(occludedWeight * 100).toInt()}%")
                GeometryAssessment.STRONG_OCCLUSION
            }
            occludedWeight >= thresholds.minimumWeightedRatio && estDurationSec >= thresholds.minimumContinuousDurationSeconds -> {
                reasonCodes.add("GEOMETRY_MEANINGFUL_OCCLUSION_${(occludedWeight * 100).toInt()}%")
                GeometryAssessment.MEANINGFUL_OCCLUSION
            }
            else -> {
                reasonCodes.add("GEOMETRY_POSSIBLE_OCCLUSION_${(occludedWeight * 100).toInt()}%")
                GeometryAssessment.POSSIBLE_OCCLUSION
            }
        }

        // 6. Compute Remaining Path Distance & Lead Time (No division by zero when stationary)
        val distToDecisionLine = remainingPathDistanceMeters ?: computePathDistanceToDecisionLine(
            samplePoints = samplePoints.map { it.position },
            turnRadiusMeters = turnRadiusMeters
        )

        val isStationary = (speedMps <= 0.05)
        val estTimeToDecisionSec: Double?
        val availLeadTimeSec: Double?
        if (isStationary) {
            estTimeToDecisionSec = null
            availLeadTimeSec = null
            reasonCodes.add("STATIONARY_PRE_ARM_NO_ZERO_DIVISION")
        } else {
            estTimeToDecisionSec = distToDecisionLine / speedMps
            availLeadTimeSec = estTimeToDecisionSec
        }

        // 7. Determine AdvisoryEligibility (Stage 2: Separate L1 Informational Pre-Arm from L2 Lead Time Eligibility)
        val isRightTurnManeuver = (maneuver == "TURN_RIGHT")
        val isLeftTurnManeuver = (maneuver == "TURN_LEFT")
        val avgY = samplePoints.map { it.position.y }.average()
        val isOppositeSide = (isRightTurnManeuver && avgY > 2.0) || (isLeftTurnManeuver && avgY < -2.0)
        val isSchoolZoneStraight = (feature.riskType == "SCHOOL_ZONE_CURVE" && maneuver == "STRAIGHT")
        val isInsufficientLeadTime = (availLeadTimeSec != null && availLeadTimeSec < reqLeadSec)

        val advisoryEligibility = when {
            isOppositeSide -> {
                reasonCodes.add("INELIGIBLE_OPPOSITE_SIDE")
                AdvisoryEligibility.INELIGIBLE_OPPOSITE_SIDE
            }
            isSchoolZoneStraight -> {
                reasonCodes.add("INELIGIBLE_UNSUPPORTED_CONTEXT_SCHOOL_ZONE_STRAIGHT")
                AdvisoryEligibility.INELIGIBLE_UNSUPPORTED_CONTEXT
            }
            isStationary && geometryAssessment != GeometryAssessment.NO_OCCLUSION -> {
                if (maneuver == "STRAIGHT") {
                    reasonCodes.add("ELIGIBLE_INFORMATIONAL_PREARM_STRAIGHT_DEPARTURE")
                    AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM
                } else {
                    reasonCodes.add("INELIGIBLE_INSUFFICIENT_LEAD_TIME_STATIONARY_TURN")
                    AdvisoryEligibility.INELIGIBLE_INSUFFICIENT_LEAD_TIME
                }
            }
            geometryAssessment != GeometryAssessment.NO_OCCLUSION && isInsufficientLeadTime -> {
                reasonCodes.add(
                    "INELIGIBLE_INSUFFICIENT_LEAD_TIME_${String.format("%.2f", availLeadTimeSec)}s_LT_${String.format("%.2f", reqLeadSec)}s"
                )
                AdvisoryEligibility.INELIGIBLE_INSUFFICIENT_LEAD_TIME
            }
            else -> {
                reasonCodes.add("ELIGIBLE")
                AdvisoryEligibility.ELIGIBLE
            }
        }

        // 8. Determine EffectiveCandidateLevel (Stage 3: Strictly enforce L1/L2 separation & SourceConsistencyGuard)
        val hasValidLeadTimeForL2 = (
            !isStationary &&
                estTimeToDecisionSec != null &&
                availLeadTimeSec != null &&
                availLeadTimeSec >= reqLeadSec
            )

        val effectiveCandidateLevel = when {
            consistencyCheck.hasUnknownSource || !consistencyCheck.isConsistent -> {
                EffectiveCandidateLevel.L0
            }
            advisoryEligibility == AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM -> {
                // Stationary informational pre-arm: strictly L1, never L2
                reasonCodes.add("EFFECTIVE_L1_INFORMATIONAL_PREARM")
                EffectiveCandidateLevel.L1
            }
            advisoryEligibility != AdvisoryEligibility.ELIGIBLE -> {
                EffectiveCandidateLevel.L0
            }
            geometryAssessment == GeometryAssessment.NO_OCCLUSION -> {
                EffectiveCandidateLevel.L0
            }
            feature.riskType == "NARROW_ROAD_JUNCTION" && maneuver == "STRAIGHT" -> {
                reasonCodes.add("NARROW_ROAD_JUNCTION_STRAIGHT_DEPARTURE_CAPPED_AT_L1")
                EffectiveCandidateLevel.L1
            }
            (geometryAssessment == GeometryAssessment.MEANINGFUL_OCCLUSION ||
                geometryAssessment == GeometryAssessment.STRONG_OCCLUSION) -> {
                if ((isRightTurnManeuver || isLeftTurnManeuver || feature.riskType == "SCHOOL_ZONE_CURVE") && hasValidLeadTimeForL2) {
                    reasonCodes.add("EFFECTIVE_L2_MEANINGFUL_TURN_CONFLICT")
                    EffectiveCandidateLevel.L2
                } else if (!isStationary) {
                    reasonCodes.add("EFFECTIVE_L1_STRAIGHT_CONFLICT")
                    EffectiveCandidateLevel.L1
                } else {
                    EffectiveCandidateLevel.L0
                }
            }
            geometryAssessment == GeometryAssessment.POSSIBLE_OCCLUSION -> {
                reasonCodes.add("EFFECTIVE_L1_POSSIBLE_CONFLICT")
                EffectiveCandidateLevel.L1
            }
            else -> {
                EffectiveCandidateLevel.L0
            }
        }

        val tDecisionDone = System.nanoTime()
        val compMs = (tDecisionDone - t0) / 1_000_000.0

        return ShadowAdvisoryResult(
            timestampMillis = currentTimeMillis,
            zoneContext = feature.riskType,
            maneuverContext = maneuver,
            geometryAssessment = geometryAssessment,
            advisoryEligibility = advisoryEligibility,
            effectiveCandidateLevel = effectiveCandidateLevel,
            peakPossibleRatio = occludedWeight,
            binocularHits = occludedCount,
            peakUniversalRatio = universalWeight,
            maxPerPointDurationSeconds = estDurationSec,
            anyPointDurationSeconds = estDurationSec,
            exposureIntegral = exposureInt,
            distanceToDecisionLineMeters = distToDecisionLine,
            estimatedTimeToDecisionLineSeconds = estTimeToDecisionSec,
            availableLeadTimeSeconds = availLeadTimeSec,
            requiredLeadTimeSeconds = reqLeadSec,
            spatialQuality = pose.quality,
            inputSources = inputSources,
            reasonCodes = reasonCodes,
            computationMillis = compMs,
            appliedToLiveStateMachine = false,
            currentSpeedMps = speedMps,
            ttsStartupLatencySeconds = thresholds.ttsStartupLatencySeconds,
            utteranceDurationSeconds = thresholds.utteranceDurationSeconds,
            responseAllowanceSeconds = thresholds.responseAllowanceSeconds,
            featureId = feature.featureId,
            inputProvenance = resolvedProvenance,
            projectionCompletedAtNanos = tProjectionDone,
            samplingCompletedAtNanos = tSamplingDone,
            occlusionCompletedAtNanos = tOcclusionDone,
            decisionCompletedAtNanos = tDecisionDone
        )
    }

    private fun computePathDistanceToDecisionLine(
        samplePoints: List<ai.pleos.app.safetywindow.core.occlusion.Point3>,
        turnRadiusMeters: Double?
    ): Double {
        val forwardPts = samplePoints.filter { it.x > 0.0 }
        if (forwardPts.isEmpty()) return 0.0

        if (turnRadiusMeters != null && abs(turnRadiusMeters) > 0.5) {
            val r = abs(turnRadiusMeters)
            var minArc = Double.POSITIVE_INFINITY
            for (pt in forwardPts) {
                val latDist = min(r - 0.1, abs(pt.y))
                val theta = atan2(pt.x, r - latDist)
                val arc = max(hypot(pt.x, pt.y), r * theta)
                if (arc < minArc) minArc = arc
            }
            return if (minArc.isFinite()) minArc else 0.0
        } else {
            return forwardPts.minOf { hypot(it.x, it.y * 0.25) }
        }
    }

    private fun determineManeuver(
        turnSignal: TurnSignalState,
        turnCurvatureInvM: Double,
        steeringAngleDeg: Double
    ): String {
        return when {
            turnSignal == TurnSignalState.RIGHT || turnCurvatureInvM >= 0.05 || steeringAngleDeg > 15.0 -> "TURN_RIGHT"
            turnSignal == TurnSignalState.LEFT || turnCurvatureInvM <= -0.05 || steeringAngleDeg < -15.0 -> "TURN_LEFT"
            else -> "STRAIGHT"
        }
    }
}
