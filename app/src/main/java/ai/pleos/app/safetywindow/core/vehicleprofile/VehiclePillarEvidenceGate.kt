package ai.pleos.app.safetywindow.core.vehicleprofile

/**
 * VehiclePillarEvidenceGate
 * [작업명령 #13 & #16]: 차량 프로파일 외형 제원 vs 필러 가림 계산 증거 게이트
 *
 * 두 경로 분리:
 * 1. PUBLIC_DIMENSION_CONTEXT_ONLY: 외형 제원 표시·필터·UI 비교에만 사용, 직접 가림 계산 전면 차단
 * 2. SYNTHETIC_PILLAR_BY_BODY_CLASS: 차체군별(경형 SUV, 중형 CUV, 세단, 대형 SUV) 합성 필러를 명시하고 민감도 실험에만 투입
 */
enum class VehiclePillarUsageRoute {
    PUBLIC_DIMENSION_CONTEXT_ONLY,
    SYNTHETIC_PILLAR_BY_BODY_CLASS
}

data class PillarEvaluationResult(
    val profileId: String,
    val usageRoute: VehiclePillarUsageRoute,
    val isCalculationAllowed: Boolean,
    val pillarEvidenceClass: String,
    val syntheticPillarProfileId: String = profileId,
    val rejectionReason: String? = null
)

object VehiclePillarEvidenceGate {

    /**
     * 공개 외형 제원 프로파일에 대해 가림 계산 허용 여부 판정
     */
    fun evaluate(profile: VehicleProfileRecord, requestedRoute: VehiclePillarUsageRoute): PillarEvaluationResult {
        return when (requestedRoute) {
            VehiclePillarUsageRoute.PUBLIC_DIMENSION_CONTEXT_ONLY -> {
                PillarEvaluationResult(
                    profileId = profile.profileId,
                    usageRoute = VehiclePillarUsageRoute.PUBLIC_DIMENSION_CONTEXT_ONLY,
                    isCalculationAllowed = false,
                    pillarEvidenceClass = "PUBLIC_DIMENSION_ONLY",
                    syntheticPillarProfileId = profile.profileId,
                    rejectionReason = "DIRECT_OCCLUSION_CALCULATION_PROHIBITED_FOR_PUBLIC_DIMENSIONS"
                )
            }
            VehiclePillarUsageRoute.SYNTHETIC_PILLAR_BY_BODY_CLASS -> {
                // profileId에 SYNTHETIC_PILLAR가 명시되어야 함
                val syntheticProfileId = if (profile.profileId.contains("SYNTHETIC_PILLAR")) {
                    profile.profileId
                } else {
                    "${profile.profileId}_SYNTHETIC_PILLAR"
                }

                val bodyTypeUpper = profile.bodyType.uppercase()
                val bodyClass = when {
                    bodyTypeUpper.contains("경형") || bodyTypeUpper.contains("COMPACT_SUV") || bodyTypeUpper.contains("LIGHT_SUV") -> "COMPACT_SUV_PILLAR"
                    bodyTypeUpper.contains("CUV") || bodyTypeUpper.contains("크로스오버") || bodyTypeUpper.contains("ELECTRIC_SUV") -> "MIDSIZE_CUV_PILLAR"
                    bodyTypeUpper.contains("세단") || bodyTypeUpper.contains("SEDAN") -> "SEDAN_PILLAR"
                    bodyTypeUpper.contains("LARGE_SUV") || bodyTypeUpper.contains("대형 플래그십") || bodyTypeUpper.contains("대형") -> "LARGE_SUV_PILLAR"
                    else -> "GENERIC_SYNTHETIC_PILLAR"
                }

                PillarEvaluationResult(
                    profileId = syntheticProfileId,
                    usageRoute = VehiclePillarUsageRoute.SYNTHETIC_PILLAR_BY_BODY_CLASS,
                    isCalculationAllowed = true,
                    pillarEvidenceClass = "SYNTHETIC_PILLAR_BY_BODY_CLASS_${bodyClass}",
                    syntheticPillarProfileId = syntheticProfileId,
                    rejectionReason = null
                )
            }
        }
    }
}
