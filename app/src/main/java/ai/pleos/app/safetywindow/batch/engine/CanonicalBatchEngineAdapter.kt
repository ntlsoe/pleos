package ai.pleos.app.safetywindow.batch.engine

import ai.pleos.app.safetywindow.batch.model.*

/**
 * Must call the same canonical trajectory, spatial transform, adaptive sampler,
 * binocular occlusion engine, decision semantics, stability policy, and demo
 * output policy used by DEMO_INTEGRATION. No batch-only fake values.
 */
interface CanonicalBatchEngineAdapter {
    suspend fun prepare(plan: BatchRunPlan): PreparedBatchRun
    suspend fun run(prepared: PreparedBatchRun, sink: suspend (BatchFrameResult) -> Unit): EngineRunReceipt
}

data class PreparedBatchRun(
    val plan: BatchRunPlan,
    val canonicalInputJson: String,
    val canonicalInputSha256: String,
    val expectedDurationMillis: Long,
    val timestepMillis: Long
)

data class EngineRunReceipt(
    val runId: String,
    val frameCount: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val engineImplementation: String,
    val engineVersion: String,
    val rawInputSha256: String
)
