package ai.pleos.app.safetywindow.demo.replay

import java.io.File

/**
 * DemoReplayRegistry
 * [작업명령 #24, #30, #31] Demo Replay Registry 저장소 로더
 *
 * 격리 및 안전 보증:
 * 1. Release 환경에서는 로더 진입 차단 (emptyList 반환)
 * 2. QUARANTINED 상태 항목은 일반 UI/앱 경로(getV4Entries, getV5Entries)로 반환 금지
 * 3. FourViewDemoScreen은 Work 24 베이스라인(138개)만 연결 유지
 */
object DemoReplayRegistry {

    private val entries = mutableListOf<DemoReplayEntry>()
    private val v4QuarantinedEntries = mutableListOf<DemoReplayEntry>()
    private val v5QuarantinedEntries = mutableListOf<DemoReplayEntry>()
    private var isInitialized = false
    private var isV4Initialized = false
    private var isV5Initialized = false

    /**
     * 환경 가드: Release 환경에서는 Registry Loader 진입 차단
     */
    var isDebugEnvironment: Boolean = true

    private fun resolveFile(relPath: String): File {
        val candidates = listOf(
            File(relPath),
            File("..", relPath),
            File("c:/Users/DONGJIN/Desktop/플레오스", relPath),
            File("P:/", relPath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relPath)
    }

    @Synchronized
    fun initializeIfNeeded() {
        if (!isDebugEnvironment) {
            entries.clear()
            return
        }

        if (isInitialized && entries.isNotEmpty()) return

        val csvFile = resolveFile("audit/results/demo_replay_registry_work24.csv").let {
            if (it.exists()) it else resolveFile("audit/batch_100/results/demo_replay_registry.csv")
        }

        if (!csvFile.exists()) {
            return
        }

        entries.clear()
        val lines = csvFile.readLines()
        if (lines.isEmpty()) return

        val header = lines.first().split(",")
        val colMap = header.mapIndexed { idx, col -> col.trim() to idx }.toMap()

        for (line in lines.drop(1)) {
            val tokens = line.split(",")
            if (tokens.size < 28) continue

            fun col(name: String): String = tokens.getOrNull(colMap[name] ?: -1)?.trim() ?: ""

            val entry = DemoReplayEntry(
                replayId = col("replayId"),
                runId = col("runId"),
                batchItemId = col("batchItemId"),
                zoneId = col("zoneId"),
                facilityName = col("facilityName"),
                selectedLinkId = col("selectedLinkId"),
                riskFeatureId = col("riskFeatureId"),
                riskFeatureType = col("riskFeatureType"),
                vehicleProfileId = col("vehicleProfileId"),
                syntheticPillarProfileId = col("syntheticPillarProfileId"),
                scenarioId = col("scenarioId"),
                speedProfileId = col("speedProfileId"),
                eyeModelId = col("eyeModelId"),
                geometryEvidenceClass = col("geometryEvidenceClass"),
                sourceCommit = col("sourceCommit"),
                inputCanonicalJsonSha256 = col("inputCanonicalJsonSha256"),
                intermediateDigest = col("intermediateDigest"),
                outputCanonicalJsonSha256 = col("outputCanonicalJsonSha256"),
                rawArtifactPath = col("rawArtifactPath"),
                maxLevel = col("maxLevel"),
                peakPossibleRatio = col("peakPossibleRatio").toDoubleOrNull() ?: 0.0,
                peakUniversalRatio = col("peakUniversalRatio").toDoubleOrNull() ?: 0.0,
                maxOcclusionMillis = col("maxOcclusionMillis").toLongOrNull() ?: 0L,
                exposureIntegral = col("exposureIntegral").toDoubleOrNull() ?: 0.0,
                ttsRequestedCount = col("ttsRequestedCount").toIntOrNull() ?: 0,
                ttsPermittedCount = col("ttsPermittedCount").toIntOrNull() ?: 0,
                ttsSpokenCount = col("ttsSpokenCount").toIntOrNull() ?: 0,
                replayEligible = col("replayEligible").toBoolean(),
                exclusionReason = col("exclusionReason").ifBlank { null }
            )
            entries.add(entry)
        }
        isInitialized = true
    }

    private fun loadV4RawIfNeeded() {
        if (isV4Initialized && v4QuarantinedEntries.isNotEmpty()) return

        val csvFile = resolveFile("audit/results/replay_registry_v4.csv")
        if (!csvFile.exists()) return

        v4QuarantinedEntries.clear()
        val lines = csvFile.readLines()
        if (lines.isEmpty()) return

        val header = lines.first().split(",")
        val colMap = header.mapIndexed { idx, col -> col.trim() to idx }.toMap()

        for (line in lines.drop(1)) {
            val tokens = line.split(",")
            if (tokens.size < 28) continue

            fun col(name: String): String = tokens.getOrNull(colMap[name] ?: -1)?.trim() ?: ""

            val entry = DemoReplayEntry(
                replayId = col("replayId"),
                runId = col("runId"),
                batchItemId = col("batchItemId"),
                zoneId = col("zoneId"),
                facilityName = col("facilityName"),
                selectedLinkId = col("selectedLinkId"),
                riskFeatureId = col("riskFeatureId"),
                riskFeatureType = col("riskFeatureType"),
                vehicleProfileId = col("vehicleProfileId"),
                syntheticPillarProfileId = col("syntheticPillarProfileId"),
                scenarioId = col("scenarioId"),
                speedProfileId = col("speedProfileId"),
                eyeModelId = col("eyeModelId"),
                geometryEvidenceClass = col("geometryEvidenceClass"),
                sourceCommit = col("sourceCommit"),
                inputCanonicalJsonSha256 = col("inputCanonicalJsonSha256"),
                intermediateDigest = col("intermediateDigest"),
                outputCanonicalJsonSha256 = col("outputCanonicalJsonSha256"),
                rawArtifactPath = col("rawArtifactPath"),
                maxLevel = col("maxLevel"),
                peakPossibleRatio = col("peakPossibleRatio").toDoubleOrNull() ?: 0.0,
                peakUniversalRatio = col("peakUniversalRatio").toDoubleOrNull() ?: 0.0,
                maxOcclusionMillis = col("maxOcclusionMillis").toLongOrNull() ?: 0L,
                exposureIntegral = col("exposureIntegral").toDoubleOrNull() ?: 0.0,
                ttsRequestedCount = col("ttsRequestedCount").toIntOrNull() ?: 0,
                ttsPermittedCount = col("ttsPermittedCount").toIntOrNull() ?: 0,
                ttsSpokenCount = col("ttsSpokenCount").toIntOrNull() ?: 0,
                replayEligible = col("replayEligible").toBoolean(),
                exclusionReason = col("exclusionReason").ifBlank { null },
                status = col("status").ifBlank { "QUARANTINED" }
            )
            v4QuarantinedEntries.add(entry)
        }
        isV4Initialized = true
    }

    private fun loadV5RawIfNeeded() {
        if (isV5Initialized && v5QuarantinedEntries.isNotEmpty()) return

        val csvFile = resolveFile("audit/results/replay_registry_v5.csv")
        if (!csvFile.exists()) return

        v5QuarantinedEntries.clear()
        val lines = csvFile.readLines()
        if (lines.isEmpty()) return

        val header = lines.first().split(",")
        val colMap = header.mapIndexed { idx, col -> col.trim() to idx }.toMap()

        for (line in lines.drop(1)) {
            val tokens = line.split(",")
            if (tokens.size < 28) continue

            fun col(name: String): String = tokens.getOrNull(colMap[name] ?: -1)?.trim() ?: ""

            val entry = DemoReplayEntry(
                replayId = col("replayId"),
                runId = col("runId"),
                batchItemId = col("batchItemId"),
                zoneId = col("zoneId"),
                facilityName = col("facilityName"),
                selectedLinkId = col("selectedLinkId"),
                riskFeatureId = col("riskFeatureId"),
                riskFeatureType = col("riskFeatureType"),
                vehicleProfileId = col("vehicleProfileId"),
                syntheticPillarProfileId = col("syntheticPillarProfileId"),
                scenarioId = col("scenarioId"),
                speedProfileId = col("speedProfileId"),
                eyeModelId = col("eyeModelId"),
                geometryEvidenceClass = col("geometryEvidenceClass"),
                sourceCommit = col("sourceCommit"),
                inputCanonicalJsonSha256 = col("inputCanonicalJsonSha256"),
                intermediateDigest = col("intermediateDigest"),
                outputCanonicalJsonSha256 = col("outputCanonicalJsonSha256"),
                rawArtifactPath = col("rawArtifactPath"),
                maxLevel = col("maxLevel"),
                peakPossibleRatio = col("peakPossibleRatio").toDoubleOrNull() ?: 0.0,
                peakUniversalRatio = col("peakUniversalRatio").toDoubleOrNull() ?: 0.0,
                maxOcclusionMillis = col("maxOcclusionMillis").toLongOrNull() ?: 0L,
                exposureIntegral = col("exposureIntegral").toDoubleOrNull() ?: 0.0,
                ttsRequestedCount = col("ttsRequestedCount").toIntOrNull() ?: 0,
                ttsPermittedCount = col("ttsPermittedCount").toIntOrNull() ?: 0,
                ttsSpokenCount = col("ttsSpokenCount").toIntOrNull() ?: 0,
                replayEligible = col("replayEligible").toBoolean(),
                exclusionReason = col("exclusionReason").ifBlank { null },
                status = col("status").ifBlank { "QUARANTINED" },
                replayPurpose = col("replayPurpose").ifBlank { "HAZARD_ADVISORY_DEMO" },
                preInjectionMaxLevel = col("preInjectionMaxLevel"),
                postInjectionLevel = col("postInjectionLevel"),
                expectedUserOutput = col("expectedUserOutput"),
                safetyContract = col("safetyContract"),
                eligibilityReason = col("eligibilityReason")
            )
            v5QuarantinedEntries.add(entry)
        }
        isV5Initialized = true
    }

    /**
     * [작업 #31 단계 8] getV4Entries()
     * 격리 수칙: QUARANTINED 상태 항목은 일반 UI/앱 경로로 일체 반환되지 않음 (0건 반환).
     * Release 환경에서는 빈 리스트 반환.
     */
    @Synchronized
    fun getV4Entries(): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        loadV4RawIfNeeded()
        return v4QuarantinedEntries.filter { it.status != "QUARANTINED" }
    }

