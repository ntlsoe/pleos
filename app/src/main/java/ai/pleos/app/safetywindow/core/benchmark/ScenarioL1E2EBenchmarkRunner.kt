package ai.pleos.app.safetywindow.core.benchmark

import ai.pleos.app.safetywindow.core.advisory.AdvisoryStabilityPolicy
import ai.pleos.app.safetywindow.core.advisory.LiveIntegrationFlags
import ai.pleos.app.safetywindow.core.advisory.ScenarioL1OverlayController
import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryEngine
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.app.safetywindow.ui.hud.ScenarioL1OverlayState
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

data class ScenarioL1E2ELatencySummaryRow(
    val sessionId: Int,
    val buildMode: String,
    val measurementTag: String,
    val transitionType: String,
    val metricName: String,
    val iterations: Int,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val meanMs: Double,
    val stdDevMs: Double,
    val appliedToLiveStateMachine: Boolean = false,
    val ttsInvoked: Boolean = false,
    val deviceInfo: String
) {
    fun toCsvRow(): String {
        return "$sessionId,$buildMode,$measurementTag,$transitionType,$metricName,$iterations," +
            "${String.format("%.6f", p50Ms)},${String.format("%.6f", p90Ms)},${String.format("%.6f", p95Ms)}," +
            "${String.format("%.6f", p99Ms)},${String.format("%.6f", maxMs)},${String.format("%.6f", meanMs)}," +
            "${String.format("%.6f", stdDevMs)},$appliedToLiveStateMachine,$ttsInvoked,\"$deviceInfo\""
    }

    companion object {
        const val CSV_HEADER =
            "session_id,build_mode,measurement_tag,transition_type,metric_name,iterations,p50_ms,p90_ms,p95_ms,p99_ms,max_ms,mean_ms,std_dev_ms,applied_to_live_state_machine,tts_invoked,device_info"
    }
}

object ScenarioL1E2EBenchmarkRunner {
    private const val TAG = "SCENARIO_L1_E2E_BENCH"

    private fun createL1RightTurnFeature(): RiskFeature {
        return RiskFeature(
            featureId = "SCENARIO_L1_CW_RIGHT",
            riskType = "CROSSWALK",
            source = "SYNTHETIC_SCENARIO_GEOJSON",
            geometryMethod = "SYNTHETIC_RECTANGLE",
            manualVerified = false,
            coordinateQuality = SpatialQuality.SCENARIO,
            widthMeters = 6.0,
            lengthMeters = 1.2,
            directionDegrees = 90.0,
            centerEnu = Triple(7.0, 5.0, 0.5),
            notes = "Synthetic L1 right-turn crosswalk"
        )
    }

    private fun createL0ClearFeature(): RiskFeature {
        return RiskFeature(
            featureId = "SCENARIO_L0_CLEAR",
            riskType = "CLEAR_ROAD",
            source = "SYNTHETIC_SCENARIO_GEOJSON",
            geometryMethod = "SYNTHETIC_RECTANGLE",
            manualVerified = false,
            coordinateQuality = SpatialQuality.SCENARIO,
            widthMeters = 2.0,
            lengthMeters = 2.0,
            directionDegrees = 0.0,
            centerEnu = Triple(0.0, 40.0, 0.0),
            notes = "Distant clear road"
        )
    }

