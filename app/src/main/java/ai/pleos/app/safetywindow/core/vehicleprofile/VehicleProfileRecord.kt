package ai.pleos.app.safetywindow.core.vehicleprofile

import org.json.JSONObject

/**
 * VehicleProfileRecord
 * [작업명령 #12] 섹션 3 & 6: 공개 제원 기반 차량 5종 프로파일 데이터 모델
 *
 * 엄격한 증거 수준: PUBLIC_DIMENSION_ONLY
 * 외형 제원(전장·전폭·전고·축거)만 공개 제원에 근거하며,
 * A필러 3D 꼭짓점 및 운전자 눈 위치는 합성 근사(SYNTHETIC_APPROXIMATION)로 유지됨.
 */
enum class VehicleVerificationLevel {
    PUBLIC_DIMENSION_ONLY,
    MANUAL_GEOMETRY,
    OEM_OFFICIAL_CAD,
    UNKNOWN
}

enum class PillarGeometryStatus {
    SYNTHETIC_APPROXIMATION,
    MANUAL_GEOMETRY,
    OEM_EXACT
}

enum class EyeModelStatus {
    REPRESENTATIVE_POPULATION_APPROXIMATION,
    MEASURED_DRIVER_MODEL
}

enum class SteeringSide {
    LEFT_HAND_DRIVE,
    RIGHT_HAND_DRIVE
}

data class VehicleProfileRecord(
    val profileId: String,
    val modelName: String,
    val modelYear: String,
    val bodyType: String,
    val lengthMm: Int,
    val widthMm: Int,
    val heightMm: Int,
    val wheelbaseMm: Int,
    val steeringSide: SteeringSide = SteeringSide.LEFT_HAND_DRIVE,
    val verificationLevel: VehicleVerificationLevel = VehicleVerificationLevel.PUBLIC_DIMENSION_ONLY,
    val pillarGeometryStatus: PillarGeometryStatus = PillarGeometryStatus.SYNTHETIC_APPROXIMATION,
    val eyeModelStatus: EyeModelStatus = EyeModelStatus.REPRESENTATIVE_POPULATION_APPROXIMATION,
    val sourceUrl: String,
    val limitations: String,
    val pillarVertices: List<Double>? = null,
    val pillarEffectiveWidthMm: Double? = null,
    val windshieldAngleDeg: Double? = null,
    val seatReferencePoint: List<Double>? = null
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("profileId", profileId)
        obj.put("modelName", modelName)
        obj.put("modelYear", modelYear)
        obj.put("bodyType", bodyType)
        obj.put("lengthMm", lengthMm)
        obj.put("widthMm", widthMm)
        obj.put("heightMm", heightMm)
        obj.put("wheelbaseMm", wheelbaseMm)
        obj.put("steeringSide", steeringSide.name)
        obj.put("verificationLevel", verificationLevel.name)
        obj.put("pillarGeometryStatus", pillarGeometryStatus.name)
        obj.put("eyeModelStatus", eyeModelStatus.name)
        obj.put("sourceUrl", sourceUrl)
        obj.put("limitations", limitations)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): VehicleProfileRecord {
            val steeringStr = obj.optString("steeringSide", "LEFT_HAND_DRIVE")
            val steering = try {
                SteeringSide.valueOf(steeringStr)
            } catch (e: Exception) {
                SteeringSide.LEFT_HAND_DRIVE
            }

            val verLevelStr = obj.optString("verificationLevel", "PUBLIC_DIMENSION_ONLY")
            val verLevel = try {
                VehicleVerificationLevel.valueOf(verLevelStr)
            } catch (e: Exception) {
                VehicleVerificationLevel.PUBLIC_DIMENSION_ONLY
            }

            val pillarStr = obj.optString("pillarGeometryStatus", "SYNTHETIC_APPROXIMATION")
            val pillar = try {
                PillarGeometryStatus.valueOf(pillarStr)
            } catch (e: Exception) {
                PillarGeometryStatus.SYNTHETIC_APPROXIMATION
            }

            val eyeStr = obj.optString("eyeModelStatus", "REPRESENTATIVE_POPULATION_APPROXIMATION")
            val eye = try {
                EyeModelStatus.valueOf(eyeStr)
            } catch (e: Exception) {
                EyeModelStatus.REPRESENTATIVE_POPULATION_APPROXIMATION
            }

            return VehicleProfileRecord(
                profileId = obj.getString("profileId"),
                modelName = obj.getString("modelName"),
                modelYear = obj.getString("modelYear"),
                bodyType = obj.getString("bodyType"),
                lengthMm = obj.getInt("lengthMm"),
                widthMm = obj.getInt("widthMm"),
                heightMm = obj.getInt("heightMm"),
                wheelbaseMm = obj.getInt("wheelbaseMm"),
                steeringSide = steering,
                verificationLevel = verLevel,
                pillarGeometryStatus = pillar,
                eyeModelStatus = eye,
                sourceUrl = obj.getString("sourceUrl"),
                limitations = obj.optString("limitations", "")
            )
        }
    }
}
