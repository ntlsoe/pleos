package ai.pleos.app.safetywindow.core.spatial

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0
)
