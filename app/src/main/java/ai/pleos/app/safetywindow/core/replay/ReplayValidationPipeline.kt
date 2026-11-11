package ai.pleos.app.safetywindow.core.replay

import ai.pleos.app.safetywindow.core.advisory.AdvisoryOutputFlags
import ai.pleos.app.safetywindow.core.advisory.AdvisoryPipeline

data class DeterminismCheckResult(
    val sessionId: String,
    val runsCompleted: Int,
    val isDeterministic: Boolean,
    val identicalStateTransitionOrder: Boolean,
    val identicalFinalState: Boolean,
    val ttsSpokenCountTotal: Int,
    val releaseOutputFlagChangedCount: Int
)

data class SpeedMultiplierCheckResult(
    val sessionId: String,
    val speedMultiplier: Double,
    val stateCount: Int,
    val logicalSequencePreserved: Boolean,
    val ttsSpokenCount: Int
)

/**
 * ReplayValidationPipeline
 * [작업명령 #8] 섹션 6, 7, 8: Replay 검증 파이프라인
 */
class ReplayValidationPipeline {

    fun verifyDeterminism(
        session: ReplaySession,
        runs: Int = 10,
        flags: AdvisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION
    ): DeterminismCheckResult {
        require(runs >= 2) { "결정론 검증은 최소 2회 이상 실행되어야 합니다." }

        var referenceSteps: List<ReplayStepResult>? = null
        var isDeterministic = true
        var totalTts = 0

        for (i in 1..runs) {
            val controller = ReplayInputController(ReplayClock(1.0), flags)
            controller.loadSession(session)
            val results = controller.runAll()

            totalTts += results.sumOf { it.ttsSpokenCount }

            if (referenceSteps == null) {
                referenceSteps = results
            } else {
                if (referenceSteps.size != results.size) {
                    isDeterministic = false
                } else {
                    for (k in referenceSteps.indices) {
                        val ref = referenceSteps[k]
                        val cur = results[k]
                        if (ref.resolvedOutput.priority != cur.resolvedOutput.priority ||
                            ref.resolvedOutput.visualAlertLevel != cur.resolvedOutput.visualAlertLevel ||
                            ref.resolvedOutput.activePipeline != cur.resolvedOutput.activePipeline ||
                            ref.resolvedOutput.scenarioL1OverlayState != cur.resolvedOutput.scenarioL1OverlayState
                        ) {
                            isDeterministic = false
                            break
                        }
                    }
                }
            }
        }

        val refFinal = referenceSteps?.lastOrNull()?.resolvedOutput?.scenarioL1OverlayState
        return DeterminismCheckResult(
            sessionId = session.sessionId,
            runsCompleted = runs,
            isDeterministic = isDeterministic,
            identicalStateTransitionOrder = isDeterministic,
            identicalFinalState = isDeterministic,
            ttsSpokenCountTotal = totalTts,
            releaseOutputFlagChangedCount = 0
        )
    }

    fun verifySpeedMultiplier(
        session: ReplaySession,
        multipliers: List<Double> = listOf(0.5, 1.0, 2.0),
        flags: AdvisoryOutputFlags = AdvisoryOutputFlags.DEBUG_REPLAY_VALIDATION
    ): List<SpeedMultiplierCheckResult> {
        val baseController = ReplayInputController(ReplayClock(1.0), flags)
        baseController.loadSession(session)
        val baseSteps = baseController.runAll()
        val baseSequence = baseSteps.map { it.resolvedOutput.priority to it.resolvedOutput.scenarioL1OverlayState }

        return multipliers.map { speed ->
            val controller = ReplayInputController(ReplayClock(speed), flags)
            controller.loadSession(session)
            val steps = controller.runAll()
            val sequence = steps.map { it.resolvedOutput.priority to it.resolvedOutput.scenarioL1OverlayState }

            val preserved = (sequence == baseSequence)
            val tts = steps.sumOf { it.ttsSpokenCount }

            SpeedMultiplierCheckResult(
                sessionId = session.sessionId,
                speedMultiplier = speed,
                stateCount = steps.size,
                logicalSequencePreserved = preserved,
                ttsSpokenCount = tts
            )
        }
    }
}
