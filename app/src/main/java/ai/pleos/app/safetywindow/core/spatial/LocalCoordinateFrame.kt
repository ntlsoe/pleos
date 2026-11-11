package ai.pleos.app.safetywindow.core.spatial

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

class LocalCoordinateFrame(
    val origin: GeoPoint
) {
    companion object {
        const val WGS84_A = 6378137.0
        const val WGS84_F = 1.0 / 298.257223563
        val WGS84_E2 = 2.0 * WGS84_F - WGS84_F * WGS84_F
    }

    private val sinPhi: Double
    private val cosPhi: Double
    private val nRad: Double
    private val mRad: Double

    init {
        val phi = Math.toRadians(origin.latitude)
        sinPhi = sin(phi)
        cosPhi = cos(phi)
        val denom = sqrt(1.0 - WGS84_E2 * sinPhi * sinPhi)
        nRad = WGS84_A / denom
        mRad = WGS84_A * (1.0 - WGS84_E2) / denom.pow(3.0)
    }

    /**
     * Converts a geodetic point (WGS84) to Local ENU coordinates in meters.
     * Throws IllegalArgumentException for polar regions (|lat| > 85 deg) or antimeridian crossings.
     */
    fun geodeticToEnu(pt: GeoPoint): Triple<Double, Double, Double> {
        require(Math.abs(pt.latitude) <= 85.0) {
            "Polar regions (|latitude| > 85 deg) are excluded from MVP LocalCoordinateFrame"
        }
        require(Math.abs(pt.longitude - origin.longitude) <= 180.0) {
            "Antimeridian crossings are excluded from MVP LocalCoordinateFrame"
        }

        val dLatRad = Math.toRadians(pt.latitude - origin.latitude)
        val dLonRad = Math.toRadians(pt.longitude - origin.longitude)

        val north = dLatRad * (mRad + origin.altitudeMeters)
        val east = dLonRad * (nRad + origin.altitudeMeters) * cosPhi
        val up = pt.altitudeMeters - origin.altitudeMeters

        return Triple(east, north, up)
    }

    /**
     * Converts Local ENU coordinates in meters back to geodetic WGS84 coordinates.
     */
    fun enuToGeodetic(east: Double, north: Double, up: Double = 0.0): GeoPoint {
        val dLatRad = north / (mRad + origin.altitudeMeters)
        val dLonRad = east / ((nRad + origin.altitudeMeters) * cosPhi)

        val lat = origin.latitude + Math.toDegrees(dLatRad)
        val lon = origin.longitude + Math.toDegrees(dLonRad)
        val alt = origin.altitudeMeters + up

        return GeoPoint(lat, lon, alt)
    }
}
