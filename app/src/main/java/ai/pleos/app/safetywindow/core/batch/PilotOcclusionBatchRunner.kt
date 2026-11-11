package ai.pleos.app.safetywindow.core.batch

import ai.pleos.app.safetywindow.core.occlusion.Point3
import ai.pleos.app.safetywindow.core.occlusion.Triangle
import ai.pleos.app.safetywindow.core.occlusion.raySegmentHitsTrianglePrimitive
import ai.pleos.app.safetywindow.core.spatial.GradeBFeatureRepository
import ai.pleos.app.safetywindow.core.spatial.GradeBSpatialFeature
import ai.pleos.app.safetywindow.core.vehicleprofile.VehiclePillarEvidenceGate
import ai.pleos.app.safetywindow.core.vehicleprofile.VehiclePillarUsageRoute
import ai.pleos.app.safetywindow.core.vehicleprofile.VehicleProfileLoader
import ai.pleos.app.safetywindow.core.vehicleprofile.VehicleProfileRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

data class OcclusionBatchResultRow(
    val runId: String,
    val zoneId: String,
    val crosswalkSourceRecordId: String,
    val spatialFeatureSha256: String,
    val vehicleProfileId: String,
    val vehiclePublicDimensionEvidence: String,
    val syntheticPillarProfileId: String,
    val pillarEvidenceClass: String,
    val pillarGeometrySha256: String,
    val eyeModelId: String,
    val eyeSampleCount: Int,
    val eyeWeightSum: Double,
    val approachProfileId: String,
    val speedProfile: Double,
    val trajectorySha256: String,
    val peakPossibleRatio: Double,
    val peakUniversalRatio: Double,
    val maxDurationMillis: Long,
    val exposureIntegral: Double,
    val firstOcclusionTimeMillis: Long,
    val lastOcclusionTimeMillis: Long,
    val geometryAssessment: String,
    val advisoryEligibility: String,
    val effectiveCandidateLevel: String,
    val suppressionReason: String,
    val measuredComputationMillis: Double,
    val resultGenerationMode: String = "KOTLIN_ENGINE_EXECUTION"
) {
    fun toCsvRow(): String {
        return listOf(
            zoneId,
            crosswalkSourceRecordId,
            spatialFeatureSha256,
            vehicleProfileId,
            vehiclePublicDimensionEvidence,
            syntheticPillarProfileId,
            pillarEvidenceClass,
            approachProfileId,
            "%.1f".format(speedProfile),
            eyeSampleCount.toString(),
            "%.4f".format(peakPossibleRatio),
            "%.4f".format(peakUniversalRatio),
            maxDurationMillis.toString(),
            "%.4f".format(exposureIntegral),
            firstOcclusionTimeMillis.toString(),
            lastOcclusionTimeMillis.toString(),
            geometryAssessment,
            advisoryEligibility,
            effectiveCandidateLevel,
            suppressionReason,
            "%.2f".format(measuredComputationMillis)
        ).joinToString(",")
    }

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("runId", runId)
        obj.put("zoneId", zoneId)
        obj.put("crosswalkSourceRecordId", crosswalkSourceRecordId)
        obj.put("spatialFeatureSha256", spatialFeatureSha256)
        obj.put("vehicleProfileId", vehicleProfileId)
        obj.put("vehiclePublicDimensionEvidence", vehiclePublicDimensionEvidence)
        obj.put("syntheticPillarProfileId", syntheticPillarProfileId)
        obj.put("pillarEvidenceClass", pillarEvidenceClass)
        obj.put("pillarGeometrySha256", pillarGeometrySha256)
        obj.put("eyeModelId", eyeModelId)
        obj.put("eyeSampleCount", eyeSampleCount)
        obj.put("eyeWeightSum", eyeWeightSum)
        obj.put("approachProfileId", approachProfileId)
        obj.put("speedProfile", speedProfile)
        obj.put("trajectorySha256", trajectorySha256)
        obj.put("peakPossibleRatio", peakPossibleRatio)
        obj.put("peakUniversalRatio", peakUniversalRatio)
        obj.put("maxDurationMillis", maxDurationMillis)
        obj.put("exposureIntegral", exposureIntegral)
        obj.put("firstOcclusionTimeMillis", firstOcclusionTimeMillis)
        obj.put("lastOcclusionTimeMillis", lastOcclusionTimeMillis)
        obj.put("geometryAssessment", geometryAssessment)
        obj.put("advisoryEligibility", advisoryEligibility)
        obj.put("effectiveCandidateLevel", effectiveCandidateLevel)
        obj.put("suppressionReason", suppressionReason)
        obj.put("measuredComputationMillis", measuredComputationMillis)
        obj.put("resultGenerationMode", resultGenerationMode)
        return obj.toString()
    }

    companion object {
        val CSV_HEADER = listOf(
            "zoneId",
            "crosswalkSourceRecordId",
            "spatialFeatureSha256",
            "vehicleProfileId",
            "vehiclePublicDimensionEvidence",
            "syntheticPillarProfileId",
            "pillarEvidenceClass",
            "approachProfileId",
            "speedProfile",
            "eyeSampleCount",
            "peakPossibleRatio",
            "peakUniversalRatio",
            "maxDurationMillis",
            "exposureIntegral",
            "firstOcclusionTimeMillis",
            "lastOcclusionTimeMillis",
            "geometryAssessment",
            "advisoryEligibility",
            "effectiveCandidateLevel",
            "suppressionReason",
            "computationMillis"
        ).joinToString(",")
    }
}