    fun runSingleSession(
        context: Context,
        sessionId: Int,
        buildMode: String,
        iterationsPerTransition: Int = 30,
        onPublishOverlayState: ((ScenarioL1OverlayState) -> Unit)? = null
    ): List<ScenarioL1E2ELatencySummaryRow> {
        val shadowEngine = ShadowAdvisoryEngine()
        val controller = ScenarioL1OverlayController(
            flags = LiveIntegrationFlags.SCENARIO_TEST_L1_ONLY,
            stabilityPolicy = AdvisoryStabilityPolicy()
        )
        val mainHandler = Handler(Looper.getMainLooper())

        val l1Feature = createL1RightTurnFeature()
        val l0Feature = createL0ClearFeature()
        val provenance = InputProvenance.createPureScenario(InputSource.SCENARIO)

        val transitions = listOf(
            "T1_ENTER_L1_VISIBLE",
            "T2_EXIT_L1_TO_HIDDEN",
            "T3_IMMEDIATE_EXIT_STALE_OR_MODE_CHANGE"
        )

        val deviceInfo = "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT} (${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull() ?: "x86_64"}, ${Runtime.getRuntime().availableProcessors()} cores)"
        val summaryRows = mutableListOf<ScenarioL1E2ELatencySummaryRow>()
        val jsonLines = mutableListOf<String>()

        Log.i(TAG, "E2E_SESSION_START session=$sessionId buildMode=$buildMode iterations=$iterationsPerTransition")

        // Warmup (5 iterations)
        for (w in 0 until 5) {
            controller.reset()
            val tWarm = 10_000L + w * 200L
            val poseWarm = VehiclePose(0.0, 0.0, 0.0, tWarm, InputSource.SCENARIO, SpatialQuality.SCENARIO)
            val warmRes = shadowEngine.evaluateShadow(
                feature = l1Feature,
                pose = poseWarm,
                speedKmh = 12.0,
                turnSignal = TurnSignalState.RIGHT,
                turnRadiusMeters = 7.0,
                remainingPathDistanceMeters = 9.0,
                currentTimeMillis = tWarm,
                provenance = provenance,
                executionMode = ExecutionMode.SCENARIO_INPUT
            )
            controller.evaluateOverlay(
                shadowResult = warmRes,
                feature = l1Feature,
                executionMode = ExecutionMode.SCENARIO_INPUT,
                isScenarioBadgeVisible = true,
                isStaticL1AllowedByCarUxPolicy = true,
                currentTimeMillis = tWarm
            )
        }

        for (transitionType in transitions) {
            val inputToDecisionArr = DoubleArray(iterationsPerTransition)
            val decisionToPublishArr = DoubleArray(iterationsPerTransition)
            val publishToRenderArr = DoubleArray(iterationsPerTransition)
            val inputToFirstRenderArr = DoubleArray(iterationsPerTransition)

            for (iter in 0 until iterationsPerTransition) {
                controller.reset()
                val baseTimeMs = 100_000L + iter * 10_000L
                val freshPose = VehiclePose(0.0, 0.0, 0.0, baseTimeMs, InputSource.SCENARIO, SpatialQuality.SCENARIO)

                // Pre-condition setup before timed transition step
                when (transitionType) {
                    "T1_ENTER_L1_VISIBLE" -> {
                        val preRes = shadowEngine.evaluateShadow(
                            feature = l1Feature,
                            pose = freshPose,
                            speedKmh = 12.0,
                            turnSignal = TurnSignalState.RIGHT,
                            turnRadiusMeters = 7.0,
                            remainingPathDistanceMeters = 9.0,
                            currentTimeMillis = baseTimeMs,
                            provenance = provenance,
                            executionMode = ExecutionMode.SCENARIO_INPUT
                        )
                        controller.evaluateOverlay(
                            shadowResult = preRes,
                            feature = l1Feature,
                            executionMode = ExecutionMode.SCENARIO_INPUT,
                            isScenarioBadgeVisible = true,
                            isStaticL1AllowedByCarUxPolicy = true,
                            currentTimeMillis = baseTimeMs
                        )
                    }
                    "T2_EXIT_L1_TO_HIDDEN" -> {
                        val preL1 = shadowEngine.evaluateShadow(
                            feature = l1Feature,
                            pose = freshPose,
                            speedKmh = 12.0,
                            turnSignal = TurnSignalState.RIGHT,
                            turnRadiusMeters = 7.0,
                            remainingPathDistanceMeters = 9.0,
                            currentTimeMillis = baseTimeMs,
                            provenance = provenance,
                            executionMode = ExecutionMode.SCENARIO_INPUT
                        )
                        controller.evaluateOverlay(preL1, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, baseTimeMs)
                        controller.evaluateOverlay(preL1, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, baseTimeMs + 200L)
                        val preL0 = shadowEngine.evaluateShadow(
                            feature = l0Feature,
                            pose = freshPose.copy(timestampMillis = baseTimeMs + 1800L),
                            speedKmh = 12.0,
                            turnSignal = TurnSignalState.RIGHT,
                            turnRadiusMeters = 7.0,
                            remainingPathDistanceMeters = 39.0,
                            currentTimeMillis = baseTimeMs + 1800L,
                            provenance = provenance,
                            executionMode = ExecutionMode.SCENARIO_INPUT
                        )
                        controller.evaluateOverlay(preL0, l0Feature, ExecutionMode.SCENARIO_INPUT, true, true, baseTimeMs + 1800L)
                    }
                    "T3_IMMEDIATE_EXIT_STALE_OR_MODE_CHANGE" -> {
                        val preL1 = shadowEngine.evaluateShadow(
                            feature = l1Feature,
                            pose = freshPose,
                            speedKmh = 12.0,
                            turnSignal = TurnSignalState.RIGHT,
                            turnRadiusMeters = 7.0,
                            remainingPathDistanceMeters = 9.0,
                            currentTimeMillis = baseTimeMs,
                            provenance = provenance,
                            executionMode = ExecutionMode.SCENARIO_INPUT
                        )
                        controller.evaluateOverlay(preL1, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, baseTimeMs)
                        controller.evaluateOverlay(preL1, l1Feature, ExecutionMode.SCENARIO_INPUT, true, true, baseTimeMs + 200L)
                    }
                }

                // Timed E2E step
                val tInputNanos = System.nanoTime()
                val evalFeature = if (transitionType == "T2_EXIT_L1_TO_HIDDEN") l0Feature else l1Feature
                val evalTimeMs = when (transitionType) {
                    "T1_ENTER_L1_VISIBLE" -> baseTimeMs + 200L
                    "T2_EXIT_L1_TO_HIDDEN" -> baseTimeMs + 2000L
                    else -> baseTimeMs + 400L
                }
                val isStaleStep = (transitionType == "T3_IMMEDIATE_EXIT_STALE_OR_MODE_CHANGE" && iter % 2 == 0)
                val evalMode = if (transitionType == "T3_IMMEDIATE_EXIT_STALE_OR_MODE_CHANGE" && iter % 2 == 1) ExecutionMode.REAL_INPUT else ExecutionMode.SCENARIO_INPUT
                val evalPose = if (isStaleStep) {
                    freshPose.copy(timestampMillis = evalTimeMs - 2500L)
                } else {
                    freshPose.copy(timestampMillis = evalTimeMs)
                }

                val shadowRes = shadowEngine.evaluateShadow(
                    feature = evalFeature,
                    pose = evalPose,
                    speedKmh = 12.0,
                    turnSignal = TurnSignalState.RIGHT,
                    turnRadiusMeters = 7.0,
                    remainingPathDistanceMeters = if (transitionType == "T2_EXIT_L1_TO_HIDDEN") 39.0 else 9.0,
                    currentTimeMillis = evalTimeMs,
                    provenance = provenance,
                    executionMode = evalMode
                )

                val prelimRecord = controller.evaluateOverlay(
                    shadowResult = shadowRes,
                    feature = evalFeature,
                    executionMode = evalMode,
                    isScenarioBadgeVisible = (evalMode == ExecutionMode.SCENARIO_INPUT),
                    isStaticL1AllowedByCarUxPolicy = true,
                    currentTimeMillis = evalTimeMs,
                    inputReceivedAtNanos = tInputNanos
                )

                val tPubNanos = System.nanoTime()
                onPublishOverlayState?.invoke(prelimRecord.stabilizedOverlayState)

                var tRenderNanos = tPubNanos
                val latch = CountDownLatch(1)
                mainHandler.post {
                    Choreographer.getInstance().postFrameCallback {
                        tRenderNanos = System.nanoTime()
                        latch.countDown()
                    }
                }
                if (!latch.await(40, TimeUnit.MILLISECONDS)) {
                    tRenderNanos = System.nanoTime()
                }

                val finalRecord = prelimRecord.copy(
                    uiStatePublishedAtNanos = tPubNanos,
                    frameRenderedAtNanos = tRenderNanos
                )

                inputToDecisionArr[iter] = finalRecord.inputToDecisionMs
                decisionToPublishArr[iter] = finalRecord.decisionToPublishMs
                publishToRenderArr[iter] = finalRecord.publishToRenderMs
                inputToFirstRenderArr[iter] = finalRecord.inputToFirstRenderMs

                if (sessionId == 1 && iter < 10) {
                    jsonLines.add(finalRecord.toLatencyJsonLine(transitionType, buildMode, sessionId))
                }
            }

            summaryRows.add(computeSummaryRow(sessionId, buildMode, transitionType, "inputToDecisionMs", inputToDecisionArr, deviceInfo))
            summaryRows.add(computeSummaryRow(sessionId, buildMode, transitionType, "decisionToPublishMs", decisionToPublishArr, deviceInfo))
            summaryRows.add(computeSummaryRow(sessionId, buildMode, transitionType, "publishToRenderMs", publishToRenderArr, deviceInfo))
            summaryRows.add(computeSummaryRow(sessionId, buildMode, transitionType, "inputToFirstRenderMs", inputToFirstRenderArr, deviceInfo))
        }

        val csvFile = File(context.filesDir, "e2e_latency_${buildMode}_session_${sessionId}.csv")
        csvFile.writeText(buildString {
            appendLine(ScenarioL1E2ELatencySummaryRow.CSV_HEADER)
            summaryRows.forEach {
                appendLine(it.toCsvRow())
                Log.i(TAG, "E2E_CSV_ROW:${it.toCsvRow()}")
            }
        })

        if (jsonLines.isNotEmpty()) {
            val jsonlFile = File(context.filesDir, "e2e_latency_${buildMode}_session_${sessionId}.jsonl")
            jsonlFile.writeText(jsonLines.joinToString("\n") + "\n")
        }

        Log.i(TAG, "E2E_SESSION_DONE session=$sessionId buildMode=$buildMode file=${csvFile.absolutePath}")
        return summaryRows
    }

