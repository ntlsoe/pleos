package ai.pleos.app.safetywindow.batch.execution

import ai.pleos.app.safetywindow.batch.model.BatchExecutionEvidence
import java.io.File
import java.nio.file.Path

/**
 * AuditCleanRunPolicy
 * [작업명령 #23] 섹션 3.1: Clean Run 정책 및 결과 디렉터리 격리
 *
 * 1. 기존 결과 디렉터리를 읽기 전용 보관 위치로 보호
 * 2. 신규 빈 출력 디렉터리 강제
 * 3. 캐시 기본 비활성화 (cacheHit=false, reusedResult=false 강제)
 * 4. 실행 증거 행 단위 검증
 */
object AuditCleanRunPolicy {

    data class CleanRunEnvironment(
        val systemTimeEpochMs: Long,
        val osName: String,
        val jvmVersion: String,
        val pythonVersion: String,
        val availableProcessors: Int,
        val sourceCommit: String,
        val inputManifestSha256: String,
        val outputDirectory: String
    )

    fun validateCleanDirectory(outputDir: File): Boolean {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
            return true
        }
        val contents = outputDir.listFiles() ?: return true
        return contents.isEmpty()
    }

    fun assertNoCacheReuse(evidence: BatchExecutionEvidence) {
        if (evidence.cacheHit) {
            throw IllegalStateException("AUDIT_VIOLATION: cacheHit=true detected in Clean Run for ${evidence.runId}")
        }
        if (evidence.reusedResult) {
            throw IllegalStateException("AUDIT_VIOLATION: reusedResult=true detected in Clean Run for ${evidence.runId}")
        }
    }
}
