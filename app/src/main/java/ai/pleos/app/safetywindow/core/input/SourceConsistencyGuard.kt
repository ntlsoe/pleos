package ai.pleos.app.safetywindow.core.input

import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.shadow.EffectiveCandidateLevel
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality

data class SourceConsistencyCheckResult(
    val isConsistent: Boolean,
    val canApplyLiveAdvisory: Boolean,
    val maxAllowedEffectiveLevel: EffectiveCandidateLevel?,
    val hasScenarioSourceWhenRequired: Boolean,
    val hasStaticProfileWhenRequired: Boolean,
    val hasUnknownSource: Boolean,
    val isRealInputBlockedByScenarioSpatial: Boolean,
    val reasonCodes: List<String>
) {
    val violationCodes: List<String> get() = reasonCodes
}

/**
 * Validates input provenance consistency and enforces live-integration isolation rules (Section 4.3).
 */
object SourceConsistencyGuard {

    fun check(
        executionMode: ExecutionMode,
        provenance: InputProvenance,
        feature: RiskFeature,
        usesSyntheticVehicleOrEyeProfile: Boolean = true
    ): SourceConsistencyCheckResult {
        val reasons = mutableListOf<String>()
        val sources = provenance.inputSources

        val isSyntheticSpatial = feature.coordinateQuality == SpatialQuality.SCENARIO ||
            feature.coordinateQuality == SpatialQuality.SYNTHETIC_SCENARIO ||
            feature.source.contains("SYNTHETIC", ignoreCase = true) ||
            feature.source.contains("SCENARIO", ignoreCase = true) ||
            provenance.spatialFeatureSource == InputSource.SCENARIO

        // Rule 1: SCENARIO spatial feature -> inputSources MUST contain SCENARIO
        val hasScenarioWhenRequired = if (isSyntheticSpatial) {
            val present = sources.contains(InputSource.SCENARIO)
            if (!present) {
                reasons.add("VIOLATION_MISSING_SCENARIO_SOURCE_FOR_SYNTHETIC_FEATURE")
            }
            present
        } else {
            true
        }

        // Rule 2: REAL_INPUT mode + SCENARIO spatial feature -> live advisory strictly forbidden
        val isRealInputBlocked = (executionMode == ExecutionMode.REAL_INPUT && (isSyntheticSpatial || sources.contains(InputSource.SCENARIO)))
        if (isRealInputBlocked) {
            reasons.add("REAL_INPUT_WITH_SCENARIO_SPATIAL_BLOCKED")
        }

        // Rule 3: UNKNOWN source present -> Effective Level capped at L0
        val hasUnknown = provenance.hasUnknownSource() || feature.source.equals("UNKNOWN", ignoreCase = true)
        if (hasUnknown) {
            reasons.add("UNKNOWN_SOURCE_CAPPED_AT_L0")
        }

        // Rule 4: Synthetic vehicle/eye profile -> STATIC_PROFILE MUST be present in inputSources
        val hasStaticProfileWhenRequired = if (usesSyntheticVehicleOrEyeProfile) {
            val present = sources.contains(InputSource.STATIC_PROFILE)
            if (!present) {
                reasons.add("VIOLATION_MISSING_STATIC_PROFILE_SOURCE")
            }
            present
        } else {
            true
        }

        val isConsistent = hasScenarioWhenRequired && hasStaticProfileWhenRequired
        val canApplyLive = isConsistent && !isRealInputBlocked && !hasUnknown
        val maxLevel = if (hasUnknown || !isConsistent) EffectiveCandidateLevel.L0 else null

        if (reasons.isEmpty()) {
            reasons.add("SOURCE_CONSISTENCY_OK")
        }

        return SourceConsistencyCheckResult(
            isConsistent = isConsistent && !isRealInputBlocked,
            canApplyLiveAdvisory = canApplyLive,
            maxAllowedEffectiveLevel = maxLevel,
            hasScenarioSourceWhenRequired = hasScenarioWhenRequired,
            hasStaticProfileWhenRequired = hasStaticProfileWhenRequired,
            hasUnknownSource = hasUnknown,
            isRealInputBlockedByScenarioSpatial = isRealInputBlocked,
            reasonCodes = reasons
        )
    }

    fun normalizeProvenanceForFeature(
        baseProvenance: InputProvenance,
        feature: RiskFeature,
        hasSyntheticTrajectoryOrOverride: Boolean = true
    ): InputProvenance {
        val isSyntheticSpatial = feature.coordinateQuality == SpatialQuality.SCENARIO ||
            feature.coordinateQuality == SpatialQuality.SYNTHETIC_SCENARIO ||
            feature.source.contains("SYNTHETIC", ignoreCase = true) ||
            feature.source.contains("SCENARIO", ignoreCase = true)

        return if (isSyntheticSpatial || hasSyntheticTrajectoryOrOverride) {
            baseProvenance.copy(
                spatialFeatureSource = InputSource.SCENARIO,
                includeExternalGeoJsonTag = InputProvenance.isExternalGeoJsonFeature(feature)
            )
        } else {
            baseProvenance
        }
    }
}
