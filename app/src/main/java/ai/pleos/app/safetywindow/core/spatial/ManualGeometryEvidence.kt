package ai.pleos.app.safetywindow.core.spatial

import java.io.File

/**
 * SpatialFeatureQuality
 * [작업명령 #8] 섹션 9: 공간 데이터 품질 등급
 */
enum class SpatialFeatureQuality {
    SYNTHETIC_SCENARIO,              // 합성 시나리오 좌표
    MANUALLY_VERIFIED_MAP_GEOMETRY,  // 현장/항공사진 수동 검증 지도 형상 (증거파일 필수)
    PUBLIC_DATA_MANUALLY_CORRECTED,  // 공공데이터 좌표 수동 보정본
    PUBLIC_POINT_DIRECTION_UNKNOWN   // 공공데이터 방향 미상 원점
}

/**
 * ManualGeometryEvidence
 * [작업명령 #8] 섹션 9: 수동 검증 지도 Feature 메타데이터 규격
 */
data class ManualGeometryEvidence(
    val featureId: String,
    val sourceName: String,
    val sourceRecordId: String,
    val originalCoordinates: String,       // e.g. "37.5665, 126.9780"
    val correctedCoordinates: String,      // e.g. "37.56652, 126.97805"
    val widthMeters: Double,
    val lengthMeters: Double,
    val directionDegrees: Double,
    val directionMethod: String,           // e.g. "ROAD_CENTERLINE_TANGENT"
    val verificationMethod: String,        // e.g. "SATELLITE_AERIAL_ORTHOPHOTO"
    val verifiedAt: String,                // ISO-8601
    val verifiedBy: String,
    val evidenceFile: String?,             // 증거 파일 경로 (상대경로 또는 절대경로)
    val coordinateQuality: SpatialFeatureQuality,
    val license: String,
    val notes: String = ""
) {
    /**
     * 품질 승격 유효성 검증:
     * evidenceFile이 존재하지 않거나 실제 디스크에 파일이 없으면
     * MANUALLY_VERIFIED_MAP_GEOMETRY 등급을 가질 수 없으며,
     * 강제로 SYNTHETIC_SCENARIO 또는 PUBLIC_DATA_MANUALLY_CORRECTED로 강등된다.
     */
    fun validateQualityEligibility(workspaceRoot: File? = null): SpatialFeatureQuality {
        if (coordinateQuality == SpatialFeatureQuality.MANUALLY_VERIFIED_MAP_GEOMETRY) {
            if (evidenceFile.isNullOrBlank()) {
                return SpatialFeatureQuality.SYNTHETIC_SCENARIO
            }
            if (workspaceRoot != null) {
                val f = File(workspaceRoot, evidenceFile)
                if (!f.exists()) {
                    return SpatialFeatureQuality.SYNTHETIC_SCENARIO
                }
            }
        }
        return coordinateQuality
    }
}
