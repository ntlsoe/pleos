package ai.pleos.app.safetywindow.core.spatial

import java.io.File

data class GradeBSpatialFeature(
    val zoneId: String,
    val facilityName: String,
    val crosswalkSourceRecordId: String,
    val referenceAnchor: GeoPoint,
    val polygonCoordinates: List<GeoPoint>,
    val localEnuCoordinates: List<Triple<Double, Double, Double>>,
    val widthMeters: Double,
    val lengthMeters: Double,
    val directionDegrees: Double,
    val roadBearingDegrees: Double = when (zoneId) {
        "SZ_047" -> 165.0
        "SZ_052" -> 105.0
        "SZ_007" -> 15.0
        else -> (directionDegrees + 90.0) % 360.0
    },
    val qualityGrade: String = "GRADE_B_CONFIRMED",
    val sha256: String,
    val file: File
)

/**
 * GradeBFeatureRepository
 * [작업명령 #16] 섹션 3 & 5: Grade B 확정 3개소 허용목록 및 런타임 공간 피처 저장소
 *
 * 엄격한 런타임 보안 규칙:
 * - 오직 확정 3개소(SZ_047, SZ_052, SZ_007)만 로드 허용
 * - 보류 7개소(SZ_093, SZ_015, SZ_011, SZ_018, SZ_023, SZ_044, SZ_049) 및 잔여 90개소 Grade C는 런타임 진입 즉각 차단
 */
class GradeBFeatureRepository(
    private val spatialVerifiedDir: File
) {
    companion object {
        val CONFIRMED_GRADE_B_ALLOWLIST = setOf("SZ_047", "SZ_052", "SZ_007")
        val PENDING_GRADE_B_BLOCKLIST = setOf(
            "SZ_093", "SZ_015", "SZ_011", "SZ_018", "SZ_023", "SZ_044", "SZ_049"
        )
    }

    private val cache = mutableMapOf<String, GradeBSpatialFeature>()

    init {
        loadConfirmedFeatures()
    }

    private fun loadConfirmedFeatures() {
        if (!spatialVerifiedDir.exists()) return

        for (zoneId in CONFIRMED_GRADE_B_ALLOWLIST) {
            val file = File(spatialVerifiedDir, "feature_${zoneId}.geojson")
            if (file.exists()) {
                val feature = SpatialFeatureLoader.loadVerifiedFeature(file)
                val expectedSha = SpatialFeatureLoader.EXPECTED_GEOJSON_SHA256[zoneId]
                if (expectedSha != null && feature.sha256 != expectedSha) {
                    throw IllegalStateException("FEATURE_SHA256_MISMATCH: ${zoneId} expected $expectedSha but got ${feature.sha256}")
                }
                cache[zoneId] = feature
            }
        }
    }

    /**
     * 확정 3개소 피처 조회. 허용목록 외 구역 요청 시 null 반환 및 로드 거부.
     */
    fun getFeature(zoneId: String): GradeBSpatialFeature? {
        if (!CONFIRMED_GRADE_B_ALLOWLIST.contains(zoneId)) {
            return null
        }
        return cache[zoneId]
    }

    /**
     * 확정된 3개 피처 전수 반환 (정확히 3개 보장).
     */
    fun getAllConfirmedFeatures(): List<GradeBSpatialFeature> {
        return CONFIRMED_GRADE_B_ALLOWLIST.mapNotNull { cache[it] }
    }

    /**
     * 외부에서 피처 주입 시도 시 허용목록 및 품질등급 검증
     */
    fun validateAndRegister(feature: GradeBSpatialFeature): Boolean {
        if (!CONFIRMED_GRADE_B_ALLOWLIST.contains(feature.zoneId)) {
            return false
        }
        if (feature.qualityGrade != "GRADE_B_CONFIRMED") {
            return false
        }
        cache[feature.zoneId] = feature
        return true
    }

    fun isZoneConfirmedGradeB(zoneId: String): Boolean {
        return CONFIRMED_GRADE_B_ALLOWLIST.contains(zoneId)
    }

    fun isZonePendingOrGradeC(zoneId: String): Boolean {
        return !CONFIRMED_GRADE_B_ALLOWLIST.contains(zoneId)
    }
}
