package ai.pleos.app.safetywindow.core.occlusion

import kotlin.math.roundToInt

data class RiskSamplePoint(
    val position: Point3,
    val weight: Double,
    val spacingMeters: Double
)

enum class RiskSpaceType {
    CROSSWALK,
    SCHOOL_ZONE_ENTRY,
    NARROW_ROAD_JUNCTION,
    INTERSECTION_EDGE,
    CUSTOM_TEST_SURFACE
}

data class RiskSurface(
    val id: String,
    val type: RiskSpaceType,
    val origin: Point3,
    val lengthX: Double,
    val widthY: Double,
    val heightZ: Double = 0.0,
    val source: String = "SYNTHETIC_TEST_ONLY"
) {
    fun samplePoints(spacing: Double = 0.5): List<RiskSamplePoint> {
        require(spacing > 0.0) { "Spacing must be positive" }
        val nx = maxOf(1, (lengthX / spacing).roundToInt())
        val ny = maxOf(1, (widthY / spacing).roundToInt())

        val dx = lengthX / nx
        val dy = widthY / ny

        val totalSamples = nx * ny
        val w = 1.0 / totalSamples

        val xStart = origin.x - lengthX / 2.0
        val yStart = origin.y - widthY / 2.0

        val points = ArrayList<RiskSamplePoint>(totalSamples)
        for (ix in 0 until nx) {
            val px = xStart + (ix + 0.5) * dx
            for (iy in 0 until ny) {
                val py = yStart + (iy + 0.5) * dy
                val pz = origin.z + heightZ
                points.add(RiskSamplePoint(Point3(px, py, pz), w, spacing))
            }
        }
        return points
    }
}
