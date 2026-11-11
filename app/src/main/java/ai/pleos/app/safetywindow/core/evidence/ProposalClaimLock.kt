package ai.pleos.app.safetywindow.core.evidence

enum class ClaimLockStatus {
    LOCKED_APPROVED,
    LOCKED_PROHIBITED,
    PENDING_VALIDATION
}

data class ProposalClaimLockEntry(
    val claimId: String,
    val approvedText: String,
    val prohibitedText: String,
    val evidenceClass: String,
    val environment: String,
    val supportingFiles: String,
    val qualifier: String,
    val expiryOrRevalidationCondition: String,
    val status: ClaimLockStatus
) {
    val evidenceLevel: String get() = evidenceClass
    val supportingFile: String get() = supportingFiles
}

/**
 * ProposalClaimLock
 * [작업명령 #9, #10, #11] 기획서 작성용 주장 잠금 레지스트리 (v3)
 */
object ProposalClaimLock {

    fun getAllEntries(): List<ProposalClaimLockEntry> {
        return listOf(
            ProposalClaimLockEntry(
                claimId = "LCK-01",
                approvedText = "3차원 양안 A필러 가림 영역 기하 계산 알고리즘 구현 완료",
                prohibitedText = "실시간 완벽 가림 감지 또는 완전 사각지대 해소",
                evidenceClass = "HOST_UNIT_TEST",
                environment = "HOST_JVM",
                supportingFiles = "core/occlusion/BinocularOcclusionEngine.kt",
                qualifier = "기하학적 투영 모델 기준 계산 (운전자 두부 고정 가정)",
                expiryOrRevalidationCondition = "실차 시야각 센서 및 시선 추적기 연동 시 재검증",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-02",
                approvedText = "Python 프로토타입과 Kotlin 엔진 간의 수학적 동치성 검증 완료",
                prohibitedText = "양산 임베디드 펌웨어 수준 최적화 완료",
                evidenceClass = "DOCUMENT_CONFIRMED",
                environment = "HOST_JVM_AND_OFFLINE",
                supportingFiles = "reports/work_report_2.md",
                qualifier = "Host JVM 및 오프라인 시뮬레이션 산출물 기준",
                expiryOrRevalidationCondition = "코어 알고리즘 수식 변경 시 재검증",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-03",
                approvedText = "합성 시나리오 브로드캐스트 기반 Scenario L1 정적 시각 오버레이 구현",
                prohibitedText = "실제 주행 HUD 경고 활성화",
                evidenceClass = "SCENARIO_VERIFIED",
                environment = "DEBUG_EMULATOR_ONLY",
                supportingFiles = "ui/hud/SafetyWindowVectorHud.kt",
                qualifier = "Debug 빌드 시나리오 브로드캐스트 입력 한정 (Release Fail-Closed)",
                expiryOrRevalidationCondition = "L2/L3 및 실제 주행 HUD 활성화 인가 시 재검증",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-04",
                approvedText = "저장된 시계열 데이터(REC-01~08)에 대한 Replay 10회 결정론적 상태 전이 검증",
                prohibitedText = "실시간 센서 지터 및 클록 드리프트 완벽 흡수",
                evidenceClass = "REPLAY_VERIFIED",
                environment = "REPLAY_PIPELINE",
                supportingFiles = "core/replay/ReplayValidationPipeline.kt;audit/results/vhal_recording_replay_determinism.csv",
                qualifier = "가상 ReplayClock 기준 0.5x, 1.0x, 2.0x 배속 일치, 0 TTS, 0 L2/L3 출력",
                expiryOrRevalidationCondition = "신규 기록 세션 추가 또는 리플레이 클록 구조 변경 시",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-05",
                approvedText = "Pleos Connect AVD(x86_64) 상에서 Profileable Release 계산 지연 p50 0.51ms 측정",
                prohibitedText = "실차 하드웨어 및 차량용 SoC 실시간성 보장",
                evidenceClass = "ANDROID_RUNTIME_MEASUREMENT",
                environment = "PLEOS_CONNECT_AVD_X86_64",
                supportingFiles = "reports/android_runtime_performance_report.md",
                qualifier = "에뮬레이터 환경 측정치이며 실차 ARM 하드웨어 성능 아님",
                expiryOrRevalidationCondition = "실차 타깃 SoC(SA8295 등) 타깃 보드 확보 시 실측",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-06",
                approvedText = "Release 빌드 전면 시각/음성 출력 기본 차단(Fail-Closed) 및 TTS 발화 0회 강제",
                prohibitedText = "100% 무결점 페일세이프 시스템",
                evidenceClass = "HOST_UNIT_TEST",
                environment = "HOST_JVM_AND_RUNTIME",
                supportingFiles = "core/advisory/AdvisoryOutputFlags.kt;core/alert/TtsKillSwitch.kt",
                qualifier = "테스트된 조건(미지정, stale, 정책제한, 재시작) 하 차단 확인",
                expiryOrRevalidationCondition = "Release 경고 출력 정책 개정 시",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-07",
                approvedText = "공개 공간정보 및 공개 지도(NGII/VWorld) 및 항공정사영상 대조를 참조하여 판교 단일 데모 구간 횡단보도 데모 Feature 1개소 수동 구성 및 오버레이 실측 검증 완료 (원본 영상 바이너리 번들 제외, 재현절차/메타데이터 제공)",
                prohibitedText = "전국 도로망 자동 구축, 지도 정밀 측량 완료, 재배포 전면 허용, 상업용 소프트웨어 패키지 내 원본 영상 포함 가능",
                evidenceClass = "MANUALLY_VERIFIED_MAP_GEOMETRY",
                environment = "OFFLINE_SPATIAL_AUDIT",
                supportingFiles = "data/spatial/verified/feature_001_source_evidence.json;data/spatial/verified/feature_001_measurement_overlay.png",
                qualifier = "단일 교차로 횡단보도 형상 수동 구성 및 오버레이 측정값 한정, 원본 사용조건에 따른 배포범위 제한 준수",
                expiryOrRevalidationCondition = "공식 정밀도로지도(HD Map) 공급 계약 체결 시",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-08",
                approvedText = "Pleos Connect AVD에서 가용한 AAOS·Android·Scenario 채널을 분류하고, Virtual Console 공식 입력 경로(HTTP-DDS)를 통해 속도(CH-01)·조향(CH-02)·방향지시등(CH-03)·브레이크(CH-04)를 주입하여 VHAL·CarPropertyManager·Recorder E2E 전달 확인(VHAL_INJECTED_AND_OBSERVED)",
                prohibitedText = "실차 주행 센서 전체 연동 완료, 실제 주행 센서 전체 연동 완료 또는 실차 입력 기록 완료",
                evidenceClass = "ANDROID_RUNTIME_MEASUREMENT",
                environment = "PLEOS_CONNECT_AVD_VIRTUAL_CONSOLE",
                supportingFiles = "audit/recordings/REC-05_VHAL_SPEED.jsonl;audit/results/virtual_console_speed_e2e.csv;audit/results/vhal_recorder_source_integrity.csv",
                qualifier = "에뮬레이터 Virtual Console 가용 채널에 한정 (REC-02는 시나리오 픽스처로 분류, REC-05~08은 VHAL 실입력으로 분리)",
                expiryOrRevalidationCondition = "실차 CAN/이더넷 물리 버스 연결 시",
                status = ClaimLockStatus.LOCKED_APPROVED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-09",
                approvedText = "실차 하드웨어 및 실차 주행 데이터 미검증 상태 명시",
                prohibitedText = "실차 주행 테스트 완료 또는 실차 탑재 완료",
                evidenceClass = "DOCUMENT_CONFIRMED",
                environment = "DOCUMENT_AUDIT",
                supportingFiles = "reports/work_report_11.md",
                qualifier = "실차 테스트는 향후 실증 과제 범위임 (주장 전면 금지)",
                expiryOrRevalidationCondition = "실차 주행 인가 및 도로 실증 완료 시",
                status = ClaimLockStatus.LOCKED_PROHIBITED
            ),
            ProposalClaimLockEntry(
                claimId = "LCK-10",
                approvedText = "실제 사고율 감소 효과 및 안전성 향상 미검증 상태 명시",
                prohibitedText = "사고율 30% 감소 또는 획기적 보행자 안전 보장",
                evidenceClass = "DOCUMENT_CONFIRMED",
                environment = "DOCUMENT_AUDIT",
                supportingFiles = "reports/work_report_11.md",
                qualifier = "안전 효과 주장 전면 금지 (실증 데이터 미보유)",
                expiryOrRevalidationCondition = "공인 시험기관 실차 안전성 실증 데이터 취득 시",
                status = ClaimLockStatus.LOCKED_PROHIBITED
            )
        )
    }

    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append("claim_id,approved_text,prohibited_text,evidence_level,supporting_file,qualifier,status\n")
        getAllEntries().forEach { e ->
            sb.append("\"${e.claimId}\",")
            sb.append("\"${e.approvedText.replace("\"", "\"\"")}\",")
            sb.append("\"${e.prohibitedText.replace("\"", "\"\"")}\",")
            sb.append("\"${e.evidenceClass}\",")
            sb.append("\"${e.supportingFiles}\",")
            sb.append("\"${e.qualifier.replace("\"", "\"\"")}\",")
            sb.append("\"${e.status.name}\"\n")
        }
        return sb.toString()
    }

    fun toCsvV3(): String {
        val sb = StringBuilder()
        sb.append("claimId,approvedText,prohibitedText,evidenceClass,environment,supportingFiles,qualifier,expiryOrRevalidationCondition,status\n")
        getAllEntries().forEach { e ->
            sb.append("\"${e.claimId}\",")
            sb.append("\"${e.approvedText.replace("\"", "\"\"")}\",")
            sb.append("\"${e.prohibitedText.replace("\"", "\"\"")}\",")
            sb.append("\"${e.evidenceClass}\",")
            sb.append("\"${e.environment}\",")
            sb.append("\"${e.supportingFiles}\",")
            sb.append("\"${e.qualifier.replace("\"", "\"\"")}\",")
            sb.append("\"${e.expiryOrRevalidationCondition.replace("\"", "\"\"")}\",")
            sb.append("\"${e.status.name}\"\n")
        }
        return sb.toString()
    }
}
