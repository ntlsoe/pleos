package ai.pleos.app.safetywindow.batch.reporting

import ai.pleos.app.safetywindow.batch.io.CsvEscaper
import ai.pleos.app.safetywindow.batch.model.*
import java.nio.file.Files
import java.nio.file.Path

class BatchReporter {
    fun writeRunSummary(path:Path,summaries:List<BatchRunSummary>) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { w ->
            w.appendLine("runId,zoneId,featureId,vehicleProfileId,scenarioId,speedProfileId,status,resultStatus,attemptCount,frameCount,maxLevel,l0Millis,l1Millis,l2Millis,l3Millis,transitionCount,ttsRequestedCount,ttsPermittedCount,ttsSpokenCount,staleSuppressionCount,qualitySuppressionCount,peakPossibleRatio,peakUniversalRatio,peakExposureIntegral,maxContinuousOcclusionMillis,computeP50Millis,computeP95Millis,computeP99Millis,rawInputSha256,rawOutputSha256,failureCode,failureDetail")
            summaries.forEach { s -> w.appendLine(CsvEscaper.row(s.runId,s.zoneId,s.featureId,s.vehicleProfileId,s.scenarioId,s.speedProfileId,s.status,s.resultStatus,s.attemptCount,s.frameCount,s.maxLevel,s.l0Millis,s.l1Millis,s.l2Millis,s.l3Millis,s.transitionCount,s.ttsRequestedCount,s.ttsPermittedCount,s.ttsSpokenCount,s.staleSuppressionCount,s.qualitySuppressionCount,s.peakPossibleRatio,s.peakUniversalRatio,s.peakExposureIntegral,s.maxContinuousOcclusionMillis,s.computeP50Millis,s.computeP95Millis,s.computeP99Millis,s.rawInputSha256,s.rawOutputSha256,s.failureCode,s.failureDetail)) }
        }
    }

    fun writeZoneCoverage(path:Path,zones:List<ZoneRecord>,plans:List<BatchRunPlan>,summaries:List<BatchRunSummary>) {
        val byZonePlan=plans.groupBy{it.zoneId}; val byZoneSummary=summaries.groupBy{it.zoneId}
        Files.newBufferedWriter(path).use { w ->
            w.appendLine("zoneId,plannedRuns,completedRuns,passedRuns,blockedRuns,failedRuns,coveragePercent,finalResult")
            zones.sortedBy{it.zoneId}.forEach { z ->
                val expected=byZonePlan[z.zoneId].orEmpty().size; val actual=byZoneSummary[z.zoneId].orEmpty(); val complete=actual.size
                val pass=actual.count{it.status==BatchRunStatus.PASSED}; val blocked=actual.count{it.status==BatchRunStatus.BLOCKED};val failed=actual.count{it.status==BatchRunStatus.FAILED}
                val pct=if(expected==0)0.0 else complete*100.0/expected
                val result=when { failed>0 -> "FAILED"; complete!=expected -> "INCOMPLETE"; blocked>0 -> "PASS_WITH_BLOCKED"; else -> "PASS_ALL_APPLICABLE_SCENARIOS" }
                w.appendLine(CsvEscaper.row(z.zoneId,expected,complete,pass,blocked,failed,pct,result))
            }
        }
    }

    fun writeDemoReplayRegistry(path:Path,summaries:List<BatchRunSummary>) {
        val eligible=summaries.filter{it.status==BatchRunStatus.PASSED}.groupBy{Triple(it.zoneId,it.vehicleProfileId,it.scenarioId)}.values.mapNotNull{group->group.maxByOrNull{it.maxLevel.ordinal}}
        Files.newBufferedWriter(path).use { w ->
            w.appendLine("runId,zoneId,vehicleProfileId,scenarioId,speedProfileId,maxLevel,rawOutputSha256")
            eligible.sortedBy{it.runId}.forEach{s->w.appendLine(CsvEscaper.row(s.runId,s.zoneId,s.vehicleProfileId,s.scenarioId,s.speedProfileId,s.maxLevel,s.rawOutputSha256))}
        }
    }
}
