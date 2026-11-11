package ai.pleos.app.safetywindow.core.input

import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose

/**
 * Signal-by-signal provenance model (Section 4.1 & 4.2).
 * Automatically computes the union of all signal sources into [inputSources].
 */
data class InputProvenance(
    val speedSource: InputSource,
    val steeringSource: InputSource,
    val turnSignalSource: InputSource,
    val brakeSource: InputSource,
    val locationSource: InputSource,
    val headingSource: InputSource,
    val spatialFeatureSource: InputSource,
    val vehicleProfileSource: InputSource = InputSource.STATIC_PROFILE,
    val eyeModelSource: InputSource = InputSource.STATIC_PROFILE,
    val includeExternalGeoJsonTag: Boolean = false
) {
    /**
     * Automatic union of all signal sources.
     * Guarantees that synthetic spatial features or static profiles are never omitted even when speed is AAOS.
     */
    val inputSources: Set<InputSource> = buildSet {
        add(speedSource)
        add(steeringSource)
        add(turnSignalSource)
        add(brakeSource)
        add(locationSource)
        add(headingSource)
        add(spatialFeatureSource)
        add(vehicleProfileSource)
        add(eyeModelSource)
        if (includeExternalGeoJsonTag) {
            add(InputSource.EXTERNAL_GEOJSON)
        }
    }

    fun hasUnknownSource(): Boolean = inputSources.contains(InputSource.UNKNOWN)

    fun hasScenarioSource(): Boolean = inputSources.contains(InputSource.SCENARIO)

    fun hasStaticProfileSource(): Boolean = inputSources.contains(InputSource.STATIC_PROFILE)

    fun toJsonObjectString(): String {
        return buildString {
            append("{")
            append("\"speed\":\"${speedSource.name}\",")
            append("\"steering\":\"${steeringSource.name}\",")
            append("\"turnSignal\":\"${turnSignalSource.name}\",")
            append("\"brake\":\"${brakeSource.name}\",")
            append("\"location\":\"${locationSource.name}\",")
            append("\"heading\":\"${headingSource.name}\",")
            append("\"spatialFeature\":\"${spatialFeatureSource.name}\",")
            append("\"vehicleProfile\":\"${vehicleProfileSource.name}\",")
            append("\"eyeModel\":\"${eyeModelSource.name}\"")
            append("}")
        }
    }

    companion object {
        fun resolveSpatialFeatureSource(feature: RiskFeature): InputSource {
            val srcUpper = feature.source.uppercase()
            return when {
                srcUpper == "UNKNOWN" -> InputSource.UNKNOWN
                feature.coordinateQuality == SpatialQuality.SCENARIO ||
                    feature.coordinateQuality == SpatialQuality.SYNTHETIC_SCENARIO ||
                    srcUpper.contains("SYNTHETIC") ||
                    srcUpper.contains("SCENARIO") ||
                    srcUpper.contains("DEMO") -> InputSource.SCENARIO
                srcUpper.contains("GEOJSON") || srcUpper.contains("PUBLIC") -> InputSource.EXTERNAL_GEOJSON
                srcUpper.contains("NAVIGATION") || srcUpper.contains("NAVI") -> InputSource.NAVIGATION
                else -> InputSource.SCENARIO
            }
        }

        fun isExternalGeoJsonFeature(feature: RiskFeature): Boolean {
            val srcUpper = feature.source.uppercase()
            return srcUpper.contains("GEOJSON") ||
                srcUpper.contains("SYNTHETIC_SCENARIO") ||
                feature.featureId.startsWith("DEMO_")
        }

        fun fromPoseAndFeature(
            pose: VehiclePose,
            feature: RiskFeature,
            speedSource: InputSource = pose.source,
            steeringSource: InputSource = pose.source,
            turnSignalSource: InputSource = pose.source,
            brakeSource: InputSource = pose.source,
            vehicleProfileSource: InputSource = InputSource.STATIC_PROFILE,
            eyeModelSource: InputSource = InputSource.STATIC_PROFILE
        ): InputProvenance {
            val spatialSrc = resolveSpatialFeatureSource(feature)
            val hasGeoJsonTag = isExternalGeoJsonFeature(feature)
            return InputProvenance(
                speedSource = speedSource,
                steeringSource = steeringSource,
                turnSignalSource = turnSignalSource,
                brakeSource = brakeSource,
                locationSource = pose.source,
                headingSource = pose.source,
                spatialFeatureSource = spatialSrc,
                vehicleProfileSource = vehicleProfileSource,
                eyeModelSource = eyeModelSource,
                includeExternalGeoJsonTag = hasGeoJsonTag
            )
        }

        fun createPureScenario(
            spatialFeatureSource: InputSource = InputSource.SCENARIO
        ): InputProvenance = InputProvenance(
            speedSource = InputSource.SCENARIO,
            steeringSource = InputSource.SCENARIO,
            turnSignalSource = InputSource.SCENARIO,
            brakeSource = InputSource.SCENARIO,
            locationSource = InputSource.SCENARIO,
            headingSource = InputSource.SCENARIO,
            spatialFeatureSource = spatialFeatureSource,
            vehicleProfileSource = InputSource.STATIC_PROFILE,
            eyeModelSource = InputSource.STATIC_PROFILE,
            includeExternalGeoJsonTag = true
        )

        fun createScenarioWithAaosVehicleState(
            spatialFeatureSource: InputSource = InputSource.SCENARIO
        ): InputProvenance = InputProvenance(
            speedSource = InputSource.AAOS,
            steeringSource = InputSource.AAOS,
            turnSignalSource = InputSource.AAOS,
            brakeSource = InputSource.AAOS,
            locationSource = InputSource.AAOS,
            headingSource = InputSource.AAOS,
            spatialFeatureSource = spatialFeatureSource,
            vehicleProfileSource = InputSource.STATIC_PROFILE,
            eyeModelSource = InputSource.STATIC_PROFILE,
            includeExternalGeoJsonTag = true
        )
    }
}