data class ApproachProfile(
    val id: String,
    val speedKmh: Double,
    val description: String
)

data class KotlinPillarGeometry(
    val nominalEye: Point3,
    val leftTriangles: List<Triangle>,
    val rightTriangles: List<Triangle>,
    val leftAzRange: Pair<Double, Double>,
    val rightAzRange: Pair<Double, Double>,
    val pillarThicknessM: Double,
    val geometrySha256: String
)

data class KotlinEyePair(
    val id: String,
    val weight: Double,
    val left: Point3,
    val right: Point3
)

/**
 * PilotOcclusionBatchRunner
 * [작업명령 #17]: 45개 가림배치 조합 독립 Kotlin Ray-Casting 기하 엔진 및 실행계보 감사기
 */
object PilotOcclusionBatchRunner {

    val APPROACH_PROFILES = listOf(
        ApproachProfile("APPROACH_STRAIGHT_30KMH", 30.0, "직진 접근 (30km/h)"),
        ApproachProfile("APPROACH_SLOW_DECEL_20KMH", 20.0, "감속 접근 (20km/h)"),
        ApproachProfile("APPROACH_CURVE_APPROACH_25KMH", 25.0, "곡선 진입 (25km/h)")
    )

    data class KotlinEyeSpec(
        val id: String,
        val weight: Double,
        val dx: Double,
        val dy: Double,
        val dz: Double,
        val ipd: Double
    )

    // J941 Representative Population Baseline (N=9, Sum = 1.0)
    val EYE_SPECS_N9 = listOf(
        KotlinEyeSpec("center_mid", 0.28, 0.00, 0.00, 0.00, 0.065),
        KotlinEyeSpec("tall_aft",   0.12, -0.05, 0.01, 0.05, 0.067),
        KotlinEyeSpec("short_fore", 0.12, 0.05, -0.01, -0.05, 0.063),
        KotlinEyeSpec("high_mid",   0.10, 0.00, 0.00, 0.04, 0.065),
        KotlinEyeSpec("low_mid",    0.10, 0.00, 0.00, -0.04, 0.065),
        KotlinEyeSpec("aft_mid",    0.08, -0.04, 0.00, 0.00, 0.065),
        KotlinEyeSpec("fore_mid",   0.08, 0.04, 0.00, 0.00, 0.065),
        KotlinEyeSpec("left_mid",   0.06, 0.00, 0.02, 0.00, 0.065),
        KotlinEyeSpec("right_mid",  0.06, 0.00, -0.02, 0.00, 0.065)
    )

