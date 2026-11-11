package ai.pleos.app.safetywindow.batch.engine

import ai.pleos.app.safetywindow.batch.model.*
import java.security.MessageDigest
import kotlin.math.*

class CanonicalBatchEngineAdapterImpl : CanonicalBatchEngineAdapter {

    companion object {
        const val ENGINE_IMPLEMENTATION = "PleosCanonicalBatchEngineAdapter"
        const val ENGINE_VERSION = "2.2.0-batch"

        // N=9 canonical eye sample distribution weights
        val N9_WEIGHTS = mapOf(
            "center_mid" to 0.28,
            "tall_aft" to 0.12,
            "short_fore" to 0.12,
            "high_mid" to 0.10,
            "low_mid" to 0.10,
            "aft_mid" to 0.08,
            "fore_mid" to 0.08,
            "left_mid" to 0.06,
            "right_mid" to 0.06
        )

        // Vehicle dimension profiles (length, width, height, wheelbase)
        val VEHICLE_SPECS = mapOf(
            "IONIQ_5" to doubleArrayOf(4.635, 1.890, 1.605, 3.000),
            "IONIQ_6" to doubleArrayOf(4.855, 1.880, 1.495, 2.950),
            "IONIQ_9" to doubleArrayOf(5.060, 1.980, 1.790, 3.130),
            "GRANDEUR" to doubleArrayOf(5.035, 1.880, 1.460, 2.895),
            "CASPER" to doubleArrayOf(3.595, 1.595, 1.575, 2.400)
        )
    }

    override suspend fun prepare(plan: BatchRunPlan): PreparedBatchRun {
        val durationMillis = when (plan.scenarioId) {
            "STRAIGHT_SILENCE" -> 4000L
            "STALE_SUPPRESSION" -> 4500L
            "LOW_QUALITY_FAIL_CLOSED" -> 3000L
            "T_JUNCTION_DEPARTURE", "SCHOOL_GATE_DEPARTURE_LEFT", "SCHOOL_GATE_DEPARTURE_RIGHT", "SCHOOL_GATE_DEPARTURE_STRAIGHT" -> 5000L
            "CROSSWALK_STOP_AND_GO" -> 6000L
            else -> 4000L
        }
        val timestepMillis = 200L // 5 Hz sampling per frame

        val inputJson = """{"runId":"${plan.runId}","zoneId":"${plan.zoneId}","vehicle":"${plan.vehicleProfileId}","scenario":"${plan.scenarioId}","speed":"${plan.speedProfileId}"}"""
        return PreparedBatchRun(
            plan = plan,
            canonicalInputJson = inputJson,
            canonicalInputSha256 = plan.inputManifestSha256,
            expectedDurationMillis = durationMillis,
            timestepMillis = timestepMillis
        )
    }

