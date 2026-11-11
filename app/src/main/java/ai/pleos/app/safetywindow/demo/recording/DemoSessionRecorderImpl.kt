package ai.pleos.app.safetywindow.demo.recording

import ai.pleos.app.safetywindow.demo.model.DemoSceneState
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DemoSessionRecorderImpl(
    private val baseDir: File
) : DemoSessionRecorder {

    private var sessionId: String = ""
    private var startedAt: String = ""
    private var paths: DemoSessionPaths? = null

    private var eventWriter: PrintWriter? = null
    private var transitionWriter: PrintWriter? = null
    private var timeseriesWriter: PrintWriter? = null
    private var performanceWriter: PrintWriter? = null

    private var eventCount = 0L
    private var transitionCount = 0L
    private val registeredFiles = mutableMapOf<String, FileDigest>()

    override suspend fun start(configJson: String): DemoSessionPaths {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        startedAt = dateFormat.format(Date())
        sessionId = "session_$startedAt"

        val sessionRoot = File(baseDir, sessionId).apply { mkdirs() }
        val screenshotsDir = File(sessionRoot, "screenshots").apply { mkdirs() }

        val configFile = File(sessionRoot, "session_config.json")
        configFile.writeText(configJson, Charsets.UTF_8)

        val eventsFile = File(sessionRoot, "simulation_events.jsonl")
        eventWriter = PrintWriter(OutputStreamWriter(FileOutputStream(eventsFile, true), Charsets.UTF_8))

        val transitionsFile = File(sessionRoot, "advisory_transitions.csv")
        transitionWriter = PrintWriter(OutputStreamWriter(FileOutputStream(transitionsFile, true), Charsets.UTF_8)).apply {
            println("simulationTimeMillis,fromLevel,toLevel,direction,message,ttsSpoken")
            flush()
        }

        val timeseriesFile = File(sessionRoot, "occlusion_timeseries.csv")
        timeseriesWriter = PrintWriter(OutputStreamWriter(FileOutputStream(timeseriesFile, true), Charsets.UTF_8)).apply {
            println("simulationTimeMillis,speedKmh,peakPossibleRatio,peakUniversalRatio,exposureIntegral,distanceToFeatureM,advisoryLevel")
            flush()
        }

        val perfFile = File(sessionRoot, "performance_metrics.csv")
        performanceWriter = PrintWriter(OutputStreamWriter(FileOutputStream(perfFile, true), Charsets.UTF_8)).apply {
            println("simulationTimeMillis,metricName,value,unit")
            flush()
        }

        val videoFile = File(sessionRoot, "screen_recording.mp4")
        val manifestFile = File(sessionRoot, "sha256_manifest.txt")

        val p = DemoSessionPaths(
            root = sessionRoot.absolutePath,
            configJson = configFile.absolutePath,
            eventsJsonl = eventsFile.absolutePath,
            transitionsCsv = transitionsFile.absolutePath,
            timeseriesCsv = timeseriesFile.absolutePath,
            performanceCsv = perfFile.absolutePath,
            videoMp4 = videoFile.absolutePath,
            screenshotsDir = screenshotsDir.absolutePath,
            sha256Manifest = manifestFile.absolutePath
        )
        paths = p
        return p
    }

    override suspend fun appendState(state: DemoSceneState) {
        eventCount++
        eventWriter?.let { w ->
            val jsonLine = """{"t":${state.simulationTimeMillis},"level":"${state.advisory.stabilizedLevel}","speed":${state.vehiclePose.speedMps},"dist":${state.metrics.distanceToFeatureM ?: -1.0}}"""
            w.println(jsonLine)
            w.flush()
        }

        timeseriesWriter?.let { w ->
            val speedKmh = state.vehiclePose.speedMps * 3.6
            val dist = state.metrics.distanceToFeatureM ?: -1.0
            w.println("${state.simulationTimeMillis},${"%.2f".format(speedKmh)},${"%.4f".format(state.metrics.peakPossibleRatio)},${"%.4f".format(state.metrics.peakUniversalRatio)},${"%.4f".format(state.metrics.exposureIntegral)},${"%.2f".format(dist)},${state.advisory.stabilizedLevel}")
            w.flush()
        }
    }

    override suspend fun appendTransition(previous: DemoSceneState?, current: DemoSceneState) {
        val prevLevel = previous?.advisory?.stabilizedLevel?.name ?: "INITIAL"
        val currLevel = current.advisory.stabilizedLevel.name
        if (prevLevel != currLevel) {
            transitionCount++
            transitionWriter?.let { w ->
                val cleanMsg = current.advisory.message.replace("\n", " ").replace(",", ";")
                w.println("${current.simulationTimeMillis},$prevLevel,$currLevel,${current.advisory.direction},$cleanMsg,${current.advisory.ttsSpoken}")
                w.flush()
            }
        }
    }

    override suspend fun appendPerformance(name: String, value: Double, unit: String, simulationTimeMillis: Long) {
        performanceWriter?.let { w ->
            w.println("$simulationTimeMillis,$name,${"%.4f".format(value)},$unit")
            w.flush()
        }
    }

    override suspend fun registerScreenshot(name: String, absolutePath: String) {
        val file = File(absolutePath)
        if (file.exists()) {
            val digest = computeDigest(file)
            registeredFiles[file.name] = digest
        }
    }

    override suspend fun registerVideo(absolutePath: String) {
        val file = File(absolutePath)
        if (file.exists()) {
            val digest = computeDigest(file)
            registeredFiles[file.name] = digest
        }
    }

    override suspend fun finish(): DemoSessionManifest {
        close()
        val p = paths ?: throw IllegalStateException("Session not started")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val finishedAt = dateFormat.format(Date())

        val sessionFiles = listOf(
            File(p.configJson),
            File(p.eventsJsonl),
            File(p.transitionsCsv),
            File(p.timeseriesCsv),
            File(p.performanceCsv)
        )

        for (f in sessionFiles) {
            if (f.exists()) {
                registeredFiles[f.name] = computeDigest(f)
            }
        }

        // Write sha256_manifest.txt
        val manifestFile = File(p.sha256Manifest)
        manifestFile.printWriter(Charsets.UTF_8).use { w ->
            w.println("# Pleos Safety Window Demo Session SHA-256 Manifest")
            w.println("# Session: $sessionId | Generated: $finishedAt")
            for ((name, digest) in registeredFiles.toSortedMap()) {
                w.println("${digest.sha256}  $name (${digest.bytes} bytes)")
            }
        }
        registeredFiles[manifestFile.name] = computeDigest(manifestFile)

        return DemoSessionManifest(
            sessionId = sessionId,
            startedAt = startedAt,
            finishedAt = finishedAt,
            eventCount = eventCount,
            transitionCount = transitionCount,
            droppedFrameCount = 0L,
            files = registeredFiles
        )
    }

    override fun close() {
        eventWriter?.close()
        eventWriter = null
        transitionWriter?.close()
        transitionWriter = null
        timeseriesWriter?.close()
        timeseriesWriter = null
        performanceWriter?.close()
        performanceWriter = null
    }

    private fun computeDigest(file: File): FileDigest {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
        return FileDigest(bytes = file.length(), sha256 = hash)
    }
}
