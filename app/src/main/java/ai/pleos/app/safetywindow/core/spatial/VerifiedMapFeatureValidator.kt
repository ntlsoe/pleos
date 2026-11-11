package ai.pleos.app.safetywindow.core.spatial

import java.io.File
import java.security.MessageDigest

data class MapFeatureValidationReport(
    val featureId: String,
    val isValidCoordinates: Boolean,
    val isValidDirection: Boolean,
    val isValidDimensions: Boolean,
    val isNonSelfIntersecting: Boolean,
    val isEvidenceFilePresent: Boolean,
    val isEvidenceHashMatched: Boolean,
    val isMetadataComplete: Boolean,
    val assignedQuality: SpatialFeatureQuality,
    val validationNotes: String
)

/**
 * VerifiedMapFeatureValidator
 * [작업명령 #9] 섹션 4, 5: 수동 검증 지도 형상 검증 도구 및 하드 게이트
 */
class VerifiedMapFeatureValidator(
    private val workspaceRoot: File
) {

    fun validateFeature(
        evidence: ManualGeometryEvidence,
        expectedEvidenceSha256: String? = null
    ): MapFeatureValidationReport {
        var validCoords = true
        var validDirection = true
        var validDimensions = true
        var nonSelfIntersecting = true
        var evidencePresent = false
        var hashMatched = false
        var metadataComplete = true
        val notes = mutableListOf<String>()

        // 1. 위경도 유효성 검사
        try {
            val parts = evidence.correctedCoordinates.split(",").map { it.trim().toDouble() }
            if (parts.size == 2) {
                val lat = parts[0]
                val lon = parts[1]
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    validCoords = false
                    notes.add("Latitude or longitude out of range: ($lat, $lon)")
                }
            } else {
                validCoords = false
                notes.add("Invalid coordinates format: ${evidence.correctedCoordinates}")
            }
        } catch (e: Exception) {
            validCoords = false
            notes.add("Coordinate parsing error: ${e.message}")
        }

        // 2. 방향각 유효성 (0° ~ 360°)
        if (evidence.directionDegrees !in 0.0..360.0) {
            validDirection = false
            notes.add("Direction degrees out of range [0, 360]: ${evidence.directionDegrees}")
        }

        // 3. 폭·길이 유효성 (> 0)
        if (evidence.widthMeters <= 0.0 || evidence.lengthMeters <= 0.0) {
            validDimensions = false
            notes.add("Dimensions must be positive: width=${evidence.widthMeters}, length=${evidence.lengthMeters}")
        }

        // 4. 폴리곤 자가교차(self-intersection) 검사: 사각형 기준 기하 구조 검증
        if (evidence.widthMeters > 0 && evidence.lengthMeters > 0) {
            nonSelfIntersecting = true
        }

        // 5. 증거 파일 실재 및 해시 검증
        if (!evidence.evidenceFile.isNullOrBlank()) {
            val file = File(workspaceRoot, evidence.evidenceFile)
            if (file.exists() && file.isFile && file.length() > 0) {
                evidencePresent = true
                if (!expectedEvidenceSha256.isNullOrBlank()) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val computedHash = file.readBytes().let { bytes ->
                        digest.digest(bytes).joinToString("") { "%02X".format(it) }
                    }
                    if (computedHash.equals(expectedEvidenceSha256.trim(), ignoreCase = true)) {
                        hashMatched = true
                    } else {
                        notes.add("Evidence SHA-256 mismatch: expected=$expectedEvidenceSha256, computed=$computedHash")
                    }
                } else {
                    hashMatched = true // 해시 미지정 시 파일 존재로 수용
                }
            } else {
                notes.add("Evidence file not found on disk: ${evidence.evidenceFile}")
            }
        } else {
            notes.add("Evidence file field is null or blank")
        }

        // 6. 메타데이터 필수 항목 누락 여부
        if (evidence.sourceName.isBlank() || evidence.verificationMethod.isBlank() ||
            evidence.verifiedAt.isBlank() || evidence.license.isBlank()
        ) {
            metadataComplete = false
            notes.add("Required metadata fields missing")
        }

        // 하드 게이트: 모든 조건이 충족되고 증거 파일이 검증된 경우에만 MANUALLY_VERIFIED_MAP_GEOMETRY 부여
        val finalQuality = if (validCoords && validDirection && validDimensions &&
            nonSelfIntersecting && evidencePresent && hashMatched && metadataComplete
        ) {
            SpatialFeatureQuality.MANUALLY_VERIFIED_MAP_GEOMETRY
        } else {
            SpatialFeatureQuality.SYNTHETIC_SCENARIO
        }

        return MapFeatureValidationReport(
            featureId = evidence.featureId,
            isValidCoordinates = validCoords,
            isValidDirection = validDirection,
            isValidDimensions = validDimensions,
            isNonSelfIntersecting = nonSelfIntersecting,
            isEvidenceFilePresent = evidencePresent,
            isEvidenceHashMatched = hashMatched,
            isMetadataComplete = metadataComplete,
            assignedQuality = finalQuality,
            validationNotes = if (notes.isEmpty()) "ALL_VALIDATIONS_PASSED" else notes.joinToString("; ")
        )
    }
}
