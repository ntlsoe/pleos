package ai.pleos.app.safetywindow.core.occlusion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

data class UncertaintyConfig(
    val posSigmaMeters: Double = 0.0,
    val headingSigmaDegrees: Double = 0.0,
    val timestampDelayMillis: Long = 0L,
    val dropoutProbability: Double = 0.0,
    val fixedBiasX: Double = 0.0,
    val fixedBiasY: Double = 0.0,
    val seed: Long = 42L
)

/**
 * Portable, deterministic Linear Congruential Generator matching Python random.Random seed behavior
 * for Gaussian perturbation.
 */
class DeterministicGaussianRng(seed: Long) {
    private var state: Long = (seed xor 0x5DEECE66DL) and ((1L shl 48) - 1)

    private fun nextBits(bits: Int): Int {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (state ushr (48 - bits)).toInt()
    }

    fun nextDouble(): Double {
        val l = ((nextBits(26).toLong() shl 27) + nextBits(27))
        return l / (1L shl 53).toDouble()
    }

    fun nextGaussian(mean: Double, sigma: Double): Double {
        if (sigma <= 0.0) return mean
        var u1 = nextDouble()
        while (u1 <= 1e-15) {
            u1 = nextDouble()
        }
        val u2 = nextDouble()
        val z0 = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return mean + z0 * sigma
    }
}

object UncertaintyModel {
    fun applyUncertainty(poses: List<VehiclePose>, config: UncertaintyConfig): List<VehiclePose> {
        if (config.posSigmaMeters == 0.0 &&
            config.headingSigmaDegrees == 0.0 &&
            config.fixedBiasX == 0.0 &&
            config.fixedBiasY == 0.0 &&
            config.dropoutProbability == 0.0 &&
            config.timestampDelayMillis == 0L
        ) {
            return poses
        }

        val rng = DeterministicGaussianRng(config.seed)
        val headingSigmaRad = config.headingSigmaDegrees * (PI / 180.0)
        val delaySec = config.timestampDelayMillis / 1000.0

        val perturbed = ArrayList<VehiclePose>(poses.size)
        var lastValidPose = poses[0]

        for (p in poses) {
            if (config.dropoutProbability > 0.0 && rng.nextDouble() < config.dropoutProbability) {
                perturbed.add(
                    VehiclePose(
                        x = lastValidPose.x,
                        y = lastValidPose.y,
                        heading = lastValidPose.heading,
                        speedMps = lastValidPose.speedMps,
                        timeSec = p.timeSec
                    )
                )
                continue
            }

            val noiseX = rng.nextGaussian(0.0, config.posSigmaMeters)
            val noiseY = rng.nextGaussian(0.0, config.posSigmaMeters)
            val noiseH = rng.nextGaussian(0.0, headingSigmaRad)

            val newX = p.x + config.fixedBiasX + noiseX
            val newY = p.y + config.fixedBiasY + noiseY
            val newH = p.heading + noiseH
            val newT = p.timeSec + delaySec

            val newPose = VehiclePose(x = newX, y = newY, heading = newH, speedMps = p.speedMps, timeSec = newT)
            perturbed.add(newPose)
            lastValidPose = newPose
        }

        return perturbed
    }
}
