package ai.pleos.app.safetywindow.core.spatial

import ai.pleos.app.safetywindow.core.occlusion.Point3
import ai.pleos.app.safetywindow.core.occlusion.RiskSamplePoint
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object RiskFeatureSampler {

    /**
     * Generates sample points on the RiskFeature rectangle in Local ENU.
     */
    fun sampleFeatureInEnu(
        feature: RiskFeature,
        spacing: Double = 0.5
    ): List<Triple<Double, Double, Double>> {
        require(spacing > 0.0) { "Spacing must be positive" }
        val (cx, cy, cz) = feature.centerEnu

        val angRad = Math.toRadians(feature.directionDegrees)
        val cosA = cos(angRad)
        val sinA = sin(angRad)

        val halfW = feature.widthMeters / 2.0
        val halfL = feature.lengthMeters / 2.0

        val nw = maxOf(2, (feature.widthMeters / spacing).roundToInt() + 1)
        val nl = maxOf(2, (feature.lengthMeters / spacing).roundToInt() + 1)

        val dw = (2.0 * halfW) / (nw - 1)
        val dl = (2.0 * halfL) / (nl - 1)

        val pointsEnu = ArrayList<Triple<Double, Double, Double>>(nw * nl)
        for (i in 0 until nw) {
            val w = -halfW + i * dw
            for (j in 0 until nl) {
                val l = -halfL + j * dl
                val rx = cx + (w * cosA - l * sinA)
                val ry = cy + (w * sinA + l * cosA)
                pointsEnu.add(Triple(rx, ry, cz))
            }
        }
        return pointsEnu
    }

    /**
     * Samples points in Local ENU and transforms them into vehicle body frame (+X forward, +Y left, +Z up).
     * Returns null if pose is stale or if spatial quality does not permit shadow computation.
     */
    fun sampleFeatureInVehicleFrame(
        feature: RiskFeature,
        pose: VehiclePose,
        spacing: Double = 0.5,
        currentTimeMillis: Long? = null,
        maxAgeMillis: Long = 1500L
    ): List<RiskSamplePoint>? {
        if (currentTimeMillis != null && pose.isStale(currentTimeMillis, maxAgeMillis)) {
            return null
        }
        if (!pose.quality.canComputeShadow()) {
            return null
        }
        if (!feature.coordinateQuality.canComputeShadow()) {
            return null
        }

        val enuPoints = sampleFeatureInEnu(feature, spacing)
        val totalCount = enuPoints.size
        val w = if (totalCount > 0) 1.0 / totalCount else 1.0

        return enuPoints.map { (e, n, u) ->
            val ptVeh = pose.enuToVehicleBody(e, n, u)
            RiskSamplePoint(
                position = ptVeh,
                weight = w,
                spacingMeters = spacing
            )
        }
    }
}
