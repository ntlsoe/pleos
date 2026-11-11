package ai.pleos.app.safetywindow.batch.model

import java.time.Instant

/**
 * BatchExecutionEvidence
 * [작업명령 #23] 섹션 3.2: 행 단위 실행 증거 모델
 *
 * 모든 배치 행에 대해 실제 계산 진입·종료, 샘플 수, 레이 수,
 * 중간 다이제스트 및 캐시 사용 여부를 기록하여 계산 진위를 독립 검증함.
 */
data class BatchExecutionEvidence(
    val runId: String,
    val batchItemId: String,
    val zoneId: String,
    val spatialFeatureSha256: String,
    val vehicleProfileId: String,
    val vehicleProfileSha256: String,
    val syntheticPillarProfileId: String,
    val syntheticPillarProfileSha256: String,
    val approachProfileId: String,
    val engineImplementation: String,
    val engineVersion: String,
    val sourceCommit: String,
    val inputCanonicalJsonSha256: String,
    val calculationStartedAt: Instant,
    val calculationFinishedAt: Instant,
    val computationNanos: Long,
    val sampleCount: Int,
    val leftEyeRayCount: Int,
    val rightEyeRayCount: Int,
    val intermediateDigest: String,
    val outputCanonicalJsonSha256: String,
    val cacheLookupAttempted: Boolean,
    val cacheHit: Boolean,
    val reusedResult: Boolean,
    val executionStatus: String,
    val failureReason: String? = null
) {
    init {
        require(computationNanos >= 0) { "computationNanos cannot be negative" }
        require(sampleCount >= 0) { "sampleCount cannot be negative" }
        require(leftEyeRayCount >= 0) { "leftEyeRayCount cannot be negative" }
        require(rightEyeRayCount >= 0) { "rightEyeRayCount cannot be negative" }
    }

    val isAuthenticCalculation: Boolean
        get() = !cacheHit && !reusedResult && executionStatus == "SUCCESS"
}