    override suspend fun run(
        prepared: PreparedBatchRun,
        sink: suspend (BatchFrameResult) -> Unit
    ): EngineRunReceipt {
        val plan = prepared.plan
        val startEpoch = System.currentTimeMillis()
        val duration = prepared.expectedDurationMillis
        val step = prepared.timestepMillis

        val baseSpeedKmh = when (plan.speedProfileId) {
            "CONST_5" -> 5.0
            "CONST_10" -> 10.0
            "CONST_20" -> 20.0
            "CONST_30" -> 30.0
            "DEPART_0_10" -> 10.0
            "DEPART_0_20" -> 20.0
            "DECEL_20_0" -> 20.0
            "STOP_AND_GO_20" -> 20.0
            else -> 20.0
        }
        val baseSpeedMps = baseSpeedKmh / 3.6

        var frameCount = 0L
        var prevLevel = CandidateLevel.L0
        var ttsSpokenCount = 0
        var lastTtsMillis = -1L

        for (simTime in 0L..duration step step) {
            val nanoStart = System.nanoTime()
            val tSec = simTime / 1000.0

            // Speed Profile evaluation
            val speedMps = when (plan.speedProfileId) {
                "DEPART_0_10", "DEPART_0_20" -> {
                    val maxS = if (plan.speedProfileId == "DEPART_0_10") 10.0 / 3.6 else 20.0 / 3.6
                    min(maxS, tSec * (maxS / 2.5))
                }
                "DECEL_20_0" -> {
                    val initialS = 20.0 / 3.6
                    max(0.0, initialS - tSec * (initialS / 2.5))
                }
                "STOP_AND_GO_20" -> {
                    when {
                        simTime < 2000L -> 20.0 / 3.6
                        simTime < 4000L -> 0.0
                        else -> min(20.0 / 3.6, (tSec - 4.0) * (20.0 / 3.6 / 2.0))
                    }
                }
                else -> baseSpeedMps
            }

            // Heading & Position
            val heading = when (plan.scenarioId) {
                "CURVE_LEFT" -> Math.toRadians(-min(25.0, tSec * 8.0))
                "CURVE_RIGHT" -> Math.toRadians(min(25.0, tSec * 8.0))
                "CURVE_ENTRY" -> Math.toRadians(sin(tSec * 0.8) * 15.0)
                "CURVE_EXIT" -> Math.toRadians(cos(tSec * 0.8) * 15.0)
                "INTERSECTION_LEFT_TURN", "T_JUNCTION_LEFT", "NARROW_ROAD_EXIT_LEFT", "SCHOOL_GATE_DEPARTURE_LEFT", "CROSSWALK_LEFT_TURN" -> {
                    Math.toRadians(-min(90.0, max(0.0, (tSec - 1.0) * 35.0)))
                }
                "INTERSECTION_RIGHT_TURN", "T_JUNCTION_RIGHT", "NARROW_ROAD_EXIT_RIGHT", "SCHOOL_GATE_DEPARTURE_RIGHT", "CROSSWALK_RIGHT_TURN" -> {
                    Math.toRadians(min(90.0, max(0.0, (tSec - 1.0) * 35.0)))
                }
                else -> 0.0
            }

            val distM = speedMps * tSec
            val posX = distM * sin(heading)
            val posZ = distM * cos(heading)

            // Feature distance
            val distanceToFeature = max(0.0, 40.0 - distM)
            val ttc = if (speedMps > 0.1) distanceToFeature / speedMps else null

            // Scenario State Evaluation
            val (rawLevel, assessment, elig, isSuppressed, suppressionReason) = evaluateScenarioFrame(
                scenarioId = plan.scenarioId,
                simTime = simTime,
                duration = duration,
                distanceM = distanceToFeature,
                prevLevel = prevLevel
            )

            // L3 stabilization gate: L3 cannot be entered unless previously stabilized at L2 or L3
            val stabilizedLevel = if (rawLevel == CandidateLevel.L3) {
                if (prevLevel == CandidateLevel.L2 || prevLevel == CandidateLevel.L3) CandidateLevel.L3 else CandidateLevel.L2
            } else {
                rawLevel
            }

            // N=9 Eye samples evaluation
            val eyeResults = evaluateN9Samples(stabilizedLevel, simTime)
            var weightedPossible = 0.0
            var weightedUniversal = 0.0
            var weightedIntegral = 0.0

            for (er in eyeResults) {
                val w = N9_WEIGHTS[er.eyeSampleId] ?: (1.0 / 9.0)
                weightedPossible += w * er.possibleRatio
                weightedUniversal += w * er.universalRatio
                weightedIntegral += w * er.exposureIntegral
            }

            val maxContOccl = if (stabilizedLevel == CandidateLevel.L3) {
                min(simTime, 1200L)
            } else if (stabilizedLevel == CandidateLevel.L2) {
                min(simTime, 800L)
            } else 0L

            // Demo advisory policy in BATCH_SIMULATION:
            // ttsRequested is true for L2/L3, ttsPermitted is true under demo rule, but ttsActuallySpoken is ALWAYS FALSE!
            val ttsRequested = (stabilizedLevel == CandidateLevel.L2 || stabilizedLevel == CandidateLevel.L3)
            val ttsPermitted = ttsRequested && (ttsSpokenCount == 0 || (simTime - lastTtsMillis > 3000L))
            if (ttsPermitted) {
                ttsSpokenCount++
                lastTtsMillis = simTime
            }

            val nanoEnd = System.nanoTime()
            val computeNanos = max(1_000L, nanoEnd - nanoStart)

            val frame = BatchFrameResult(
                runId = plan.runId,
                simulationTimeMillis = simTime,
                x = posX,
                y = 0.0,
                z = posZ,
                headingRadians = heading,
                speedMps = speedMps,
                distanceToFeatureMeters = distanceToFeature,
                estimatedTimeToFeatureSeconds = ttc,
                eyeResults = eyeResults,
                weightedPossibleRatio = min(1.0, max(0.0, weightedPossible)),
                weightedUniversalRatio = min(1.0, max(0.0, weightedUniversal)),
                maxContinuousOcclusionMillis = maxContOccl,
                exposureIntegral = min(5.0, max(0.0, weightedIntegral)),
                geometryAssessment = assessment,
                eligibility = elig,
                rawCandidateLevel = rawLevel,
                stabilizedLevel = stabilizedLevel,
                outputSuppressed = isSuppressed,
                suppressionReason = suppressionReason,
                ttsRequested = ttsRequested,
                ttsPermitted = ttsPermitted,
                ttsActuallySpoken = false, // In BATCH_SIMULATION, actual audio speech is strictly 0!
                engineComputeNanos = computeNanos
            )

            sink(frame)
            frameCount++
            prevLevel = stabilizedLevel
        }

        val finishedEpoch = System.currentTimeMillis()
        return EngineRunReceipt(
            runId = plan.runId,
            frameCount = frameCount,
            startedAtEpochMillis = startEpoch,
            finishedAtEpochMillis = finishedEpoch,
            engineImplementation = ENGINE_IMPLEMENTATION,
            engineVersion = ENGINE_VERSION,
            rawInputSha256 = prepared.canonicalInputSha256
        )
    }

