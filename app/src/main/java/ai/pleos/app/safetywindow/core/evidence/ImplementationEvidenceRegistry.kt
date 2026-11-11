package ai.pleos.app.safetywindow.core.evidence

enum class EvidenceClass {
    IMPLEMENTED_AND_TESTED,
    IMPLEMENTED_SCENARIO_ONLY,
    ANDROID_AVD_MEASURED,
    HOST_UNIT_TEST_ONLY,
    SYNTHETIC_SIMULATION_ONLY,
    DOCUMENT_CONFIRMED,
    UNVERIFIED,
    EXCLUDED
}

data class ImplementationClaim(
    val claimId: String,
    val claimText: String,
    val evidenceClass: EvidenceClass,
    val supportingFiles: String,
    val testName: String,
    val buildVariant: String,
    val environment: String,
    val canUseInProposal: Boolean,
    val requiredQualifier: String,
    val unresolvedRisk: String
)

/**
 * ImplementationEvidenceRegistry
 * [작업명령 #8] 섹션 11: 구현 사실 및 한계 레지스트리
 */
object ImplementationEvidenceRegistry {

    fun getAllClaims(): List<ImplementationClaim> {
        return listOf(
            ImplementationClaim(
                claimId = "CLM-01",
                claimText = "3차원 양안 A필러 가림 영역 기하 계산 알고리즘 구현",
                evidenceClass = EvidenceClass.IMPLEMENTED_AND_TESTED,
                supportingFiles = "core/geometry/BinocularOcclusionEngine.kt",
                testName = "BinocularOcclusionEngineTest",
                buildVariant = "Debug / Release",
                environment = "Host JVM / Android Runtime",
                canUseInProposal = true,
                requiredQualifier = "기하학적 투영 모델 기준 계산",
                unresolvedRisk = "운전자 두부 이동 시 시점 오차 발생 가능"
            ),
            ImplementationClaim(
                claimId = "CLM-02",
                claimText = "Scenario 전용 L1 시각 안내 오버레이 구현",
                evidenceClass = EvidenceClass.IMPLEMENTED_SCENARIO_ONLY,
                supportingFiles = "ui/hud/SafetyWindowVectorHud.kt",
                testName = "ScenarioSinglePipelineTest",
                buildVariant = "Debug Only",
                environment = "Pleos Connect AVD",
                canUseInProposal = true,
                requiredQualifier = "합성 시나리오 브로드캐스트 입력 한정",
                unresolvedRisk = "실제 센서 및 외부 차량 인터페이스 미연결"
            ),
            ImplementationClaim(
                claimId = "CLM-03",
                claimText = "Release 빌드 전면 시각/음성 출력 기본 차단(Fail-Closed)",
                evidenceClass = EvidenceClass.IMPLEMENTED_AND_TESTED,
                supportingFiles = "core/advisory/AdvisoryOutputFlags.kt",
                testName = "ReleaseSafeDefaultsTest",
                buildVariant = "Release",
                environment = "Host JVM / Android Runtime",
                canUseInProposal = true,
                requiredQualifier = "현재 설정 및 테스트 환경에서 검증",
                unresolvedRisk = "차량 OEM 인터페이스 탑재 시 별도 승인 프로세스 필요"
            ),
            ImplementationClaim(
                claimId = "CLM-04",
                claimText = "AVD 환경 적응형 표본화 계산 지연 p50 약 0.50~0.52ms",
                evidenceClass = EvidenceClass.ANDROID_AVD_MEASURED,
                supportingFiles = "reports/android_runtime_performance_report.md",
                testName = "Android Benchmark Script",
                buildVariant = "Profileable Release",
                environment = "Pleos Connect AVD (x86_64)",
                canUseInProposal = true,
                requiredQualifier = "AVD 에뮬레이터 환경 측정치 (실차 하드웨어 측정치 아님)",
                unresolvedRisk = "차량용 저사양 ARM SoC 성능 차이 존재"
            ),
            ImplementationClaim(
                claimId = "CLM-05",
                claimText = "TTS 하드 킬스위치를 통한 실제 음성 발화 0회 강제",
                evidenceClass = EvidenceClass.IMPLEMENTED_AND_TESTED,
                supportingFiles = "core/alert/TtsKillSwitch.kt",
                testName = "TtsKillSwitchTest",
                buildVariant = "Debug / Release",
                environment = "Host JVM / AVD",
                canUseInProposal = true,
                requiredQualifier = "소프트웨어 게이트 기반 차단",
                unresolvedRisk = "외부 서드파티 내비게이션 음성과 충돌 가능성"
            ),
            ImplementationClaim(
                claimId = "CLM-06",
                claimText = "Replay Mode를 통한 10회 동일 실행 결정론적 재현",
                evidenceClass = EvidenceClass.IMPLEMENTED_AND_TESTED,
                supportingFiles = "core/replay/ReplayValidationPipeline.kt",
                testName = "ReplayDeterminismTest",
                buildVariant = "Debug Only",
                environment = "Host JVM / AVD",
                canUseInProposal = true,
                requiredQualifier = "저장된 시계열 데이터 재현 한정",
                unresolvedRisk = "실시간 센서 지터 및 클록 드리프트 미반영"
            ),
            ImplementationClaim(
                claimId = "CLM-07",
                claimText = "실제 사고율 감소 및 차량 안전성 향상",
                evidenceClass = EvidenceClass.EXCLUDED,
                supportingFiles = "N/A",
                testName = "N/A",
                buildVariant = "N/A",
                environment = "N/A",
                canUseInProposal = false,
                requiredQualifier = "주장 전면 금지 (실차 주행 데이터 미보유)",
                unresolvedRisk = "통계적 유의성 및 실제 안전 효과 미입증"
            ),
            ImplementationClaim(
                claimId = "CLM-08",
                claimText = "전국 도로망 공공데이터 자동 적용",
                evidenceClass = EvidenceClass.UNVERIFIED,
                supportingFiles = "N/A",
                testName = "N/A",
                buildVariant = "N/A",
                environment = "N/A",
                canUseInProposal = false,
                requiredQualifier = "수동 검증 증거 미보유 시 적용 불가",
                unresolvedRisk = "공공데이터 위치 편차 및 방향각 결측"
            )
        )
    }

    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append("claim_id,claim_text,evidence_class,supporting_files,test_name,build_variant,environment,can_use_in_proposal,required_qualifier,unresolved_risk\n")
        getAllClaims().forEach { c ->
            sb.append("\"${c.claimId}\",")
            sb.append("\"${c.claimText}\",")
            sb.append("\"${c.evidenceClass.name}\",")
            sb.append("\"${c.supportingFiles}\",")
            sb.append("\"${c.testName}\",")
            sb.append("\"${c.buildVariant}\",")
            sb.append("\"${c.environment}\",")
            sb.append("${c.canUseInProposal},")
            sb.append("\"${c.requiredQualifier}\",")
            sb.append("\"${c.unresolvedRisk}\"\n")
        }
        return sb.toString()
    }
}
