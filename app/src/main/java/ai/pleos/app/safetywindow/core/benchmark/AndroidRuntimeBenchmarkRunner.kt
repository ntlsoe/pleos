package ai.pleos.app.safetywindow.core.benchmark

import ai.pleos.app.safetywindow.core.occlusion.BinocularOcclusionEngine
import ai.pleos.app.safetywindow.core.occlusion.ConfigurableEyePopulationModel
import ai.pleos.app.safetywindow.core.occlusion.Point3
import ai.pleos.app.safetywindow.core.occlusion.RiskSamplePoint
import ai.pleos.app.safetywindow.core.occlusion.RiskSpaceType
import ai.pleos.app.safetywindow.core.occlusion.RiskSurface
import ai.pleos.app.safetywindow.core.occlusion.VehiclePillarGeometry
import ai.pleos.app.safetywindow.core.occlusion.VehiclePose
import ai.pleos.app.safetywindow.core.spatial.AdaptiveRiskFeatureSampler
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

data class AndroidBenchmarkRow(
    val sessionId: Int,
    val buildMode: String,
    val measurementTag: String,
    val configId: String,
    val samplePointCount: Int,
    val eyePairCount: Int,
    val warmupIterations: Int,
    val measurementIterations: Int,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val meanMs: Double,
    val stdDevMs: Double,
    val gcCount: Long,
    val allocBytesPerEval: Long,
    val uiThreadIsolated: Boolean,
    val workerThreadName: String,
    val deviceInfo: String
) {
    fun toCsvRow(): String {
        return "$sessionId,$buildMode,$measurementTag,$configId,$samplePointCount,$eyePairCount," +
            "$warmupIterations,$measurementIterations," +
            "${String.format("%.6f", p50Ms)},${String.format("%.6f", p90Ms)},${String.format("%.6f", p95Ms)}," +
            "${String.format("%.6f", p99Ms)},${String.format("%.6f", maxMs)},${String.format("%.6f", meanMs)}," +
            "${String.format("%.6f", stdDevMs)},$gcCount,$allocBytesPerEval,$uiThreadIsolated,\"$workerThreadName\",\"$deviceInfo\""
    }

    companion object {
        const val CSV_HEADER =
            "session_id,build_mode,measurement_tag,config_id,sample_point_count,eye_pair_count,warmup_iterations,measurement_iterations,p50_ms,p90_ms,p95_ms,p99_ms,max_ms,mean_ms,std_dev_ms,gc_count,alloc_bytes_per_eval,ui_thread_isolated,worker_thread_name,device_info"
    }
}

object AndroidRuntimeBenchmarkRunner {
    private const val TAG = "ANDROID_BENCHMARK_CSV"

    private fun createFixedPoints(targetCount: Int): List<RiskSamplePoint> {
        val lx = 3.0
        val wy = 5.0
        val spacing = when (targetCount) {
            77 -> 0.5
            273 -> 0.25
            1025 -> 0.125
            else -> 0.5
        }
        val nw = when (targetCount) {
            77 -> 11
            273 -> 21
            1025 -> 41
            else -> 11
        }
        val nl = when (targetCount) {
            77 -> 7
            273 -> 13
            1025 -> 25
            else -> 7
        }
        val pts = ArrayList<RiskSamplePoint>(targetCount)
        val halfW = wy / 2.0
        val halfL = lx / 2.0
        val dw = wy / (nw - 1)
        val dl = lx / (nl - 1)
        val w = 1.0 / targetCount
        for (i in 0 until nw) {
            val y = -halfW + i * dw
            for (j in 0 until nl) {
                val x = -halfL + j * dl
                pts.add(RiskSamplePoint(Point3(7.0 + x, -4.0 + y, 0.5), w, spacing))
            }
        }
        return pts
    }

