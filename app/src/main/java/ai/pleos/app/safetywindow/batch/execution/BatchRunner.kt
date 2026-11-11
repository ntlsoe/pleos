package ai.pleos.app.safetywindow.batch.execution

import ai.pleos.app.safetywindow.batch.engine.*
import ai.pleos.app.safetywindow.batch.io.*
import ai.pleos.app.safetywindow.batch.model.*
import ai.pleos.app.safetywindow.batch.validation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Files
import kotlin.math.ceil

class BatchRunner(
    private val engine: CanonicalBatchEngineAdapter,
    private val validator: InvariantValidator,
    private val artifacts: BatchArtifactLayout,
    private val parallelism: Int = Runtime.getRuntime().availableProcessors().coerceIn(1,8)
) {
    suspend fun execute(plans: List<BatchRunPlan>, scenarioById: Map<String,ScenarioDefinition>): List<BatchRunSummary> = coroutineScope {
        artifacts.ensure()
        val semaphore=Semaphore(parallelism)
        plans.map { plan -> async(Dispatchers.Default) { semaphore.withPermit { executeWithRetry(plan, scenarioById.getValue(plan.scenarioId)) } } }.awaitAll()
    }

    private suspend fun executeWithRetry(plan: BatchRunPlan, scenario: ScenarioDefinition): BatchRunSummary {
        var last: Throwable?=null
        repeat(plan.attemptLimit) { attempt ->
            try { return executeOnce(plan,scenario,attempt+1) }
            catch(t:Throwable) { last=t }
        }
        return failedSummary(plan,plan.attemptLimit,last)
    }

    private suspend fun executeOnce(plan: BatchRunPlan, scenario: ScenarioDefinition, attempt: Int): BatchRunSummary {
        val prepared=engine.prepare(plan)
        require(prepared.canonicalInputSha256==plan.inputManifestSha256 || prepared.canonicalInputSha256.isNotBlank())
        val frames=ArrayList<BatchFrameResult>()
        val rawPath=artifacts.rawPath(plan)
        AtomicGzipJsonlWriter(rawPath).use { writer ->
            engine.run(prepared) { frame ->
                validator.validateFrame(frame,frames.lastOrNull())
                frames += frame
                writer.append(frameToJson(frame))
            }
        }
        validator.validateRun(plan,frames,scenario.expectedSilence)
        val hash=Digests.sha256(rawPath)
        return summarize(plan,frames,attempt,hash)
    }

    private fun summarize(plan:BatchRunPlan,frames:List<BatchFrameResult>,attempt:Int,hash:String):BatchRunSummary {
        val ns=frames.map { it.engineComputeNanos }.sorted()
        fun pct(p:Double)= if(ns.isEmpty()) 0.0 else ns[ceil((ns.size-1)*p).toInt()]/1_000_000.0
        fun duration(level:CandidateLevel)=frames.zipWithNext().filter { it.first.stabilizedLevel==level }.sumOf { it.second.simulationTimeMillis-it.first.simulationTimeMillis }
        return BatchRunSummary(plan.runId,plan.zoneId,plan.featureId,plan.vehicleProfileId,plan.scenarioId,plan.speedProfileId,
            BatchRunStatus.PASSED,ResultStatus.PASS_ALL_APPLICABLE_SCENARIOS,attempt,frames.size.toLong(),
            frames.maxBy { it.stabilizedLevel.ordinal }.stabilizedLevel,duration(CandidateLevel.L0),duration(CandidateLevel.L1),duration(CandidateLevel.L2),duration(CandidateLevel.L3),
            frames.zipWithNext().count{it.first.stabilizedLevel!=it.second.stabilizedLevel},frames.count{it.ttsRequested},frames.count{it.ttsPermitted},frames.count{it.ttsActuallySpoken},
            frames.count{it.eligibility==AdvisoryEligibility.INELIGIBLE_STALE_INPUT},frames.count{it.eligibility==AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY},
            frames.maxOf{it.weightedPossibleRatio},frames.maxOf{it.weightedUniversalRatio},frames.maxOf{it.exposureIntegral},frames.maxOf{it.maxContinuousOcclusionMillis},pct(.50),pct(.95),pct(.99),
            plan.inputManifestSha256,hash,null,null)
    }

    private fun failedSummary(plan:BatchRunPlan,attempt:Int,t:Throwable?)=BatchRunSummary(plan.runId,plan.zoneId,plan.featureId,plan.vehicleProfileId,plan.scenarioId,plan.speedProfileId,
        BatchRunStatus.FAILED,ResultStatus.FAILED_ENGINE,attempt,0,CandidateLevel.NONE_DATA_INVALID,0,0,0,0,0,0,0,0,0,0,0.0,0.0,0.0,0,0.0,0.0,0.0,plan.inputManifestSha256,"",t?.javaClass?.simpleName,t?.message)

    private fun frameToJson(f:BatchFrameResult):String = buildString {
        append('{'); append("\"runId\":\"").append(f.runId).append("\",")
        append("\"simulationTimeMillis\":").append(f.simulationTimeMillis).append(',')
        append("\"speedMps\":").append(f.speedMps).append(',')
        append("\"possibleRatio\":").append(f.weightedPossibleRatio).append(',')
        append("\"universalRatio\":").append(f.weightedUniversalRatio).append(',')
        append("\"geometryAssessment\":\"").append(f.geometryAssessment).append("\",")
        append("\"eligibility\":\"").append(f.eligibility).append("\",")
        append("\"stabilizedLevel\":\"").append(f.stabilizedLevel).append("\",")
        append("\"outputSuppressed\":").append(f.outputSuppressed).append(',')
        append("\"ttsRequested\":").append(f.ttsRequested).append(',')
        append("\"ttsPermitted\":").append(f.ttsPermitted).append(',')
        append("\"ttsActuallySpoken\":").append(f.ttsActuallySpoken).append('}')
    }
}
