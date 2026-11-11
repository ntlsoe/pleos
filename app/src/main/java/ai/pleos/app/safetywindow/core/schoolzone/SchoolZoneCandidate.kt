package ai.pleos.app.safetywindow.core.schoolzone

import org.json.JSONObject

/**
 * SchoolZoneCandidate
 * [작업명령 #12] 섹션 4 & 7: 전국 어린이보호구역 표준데이터 기반 후보 모델
 *
 * 엄격한 품질 상태:
 * geometryQuality = PUBLIC_POINT_DIRECTION_UNKNOWN
 * crosswalkMatchStatus = NOT_MATCHED
 * polygonStatus = NOT_BUILT
 * eligibleForOcclusionBatch = false
 * manualVerificationRequired = true
 */
enum class QualityGrade {
    GRADE_A, // 공공데이터+지도/항공사진+측정 오버레이+증거 해시
    GRADE_B, // 공공데이터+지도 형상 확인+방향·Polygon 검증
    GRADE_C, // 보호구역 중심점만 확인 (현재 100곳 후보)
    GRADE_D, // 좌표 또는 출처 불명확
    FAIL_CLOSED // 방향·Polygon·출처 중 하나라도 누락 시 L0 차단
}

enum class GeometryQuality {
    PUBLIC_POINT_DIRECTION_UNKNOWN,
    VERIFIED_POINT_AND_POLYGON,
    MANUAL_GEOMETRY_VERIFIED
}

enum class CrosswalkMatchStatus {
    NOT_MATCHED,
    CANDIDATE_FOUND,
    AMBIGUOUS_MULTIPLE,
    VERIFIED_MATCHED
}

enum class PolygonStatus {
    NOT_BUILT,
    PROPOSED,
    VERIFIED_POLYGON
}

data class SchoolZoneCandidate(
    val zoneId: String,
    val facilityType: String,
    val facilityName: String,
    val roadAddress: String,
    val latitude: Double,
    val longitude: Double,
    val managementInstitution: String,
    val protectionRoadWidthRaw: String,
    val protectionRoadWidthMeters: Double? = null,
    val referenceDate: String,
    val regionGroup: String,
    val selectionStage: String = "CANDIDATE_100",
    val geometryQuality: String = "PUBLIC_POINT_DIRECTION_UNKNOWN",
    val crosswalkMatchStatus: String = "NOT_MATCHED",
    val directionDegrees: Double? = null,
    val polygonStatus: String = "NOT_BUILT",
    val eligibleForOcclusionBatch: Boolean = false,
    val manualVerificationRequired: Boolean = true,
    val sourceUrl: String,
    val crosswalkSourceUrl: String,
    val notes: String = ""
) {
    fun toGeoJsonObject(): JSONObject {
        val feature = JSONObject()
        feature.put("type", "Feature")
        feature.put("id", zoneId)

        val geometry = JSONObject()
        geometry.put("type", "Point")
        val coords = org.json.JSONArray()
        coords.put(longitude)
        coords.put(latitude)
        geometry.put("coordinates", coords)
        feature.put("geometry", geometry)

        val props = JSONObject()
        props.put("zoneId", zoneId)
        props.put("facilityType", facilityType)
        props.put("facilityName", facilityName)
        props.put("roadAddress", roadAddress)
        props.put("managementInstitution", managementInstitution)
        props.put("protectionRoadWidthRaw", protectionRoadWidthRaw)
        props.put("referenceDate", referenceDate)
        props.put("regionGroup", regionGroup)
        props.put("selectionStage", selectionStage)
        props.put("geometryQuality", geometryQuality)
        props.put("crosswalkMatchStatus", crosswalkMatchStatus)
        if (directionDegrees != null) {
            props.put("directionDegrees", directionDegrees)
        } else {
            props.put("directionDegrees", JSONObject.NULL)
        }
        props.put("polygonStatus", polygonStatus)
        props.put("eligibleForOcclusionBatch", eligibleForOcclusionBatch)
        props.put("manualVerificationRequired", manualVerificationRequired)
        props.put("sourceUrl", sourceUrl)
        props.put("crosswalkSourceUrl", crosswalkSourceUrl)
        props.put("notes", notes)
        feature.put("properties", props)

        return feature
    }

    companion object {
        fun fromGeoJsonFeature(feature: JSONObject): SchoolZoneCandidate {
            val geometry = feature.getJSONObject("geometry")
            require(geometry.getString("type") == "Point") { "Geometry must be Point" }
            val coords = geometry.getJSONArray("coordinates")
            val lon = coords.getDouble(0)
            val lat = coords.getDouble(1)

            val props = feature.getJSONObject("properties")
            val widthRaw = props.optString("protectionRoadWidthRaw", "")
            val parsedWidth = parseWidth(widthRaw)

            val dirVal = if (props.isNull("directionDegrees")) null else props.optDouble("directionDegrees")

            return SchoolZoneCandidate(
                zoneId = props.getString("zoneId"),
                facilityType = props.getString("facilityType"),
                facilityName = props.getString("facilityName"),
                roadAddress = props.getString("roadAddress"),
                latitude = lat,
                longitude = lon,
                managementInstitution = props.getString("managementInstitution"),
                protectionRoadWidthRaw = widthRaw,
                protectionRoadWidthMeters = parsedWidth,
                referenceDate = props.getString("referenceDate"),
                regionGroup = props.getString("regionGroup"),
                selectionStage = props.optString("selectionStage", "CANDIDATE_100"),
                geometryQuality = props.optString("geometryQuality", "PUBLIC_POINT_DIRECTION_UNKNOWN"),
                crosswalkMatchStatus = props.optString("crosswalkMatchStatus", "NOT_MATCHED"),
                directionDegrees = dirVal,
                polygonStatus = props.optString("polygonStatus", "NOT_BUILT"),
                eligibleForOcclusionBatch = props.optBoolean("eligibleForOcclusionBatch", false),
                manualVerificationRequired = props.optBoolean("manualVerificationRequired", true),
                sourceUrl = props.getString("sourceUrl"),
                crosswalkSourceUrl = props.getString("crosswalkSourceUrl"),
                notes = props.optString("notes", "")
            )
        }

        fun parseWidth(raw: String): Double? {
            if (raw.isBlank()) return null
            val clean = raw.trim()
            if (clean.contains("~")) {
                val parts = clean.split("~")
                val low = parts[0].trim().toDoubleOrNull()
                val high = parts[1].trim().toDoubleOrNull()
                if (low != null && high != null) return (low + high) / 2.0
            }
            return clean.toDoubleOrNull()
        }
    }
}
