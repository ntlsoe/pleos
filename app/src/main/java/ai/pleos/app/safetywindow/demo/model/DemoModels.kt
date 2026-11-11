package ai.pleos.app.safetywindow.demo.model

/**
 * DemoExecutionMode
 *
 * Execution modes distinguishing real automotive input from scenario / replay and demo integration.
 */
enum class DemoExecutionMode {
    REAL_INPUT,
    SCENARIO_INPUT,
    REPLAY_INPUT,
    PARKED_VALIDATION,
    DEMO_INTEGRATION
}

enum class DemoLevel {
    L0,
    L1,
    L2,
    L3,
    NONE_DATA_INVALID
}

enum class DemoDirection {
    LEFT,
    RIGHT,
    BOTH,
    AROUND,
    NONE
}

enum class GeometryAssessment {
    NO_OCCLUSION,
    POSSIBLE_OCCLUSION,
    MEANINGFUL_OCCLUSION,
    STRONG_OCCLUSION,
    INDETERMINATE
}

enum class AdvisoryEligibility {
    ELIGIBLE,
    ELIGIBLE_INFORMATIONAL_PREARM,
    INELIGIBLE_STALE_INPUT,
    INELIGIBLE_LOW_SPATIAL_QUALITY,
    INELIGIBLE_UNKNOWN_DIRECTION,
    INELIGIBLE_OPPOSITE_SIDE,
    INELIGIBLE_INSUFFICIENT_LEAD_TIME,
    INELIGIBLE_POLICY_RESTRICTION,
    INELIGIBLE_UNSUPPORTED_CONTEXT,
    INELIGIBLE_SYNTHETIC,
    INELIGIBLE_USER_DISABLED
}

enum class RiskFeatureType {
    CROSSWALK,
    FOUR_WAY_INTERSECTION,
    T_JUNCTION,
    CURVED_ROAD,
    NARROW_ROAD_EXIT,
    SCHOOL_GATE,
    ROAD_EDGE,
    CUSTOM_RISK_SURFACE
}

enum class VisualClass {
    VISIBLE,
    POSSIBLE,
    MEANINGFUL,
    STRONG
}

enum class DemoCamera {
    EXTERNAL_CHASE,
    DRIVER_EYE,
    TOP_DOWN,
    FREE
}

data class Point3D(
    val x: Double,
    val y: Double,
    val z: Double
)

data class VehiclePose3D(
    val position: Point3D,
    val headingRadians: Double,
    val speedMps: Double
)

data class ColoredSample(
    val point: Point3D,
    val visualClass: VisualClass,
    val weight: Double
)

data class RiskSurfaceVisual(
    val id: String,
    val type: RiskFeatureType,
    val vertices: List<Point3D>,
    val quality: String
)

data class VehicleVisualProfile(
    val id: String,
    val label: String,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val wheelbaseM: Double,
    val evidenceLabel: String
)

data class VisibilityVolume(
    val id: String,
    val vertices: List<Point3D>,
    val visualClass: VisualClass,
    val alpha: Float
)

data class DemoAdvisoryState(
    val rawLevel: DemoLevel,
    val stabilizedLevel: DemoLevel,
    val geometryAssessment: GeometryAssessment,
    val eligibility: AdvisoryEligibility,
    val direction: DemoDirection,
    val message: String,
    val outputSuppressed: Boolean,
    val suppressionReason: String?,
    val ttsRequested: Boolean,
    val ttsPermitted: Boolean,
    val ttsSpoken: Boolean,
    val visible: Boolean
)

data class DemoMetrics(
    val peakPossibleRatio: Double,
    val peakUniversalRatio: Double,
    val maxDurationMillis: Long,
    val exposureIntegral: Double,
    val distanceToFeatureM: Double?,
    val estimatedTimeToFeatureS: Double?,
    val computeMillis: Double
)

data class DemoSceneState(
    val sessionId: String,
    val simulationTimeMillis: Long,
    val executionMode: DemoExecutionMode,
    val scenarioId: String,
    val zoneId: String?,
    val batchRunId: String?,
    val vehiclePose: VehiclePose3D,
    val vehicleProfile: VehicleVisualProfile,
    val trajectory: List<Point3D>,
    val pillarMeshes: List<List<Point3D>>,
    val eyeSamples: List<Point3D>,
    val riskSurfaces: List<RiskSurfaceVisual>,
    val visibilityVolumes: List<VisibilityVolume>,
    val occlusionSamples: List<ColoredSample>,
    val advisory: DemoAdvisoryState,
    val metrics: DemoMetrics,
    val dataQualityLabel: String,
    val virtualEnvironmentBadge: String = "가상환경 시연"
)
