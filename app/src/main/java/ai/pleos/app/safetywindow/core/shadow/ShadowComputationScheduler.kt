package ai.pleos.app.safetywindow.core.shadow

import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Experimental prototype scheduler rates for Shadow Advisory computation (Section 9).
 * Not a finalized product policy.
 */
enum class ShadowComputationRate(val frequencyHz: Int, val intervalMillis: Long) {
    OFF(0, Long.MAX_VALUE),
    LOW(2, 500L),
    MEDIUM(5, 200L),
    HIGH(10, 100L)
}

enum class SchedulerTriggerReason {
    SKIPPED_RATE_OFF,
    SKIPPED_INTERVAL_NOT_ELAPSED,
    SKIPPED_NO_INPUT_CHANGE,
    IMMEDIATE_STALE_STATE_CHANGE,
    IMMEDIATE_FEATURE_CHANGE,
    IMMEDIATE_SPATIAL_QUALITY_CHANGE,
    IMMEDIATE_MANEUVER_CHANGE,
    IMMEDIATE_HEADING_DELTA,
    IMMEDIATE_DISPLACEMENT_DELTA,
    PERIODIC_TIMER_ELAPSED
}

data class SchedulerDecision(
    val shouldEvaluate: Boolean,
    val triggerReason: SchedulerTriggerReason,
    val activeRate: ShadowComputationRate
)

/**
 * Prototype scheduler for Shadow Advisory Mode.
 * Supports both fixed periodic scheduling (OFF, 2Hz, 5Hz, 10Hz) and event-driven input change detection.
 * Strictly isolated from live UI, TTS, and L0-L3 state machine.
 */
class ShadowComputationScheduler(
    var rate: ShadowComputationRate = ShadowComputationRate.HIGH,
    val eventDrivenEnabled: Boolean = true,
    val minDisplacementMeters: Double = 0.25,
    val minHeadingDeltaRadians: Double = Math.toRadians(1.5),
    val maxStaleAgeMillis: Long = 1500L
) {
    private var lastEvaluatedTimeMillis: Long? = null
    private var lastFeatureId: String? = null
    private var lastLocalX: Double? = null
    private var lastLocalY: Double? = null
    private var lastHeadingRad: Double? = null
    private var lastManeuver: String? = null
    private var lastQuality: SpatialQuality? = null
    private var lastStaleState: Boolean? = null

    fun reset() {
        lastEvaluatedTimeMillis = null
        lastFeatureId = null
        lastLocalX = null
        lastLocalY = null
        lastHeadingRad = null
        lastManeuver = null
        lastQuality = null
        lastStaleState = null
    }

    fun shouldEvaluate(
        currentTimeMillis: Long,
        feature: RiskFeature,
        pose: VehiclePose,
        maneuverContext: String
    ): SchedulerDecision {
        if (rate == ShadowComputationRate.OFF) {
            return SchedulerDecision(false, SchedulerTriggerReason.SKIPPED_RATE_OFF, rate)
        }

        val currentStale = pose.isStale(currentTimeMillis, maxStaleAgeMillis) || pose.quality == SpatialQuality.STALE

        // 1. Check immediate event-driven triggers
        if (eventDrivenEnabled) {
            if (lastStaleState == null || currentStale != lastStaleState) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_STALE_STATE_CHANGE, rate)
            }
            if (lastFeatureId != feature.featureId) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_FEATURE_CHANGE, rate)
            }
            if (lastQuality != pose.quality) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_SPATIAL_QUALITY_CHANGE, rate)
            }
            if (lastManeuver != maneuverContext) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_MANEUVER_CHANGE, rate)
            }

            // Cap continuous pose-delta evaluations at rate.intervalMillis (max 5Hz or 10Hz)
            val prevTime = lastEvaluatedTimeMillis
            if (prevTime != null && (currentTimeMillis - prevTime) < rate.intervalMillis) {
                return SchedulerDecision(false, SchedulerTriggerReason.SKIPPED_INTERVAL_NOT_ELAPSED, rate)
            }

            val lx = lastLocalX
            val ly = lastLocalY
            val lh = lastHeadingRad
            val disp = if (lx != null && ly != null) hypot(pose.localX - lx, pose.localY - ly) else Double.POSITIVE_INFINITY
            val hDelta = if (lh != null) angularDifferenceRadians(pose.headingRadians, lh) else Double.POSITIVE_INFINITY

            if (hDelta >= minHeadingDeltaRadians) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_HEADING_DELTA, rate)
            }
            if (disp >= minDisplacementMeters) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.IMMEDIATE_DISPLACEMENT_DELTA, rate)
            }

            // Inputs have not meaningfully changed; suppress redundant duplicate evaluation
            return SchedulerDecision(false, SchedulerTriggerReason.SKIPPED_NO_INPUT_CHANGE, rate)
        } else {
            // Fixed periodic mode (e.g. unconditional 10Hz, 5Hz, 2Hz)
            val prevTime = lastEvaluatedTimeMillis
            if (prevTime == null || (currentTimeMillis - prevTime) >= rate.intervalMillis) {
                recordState(currentTimeMillis, feature, pose, maneuverContext, currentStale)
                return SchedulerDecision(true, SchedulerTriggerReason.PERIODIC_TIMER_ELAPSED, rate)
            }
            return SchedulerDecision(false, SchedulerTriggerReason.SKIPPED_INTERVAL_NOT_ELAPSED, rate)
        }
    }

    private fun recordState(
        currentTimeMillis: Long,
        feature: RiskFeature,
        pose: VehiclePose,
        maneuverContext: String,
        currentStale: Boolean
    ) {
        lastEvaluatedTimeMillis = currentTimeMillis
        lastFeatureId = feature.featureId
        lastLocalX = pose.localX
        lastLocalY = pose.localY
        lastHeadingRad = pose.headingRadians
        lastManeuver = maneuverContext
        lastQuality = pose.quality
        lastStaleState = currentStale
    }

    companion object {
        fun angularDifferenceRadians(a: Double, b: Double): Double {
            var diff = (a - b) % (2.0 * PI)
            if (diff < -PI) diff += 2.0 * PI
            if (diff > PI) diff -= 2.0 * PI
            return abs(diff)
        }
    }
}