    /**
     * [작업 #31 단계 8] getV5Entries()
     * 격리 수칙: Registry v5의 모든 항목은 QUARANTINED 상태이므로 일반 호출 시 0건 반환.
     * Release 환경에서는 빈 리스트 반환.
     */
    @Synchronized
    fun getV5Entries(): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        loadV5RawIfNeeded()
        return v5QuarantinedEntries.filter { it.status != "QUARANTINED" }
    }

    /**
     * 감사 전용: 격리된 v4 원천 데이터 조회 (앱 시연용 호출 불가)
     */
    @Synchronized
    fun getQuarantinedV4EntriesForAuditOnly(): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        loadV4RawIfNeeded()
        return v4QuarantinedEntries.toList()
    }

    /**
     * 감사 전용: 격리된 v5 원천 데이터 조회 (앱 시연용 호출 불가)
     */
    @Synchronized
    fun getQuarantinedV5EntriesForAuditOnly(): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        loadV5RawIfNeeded()
        return v5QuarantinedEntries.toList()
    }

    fun getAllEntries(): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        initializeIfNeeded()
        return entries.toList()
    }

    fun findReplay(replayId: String): DemoReplayEntry? {
        if (!isDebugEnvironment) return null
        initializeIfNeeded()
        return entries.firstOrNull { it.replayId == replayId }
    }

    fun findRun(runId: String): DemoReplayEntry? {
        if (!isDebugEnvironment) return null
        initializeIfNeeded()
        return entries.firstOrNull { it.runId == runId }
    }

    fun filter(
        zoneId: String? = null,
        vehicleId: String? = null,
        scenarioId: String? = null,
        speedId: String? = null
    ): List<DemoReplayEntry> {
        if (!isDebugEnvironment) return emptyList()
        initializeIfNeeded()
        return entries.filter { r ->
            (zoneId == null || r.zoneId == zoneId) &&
            (vehicleId == null || r.vehicleProfileId == vehicleId) &&
            (scenarioId == null || r.scenarioId == scenarioId) &&
            (speedId == null || r.speedProfileId == speedId)
        }
    }

    fun getZoneList(): List<String> {
        if (!isDebugEnvironment) return emptyList()
        initializeIfNeeded()
        return entries.map { it.zoneId }.distinct().sorted()
    }

    fun getVehicleList(): List<String> {
        return listOf("CASPER", "IONIQ_6", "GRANDEUR", "IONIQ_5", "IONIQ_9")
    }

    fun getScenarioList(): List<String> {
        if (!isDebugEnvironment) return emptyList()
        initializeIfNeeded()
        return entries.map { it.scenarioId }.distinct().sorted()
    }

    fun getSpeedList(): List<String> {
        if (!isDebugEnvironment) return emptyList()
        initializeIfNeeded()
        return entries.map { it.speedProfileId }.distinct().sorted()
    }
}
