package ai.pleos.app.safetywindow.batch.validation

import ai.pleos.app.safetywindow.batch.model.*

class InvariantViolation(val code: String, message: String) : IllegalStateException(message)

class InvariantValidator {
    fun validateFrame(frame: BatchFrameResult, previous: BatchFrameResult?) {
        require(frame.eyeResults.size == 9) { "N=9 eye results required" }
        require(frame.eyeResults.all { it.possibleRatio in 0.0..1.0 && it.universalRatio in 0.0..1.0 })
        if (previous != null && frame.simulationTimeMillis <= previous.simulationTimeMillis) violation("CLOCK_NOT_MONOTONIC","Simulation clock must increase")
        if (frame.ttsActuallySpoken) violation("ACTUAL_TTS_IN_BATCH","Batch must never speak")
        if (frame.eligibility.name.startsWith("INELIGIBLE") && frame.stabilizedLevel in setOf(CandidateLevel.L2,CandidateLevel.L3)) violation("INELIGIBLE_HIGH_LEVEL","Ineligible frame cannot stabilize at L2/L3")
        if (frame.eligibility == AdvisoryEligibility.INELIGIBLE_STALE_INPUT && !frame.outputSuppressed) violation("STALE_NOT_SUPPRESSED","Stale input must suppress output")
        if (frame.eligibility == AdvisoryEligibility.INELIGIBLE_LOW_SPATIAL_QUALITY && frame.stabilizedLevel in setOf(CandidateLevel.L2,CandidateLevel.L3)) violation("LOW_QUALITY_HIGH_LEVEL","Low quality cannot yield L2/L3")
        if (frame.stabilizedLevel == CandidateLevel.L3 && previous?.stabilizedLevel !in setOf(CandidateLevel.L2,CandidateLevel.L3)) violation("L3_WITHOUT_L2","L3 must be preceded by stabilized L2")
    }

    fun validateRun(plan: BatchRunPlan, frames: List<BatchFrameResult>, expectedSilence: Boolean) {
        if (frames.isEmpty()) violation("EMPTY_RUN","${plan.runId} produced no frames")
        frames.forEachIndexed { i, f -> validateFrame(f, frames.getOrNull(i-1)) }
        if (expectedSilence && frames.any { it.stabilizedLevel != CandidateLevel.L0 && it.stabilizedLevel != CandidateLevel.NONE_DATA_INVALID }) violation("SILENCE_SCENARIO_ALERTED","Expected-silence run alerted")
        if (frames.last().outputSuppressed.not() && frames.last().stabilizedLevel != CandidateLevel.L0) violation("FINAL_NOT_HIDDEN","Scenario must finish L0/hidden")
    }

    private fun violation(code: String, message: String): Nothing = throw InvariantViolation(code,message)
}
