package ai.pleos.app.safetywindow.demo.replay

import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * DemoReplayLoader
 * [작업명령 #24] 섹션 5: 원시 산출물 (.jsonl.gz) 로더 및 해시 검증기
 */
object DemoReplayLoader {

    private fun resolveFile(relPath: String): File {
        val candidates = listOf(
            File(relPath),
            File("..", relPath),
            File("c:/Users/DONGJIN/Desktop/플레오스", relPath),
            File("P:/", relPath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relPath)
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        file.inputStream().use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun loadFrames(entry: DemoReplayEntry): List<DemoRawInputFrame> {
        val file = resolveFile(entry.rawArtifactPath)
        if (!file.exists()) {
            throw IllegalStateException("RAW_ARTIFACT_NOT_FOUND: ${file.absolutePath}")
        }

        // SHA-256 integrity verification
        val actualSha = computeSha256(file)
        if (actualSha != entry.outputCanonicalJsonSha256) {
            throw IllegalStateException("RAW_HASH_MISMATCH: expected=${entry.outputCanonicalJsonSha256}, actual=$actualSha")
        }

        val frames = mutableListOf<DemoRawInputFrame>()
        GZIPInputStream(FileInputStream(file)).use { gz ->
            BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isEmpty()) continue

                    val json = JSONObject(trimmed)
                    // Legacy artifacts contain outputs; the input DTO explicitly whitelists raw fields only.
                    val frame = DemoRawInputFrame(
                        simulationTimeMillis = json.optLong("simulationTimeMillis", 0L),
                        possibleRatio = json.optDouble("possibleRatio", 0.0),
                        universalRatio = json.optDouble("universalRatio", 0.0),
                        exposureIntegral = json.optDouble("exposureIntegral", 0.0),
                        maxContinuousOcclusionMillis = json.optLong("maxContinuousOcclusionMillis", 0L)
                    )
                    frames.add(frame)
                }
            }
        }
        return frames
    }
}
