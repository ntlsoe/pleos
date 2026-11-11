package ai.pleos.app.safetywindow

import ai.pleos.app.safetywindow.core.advisory.GeometryAdvisoryRawFrame
import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.core.model.InputSource
import ai.pleos.app.safetywindow.core.spatial.RiskFeature
import ai.pleos.app.safetywindow.core.spatial.SpatialQuality
import ai.pleos.app.safetywindow.core.spatial.VehiclePose
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.app.safetywindow.ui.hud.SafetyViewModel
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest

/** Debug-only raw JSONL player. It reads no candidate or output fields from the fixture. */
internal object Work48Set07RawFixtureProvider {
    fun play(context: Context, viewModel: SafetyViewModel) {
        Thread({
            viewModel.beginGeometryRawFixture()
            val lines = context.assets.open("work48_set07_raw_fixture.jsonl").bufferedReader().useLines { sequence ->
                sequence.filter { it.isNotBlank() }.toList()
            }
            lines.forEachIndexed { index, line ->
                    val frame = parse(line)
                    val sha = sha256(line)
                    val evaluation = viewModel.evaluateGeometryRawFrame(frame)
                    Log.i(
                        "Work48FixtureTrace",
                        "inputSha256=$sha timestamp=${frame.timestampMillis} candidate=${evaluation?.decision?.geometryCandidateLevel} effective=${evaluation?.decision?.effectiveLevel} eligibility=${evaluation?.decision?.advisoryEligibility} hits=${evaluation?.geometry?.binocularHits} ratio=${evaluation?.geometry?.peakPossibleRatio} continuousMs=${evaluation?.continuousOcclusionMs} reason=${evaluation?.decision?.suppressionReason}"
                    )
                    if (index + 1 < lines.size) {
                        val nextTimestamp = JSONObject(lines[index + 1]).getLong("timestampMillis")
                        Thread.sleep((nextTimestamp - frame.timestampMillis).coerceAtLeast(0L))
                    }
            }
            Thread.sleep(600L)
            viewModel.endGeometryRawFixture()
        }, "Work48-Set07-RawFixture").apply { isDaemon = true }.start()
    }

    internal fun parse(line: String): GeometryAdvisoryRawFrame {
        val json = JSONObject(line)
        val timestamp = json.getLong("timestampMillis")
        val featureQuality = SpatialQuality.valueOf(json.getString("featureQuality"))
        val poseQuality = SpatialQuality.valueOf(json.getString("poseQuality"))
        val feature = RiskFeature(
            featureId = json.getString("featureId"),
            riskType = json.getString("riskType"),
            source = json.getString("source"),
            geometryMethod = json.getString("geometryMethod"),
            manualVerified = json.getBoolean("manualVerified"),
            coordinateQuality = featureQuality,
            widthMeters = json.getDouble("widthMeters"),
            lengthMeters = json.getDouble("lengthMeters"),
            directionDegrees = json.getDouble("directionDegrees"),
            centerEnu = Triple(json.getDouble("centerEast"), json.getDouble("centerNorth"), json.getDouble("centerUp"))
        )
        val pose = VehiclePose(
            localX = json.getDouble("poseX"),
            localY = json.getDouble("poseY"),
            headingRadians = json.getDouble("headingRadians"),
            timestampMillis = timestamp,
            source = InputSource.valueOf(json.getString("poseSource")),
            quality = poseQuality,
            speedMps = json.getDouble("speedMps")
        )
        return GeometryAdvisoryRawFrame(
            feature = feature,
            pose = pose,
            speedKmh = json.getDouble("speedKmh"),
            turnSignal = TurnSignalState.valueOf(json.getString("turnSignal")),
            steeringAngleDeg = json.getDouble("steeringAngleDeg"),
            turnRadiusMeters = json.getDouble("turnRadiusMeters"),
            remainingPathDistanceMeters = json.getDouble("remainingPathDistanceMeters"),
            timestampMillis = timestamp,
            executionMode = ExecutionMode.valueOf(json.getString("executionMode"))
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
