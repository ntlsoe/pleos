package ai.pleos.app.safetywindow.core.schoolzone

import java.io.File

/**
 * PilotZoneSelector
 * [작업명령 #12] 섹션 9: 지역·시설유형·도로폭 다양성을 반영한 Pilot 후보 10곳 제안기
 *
 * 엄격한 품질 원칙:
 * 제안된 10곳의 상태는 PROPOSED_PILOT_10이며,
 * 수동 검증(지도/항공정사영상/오버레이 실측) 전에는 PILOT_10이나 A/B등급으로 승격할 수 없음 (Grade C 유지).
 */
data class PilotProposalEntry(
    val pilotRank: Int,
    val zoneId: String,
    val facilityName: String,
    val facilityType: String,
    val regionGroup: String,
    val roadAddress: String,
    val protectionRoadWidthRaw: String,
    val latitude: Double,
    val longitude: Double,
    val selectionStage: String = "PROPOSED_PILOT_10",
    val qualityGrade: QualityGrade = QualityGrade.GRADE_C,
    val eligibleForOcclusionBatch: Boolean = false,
    val selectionRationale: String
)

object PilotZoneSelector {

    fun selectPilot10(candidates: List<SchoolZoneCandidate>): List<PilotProposalEntry> {
        require(candidates.size >= 10) { "Candidate pool must have at least 10 zones" }

        val selected = mutableListOf<SchoolZoneCandidate>()
        val seenZoneIds = mutableSetOf<String>()

        fun tryAdd(candidate: SchoolZoneCandidate?): Boolean {
            if (candidate != null && candidate.zoneId !in seenZoneIds) {
                selected.add(candidate)
                seenZoneIds.add(candidate.zoneId)
                return true
            }
            return false
        }

        // 1. 희소 시설유형 우선 배분 (특수학교, 유치원, 어린이집)
        tryAdd(candidates.firstOrNull { it.facilityType == "어린이집" })
        tryAdd(candidates.firstOrNull { it.facilityType == "특수학교" })
        tryAdd(candidates.firstOrNull { it.facilityType == "유치원" })

        // 2. 지역 대표성 배분 (부산광역시, 세종특별자치시, 충청남도, 전남광주)
        tryAdd(candidates.firstOrNull { it.regionGroup == "부산광역시" && it.zoneId !in seenZoneIds })
        tryAdd(candidates.firstOrNull { it.regionGroup == "세종특별자치시" && it.zoneId !in seenZoneIds })
        tryAdd(candidates.firstOrNull { it.regionGroup == "충청남도" && it.protectionRoadWidthMeters != null && it.protectionRoadWidthMeters >= 10.0 && it.zoneId !in seenZoneIds })
        tryAdd(candidates.firstOrNull { it.regionGroup == "충청남도" && it.protectionRoadWidthMeters != null && it.protectionRoadWidthMeters <= 5.0 && it.zoneId !in seenZoneIds })
        tryAdd(candidates.firstOrNull { it.regionGroup == "전남광주통합특별시" && it.protectionRoadWidthMeters != null && it.protectionRoadWidthMeters in 6.0..9.0 && it.zoneId !in seenZoneIds })
        tryAdd(candidates.firstOrNull { it.regionGroup == "전남광주통합특별시" && it.facilityType == "초등학교" && it.zoneId !in seenZoneIds })

        // 3. 10개 채우기
        for (c in candidates) {
            if (selected.size >= 10) break
            tryAdd(c)
        }

        val entries = selected.take(10).mapIndexed { idx, z ->
            val rationale = buildRationale(z)
            PilotProposalEntry(
                pilotRank = idx + 1,
                zoneId = z.zoneId,
                facilityName = z.facilityName,
                facilityType = z.facilityType,
                regionGroup = z.regionGroup,
                roadAddress = z.roadAddress,
                protectionRoadWidthRaw = z.protectionRoadWidthRaw,
                latitude = z.latitude,
                longitude = z.longitude,
                selectionStage = "PROPOSED_PILOT_10",
                qualityGrade = QualityGrade.GRADE_C,
                eligibleForOcclusionBatch = false, // 가림 계산 불가 유지
                selectionRationale = rationale
            )
        }

        return entries
    }

    private fun buildRationale(z: SchoolZoneCandidate): String {
        return "다양성 지표 반영: 지역(${z.regionGroup}), 유형(${z.facilityType}), 폭(${z.protectionRoadWidthRaw}m). 수동 검증 전 Grade C 유지."
    }

    fun exportProposalCsv(proposals: List<PilotProposalEntry>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("pilotRank,zoneId,facilityName,facilityType,regionGroup,roadAddress,protectionRoadWidthRaw,latitude,longitude,selectionStage,qualityGrade,eligibleForOcclusionBatch,selectionRationale")

        for (p in proposals) {
            lines.add(
                "${p.pilotRank},${p.zoneId},\"${p.facilityName}\",\"${p.facilityType}\"," +
                "\"${p.regionGroup}\",\"${p.roadAddress}\",\"${p.protectionRoadWidthRaw}\"," +
                "${p.latitude},${p.longitude},${p.selectionStage},${p.qualityGrade.name}," +
                "${p.eligibleForOcclusionBatch},\"${p.selectionRationale}\""
            )
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
