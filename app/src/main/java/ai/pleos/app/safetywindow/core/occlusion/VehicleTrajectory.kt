package ai.pleos.app.safetywindow.core.occlusion

import kotlin.math.cos
import kotlin.math.sin

data class VehiclePose(
    val x: Double,
    val y: Double,
    val heading: Double, // radians (0 = facing +X)
    val speedMps: Double,
    val timeSec: Double
) {
    fun worldToVehicle(pWorld: Point3): Point3 {
        val dx = pWorld.x - x
        val dy = pWorld.y - y
        val cosH = cos(heading)
        val sinH = sin(heading)

        val xVeh = cosH * dx + sinH * dy
        val yVeh = -sinH * dx + cosH * dy
        val zVeh = pWorld.z
        return Point3(xVeh, yVeh, zVeh)
    }
}

object VehicleTrajectoryGenerator {
    val ACCELERATION_PROFILES = mapOf(
        "GENTLE" to 0.8,
        "NORMAL" to 1.5,
        "BRISK" to 2.5
    )

    fun generateStraight(speedKmh: Double, durationSec: Double, dt: Double = 0.05): List<VehiclePose> {
        val speedMps = speedKmh / 3.6
        val numSteps = (durationSec / dt).toInt() + 1
        val poses = ArrayList<VehiclePose>(numSteps)
        for (i in 0 until numSteps) {
            val t = i * dt
            val x = speedMps * t
            poses.add(VehiclePose(x = x, y = 0.0, heading = 0.0, speedMps = speedMps, timeSec = t))
        }
        return poses
    }

    fun generateConstantRadius(
        radiusM: Double,
        speedKmh: Double,
        isRight: Boolean,
        durationSec: Double,
        dt: Double = 0.05
    ): List<VehiclePose> {
        require(radiusM > 0.0) { "Radius must be positive" }
        val speedMps = speedKmh / 3.6
        val omega = (speedMps / radiusM) * (if (isRight) -1.0 else 1.0)

        val numSteps = (durationSec / dt).toInt() + 1
        val poses = ArrayList<VehiclePose>(numSteps)
        var currX = 0.0
        var currY = 0.0
        var currH = 0.0

        for (i in 0 until numSteps) {
            val t = i * dt
            poses.add(VehiclePose(x = currX, y = currY, heading = currH, speedMps = speedMps, timeSec = t))
            currH += omega * dt
            currX += speedMps * cos(currH) * dt
            currY += speedMps * sin(currH) * dt
        }
        return poses
    }

    fun generateStopAndGo(
        targetSpeedKmh: Double,
        accelProfile: String,
        durationSec: Double,
        dt: Double = 0.05,
        turnRadiusM: Double = 0.0,
        isRight: Boolean = false
    ): List<VehiclePose> {
        val targetV = targetSpeedKmh / 3.6
        val a = ACCELERATION_PROFILES[accelProfile.uppercase()] ?: 1.5
        val numSteps = (durationSec / dt).toInt() + 1

        val poses = ArrayList<VehiclePose>(numSteps)
        var currX = 0.0
        var currY = 0.0
        var currH = 0.0
        var currV = 0.0

        for (i in 0 until numSteps) {
            val t = i * dt
            poses.add(VehiclePose(x = currX, y = currY, heading = currH, speedMps = currV, timeSec = t))
            if (currV < targetV) {
                currV = minOf(targetV, currV + a * dt)
            }
            if (turnRadiusM > 0.1) {
                val omega = (currV / turnRadiusM) * (if (isRight) -1.0 else 1.0)
                currH += omega * dt
            }
            currX += currV * cos(currH) * dt
            currY += currV * sin(currH) * dt
        }
        return poses
    }
}
