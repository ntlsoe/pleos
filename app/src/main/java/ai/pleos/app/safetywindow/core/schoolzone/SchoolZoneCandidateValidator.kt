package ai.pleos.app.safetywindow.core.schoolzone

/**
 * SchoolZoneCandidateValidator
 * [작업명령 #12] 섹션 4 & 7: 어린이보호구역 후보 데이터 무결성 및 가림 차단 검증기
 */
object SchoolZoneCandidateValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )

    fun validate(
        candidate: SchoolZoneCandidate,
        existingZoneIds: Set<String> = emptySet(),
        existingCoordinates: Set<Pair<Double, Double>> = emptySet()
    ): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. zoneId 식별자 검증
        if (candidate.zoneId.isBlank()) {
            errors.add("zoneId must not be blank")
        } else if (!candidate.zoneId.matches(Regex("^SZ_\\d{3,}$"))) {
            errors.add("zoneId '${candidate.zoneId}' must match format SZ_###")
        }
        if (candidate.zoneId in existingZoneIds) {
            errors.add("Duplicate zoneId detected: ${candidate.zoneId}")
        }

        // 2. 대한민국 지리적 위·경도 유효 범위 검증 (WGS84)
        if (candidate.latitude !in 33.0..39.0) {
            errors.add("Latitude out of Korean bounding box (33.0~39.0): ${candidate.latitude}")
        }
        if (candidate.longitude !in 124.0..132.0) {
            errors.add("Longitude out of Korean bounding box (124.0~132.0): ${candidate.longitude}")
        }

        // 좌표 중복 검증
        val coordPair = Pair(candidate.latitude, candidate.longitude)
        if (coordPair in existingCoordinates) {
            errors.add("Duplicate coordinates detected: $coordPair")
        }

        // 3. 필수 메타데이터 누락 검증
        if (candidate.facilityName.isBlank()) errors.add("facilityName must not be blank")
        if (candidate.facilityType.isBlank()) errors.add("facilityType must not be blank")
        if (candidate.roadAddress.isBlank()) errors.add("roadAddress must not be blank")
        if (candidate.managementInstitution.isBlank()) errors.add("managementInstitution must not be blank")
        if (candidate.referenceDate.isBlank()) errors.add("referenceDate must not be blank")
        if (candidate.sourceUrl.isBlank()) errors.add("sourceUrl must not be blank")
        if (candidate.crosswalkSourceUrl.isBlank()) errors.add("crosswalkSourceUrl must not be blank")

        // 4. 기하 품질 및 Fail-Closed 상태 강제 검증
        if (candidate.geometryQuality != "PUBLIC_POINT_DIRECTION_UNKNOWN") {
            errors.add("Invalid geometryQuality: '${candidate.geometryQuality}'. Must be 'PUBLIC_POINT_DIRECTION_UNKNOWN' for v1 candidates")
        }
        if (candidate.polygonStatus != "NOT_BUILT") {
            errors.add("Invalid polygonStatus: '${candidate.polygonStatus}'. Arbitrary polygon expansion is strictly prohibited; must be 'NOT_BUILT'")
        }
        if (candidate.directionDegrees != null) {
            errors.add("directionDegrees must be null (direction not yet verified): ${candidate.directionDegrees}")
        }
        if (candidate.eligibleForOcclusionBatch) {
            errors.add("eligibleForOcclusionBatch must be false for unverified point candidates")
        }
        if (!candidate.manualVerificationRequired) {
            errors.add("manualVerificationRequired must be true for unverified point candidates")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
