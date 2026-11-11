package ai.pleos.app.safetywindow.core.occlusion

data class EyePair(
    val left: Point3,
    val right: Point3
)

data class WeightedEyePair(
    val eyePair: EyePair,
    val weight: Double,
    val source: String,
    val sampleId: String
)

interface EyePopulationModel {
    val modelId: String
    val source: String
    fun generateSamples(): List<WeightedEyePair>
}

data class EyeSampleSpec(
    val id: String,
    val weight: Double,
    val deltaX: Double,
    val deltaY: Double,
    val deltaZ: Double,
    val ipdMeters: Double
)

class ConfigurableEyePopulationModel(
    override val modelId: String,
    override val source: String,
    val nominalEye: Point3,
    val sampleSpecs: List<EyeSampleSpec>
) : EyePopulationModel {

    override fun generateSamples(): List<WeightedEyePair> {
        return sampleSpecs.map { s ->
            val cx = nominalEye.x + s.deltaX
            val cy = nominalEye.y + s.deltaY
            val cz = nominalEye.z + s.deltaZ
            val halfIpd = s.ipdMeters / 2.0
            val left = Point3(cx, cy + halfIpd, cz)
            val right = Point3(cx, cy - halfIpd, cz)
            WeightedEyePair(
                eyePair = EyePair(left, right),
                weight = s.weight,
                source = source,
                sampleId = s.id
            )
        }
    }

    companion object {
        fun createSyntheticCenter(nominalEye: Point3): ConfigurableEyePopulationModel {
            return ConfigurableEyePopulationModel(
                modelId = "SyntheticCenterProfile",
                source = "SYNTHETIC_TEST_ONLY",
                nominalEye = nominalEye,
                sampleSpecs = listOf(
                    EyeSampleSpec("center_nominal", 1.0, 0.0, 0.0, 0.0, 0.065)
                )
            )
        }

        fun createRepresentativePopulation(nominalEye: Point3): ConfigurableEyePopulationModel {
            return ConfigurableEyePopulationModel(
                modelId = "RepresentativePopulationApproximation",
                source = "J941_CONCEPT_APPROXIMATION",
                nominalEye = nominalEye,
                sampleSpecs = listOf(
                    EyeSampleSpec("center_mid", 0.28, 0.00, 0.00, 0.00, 0.065),
                    EyeSampleSpec("tall_aft", 0.12, -0.05, 0.01, 0.05, 0.067),
                    EyeSampleSpec("short_fore", 0.12, 0.05, -0.01, -0.05, 0.063),
                    EyeSampleSpec("high_mid", 0.10, 0.00, 0.00, 0.04, 0.065),
                    EyeSampleSpec("low_mid", 0.10, 0.00, 0.00, -0.04, 0.065),
                    EyeSampleSpec("aft_mid", 0.08, -0.04, 0.00, 0.00, 0.065),
                    EyeSampleSpec("fore_mid", 0.08, 0.04, 0.00, 0.00, 0.065),
                    EyeSampleSpec("left_mid", 0.06, 0.00, 0.02, 0.00, 0.065),
                    EyeSampleSpec("right_mid", 0.06, 0.00, -0.02, 0.00, 0.065)
                )
            )
        }
    }
}
