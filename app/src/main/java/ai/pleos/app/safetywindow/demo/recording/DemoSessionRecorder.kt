package ai.pleos.app.safetywindow.demo.recording

import ai.pleos.app.safetywindow.demo.model.DemoSceneState
import java.io.Closeable

data class DemoSessionPaths(
    val root: String,
    val configJson: String,
    val eventsJsonl: String,
    val transitionsCsv: String,
    val timeseriesCsv: String,
    val performanceCsv: String,
    val videoMp4: String,
    val screenshotsDir: String,
    val sha256Manifest: String
)

interface DemoVideoRecorder {
    suspend fun requestUserConsent(): Boolean
    suspend fun start(sessionId: String, outputMp4: String, width: Int = 1920, height: Int = 1080, fps: Int = 30)
    suspend fun stop(): Result<String>
    val isRecording: Boolean
}

interface DemoSessionRecorder : Closeable {
    suspend fun start(configJson: String): DemoSessionPaths
    suspend fun appendState(state: DemoSceneState)
    suspend fun appendTransition(previous: DemoSceneState?, current: DemoSceneState)
    suspend fun appendPerformance(name: String, value: Double, unit: String, simulationTimeMillis: Long)
    suspend fun registerScreenshot(name: String, absolutePath: String)
    suspend fun registerVideo(absolutePath: String)
    suspend fun finish(): DemoSessionManifest
}

data class DemoSessionManifest(
    val sessionId: String,
    val startedAt: String,
    val finishedAt: String,
    val eventCount: Long,
    val transitionCount: Long,
    val droppedFrameCount: Long,
    val files: Map<String, FileDigest>
)

data class FileDigest(val bytes: Long, val sha256: String)

interface MediaProjectionRecorder : DemoVideoRecorder