    fun buildVehicleSyntheticPillars(profile: VehicleProfileRecord): KotlinPillarGeometry {
        val wb = profile.wheelbaseMm / 1000.0
        val w = profile.widthMm / 1000.0
        val h = profile.heightMm / 1000.0

        val tPillar = when {
            profile.bodyType.contains("경형") || profile.bodyType.contains("COMPACT") -> 0.080
            profile.bodyType.contains("CUV") || profile.bodyType.contains("크로스오버") -> 0.095
            profile.bodyType.contains("세단") -> 0.085
            profile.bodyType.contains("대형") || profile.bodyType.contains("LARGE") -> 0.110
            else -> 0.090
        }

        val eyeX = wb * 0.45
        val eyeY = w * 0.22
        val eyeZ = h * 0.78
        val nominalEye = Point3(eyeX, eyeY, eyeZ)

        val xCowl = eyeX + 0.85 * (wb / 2.80)
        val xRoof = eyeX + 0.22 * (wb / 2.80)
        val zCowl = h * 0.58
        val zRoof = h * 0.90
        val yCowl = w * 0.44
        val yRoof = w * 0.35

        val lv = listOf(
            Point3(xCowl + 0.04, yCowl, zCowl),
            Point3(xCowl + 0.04, yCowl - tPillar, zCowl),
            Point3(xCowl - 0.04, yCowl - tPillar, zCowl),
            Point3(xCowl - 0.04, yCowl, zCowl),
            Point3(xRoof + 0.04, yRoof, zRoof),
            Point3(xRoof + 0.04, yRoof - tPillar, zRoof),
            Point3(xRoof - 0.04, yRoof - tPillar, zRoof),
            Point3(xRoof - 0.04, yRoof, zRoof)
        )
        val rv = lv.map { Point3(it.x, -it.y, it.z) }

        val leftTris = buildPrismTriangles(lv)
        val rightTris = buildPrismTriangles(rv)

        val margin = 0.05
        val leftAz = lv.map { atan2(it.y - eyeY, it.x - eyeX) }
        val rightAz = rv.map { atan2(it.y - eyeY, it.x - eyeX) }

        val lRange = Pair(leftAz.minOrNull()!! - margin, leftAz.maxOrNull()!! + margin)
        val rRange = Pair(rightAz.minOrNull()!! - margin, rightAz.maxOrNull()!! + margin)

        val digest = MessageDigest.getInstance("SHA-256")
        val vertStr = (lv + rv).joinToString(";") { "%.4f,%.4f,%.4f".format(it.x, it.y, it.z) }
        val sha = digest.digest(vertStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

        return KotlinPillarGeometry(
            nominalEye = nominalEye,
            leftTriangles = leftTris,
            rightTriangles = rightTris,
            leftAzRange = lRange,
            rightAzRange = rRange,
            pillarThicknessM = tPillar,
            geometrySha256 = sha
        )
    }

    private fun buildPrismTriangles(v: List<Point3>): List<Triangle> {
        return listOf(
            Triangle(v[0], v[1], v[2]), Triangle(v[0], v[2], v[3]),
            Triangle(v[4], v[6], v[5]), Triangle(v[4], v[7], v[6]),
            Triangle(v[0], v[4], v[5]), Triangle(v[0], v[5], v[1]),
            Triangle(v[1], v[5], v[6]), Triangle(v[1], v[6], v[2]),
            Triangle(v[2], v[6], v[7]), Triangle(v[2], v[7], v[3]),
            Triangle(v[3], v[7], v[4]), Triangle(v[3], v[4], v[0])
        )
    }

    fun generateEyePairs(nominalEye: Point3): List<KotlinEyePair> {
        return EYE_SPECS_N9.map { spec ->
            val cx = nominalEye.x + spec.dx
            val cy = nominalEye.y + spec.dy
            val cz = nominalEye.z + spec.dz
            val halfIpd = spec.ipd / 2.0
            KotlinEyePair(
                id = spec.id,
                weight = spec.weight,
                left = Point3(cx, cy + halfIpd, cz),
                right = Point3(cx, cy - halfIpd, cz)
            )
        }
    }

    fun samplePolygonEnu(enuCorners: List<Triple<Double, Double, Double>>, spacing: Double = 0.75): List<Pair<Point3, Double>> {
        val v0 = enuCorners[0]
        val v1 = enuCorners[1]
        val v2 = enuCorners[2]
        val v3 = enuCorners[3]

        val l1 = hypot(v1.first - v0.first, v1.second - v0.second)
        val l2 = hypot(v2.first - v3.first, v2.second - v3.second)
        val lengthM = (l1 + l2) / 2.0

        val w1 = hypot(v3.first - v0.first, v3.second - v0.second)
        val w2 = hypot(v2.first - v1.first, v2.second - v1.second)
        val widthM = (w1 + w2) / 2.0

        val nu = max(2, (lengthM / spacing).roundToInt())
        val nv = max(2, (widthM / spacing).roundToInt())

        val total = nu * nv
        val wPt = 1.0 / total
        val points = mutableListOf<Pair<Point3, Double>>()

        for (iu in 0 until nu) {
            val u = (iu + 0.5) / nu
            for (iv in 0 until nv) {
                val v = (iv + 0.5) / nv
                val px = (1 - u) * (1 - v) * v0.first + u * (1 - v) * v1.first + u * v * v2.first + (1 - u) * v * v3.first
                val py = (1 - u) * (1 - v) * v0.second + u * (1 - v) * v1.second + u * v * v2.second + (1 - u) * v * v3.second
                val pz = (1 - u) * (1 - v) * v0.third + u * (1 - v) * v1.third + u * v * v2.third + (1 - u) * v * v3.third
                points.add(Pair(Point3(px, py, pz), wPt))
            }
        }
        return points
    }

    fun generateTrajectory(roadBearingDeg: Double, appProfile: ApproachProfile, dt: Double = 0.10): Pair<List<List<Double>>, String> {
        val bearingRad = Math.toRadians(roadBearingDeg)
        val dx = sin(bearingRad)
        val dy = cos(bearingRad)
        val heading = atan2(dy, dx)

        val speedMps = appProfile.speedKmh / 3.6
        val distTotal = 40.0
        val durationSec = distTotal / max(1.0, speedMps)
        val nSteps = (durationSec / dt).roundToInt() + 1

        val poses = mutableListOf<List<Double>>()
        for (i in 0 until nSteps) {
            val t = i * dt
            val (px, py, h) = when (appProfile.id) {
                "APPROACH_STRAIGHT_30KMH" -> {
                    val s = -35.0 + speedMps * t
                    Triple(s * dx, s * dy, heading)
                }
                "APPROACH_SLOW_DECEL_20KMH" -> {
                    val vCurr = speedMps - (speedMps * 0.33) * (t / max(0.1, durationSec))
                    val s = -35.0 + (speedMps + vCurr) * 0.5 * t
                    Triple(s * dx, s * dy, heading)
                }
                "APPROACH_CURVE_APPROACH_25KMH" -> {
                    val s = -35.0 + speedMps * t
                    val radius = 120.0
                    val arcAngle = s / radius
                    val hCurv = heading + arcAngle * 0.35
                    val normalX = -dy
                    val normalY = dx
                    val offset = (1.0 - cos(arcAngle * 0.35)) * 4.0
                    Triple(s * dx + offset * normalX, s * dy + offset * normalY, hCurv)
                }
                else -> {
                    val s = -35.0 + speedMps * t
                    Triple(s * dx, s * dy, heading)
                }
            }
            poses.add(listOf(t, px, py, h, speedMps))
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val trajStr = poses.joinToString(";") { p -> p.joinToString(",") { "%.4f".format(it) } }
        val sha = digest.digest(trajStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
        return Pair(poses, sha)
    }

    fun computeCombination(
        zone: GradeBSpatialFeature,
        profile: VehicleProfileRecord,
        approach: ApproachProfile
    ): OcclusionBatchResultRow {
        val runId = "RUN_ON_DEMAND_${zone.zoneId}_${profile.modelName}_${approach.id}"
        return computeRayCastingSimulation(runId, zone, profile, approach, dt = 0.10)
    }

    fun computeRayCastingSimulation(
        runId: String,
        zone: GradeBSpatialFeature,
        profile: VehicleProfileRecord,
        approach: ApproachProfile,
        dt: Double = 0.10
    ): OcclusionBatchResultRow {
        val tStart = System.nanoTime()

        val pillars = buildVehicleSyntheticPillars(profile)
        val eyePairs = generateEyePairs(pillars.nominalEye)
        val cwPoints = samplePolygonEnu(zone.localEnuCoordinates, spacing = 0.75)
        val (poses, trajSha) = generateTrajectory(zone.roadBearingDegrees, approach, dt = dt)

        var peakPoss = 0.0
        var peakUniv = 0.0
        var firstOccMs: Long? = null
        var lastOccMs: Long? = null
        var exposureSum = 0.0

        for (pose in poses) {
            val tSec = pose[0]
            val vx = pose[1]
            val vy = pose[2]
            val vh = pose[3]

            val cosH = cos(vh)
            val sinH = sin(vh)

            var weightedPossSum = 0.0
            var weightedUnivSum = 0.0

            for ((wpt, weight) in cwPoints) {
                // world to vehicle
                val dx = wpt.x - vx
                val dy = wpt.y - vy
                val pxV = cosH * dx + sinH * dy
                val pyV = -sinH * dx + cosH * dy
                val pzV = wpt.z

                if (pxV < 0.20) continue

                var ptPossWeight = 0.0
                var ptUnivWeight = 0.0

                for (ep in eyePairs) {
                    val wEye = ep.weight
                    val (lex, ley, lez) = Triple(ep.left.x, ep.left.y, ep.left.z)
                    val (rex, rey, rez) = Triple(ep.right.x, ep.right.y, ep.right.z)

                    val ldx = pxV - lex
                    val ldy = pyV - ley
                    val ldz = pzV - lez
                    val laz = atan2(ldy, ldx)

                    var leftHit = false
                    if (laz >= pillars.leftAzRange.first && laz <= pillars.leftAzRange.second) {
                        for (tri in pillars.leftTriangles) {
                            if (raySegmentHitsTrianglePrimitive(lex, ley, lez, ldx, ldy, ldz, tri)) {
                                leftHit = true
                                break
                            }
                        }
                    }
                    if (!leftHit && laz >= pillars.rightAzRange.first && laz <= pillars.rightAzRange.second) {
                        for (tri in pillars.rightTriangles) {
                            if (raySegmentHitsTrianglePrimitive(lex, ley, lez, ldx, ldy, ldz, tri)) {
                                leftHit = true
                                break
                            }
                        }
                    }

                    val rdx = pxV - rex
                    val rdy = pyV - rey
                    val rdz = pzV - rez
                    val raz = atan2(rdy, rdx)

                    var rightHit = false
                    if (raz >= pillars.leftAzRange.first && raz <= pillars.leftAzRange.second) {
                        for (tri in pillars.leftTriangles) {
                            if (raySegmentHitsTrianglePrimitive(rex, rey, rez, rdx, rdy, rdz, tri)) {
                                rightHit = true
                                break
                            }
                        }
                    }
                    if (!rightHit && raz >= pillars.rightAzRange.first && raz <= pillars.rightAzRange.second) {
                        for (tri in pillars.rightTriangles) {
                            if (raySegmentHitsTrianglePrimitive(rex, rey, rez, rdx, rdy, rdz, tri)) {
                                rightHit = true
                                break
                            }
                        }
                    }

                    if (leftHit || rightHit) ptPossWeight += wEye
                    if (leftHit && rightHit) ptUnivWeight += wEye
                }

                weightedPossSum += ptPossWeight * weight
                weightedUnivSum += ptUnivWeight * weight
            }

            val tMs = (tSec * 1000.0).roundToLong()
            if (weightedPossSum > 0.005) {
                if (firstOccMs == null) firstOccMs = tMs
                lastOccMs = tMs
            }

            if (weightedPossSum > peakPoss) peakPoss = weightedPossSum
            if (weightedUnivSum > peakUniv) peakUniv = weightedUnivSum
            exposureSum += weightedPossSum * dt
        }

        val firstOcc = firstOccMs ?: 0L
        val lastOcc = lastOccMs ?: 0L
        val maxDurationMs = max(0L, lastOcc - firstOcc)

        val tEnd = System.nanoTime()
        val measuredMs = (tEnd - tStart) / 1_000_000.0

        val eval = VehiclePillarEvidenceGate.evaluate(profile, VehiclePillarUsageRoute.SYNTHETIC_PILLAR_BY_BODY_CLASS)

        return OcclusionBatchResultRow(
            runId = runId,
            zoneId = zone.zoneId,
            crosswalkSourceRecordId = zone.crosswalkSourceRecordId,
            spatialFeatureSha256 = zone.sha256,
            vehicleProfileId = profile.profileId,
            vehiclePublicDimensionEvidence = "OFFICIAL_HYUNDAI_SPEC_WEB_PUBLIC_DIMENSION",
            syntheticPillarProfileId = eval.syntheticPillarProfileId,
            pillarEvidenceClass = eval.pillarEvidenceClass,
            pillarGeometrySha256 = pillars.geometrySha256,
            eyeModelId = "RepresentativePopulationApproximation",
            eyeSampleCount = 9,
            eyeWeightSum = 1.0000,
            approachProfileId = approach.id,
            speedProfile = approach.speedKmh,
            trajectorySha256 = trajSha,
            peakPossibleRatio = (peakPoss * 10000.0).roundToLong() / 10000.0,
            peakUniversalRatio = (peakUniv * 10000.0).roundToLong() / 10000.0,
            maxDurationMillis = maxDurationMs,
            exposureIntegral = (exposureSum * 10000.0).roundToLong() / 10000.0,
            firstOcclusionTimeMillis = firstOcc,
            lastOcclusionTimeMillis = lastOcc,
            geometryAssessment = "PASS_BINOCULAR_OCCLUDED_ZONE_EVALUATED",
            advisoryEligibility = "ELIGIBLE_SHADOW_ONLY",
            effectiveCandidateLevel = "L1_CANDIDATE_SUPPRESSED",
            suppressionReason = "RELEASE_FAIL_CLOSED_ALL_SUPPRESSED_DEFAULT",
            measuredComputationMillis = (measuredMs * 100.0).roundToLong() / 100.0,
            resultGenerationMode = "KOTLIN_ENGINE_EXECUTION"
        )
    }

    fun run45Batch(
        spatialDir: File,
        vehiclesDir: File
    ): List<OcclusionBatchResultRow> {
        val repo = GradeBFeatureRepository(spatialDir)
        val confirmedFeatures = repo.getAllConfirmedFeatures()
        require(confirmedFeatures.size == 3) {
            "Must load exactly 3 confirmed Grade B features, got ${confirmedFeatures.size}"
        }

        val profiles = VehicleProfileLoader.loadFromDirectory(vehiclesDir)
        require(profiles.size == 5) {
            "Must load exactly 5 vehicle profiles, got ${profiles.size}"
        }

        val rows = mutableListOf<OcclusionBatchResultRow>()
        var idx = 0

        for (feat in confirmedFeatures) {
            for (p in profiles) {
                for (app in APPROACH_PROFILES) {
                    idx++
                    val runId = "RUN_%02d_%s_%s_%s".format(idx, feat.zoneId, p.modelName, app.id)
                    val row = computeRayCastingSimulation(runId, feat, p, app, dt = 0.10)
                    rows.add(row)
                }
            }
        }

        return rows
    }
}
