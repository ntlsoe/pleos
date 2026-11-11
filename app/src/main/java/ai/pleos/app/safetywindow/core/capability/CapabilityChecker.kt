package ai.pleos.app.safetywindow.core.capability

import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * CapabilityChecker
 *
 * 차종/트림 하드코딩 분기 없이 동적으로 AAOS 차량 센서 가용성,
 * Pleos FusedLocation, Vehicle SDK, ADAS 가용성을 사전 검증하는 공용 유틸리티.
 */
object CapabilityChecker {

    private const val TAG = "CapabilityChecker"

    data class SystemCapabilities(
        val isSpeedSensorAvailable: Boolean,
        val isSteeringSensorAvailable: Boolean,
        val isFusedLocationSupported: Boolean,
        val isAutomotivePlatform: Boolean,
        val hasLocationPermission: Boolean
    )

    fun checkAllCapabilities(
        context: Context,
        carPropertyManager: CarPropertyManager?
    ): SystemCapabilities {
        val speedAvail = isSpeedSensorAvailable(carPropertyManager)
        val steeringAvail = isSteeringSensorAvailable(carPropertyManager)
        val fusedLocAvail = isFusedLocationSupported(context)
        val isAuto = isAutomotivePlatform(context)
        val hasLocPerm = hasLocationPermissions(context)

        Log.i(
            TAG,
            "Capabilities: Speed=$speedAvail, Steering=$steeringAvail, " +
                    "FusedLocation=$fusedLocAvail, Automotive=$isAuto, LocPerm=$hasLocPerm"
        )

        return SystemCapabilities(
            isSpeedSensorAvailable = speedAvail,
            isSteeringSensorAvailable = steeringAvail,
            isFusedLocationSupported = fusedLocAvail,
            isAutomotivePlatform = isAuto,
            hasLocationPermission = hasLocPerm
        )
    }

    fun isSpeedSensorAvailable(carPropertyManager: CarPropertyManager?): Boolean {
        if (carPropertyManager == null) return false
        return try {
            carPropertyManager.isPropertyAvailable(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Speed sensor check failed: ${e.message}")
            false
        }
    }

    fun isSteeringSensorAvailable(carPropertyManager: CarPropertyManager?): Boolean {
        if (carPropertyManager == null) return false
        return try {
            // Check WHEEL_TICK or custom steering if present in AAOS
            carPropertyManager.isPropertyAvailable(VehiclePropertyIds.WHEEL_TICK, 0)
        } catch (e: Exception) {
            false
        }
    }

    fun isFusedLocationSupported(context: Context): Boolean {
        return try {
            val clazz = Class.forName("ai.pleos.playground.location.fused.FusedLocation")
            val isSupportedMethod = clazz.getMethod("isSupported", Context::class.java)
            (isSupportedMethod.invoke(null, context) as? Boolean) ?: true
        } catch (e: Exception) {
            Log.d(TAG, "FusedLocation.isSupported fallback: ${e.message}")
            true
        }
    }

    fun isAutomotivePlatform(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    fun hasLocationPermissions(context: Context): Boolean {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