    fun runSingleSession(
        context: Context,
        sessionId: Int,
        buildMode: String,
        warmupIterations: Int = 100,
        measurementIterations: Int = 1000
    ): List<AndroidBenchmarkRow> {
        val isUiThread = (Looper.myLooper() == Looper.getMainLooper())
        val uiThreadIsolated = !isUiThread
        val workerName = Thread.currentThread().name

        val vehicle = VehiclePillarGeometry.createRepresentativeSedanV0()
        val eyeModel = ConfigurableEyePopulationModel.createRepresentativePopulation(vehicle.driverEyeNominal)
        val engine = BinocularOcclusionEngine(vehicle, eyeModel)
        val adaptiveSampler = AdaptiveRiskFeatureSampler(vehicle, eyeModel)
        val dummyPose = VehiclePose(0.0, 0.0, 0.0, 3.33, 0.0)
        val poseList = listOf(dummyPose)

        val adaptiveSurface = RiskSurface(
            id = "bench_adaptive_surface",
            type = RiskSpaceType.CROSSWALK,
            origin = Point3(7.0, -4.0, 0.5),
            lengthX = 3.0,
            widthY = 5.0,
            heightZ = 0.0
        )
        val adaptivePreview = adaptiveSampler.sampleAdaptiveSurface(adaptiveSurface, engine, poseList, 1.0 / 7.0)

        data class ConfigSpec(
            val configId: String,
            val isAdaptive: Boolean,
            val fixedPoints: List<RiskSamplePoint>
        )

        val configs = listOf(
            ConfigSpec("FIXED_0.5m_x_9_EYES", false, createFixedPoints(77)),
            ConfigSpec("FIXED_0.25m_x_9_EYES", false, createFixedPoints(273)),
            ConfigSpec("ADAPTIVE_0.5_TO_0.25m_x_9_EYES", true, adaptivePreview.points),
            ConfigSpec("FIXED_0.125m_x_9_EYES_OFFLINE_REF", false, createFixedPoints(1025))
        )

        val deviceInfo = "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT} (${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull() ?: "x86_64"}, ${Runtime.getRuntime().availableProcessors()} cores)"
        val rows = mutableListOf<AndroidBenchmarkRow>()

        Log.i(TAG, "SESSION_START session=$sessionId buildMode=$buildMode uiIsolated=$uiThreadIsolated thread=$workerName")

        for (cfg in configs) {
            // 1. Warmup
            for (w in 0 until warmupIterations) {
                if (cfg.isAdaptive) {
                    val sampled = adaptiveSampler.sampleAdaptiveSurface(adaptiveSurface, engine, poseList, 1.0 / 7.0)
                    for (pt in sampled.points) {
                        engine.evaluateRiskPointAtPose(pt.position, dummyPose)
                    }
                } else {
                    for (pt in cfg.fixedPoints) {
                        engine.evaluateRiskPointAtPose(pt.position, dummyPose)
                    }
                }
            }

            Runtime.getRuntime().gc()
            try {
                Thread.sleep(15)
            } catch (_: InterruptedException) {}

            val gcBefore = getGcCountSafe()
            val memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

            val timesMs = DoubleArray(measurementIterations)
            for (i in 0 until measurementIterations) {
                val t0 = System.nanoTime()
                if (cfg.isAdaptive) {
                    val sampled = adaptiveSampler.sampleAdaptiveSurface(adaptiveSurface, engine, poseList, 1.0 / 7.0)
                    for (pt in sampled.points) {
                        engine.evaluateRiskPointAtPose(pt.position, dummyPose)
                    }
                } else {
                    for (pt in cfg.fixedPoints) {
                        engine.evaluateRiskPointAtPose(pt.position, dummyPose)
                    }
                }
                val t1 = System.nanoTime()
                timesMs[i] = (t1 - t0) / 1_000_000.0
            }

            val memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val gcAfter = getGcCountSafe()
            val gcDelta = max(0L, gcAfter - gcBefore)
            val allocPerEval = max(0L, memAfter - memBefore) / measurementIterations

            timesMs.sort()
            val p50 = timesMs[measurementIterations / 2]
            val p90 = timesMs[(measurementIterations * 0.90).toInt()]
            val p95 = timesMs[(measurementIterations * 0.95).toInt()]
            val p99 = timesMs[(measurementIterations * 0.99).toInt()]
            val maxVal = timesMs[measurementIterations - 1]
            val meanVal = timesMs.average()
            var sqDiff = 0.0
            for (v in timesMs) {
                val d = v - meanVal
                sqDiff += d * d
            }
            val stdVal = sqrt(sqDiff / measurementIterations)

            val row = AndroidBenchmarkRow(
                sessionId = sessionId,
                buildMode = buildMode,
                measurementTag = "ANDROID_RUNTIME_MEASUREMENT",
                configId = cfg.configId,
                samplePointCount = cfg.fixedPoints.size,
                eyePairCount = 9,
                warmupIterations = warmupIterations,
                measurementIterations = measurementIterations,
                p50Ms = p50,
                p90Ms = p90,
                p95Ms = p95,
                p99Ms = p99,
                maxMs = maxVal,
                meanMs = meanVal,
                stdDevMs = stdVal,
                gcCount = gcDelta,
                allocBytesPerEval = allocPerEval,
                uiThreadIsolated = uiThreadIsolated,
                workerThreadName = workerName,
                deviceInfo = deviceInfo
            )
            rows.add(row)
            Log.i(TAG, "CSV_ROW:${row.toCsvRow()}")
        }

        val outFile = File(context.filesDir, "benchmark_${buildMode}_session_${sessionId}.csv")
        val content = buildString {
            appendLine(AndroidBenchmarkRow.CSV_HEADER)
            rows.forEach { appendLine(it.toCsvRow()) }
        }
        outFile.writeText(content)
        Log.i(TAG, "SESSION_DONE session=$sessionId buildMode=$buildMode file=${outFile.absolutePath}")
        return rows
    }

    private fun getGcCountSafe(): Long {
        return try {
            val stat = Debug.getRuntimeStat("art.gc.gc-count")
            stat?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }
}
