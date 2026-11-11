package ai.pleos.app.safetywindow.core.advisory

import ai.pleos.app.safetywindow.core.input.InputProvenance
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryEngine
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState

/** Raw-only frame contract; computed levels and output decisions are deliberately absent. */
data class GeometryAdvisoryRawFrame(
    val feature: RiskFeature,
    val pose: VehiclePose,
    val speedKmh: Double,
    val turnSignal: TurnSignalState,
    val steeringAngleDeg: Double,
    val turnRadiusMeters: Double?,
    val remainingPathDistanceMeters: Double?,
    val timestampMillis: Long,
    val executionMode: ExecutionMode
)

data class GeometryAdvisoryEvaluation(
    val geometry: ai.pleos.app.safetywindow.core.shadow.ShadowAdvisoryResult,
    val continuousOcclusionMs: Long,
    val decision: AdvisoryDecision
)

class GeometryAdvisoryEvaluator(
    private val engine: ShadowAdvisoryEngine = ShadowAdvisoryEngine()
) {
    private var trackedFeatureId: String? = null
    private var firstContinuousHitAt: Long? = null
    private var lastHitAt: Long? = null

    fun reset() {
        trackedFeatureId = null
        firstContinuousHitAt = null
        lastHitAt = null
    }

    fun evaluate(
        frame: GeometryAdvisoryRawFrame,
        userEnabled: Boolean,
        carUxRestricted: Boolean,
        visualFlag: Boolean,
        ttsFlag: Boolean,
        voiceMuted: Boolean
    ): GeometryAdvisoryEvaluation {
        val provenance = InputProvenance.fromPoseAndFeature(frame.pose, frame.feature)
        val result = engine.evaluateShadow(
            feature = frame.feature,
            pose = frame.pose,
            speedKmh = frame.speedKmh,
            turnSignal = frame.turnSignal,
            steeringAngleDeg = frame.steeringAngleDeg,
            currentTimeMillis = frame.timestampMillis,
            turnRadiusMeters = frame.turnRadiusMeters,
            remainingPathDistanceMeters = frame.remainingPathDistanceMeters,
            provenance = provenance,
            executionMode = frame.executionMode
        )
        val hasHit = result.binocularHits > 0 && result.advisoryEligibility.name.startsWith("ELIGIBLE")
        val previousHitAt = lastHitAt
        if (!hasHit || trackedFeatureId != frame.feature.featureId ||
            previousHitAt == null || frame.timestampMillis <= previousHitAt ||
            frame.timestampMillis - previousHitAt > 500L
        ) {
            firstContinuousHitAt = if (hasHit) frame.timestampMillis else null
        }
        trackedFeatureId = if (hasHit) frame.feature.featureId else null
        lastHitAt = if (hasHit) frame.timestampMillis else null
        val continuousMs = if (hasHit) (frame.timestampMillis - (firstContinuousHitAt ?: frame.timestampMillis)).coerceAtLeast(0L) else 0L
        val decision = AdvisoryDecisionResolver.resolve(
            geometry = result,
            continuousOcclusionMs = continuousMs,
            inputMode = frame.executionMode,
            userEnabled = userEnabled,
            carUxRestricted = carUxRestricted,
            visualFlag = visualFlag,
            ttsFlag = ttsFlag && !voiceMuted,
            evaluatedAt = frame.timestampMillis
        )
        return GeometryAdvisoryEvaluation(result, continuousMs, decision)
    }
}
