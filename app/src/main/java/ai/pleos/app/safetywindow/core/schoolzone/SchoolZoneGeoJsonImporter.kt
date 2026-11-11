package ai.pleos.app.safetywindow.core.schoolzone

import org.json.JSONObject
import java.io.File

/**
 * SchoolZoneGeoJsonImporter
 * [작업명령 #12] 섹션 5 & 7: 전국 어린이보호구역 100곳 GeoJSON 임포터 및 검증기
 */
object SchoolZoneGeoJsonImporter {

    fun importGeoJson(file: File): List<SchoolZoneCandidate> {
        require(file.exists()) { "GeoJSON file not found: ${file.absolutePath}" }
        val text = file.readText(Charsets.UTF_8)
        val root = JSONObject(text)
        require(root.optString("type") == "FeatureCollection") { "Root must be FeatureCollection" }

        val features = root.getJSONArray("features")
        val candidates = mutableListOf<SchoolZoneCandidate>()
        val seenZoneIds = mutableSetOf<String>()
        val seenCoordinates = mutableSetOf<Pair<Double, Double>>()

        for (i in 0 until features.length()) {
            val featObj = features.getJSONObject(i)
            val candidate = SchoolZoneCandidate.fromGeoJsonFeature(featObj)

            val validation = SchoolZoneCandidateValidator.validate(candidate, seenZoneIds, seenCoordinates)
            require(validation.isValid) {
                "Validation failed for candidate ${candidate.zoneId} at index $i: ${validation.errors.joinToString("; ")}"
            }

            seenZoneIds.add(candidate.zoneId)
            seenCoordinates.add(Pair(candidate.latitude, candidate.longitude))
            candidates.add(candidate)
        }

        return candidates
    }

    fun exportImportCsv(candidates: List<SchoolZoneCandidate>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("zoneId,facilityType,facilityName,roadAddress,latitude,longitude,managementInstitution,protectionRoadWidthRaw,referenceDate,regionGroup,geometryQuality,crosswalkMatchStatus,polygonStatus,eligibleForOcclusionBatch,manualVerificationRequired,validationStatus")

        for (c in candidates) {
            val v = SchoolZoneCandidateValidator.validate(c)
            val status = if (v.isValid) "VALIDATED_PASS" else "REJECTED"
            lines.add(
                "${c.zoneId},\"${c.facilityType}\",\"${c.facilityName}\",\"${c.roadAddress}\"," +
                "${c.latitude},${c.longitude},\"${c.managementInstitution}\",\"${c.protectionRoadWidthRaw}\"," +
                "\"${c.referenceDate}\",\"${c.regionGroup}\",${c.geometryQuality},${c.crosswalkMatchStatus}," +
                "${c.polygonStatus},${c.eligibleForOcclusionBatch},${c.manualVerificationRequired},$status"
            )
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
