package ai.pleos.app.safetywindow.demo.render

import android.view.Surface
import ai.pleos.app.safetywindow.demo.model.DemoCamera
import ai.pleos.app.safetywindow.demo.model.DemoSceneState

interface DemoRenderer {
    fun attach(viewId: String, surface: Surface, camera: DemoCamera)
    fun detach(viewId: String)
    fun resize(viewId: String, width: Int, height: Int)
    fun submit(state: DemoSceneState)
    fun setPrimaryCamera(camera: DemoCamera)
    fun renderFrame(frameTimeNanos: Long)
    fun capturePng(viewId: String, outputPath: String, onComplete: (Result<String>) -> Unit)
    fun release()
}

/** Connect current repository objects to the immutable demo state. */
interface DemoEngineAdapter {
    suspend fun reset(config: DemoScenarioConfig): DemoSceneState
    suspend fun step(simulationTimeMillis: Long): DemoSceneState
}

data class DemoScenarioConfig(
    val scenarioId: String,
    val zoneId: String?,
    val featureId: String?,
    val vehicleProfileId: String,
    val approachId: String,
    val initialSpeedMps: Double,
    val playbackRate: Double,
    val ttsEnabled: Boolean,
    val dataQualityMode: String,
    val eyeModelId: String
)
