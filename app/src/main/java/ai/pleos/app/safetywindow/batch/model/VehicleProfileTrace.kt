package ai.pleos.app.safetywindow.batch.model

/**
 * VehicleProfileTrace
 * [작업명령 #23] 섹션 5: 차량 5종 입력 계보 및 파라미터 전달 추적 모델
 */
data class VehicleProfileLineage(
    val vehicleProfileId: String,
    val vehicleName: String,
    val bodyClass: String,
    val lengthMm: Int,
    val widthMm: Int,
    val heightMm: Int,
    val wheelbaseMm: Int,
    val dimensionSourceEvidence: String,
    val syntheticPillarProfileId: String,
    val eyeModelId: String,
    val mappingRuleId: String,
    val mappingReason: String,
    val evidenceClass: String,
    val limitations: String,
    val profileSha256: String
)

data class VehicleParameterTrace(
    val vehicleProfileId: String,
    val eyeX: Double,
    val eyeY: Double,
    val eyeZ: Double,
    val leftEyeOffset: Double,
    val rightEyeOffset: Double,
    val pillarBaseX: Double,
    val pillarBaseY: Double,
    val pillarBaseZ: Double,
    val pillarTopX: Double,
    val pillarTopY: Double,
    val pillarTopZ: Double,
    val pillarWidthM: Double,
    val wheelbaseM: Double,
    val bodyClassScalingFactor: Double,
    val headingPoseTransform: String,
    val relativeDistAngle: String
) {
    fun toIntermediateDigest(): String {
        val payload = "$vehicleProfileId:$eyeX:$eyeY:$eyeZ:$pillarBaseX:$pillarTopX:$pillarWidthM:$wheelbaseM"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
