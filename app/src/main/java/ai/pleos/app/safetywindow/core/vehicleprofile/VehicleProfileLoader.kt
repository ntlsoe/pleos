package ai.pleos.app.safetywindow.core.vehicleprofile

import org.json.JSONObject
import java.io.File

/**
 * VehicleProfileLoader
 * [작업명령 #12] 섹션 5 & 6: 차량 5종 프로파일 로더 및 감사 매트릭스 생성
 */
object VehicleProfileLoader {

    fun loadFromFile(file: File): VehicleProfileRecord {
        require(file.exists()) { "Profile file does not exist: ${file.absolutePath}" }
        val text = file.readText(Charsets.UTF_8)
        val json = JSONObject(text)
        val record = VehicleProfileRecord.fromJsonObject(json)
        val validation = VehicleProfileValidator.validate(record)
        require(validation.isValid) {
            "VehicleProfile validation failed for ${file.name}: ${validation.errors.joinToString("; ")}"
        }
        return record
    }

    fun loadFromDirectory(dir: File): List<VehicleProfileRecord> {
        require(dir.exists() && dir.isDirectory) { "Directory does not exist: ${dir.absolutePath}" }
        val jsonFiles = dir.listFiles { f -> f.isFile && f.extension == "json" && f.name != "manifest.json" }?.sortedBy { it.name } ?: emptyList()
        val loaded = mutableListOf<VehicleProfileRecord>()
        val seenProfileIds = mutableSetOf<String>()

        for (file in jsonFiles) {
            val record = loadFromFile(file)
            val validation = VehicleProfileValidator.validate(record, seenProfileIds)
            require(validation.isValid) {
                "VehicleProfile directory validation failed for ${file.name}: ${validation.errors.joinToString("; ")}"
            }
            seenProfileIds.add(record.profileId)
            loaded.add(record)
        }
        return loaded
    }

    fun exportImportCsv(profiles: List<VehicleProfileRecord>, outputFile: File) {
        val lines = mutableListOf<String>()
        lines.add("profileId,modelName,modelYear,bodyType,lengthMm,widthMm,heightMm,wheelbaseMm,steeringSide,verificationLevel,pillarGeometryStatus,eyeModelStatus,sourceUrl,validationStatus")

        for (p in profiles) {
            val v = VehicleProfileValidator.validate(p)
            val status = if (v.isValid) "VALIDATED_PASS" else "REJECTED"
            lines.add(
                "${p.profileId},\"${p.modelName}\",\"${p.modelYear}\",\"${p.bodyType}\"," +
                "${p.lengthMm},${p.widthMm},${p.heightMm},${p.wheelbaseMm}," +
                "${p.steeringSide.name},${p.verificationLevel.name},${p.pillarGeometryStatus.name}," +
                "${p.eyeModelStatus.name},\"${p.sourceUrl}\",$status"
            )
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
