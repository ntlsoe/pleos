package ai.pleos.app.safetywindow.data.spatial

import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.policy.RiskPolicy
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class CautionType {
    SCHOOL_ZONE,
    ACCIDENT_PRONE_AREA,
    NARROW_ROAD,
    PEDESTRIAN_CROSSWALK
}

enum class TbtDirection {
    NONE,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    U_TURN
}

data class CautionPoint(
    val id: String,
    val type: CautionType,
    val distanceMeters: Float,
    val latitude: Double,
    val longitude: Double,
    val speedLimitKmh: Int = 30,
    val source: InputSource = InputSource.NAVIGATION
)

data class TbtInfo(
    val direction: TbtDirection,
    val distanceMeters: Float,
    val roadName: String = "",
    val source: InputSource = InputSource.NAVIGATION
)

/**
 * SpatialContextEngine
 *
 * 내비게이션(NaviHelper) 및 지오펜싱(FusedLocation) 기반 공간 위험 컨텍스트 수신.
 * 하드코딩 없이 RiskPolicy에 외부화된 파라미터로 동작하며,
 * 스쿨존/협소도로 진입 자체만으로 단독 경고를 발령하지 않고 배경 Zone Context로만 유지한다.
 */
class SpatialContextEngine(
    private val context: Context,
    private val policy: RiskPolicy = RiskPolicy()
) {

    private val _activeCautionPoint = MutableStateFlow<CautionPoint?>(null)
    val activeCautionPoint: StateFlow<CautionPoint?> = _activeCautionPoint.asStateFlow()

    private val _currentTbtInfo = MutableStateFlow(TbtInfo(TbtDirection.NONE, 0f))
    val currentTbtInfo: StateFlow<TbtInfo> = _currentTbtInfo.asStateFlow()

    private val _isSchoolZoneTimeWeighted = MutableStateFlow(false)
    val isSchoolZoneTimeWeighted: StateFlow<Boolean> = _isSchoolZoneTimeWeighted.asStateFlow()

    init {
        evaluateTimeWeighting()
    }

    fun evaluateTimeWeighting(
        hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        minute: Int = Calendar.getInstance().get(Calendar.MINUTE)
    ): Boolean {
        if (!policy.schoolZoneTimeWeightEnabled) {
            _isSchoolZoneTimeWeighted.value = false
            return false
        }
        val isWeighted = policy.schoolZoneTimeRanges.any { it.contains(hour, minute) }
        _isSchoolZoneTimeWeighted.value = isWeighted
        return isWeighted
    }

    fun onNaviCautionPointReceived(point: CautionPoint) {
        val baseThreshold = policy.defaultCautionDistanceMeters
        val effectiveThreshold = if (point.type == CautionType.SCHOOL_ZONE && _isSchoolZoneTimeWeighted.value) {
            baseThreshold + policy.preActivationDistanceMeters
        } else {
            baseThreshold
        }

        if (point.distanceMeters <= effectiveThreshold) {
            _activeCautionPoint.value = point
            Log.d(TAG, "Zone Context active: ${point.type} (dist=${point.distanceMeters}m, threshold=${effectiveThreshold}m)")
        } else {
            if (_activeCautionPoint.value?.id == point.id) {
                _activeCautionPoint.value = null
            }
        }
    }

    fun onTbtInfoReceived(tbt: TbtInfo) {
        _currentTbtInfo.value = tbt
    }

    companion object {
        private const val TAG = "SpatialContextEngine"
    }
}
