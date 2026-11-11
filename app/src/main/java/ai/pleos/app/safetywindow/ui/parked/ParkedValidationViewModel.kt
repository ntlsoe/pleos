package ai.pleos.app.safetywindow.ui.parked

import ai.pleos.app.safetywindow.core.batch.ApproachProfile
import ai.pleos.app.safetywindow.core.batch.OcclusionBatchResultRow
import ai.pleos.app.safetywindow.core.batch.PilotOcclusionBatchRunner
import ai.pleos.app.safetywindow.core.spatial.GradeBFeatureRepository
import ai.pleos.app.safetywindow.core.spatial.GradeBSpatialFeature
import ai.pleos.app.safetywindow.core.vehicleprofile.VehicleProfileLoader
import ai.pleos.app.safetywindow.core.vehicleprofile.VehicleProfileRecord
import ai.pleos.app.safetywindow.core.vehicleprofile.VehiclePillarEvidenceGate
import ai.pleos.app.safetywindow.core.vehicleprofile.VehiclePillarUsageRoute
import java.io.File

data class ParkedValidationUiState(
    val selectedFeature: GradeBSpatialFeature?,
    val selectedVehicle: VehicleProfileRecord?,
    val selectedApproach: ApproachProfile?,
    val batchResult: OcclusionBatchResultRow?,
    val isReplayActive: Boolean = false,
    val overlayVisible: Boolean = false,
    val overlayStatus: String = "HIDDEN",
    val disclaimer: String = DISCLAIMER_TEXT
) {
    companion object {
        const val DISCLAIMER_TEXT =
            "본 화면은 공개 외형 제원 및 차체군별 합성 필러 형상 기반의 정차 검증(Parked Validation) 화면이며, " +
            "양산차의 실제 A필러 사각지대나 실제 도로 주행 경고로 적용되지 않습니다. (Release 안전 차단 유지)"
    }
}

/**
 * ParkedValidationViewModel
 * [작업명령 #16] 섹션 7: 정차 상태 검증(Parked Validation) 화면 뷰모델
 *
 * 엄격한 원칙:
 * - 화면 표시값은 자체 별도 계산 없이 런타임 엔진(PilotOcclusionBatchRunner) 결과를 100% 바인딩하여 사용
 * - Release에서는 시연·Replay·오버레이가 완전 차단(Hidden) 상태 유지
 */
class ParkedValidationViewModel(
    private val repository: GradeBFeatureRepository,
    private val vehiclesDir: File
) {
    private val confirmedFeatures: List<GradeBSpatialFeature> = repository.getAllConfirmedFeatures()
    private val vehicleProfiles: List<VehicleProfileRecord> = if (vehiclesDir.exists()) {
        VehicleProfileLoader.loadFromDirectory(vehiclesDir)
    } else emptyList()

    private val approaches: List<ApproachProfile> = PilotOcclusionBatchRunner.APPROACH_PROFILES

    private var currentZoneId: String = confirmedFeatures.firstOrNull()?.zoneId ?: "SZ_047"
    private var currentVehicleId: String = vehicleProfiles.firstOrNull()?.profileId ?: "HYUNDAI_IONIQ5_PUBLIC_DIMENSION_APPROX_V1"
    private var currentApproachId: String = approaches.firstOrNull()?.id ?: "APPROACH_STRAIGHT_30KMH"

    private var isReplayActive: Boolean = false
    private var overlayStatus: String = "HIDDEN"

    fun getUiState(): ParkedValidationUiState {
        val feat = repository.getFeature(currentZoneId)
        val veh = vehicleProfiles.find { it.profileId == currentVehicleId }
        val app = approaches.find { it.id == currentApproachId }

        val result = if (feat != null && veh != null && app != null) {
            PilotOcclusionBatchRunner.computeCombination(feat, veh, app)
        } else null

        return ParkedValidationUiState(
            selectedFeature = feat,
            selectedVehicle = veh,
            selectedApproach = app,
            batchResult = result,
            isReplayActive = isReplayActive,
            overlayVisible = overlayStatus != "HIDDEN",
            overlayStatus = overlayStatus
        )
    }

    fun selectZone(zoneId: String) {
        if (repository.isZoneConfirmedGradeB(zoneId)) {
            currentZoneId = zoneId
            resetReplay()
        }
    }

    fun selectVehicle(profileId: String) {
        if (vehicleProfiles.any { it.profileId == profileId }) {
            currentVehicleId = profileId
            resetReplay()
        }
    }

    fun selectApproach(approachId: String) {
        if (approaches.any { it.id == approachId }) {
            currentApproachId = approachId
            resetReplay()
        }
    }

    fun startReplayDemo() {
        isReplayActive = true
        // In Scenario/Replay demo mode, static informational overlay activates
        overlayStatus = "SCENARIO_L1_OVERLAY_ACTIVE"
    }

    fun stopReplayDemo() {
        resetReplay()
    }

    fun handleStalePosition() {
        // Stale or invalid signals immediately trigger Fail-Closed Hidden
        overlayStatus = "HIDDEN"
        isReplayActive = false
    }

    private fun resetReplay() {
        isReplayActive = false
        overlayStatus = "HIDDEN"
    }
}
