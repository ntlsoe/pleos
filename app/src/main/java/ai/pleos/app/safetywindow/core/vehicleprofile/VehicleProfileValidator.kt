package ai.pleos.app.safetywindow.core.vehicleprofile

/**
 * VehicleProfileValidator
 * [작업명령 #12] 섹션 3, 6 & 11: 차량 프로파일 무결성 및 클레임 방어 검증기
 */
object VehicleProfileValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )

    fun validate(record: VehicleProfileRecord, existingProfileIds: Set<String> = emptySet()): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. profileId 검증
        if (record.profileId.isBlank()) {
            errors.add("profileId must not be blank")
        } else if (!record.profileId.endsWith("PUBLIC_DIMENSION_APPROX_V1")) {
            errors.add("profileId '${record.profileId}' must end with 'PUBLIC_DIMENSION_APPROX_V1'")
        }
        if (record.profileId in existingProfileIds) {
            errors.add("Duplicate profileId detected: ${record.profileId}")
        }

        // 2. 외형 제원 양수 및 mm 단위 검증
        if (record.lengthMm <= 0) errors.add("lengthMm must be positive: ${record.lengthMm}")
        if (record.widthMm <= 0) errors.add("widthMm must be positive: ${record.widthMm}")
        if (record.heightMm <= 0) errors.add("heightMm must be positive: ${record.heightMm}")
        if (record.wheelbaseMm <= 0) errors.add("wheelbaseMm must be positive: ${record.wheelbaseMm}")

        // 상식적 차량 치수 범위 검증 (mm 단위)
        if (record.lengthMm !in 2500..6500) errors.add("lengthMm out of valid automotive range (2500~6500mm): ${record.lengthMm}")
        if (record.widthMm !in 1400..2500) errors.add("widthMm out of valid automotive range (1400~2500mm): ${record.widthMm}")
        if (record.heightMm !in 1200..2500) errors.add("heightMm out of valid automotive range (1200~2500mm): ${record.heightMm}")
        if (record.wheelbaseMm !in 1800..4500) errors.add("wheelbaseMm out of valid automotive range (1800~4500mm): ${record.wheelbaseMm}")

        // 3. 증거 수준 강제 (PUBLIC_DIMENSION_ONLY)
        if (record.verificationLevel != VehicleVerificationLevel.PUBLIC_DIMENSION_ONLY) {
            errors.add("Prohibited verificationLevel: ${record.verificationLevel}. Must be PUBLIC_DIMENSION_ONLY")
        }

        // 4. 합성 필러 승격 금지 (OEM/MANUAL_GEOMETRY 승격 차단)
        if (record.pillarGeometryStatus != PillarGeometryStatus.SYNTHETIC_APPROXIMATION) {
            errors.add("Prohibited pillarGeometryStatus: ${record.pillarGeometryStatus}. Must remain SYNTHETIC_APPROXIMATION in v1")
        }

        // 5. 대표 눈 모델 승격 금지 (MEASURED_DRIVER_MODEL 승격 차단)
        if (record.eyeModelStatus != EyeModelStatus.REPRESENTATIVE_POPULATION_APPROXIMATION) {
            errors.add("Prohibited eyeModelStatus: ${record.eyeModelStatus}. Must remain REPRESENTATIVE_POPULATION_APPROXIMATION in v1")
        }

        // 6. 공식 출처 URL 필수 검증
        if (record.sourceUrl.isBlank()) {
            errors.add("sourceUrl must not be blank")
        } else if (!record.sourceUrl.startsWith("http://") && !record.sourceUrl.startsWith("https://")) {
            errors.add("sourceUrl must be a valid HTTP/HTTPS URL: ${record.sourceUrl}")
        }

        // 7. 차종명 및 모델 연도 필수
        if (record.modelName.isBlank()) errors.add("modelName must not be blank")
        if (record.modelYear.isBlank()) errors.add("modelYear must not be blank")
        if (record.bodyType.isBlank()) errors.add("bodyType must not be blank")

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
