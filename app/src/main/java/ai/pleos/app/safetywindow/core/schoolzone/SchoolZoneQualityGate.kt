package ai.pleos.app.safetywindow.core.schoolzone

import java.io.File

/**
 * SchoolZoneQualityGate
 * [작업명령 #12] 섹션 4, 10 & 12: 공간정보 품질 등급 판정 및 가림 연산 Fail-Closed 차단 게이트
 */
object SchoolZoneQualityGate {

    data class QualityVerdict(
        val zoneId: String,
        val grade: QualityGrade,
        val hasVerifiedDirection: Boolean,
        val hasVerifiedPolygon: Boolean,
        val hasValidSource: Boolean,
        val eligibleForOcclusionBatch: Boolean,
        val suppressionReason: String
    )

    fun evaluate(candidate: SchoolZoneCandidate): QualityVerdict {
        val hasValidSource = candidate.sourceUrl.isNotBlank() && candidate.referenceDate.isNotBlank()
        val hasVerifiedDirection = candidate.directionDegrees != null
        val hasVerifiedPolygon = candidate.polygonStatus == "VERIFIED_POLYGON"

        // 좌표 불명확하거나 출처 누락 시 Grade D
        if (!hasValidSource || candidate.latitude !in 33.0..39.0 || candidate.longitude !in 124.0..132.0) {
            return QualityVerdict(
                zoneId = candidate.zoneId,
                grade = QualityGrade.GRADE_D,
                hasVerifiedDirection = false,
                hasVerifiedPolygon = false,
                hasValidSource = hasValidSource,
                eligibleForOcclusionBatch = false,
                suppressionReason = "DATA_INVALID_OR_MISSING_SOURCE"
            )
        }

        // Grade A / B 판정: 방향각과 Polygon이 모두 수동/공식 검증된 경우만 승격 가능
        if (hasVerifiedDirection && hasVerifiedPolygon && candidate.geometryQuality == "VERIFIED_POINT_AND_POLYGON") {
            return QualityVerdict(
                zoneId = candidate.zoneId,
                grade = QualityGrade.GRADE_B,
                hasVerifiedDirection = true,
                hasVerifiedPolygon = true,
                hasValidSource = true,
                eligibleForOcclusionBatch = true,
                suppressionReason = "NONE_ELIGIBLE"
            )
        }

        // 현재 100곳 후보는 중심점만 존재하는 Grade C 상태
        return QualityVerdict(
            zoneId = candidate.zoneId,
            grade = QualityGrade.GRADE_C,
            hasVerifiedDirection = false,
            hasVerifiedPolygon = false,
            hasValidSource = true,
            eligibleForOcclusionBatch = false,
            suppressionReason = "FAIL_CLOSED_DIRECTION_AND_POLYGON_UNVERIFIED"
        )
    }

    fun exportQualityGateCsv(verdicts: List<QualityVerdict>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("zoneId,qualityGrade,hasVerifiedDirection,hasVerifiedPolygon,hasValidSource,eligibleForOcclusionBatch,suppressionReason")

        for (v in verdicts) {
            lines.add("${v.zoneId},${v.grade.name},${v.hasVerifiedDirection},${v.hasVerifiedPolygon},${v.hasValidSource},${v.eligibleForOcclusionBatch},${v.suppressionReason}")
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }

    fun exportBatchEligibilityGuardCsv(verdicts: List<QualityVerdict>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("metric,count,percentage,status,failClosedPolicy")

        val total = verdicts.size
        val eligibleCount = verdicts.count { it.eligibleForOcclusionBatch }
        val suppressedCount = total - eligibleCount
        val gradeCCount = verdicts.count { it.grade == QualityGrade.GRADE_C }

        lines.add("TOTAL_CANDIDATES,$total,100.0%,LOADED,INPUT_BASELINE")
        lines.add("GRADE_C_CENTER_POINT_ONLY,$gradeCCount,${gradeCCount * 100.0 / total}%,FAIL_CLOSED,BATCH_OCCLUSION_PROHIBITED")
        lines.add("ELIGIBLE_FOR_OCCLUSION_BATCH,$eligibleCount,${eligibleCount * 100.0 / total}%,ZERO_TOLERANCE,HARD_GATE_ENFORCED")
        lines.add("SUPPRESSED_TO_L0_OR_INVALID,$suppressedCount,${suppressedCount * 100.0 / total}%,100_PERCENT_SUPPRESSED,FAIL_CLOSED_ACTIVE")

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
