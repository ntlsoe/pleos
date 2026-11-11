package ai.pleos.app.safetywindow.core.schoolzone

import java.io.File
import kotlin.math.*

/**
 * CrosswalkCandidateMatcher
 * [작업명령 #12] 섹션 8: 전국횡단보도표준데이터 기반 후보 매칭 및 반경 검색 모듈
 */
data class CrosswalkRecord(
    val crosswalkId: String,
    val roadAddress: String,
    val roadName: String,
    val latitude: Double,
    val longitude: Double,
    val widthMeters: Double,
    val lengthMeters: Double,
    val laneCount: Int,
    val hasTrafficLight: Boolean,
    val managementInstitution: String
)

data class CrosswalkMatchCandidate(
    val zoneId: String,
    val crosswalkId: String,
    val distanceMeters: Double,
    val roadNameMatched: Boolean,
    val managementInstitutionMatched: Boolean,
    val compositeScore: Double,
    val rank: Int,
    val widthMeters: Double,
    val lengthMeters: Double,
    val laneCount: Int,
    val hasTrafficLight: Boolean,
    val directionDegrees: Double? = null,
    val matchStatus: String,
    val autoConfirmed: Boolean = false,
    val rationale: String
)

object CrosswalkCandidateMatcher {

    private const val EARTH_RADIUS_METERS = 6371000.0

    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun findCandidates(
        zone: SchoolZoneCandidate,
        crosswalks: List<CrosswalkRecord>,
        radiusMeters: Double = 50.0
    ): List<CrosswalkMatchCandidate> {
        val inRadius = mutableListOf<Pair<CrosswalkRecord, Double>>()

        for (cw in crosswalks) {
            val dist = computeDistanceMeters(zone.latitude, zone.longitude, cw.latitude, cw.longitude)
            if (dist <= radiusMeters) {
                inRadius.add(Pair(cw, dist))
            }
        }

        if (inRadius.isEmpty()) {
            return listOf(
                CrosswalkMatchCandidate(
                    zoneId = zone.zoneId,
                    crosswalkId = "NONE",
                    distanceMeters = -1.0,
                    roadNameMatched = false,
                    managementInstitutionMatched = false,
                    compositeScore = 0.0,
                    rank = 0,
                    widthMeters = 0.0,
                    lengthMeters = 0.0,
                    laneCount = 0,
                    hasTrafficLight = false,
                    directionDegrees = null,
                    matchStatus = "NO_CANDIDATE_IN_RADIUS",
                    autoConfirmed = false,
                    rationale = "반경 ${radiusMeters}m 이내 횡단보도 표준데이터 후보 부재. Fail-Closed 유지."
                )
            )
        }

        // 복수 후보 점수 산출 및 순위화
        val scored = inRadius.map { (cw, dist) ->
            // 도로명 매칭 보조 검사
            val roadTokens = zone.roadAddress.split(" ").filter { it.endsWith("로") || it.endsWith("길") }
            val roadMatched = roadTokens.any { cw.roadAddress.contains(it) || cw.roadName.contains(it) }

            // 관리기관 보조 검사
            val instMatched = zone.managementInstitution.isNotBlank() &&
                    (zone.managementInstitution.contains(cw.managementInstitution) || cw.managementInstitution.contains(zone.managementInstitution))

            // 복합 점수: 거리 역수 비중 70% + 도로명 20% + 기관 10%
            val distScore = max(0.0, 1.0 - (dist / radiusMeters))
            val compScore = (distScore * 0.7) + (if (roadMatched) 0.2 else 0.0) + (if (instMatched) 0.1 else 0.0)

            Triple(cw, dist, compScore)
        }.sortedByDescending { it.third }

        val isAmbiguous = scored.size >= 2 && (abs(scored[0].third - scored[1].third) < 0.05 || abs(scored[0].second - scored[1].second) < 5.0)

        return scored.mapIndexed { idx, (cw, dist, compScore) ->
            val status = if (isAmbiguous) "AMBIGUOUS_MULTIPLE_CANDIDATES" else "CANDIDATE_RANKED"
            CrosswalkMatchCandidate(
                zoneId = zone.zoneId,
                crosswalkId = cw.crosswalkId,
                distanceMeters = round(dist * 10.0) / 10.0,
                roadNameMatched = zone.roadAddress.contains(cw.roadName) || cw.roadAddress.contains(zone.roadAddress.take(8)),
                managementInstitutionMatched = zone.managementInstitution == cw.managementInstitution,
                compositeScore = round(compScore * 1000.0) / 1000.0,
                rank = idx + 1,
                widthMeters = cw.widthMeters,
                lengthMeters = cw.lengthMeters,
                laneCount = cw.laneCount,
                hasTrafficLight = cw.hasTrafficLight,
                directionDegrees = null, // 표준데이터 미제공 유지
                matchStatus = status,
                autoConfirmed = false, // 자동 확정 금지 (수동 검증 전까지 확정 불가)
                rationale = "반경 ${radiusMeters}m 내 거리 ${dist.toInt()}m, 복합점수 ${round(compScore*100)/100}. 방향각 미제공 상태 보존."
            )
        }
    }

    fun exportMatchingCsv(matches: List<CrosswalkMatchCandidate>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("zoneId,crosswalkId,distanceMeters,roadNameMatched,managementInstitutionMatched,compositeScore,rank,widthMeters,lengthMeters,laneCount,hasTrafficLight,directionDegrees,matchStatus,autoConfirmed,rationale")

        for (m in matches) {
            val dirStr = m.directionDegrees?.toString() ?: "NULL"
            lines.add(
                "${m.zoneId},${m.crosswalkId},${m.distanceMeters},${m.roadNameMatched}," +
                "${m.managementInstitutionMatched},${m.compositeScore},${m.rank}," +
                "${m.widthMeters},${m.lengthMeters},${m.laneCount},${m.hasTrafficLight}," +
                "$dirStr,${m.matchStatus},${m.autoConfirmed},\"${m.rationale}\""
            )
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