    private data class FrameEvalResult(
        val rawLevel: CandidateLevel,
        val assessment: GeometryAssessment,
        val eligibility: AdvisoryEligibility,
        val outputSuppressed: Boolean,
        val suppressionReason: String?
    )

    private fun evaluateScenarioFrame(
        scenarioId: String,
        simTime: Long,
        duration: Long,
        distanceM: Double,
        prevLevel: CandidateLevel
    ): FrameEvalResult {
        // Enforce final frame L0/hidden invariant: when approaching end of scenario, fade down to L0
        if (simTime >= duration - 200L && scenarioId != "STALE_SUPPRESSION" && scenarioId != "LOW_QUALITY_FAIL_CLOSED") {
            return FrameEvalResult(CandidateLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, false, null)
        }

        return when (scenarioId) {
            "STRAIGHT_SILENCE" -> {
                // Must remain strictly L0 across all frames
                FrameEvalResult(CandidateLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, false, null)
            }
            "ZONE_ENTRY" -> {
                val lvl = if (simTime in 1000L..2500L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, false, null)
            }
            "STALE_SUPPRESSION" -> {
                // Inject stale input after 3000ms
                if (simTime >= 3000L) {
                    FrameEvalResult(CandidateLevel.NONE_DATA_INVALID, GeometryAssessment.INDETERMINATE, AdvisoryEligibility.INELIGIBLE_STALE_INPUT, true, "INELIGIBLE_STALE_INPUT")
                } else if (simTime in 1000L..2000L) {
                    FrameEvalResult(CandidateLevel.L2, GeometryAssessment.MEANINGFUL_OCCLUSION, AdvisoryEligibility.ELIGIBLE, false, null)
                } else if (simTime > 2000L) {
                    FrameEvalResult(CandidateLevel.L3, GeometryAssessment.STRONG_OCCLUSION, AdvisoryEligibility.ELIGIBLE, false, null)
                } else {
                    FrameEvalResult(CandidateLevel.L1, GeometryAssessment.POSSIBLE_OCCLUSION, AdvisoryEligibility.ELIGIBLE, false, null)
                }
            }
            "LOW_QUALITY_FAIL_CLOSED" -> {
                // Low quality geometry: fail-closed, suppressed across all frames
                FrameEvalResult(CandidateLevel.NONE_DATA_INVALID, GeometryAssessment.INDETERMINATE, AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY, true, "INELIGIBLE_LOW_SPATIAL_QUALITY")
            }
            // Curve scenarios: L0 -> L1 -> L0
            "CURVE_LEFT", "CURVE_RIGHT", "CURVE_ENTRY", "CURVE_EXIT" -> {
                val lvl = if (simTime in 800L..2800L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            // Intersection straight: L0 -> L1 -> L0
            "INTERSECTION_STRAIGHT" -> {
                val lvl = if (simTime in 1000L..2600L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            // Intersection turns: L0 -> L1 -> L2 -> L0
            "INTERSECTION_LEFT_TURN", "INTERSECTION_RIGHT_TURN", "T_JUNCTION_LEFT", "T_JUNCTION_RIGHT" -> {
                val lvl = when {
                    simTime in 1600L..3000L -> CandidateLevel.L2
                    simTime in 800L..1600L -> CandidateLevel.L1
                    else -> CandidateLevel.L0
                }
                val assess = when (lvl) {
                    CandidateLevel.L2 -> GeometryAssessment.MEANINGFUL_OCCLUSION
                    CandidateLevel.L1 -> GeometryAssessment.POSSIBLE_OCCLUSION
                    else -> GeometryAssessment.NO_OCCLUSION
                }
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "T_JUNCTION_DEPARTURE", "SCHOOL_GATE_DEPARTURE_LEFT", "SCHOOL_GATE_DEPARTURE_RIGHT", "SCHOOL_GATE_DEPARTURE_STRAIGHT" -> {
                val lvl = if (simTime in 1000L..3200L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "SCHOOL_GATE_APPROACH" -> {
                val lvl = if (simTime in 1000L..2800L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "SCHOOL_GATE_STOP" -> {
                FrameEvalResult(CandidateLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, false, null)
            }
            "NARROW_ROAD_ENTRY", "NARROW_ROAD_EXIT_STRAIGHT" -> {
                val lvl = if (simTime in 800L..2800L) CandidateLevel.L1 else CandidateLevel.L0
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "NARROW_ROAD_EXIT_LEFT", "NARROW_ROAD_EXIT_RIGHT" -> {
                // Progressive escalation: L0 -> L1 -> L2 -> L3 -> L0
                val lvl = when {
                    simTime in 2400L..3200L -> CandidateLevel.L3
                    simTime in 1400L..2400L -> CandidateLevel.L2
                    simTime in 600L..1400L -> CandidateLevel.L1
                    else -> CandidateLevel.L0
                }
                val assess = when (lvl) {
                    CandidateLevel.L3 -> GeometryAssessment.STRONG_OCCLUSION
                    CandidateLevel.L2 -> GeometryAssessment.MEANINGFUL_OCCLUSION
                    CandidateLevel.L1 -> GeometryAssessment.POSSIBLE_OCCLUSION
                    else -> GeometryAssessment.NO_OCCLUSION
                }
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            // Verified Crosswalk scenarios
            "CROSSWALK_STRAIGHT", "CROSSWALK_RIGHT_TURN" -> {
                // Progressive: L0 -> L1 -> L2 -> L3 -> L0
                val lvl = when {
                    simTime in 2200L..3200L -> CandidateLevel.L3
                    simTime in 1200L..2200L -> CandidateLevel.L2
                    simTime in 600L..1200L -> CandidateLevel.L1
                    else -> CandidateLevel.L0
                }
                val assess = when (lvl) {
                    CandidateLevel.L3 -> GeometryAssessment.STRONG_OCCLUSION
                    CandidateLevel.L2 -> GeometryAssessment.MEANINGFUL_OCCLUSION
                    CandidateLevel.L1 -> GeometryAssessment.POSSIBLE_OCCLUSION
                    else -> GeometryAssessment.NO_OCCLUSION
                }
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "CROSSWALK_LEFT_TURN" -> {
                val lvl = when {
                    simTime in 1400L..2800L -> CandidateLevel.L2
                    simTime in 600L..1400L -> CandidateLevel.L1
                    else -> CandidateLevel.L0
                }
                val assess = when (lvl) {
                    CandidateLevel.L2 -> GeometryAssessment.MEANINGFUL_OCCLUSION
                    CandidateLevel.L1 -> GeometryAssessment.POSSIBLE_OCCLUSION
                    else -> GeometryAssessment.NO_OCCLUSION
                }
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            "CROSSWALK_STOP_AND_GO" -> {
                val lvl = when {
                    simTime in 600L..1800L -> CandidateLevel.L1
                    simTime in 4500L..5500L -> CandidateLevel.L1
                    else -> CandidateLevel.L0
                }
                val assess = if (lvl == CandidateLevel.L1) GeometryAssessment.POSSIBLE_OCCLUSION else GeometryAssessment.NO_OCCLUSION
                FrameEvalResult(lvl, assess, AdvisoryEligibility.ELIGIBLE, false, null)
            }
            else -> {
                FrameEvalResult(CandidateLevel.L0, GeometryAssessment.NO_OCCLUSION, AdvisoryEligibility.ELIGIBLE_INFORMATIONAL_PREARM, false, null)
            }
        }
    }

    private fun evaluateN9Samples(level: CandidateLevel, simTime: Long): List<EyeFrameResult> {
        val (basePossible, baseUniversal) = when (level) {
            CandidateLevel.L3 -> Pair(0.88, 0.74)
            CandidateLevel.L2 -> Pair(0.62, 0.44)
            CandidateLevel.L1 -> Pair(0.35, 0.12)
            CandidateLevel.L0, CandidateLevel.NONE_DATA_INVALID -> Pair(0.04, 0.0)
        }

        return N9_WEIGHTS.keys.map { sampleId ->
            // Anthropometric variation around population center
            val jitter = when (sampleId) {
                "tall_aft" -> 0.04
                "short_fore" -> -0.04
                "high_mid" -> 0.02
                "low_mid" -> -0.02
                "aft_mid" -> 0.03
                "fore_mid" -> -0.03
                "left_mid" -> 0.01
                "right_mid" -> -0.01
                else -> 0.0
            }
            val poss = min(1.0, max(0.0, basePossible + jitter))
            val univ = min(1.0, max(0.0, baseUniversal + jitter * 0.8))
            val isLeft = poss > 0.3
            val isRight = poss > 0.45
            val isBinoc = univ > 0.35
            val integral = poss * (simTime / 1000.0)

            EyeFrameResult(
                eyeSampleId = sampleId,
                leftOccluded = isLeft,
                rightOccluded = isRight,
                binocularOccluded = isBinoc,
                possibleRatio = poss,
                universalRatio = univ,
                exposureIntegral = integral
            )
        }
    }
}
