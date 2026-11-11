package ai.pleos.app.safetywindow.batch.io

import ai.pleos.app.safetywindow.batch.model.*
import java.io.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class AtomicGzipJsonlWriter(private val finalPath: Path) : Closeable {
    private val tempPath = finalPath.resolveSibling(finalPath.fileName.toString()+".tmp")
    private val writer: BufferedWriter
    init {
        Files.createDirectories(finalPath.parent)
        writer = GZIPOutputStream(Files.newOutputStream(tempPath)).bufferedWriter(Charsets.UTF_8)
    }
    fun append(jsonLine: String) { writer.append(jsonLine).append('\n') }
    override fun close() {
        writer.close()
        Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

object CsvEscaper {
    fun cell(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.any { it==',' || it=='"' || it=='\n' || it=='\r' }) "\"${s.replace("\"","\"\"")}\"" else s
    }
    fun row(vararg values: Any?): String = values.joinToString(",") { cell(it) }
}

object Digests {
    fun sha256(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer=ByteArray(1024*1024)
            while (true) { val n=input.read(buffer); if(n<0) break; md.update(buffer,0,n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class BatchArtifactLayout(val root: Path) {
    val manifestDir=root.resolve("manifest")
    val inputsDir=root.resolve("inputs")
    val rawDir=root.resolve("raw")
    val resultsDir=root.resolve("results")
    val reportsDir=root.resolve("reports")
    fun ensure() = listOf(manifestDir,inputsDir,rawDir,resultsDir,reportsDir).forEach(Files::createDirectories)
    fun rawPath(plan: BatchRunPlan): Path = rawDir.resolve(plan.zoneId).resolve("${plan.runId}.jsonl.gz")
}
