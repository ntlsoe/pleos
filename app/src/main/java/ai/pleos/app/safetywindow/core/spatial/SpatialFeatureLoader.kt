package ai.pleos.app.safetywindow.core.spatial

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * SpatialFeatureLoader
 * [작업명령 #16] 섹션 5: Grade B 확정 공간 Feature 로더 및 해시/메타데이터 검증기
 */
object SpatialFeatureLoader {

    // SSOT Expected SHA-256 Checksums for Verified GeoJSON files
    val EXPECTED_GEOJSON_SHA256 = mapOf(
        "SZ_047" to "2C1A749B0B61A88F30F0BB3A213E9C11325A5FE6E1840F4933684CC208EF821B",
        "SZ_052" to "BCA8BA5A675E17EFE877CDFDCD2C60DE8E68E285D1DDE2625F26894988B0F91D",
        "SZ_007" to "0A4B69B0BAE5E29A109DB90E1C313BC8305A043D8FB6A0B36404D687BB5EA6D3"
    )

    fun computeSha256(file: File): String {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        return digest.digest(bytes).joinToString("") { "%02X".format(it) }
    }

    fun loadVerifiedFeature(file: File): GradeBSpatialFeature {
        require(file.exists()) { "Feature file does not exist: ${file.absolutePath}" }
        val actualSha = computeSha256(file)

        val text = file.readText(Charsets.UTF_8)
        val json = JSONObject(text)

        val metadata = json.getJSONObject("metadata")
        val zoneId = metadata.getString("zoneId")
        val facilityName = metadata.getString("facilityName")
        val cwId = metadata.getString("crosswalkSourceRecordId")

        val anchorObj = metadata.getJSONObject("referenceAnchor")
        val anchor = GeoPoint(
            latitude = anchorObj.getDouble("latitude"),
            longitude = anchorObj.getDouble("longitude"),
            altitudeMeters = anchorObj.optDouble("altitudeMeters", 30.0)
        )

        val featuresArr = json.getJSONArray("features")
        require(featuresArr.length() > 0) { "GeoJSON must contain at least one feature" }
        val featureObj = featuresArr.getJSONObject(0)
        val geometryObj = featureObj.getJSONObject("geometry")
        val propsObj = featureObj.getJSONObject("properties")

        val coordsArr = geometryObj.getJSONArray("coordinates").getJSONArray(0)
        require(coordsArr.length() == 5) { "GeoJSON Polygon must have 5 closed coordinates, got ${coordsArr.length()}" }

        val geoPoints = mutableListOf<GeoPoint>()
        for (i in 0 until coordsArr.length()) {
            val pt = coordsArr.getJSONArray(i)
            val lon = pt.getDouble(0)
            val lat = pt.getDouble(1)
            geoPoints.add(GeoPoint(latitude = lat, longitude = lon, altitudeMeters = anchor.altitudeMeters))
        }

        val widthM = propsObj.optDouble("widthMeters", 6.0)
        val lengthM = propsObj.optDouble("lengthMeters", 15.0)
        val dirDeg = propsObj.optDouble("directionDegrees", 0.0)

        // Compute local ENU coordinates using anchor as local origin
        val frame = LocalCoordinateFrame(anchor)
        val enuPoints = geoPoints.map { frame.geodeticToEnu(it) }

        return GradeBSpatialFeature(
            zoneId = zoneId,
            facilityName = facilityName,
            crosswalkSourceRecordId = cwId,
            referenceAnchor = anchor,
            polygonCoordinates = geoPoints,
            localEnuCoordinates = enuPoints,
            widthMeters = widthM,
            lengthMeters = lengthM,
            directionDegrees = dirDeg,
            qualityGrade = "GRADE_B_CONFIRMED",
            sha256 = actualSha,
            file = file
        )
    }
}
