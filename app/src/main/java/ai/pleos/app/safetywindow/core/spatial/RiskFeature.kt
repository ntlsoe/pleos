package ai.pleos.app.safetywindow.core.spatial

data class RiskFeature(
    val featureId: String,
    val riskType: String,
    val source: String,
    val geometryMethod: String,
    val manualVerified: Boolean,
    val coordinateQuality: SpatialQuality,
    val widthMeters: Double,
    val lengthMeters: Double,
    val directionDegrees: Double,
    val centerEnu: Triple<Double, Double, Double>,
    val sourceDate: String = "2026-11-07",
    val notes: String = ""
)