    private fun computeSummaryRow(
        sessionId: Int,
        buildMode: String,
        transitionType: String,
        metricName: String,
        values: DoubleArray,
        deviceInfo: String
    ): ScenarioL1E2ELatencySummaryRow {
        val sorted = values.sortedArray()
        val n = sorted.size
        val p50 = sorted[n / 2]
        val p90 = sorted[(n * 0.90).toInt().coerceAtMost(n - 1)]
        val p95 = sorted[(n * 0.95).toInt().coerceAtMost(n - 1)]
        val p99 = sorted[(n * 0.99).toInt().coerceAtMost(n - 1)]
        val maxVal = sorted[n - 1]
        val meanVal = sorted.average()
        var sqDiff = 0.0
        for (v in sorted) {
            val d = v - meanVal
            sqDiff += d * d
        }
        val stdVal = sqrt(sqDiff / n)

        return ScenarioL1E2ELatencySummaryRow(
            sessionId = sessionId,
            buildMode = buildMode,
            measurementTag = "ANDROID_RUNTIME_MEASUREMENT",
            transitionType = transitionType,
            metricName = metricName,
            iterations = n,
            p50Ms = p50,
            p90Ms = p90,
            p95Ms = p95,
            p99Ms = p99,
            maxMs = maxVal,
            meanMs = meanVal,
            stdDevMs = stdVal,
            appliedToLiveStateMachine = false,
            ttsInvoked = false,
            deviceInfo = deviceInfo
        )
    }
}
