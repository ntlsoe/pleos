package ai.pleos.app.safetywindow.core.spatial

import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.occlusion.Point3
import kotlin.math.cos
import kotlin.math.sin

data class VehiclePose(
    val localX: Double,
    val localY: Double,
    val headingRadians: Double,
    val timestampMillis: Long,
    val source: InputSource,
    val quality: SpatialQuality,
    val speedMps: Double = 0.0
) {
    fun isStale(currentTimeMillis: Long, maxAgeMillis: Long = 1500L): Boolean {
        return (currentTimeMillis - timestampMillis) > maxAgeMillis
    }

    /**
     * Transforms an ENU point (East, North, Up) to ISO 8855-aligned Vehicle Body coordinates:
     *   +X_veh: Forward
     *   +Y_veh: Lateral Left
     *   +Z_veh: Upward
     * Heading is clockwise azimuth from North:
     *   Forward vector in ENU = (sin(h), cos(h), 0)
     *   Left vector in ENU = (-cos(h), sin(h), 0)
     */
    fun enuToVehicleBody(east: Double, north: Double, up: Double = 0.0): Point3 {
        val dx = east - localX
        val dy = north - localY
        val sinH = sin(headingRadians)
        val cosH = cos(headingRadians)

        val xVeh = dx * sinH + dy * cosH
        val yVeh = -dx * cosH + dy * sinH
        val zVeh = up

        return Point3(xVeh, yVeh, zVeh)
    }

    /**
     * Converts to occlusion engine's VehiclePose for trajectory simulation.
     */
    fun toOcclusionEnginePose(timeSec: Double = 0.0): ai.pleos.app.safetywindow.core.occlusion.VehiclePose {
        return ai.pleos.app.safetywindow.core.occlusion.VehiclePose(
            x = localX,
            y = localY,
            heading = headingRadians,
            speedMps = speedMps,
            timeSec = timeSec
        )
    }
}
