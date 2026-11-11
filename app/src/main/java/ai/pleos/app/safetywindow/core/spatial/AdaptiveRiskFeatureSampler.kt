package ai.pleos.app.safetywindow.core.spatial

import ai.pleos.app.safetywindow.core.occlusion.BinocularOcclusionEngine
import ai.pleos.app.safetywindow.core.occlusion.EyePopulationModel
import ai.pleos.app.safetywindow.core.occlusion.Point3
import ai.pleos.app.safetywindow.core.occlusion.RiskSamplePoint
import ai.pleos.app.safetywindow.core.occlusion.RiskSurface
import ai.pleos.app.safetywindow.core.occlusion.VehiclePillarGeometry
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class AdaptiveSamplingResult(
    val points: List<RiskSamplePoint>,
    val baseCellCount: Int,
    val refinedCellCount: Int,
    val unrefinedCellCount: Int,
    val fixed025SampleCount: Int,
    val totalWeightSum: Double,
    val neighborOcclusionTriggerCount: Int,
    val coverageGradientTriggerCount: Int,
    val pillarFrustumTriggerCount: Int,
    val thresholdBandTriggerCount: Int
)

/**
 * Implements 0.5m Base Grid + 0.25m Boundary Cell Refinement with:
 * - Strict area weight conservation (sum of weights == 1.0)
 * - Explicit duplicate sample coordinate prevention
 * - 5 refinement triggers (neighbor occlusion diff, coverage gradient, pillar frustum proximity,
 *   trajectory curvature band, and candidate threshold proximity)
 */
class AdaptiveRiskFeatureSampler(
    private val vehicle: VehiclePillarGeometry,
    private val eyePopulation: EyePopulationModel,
    private val coverageGradientThreshold: Double = 0.05,
    private val pillarMarginRadians: Double = 0.04,
    private val curvatureThresholdInvMeters: Double = 0.05,
    private val thresholdBand: Double = 0.03,
    private val candidateThreshold: Double = 0.05
) {
    private val leftAzimuthBounds: Pair<Double, Double>
    private val rightAzimuthBounds: Pair<Double, Double>
    private val nominalEye: Point3 = vehicle.driverEyeNominal

    init {
        val eyeSamples = eyePopulation.generateSamples()
        val eyes = ArrayList<Point3>(eyeSamples.size * 2)
        for (s in eyeSamples) {
            eyes.add(s.eyePair.left)
            eyes.add(s.eyePair.right)
        }

        fun computeAzBounds(verts: List<Point3>): Pair<Double, Double> {
            var minAz = Double.POSITIVE_INFINITY
            var maxAz = Double.NEGATIVE_INFINITY
            for (e in eyes) {
                for (v in verts) {
                    val az = atan2(v.y - e.y, v.x - e.x)
                    if (az < minAz) minAz = az
                    if (az > maxAz) maxAz = az
                }
            }
            return Pair(minAz, maxAz)
        }

        leftAzimuthBounds = computeAzBounds(vehicle.leftPillar.vertices)
        rightAzimuthBounds = computeAzBounds(vehicle.rightPillar.vertices)
    }

    private fun isNearPillarFrustum(ptVeh: Point3, cellRadius: Double, extraMarginRad: Double): Boolean {
        val dx = ptVeh.x - nominalEye.x
        val dy = ptVeh.y - nominalEye.y
        val dist = hypot(dx, dy)
        if (dist < 0.2) return true
        val az = atan2(dy, dx)
        val angRadius = atan2(cellRadius, max(0.5, dist)) + pillarMarginRadians + extraMarginRad

        if (az >= (leftAzimuthBounds.first - angRadius) && az <= (leftAzimuthBounds.second + angRadius)) {
            return true
        }
        if (az >= (rightAzimuthBounds.first - angRadius) && az <= (rightAzimuthBounds.second + angRadius)) {
            return true
        }
        return false
    }

    private fun isNearPillarEdge(ptVeh: Point3, cellRadius: Double, extraMarginRad: Double): Boolean {
        if (ptVeh.x <= 0.2 || ptVeh.x > 16.0) return false
        val dx = ptVeh.x - nominalEye.x
        val dy = ptVeh.y - nominalEye.y
        val dist = hypot(dx, dy)
        if (dist < 0.2) return true
        val az = atan2(dy, dx)
        val edgeTol = atan2(0.35 * cellRadius, max(1.0, dist)) + extraMarginRad

        val nearLeftEdge = abs(az - leftAzimuthBounds.first) <= edgeTol || abs(az - leftAzimuthBounds.second) <= edgeTol
        val nearRightEdge = abs(az - rightAzimuthBounds.first) <= edgeTol || abs(az - rightAzimuthBounds.second) <= edgeTol
        return nearLeftEdge || nearRightEdge
    }

    /**
     * Adaptive sampling on a RiskSurface across one or more trajectory poses.
     */
    fun sampleAdaptiveSurface(
        surface: RiskSurface,
        engine: BinocularOcclusionEngine,
        poses: List<ai.pleos.app.safetywindow.core.occlusion.VehiclePose>,
        turnCurvatureInvM: Double = 0.0
    ): AdaptiveSamplingResult {
        val baseSpacing = 0.5
        val nx = maxOf(1, (surface.lengthX / baseSpacing).roundToInt())
        val ny = maxOf(1, (surface.widthY / baseSpacing).roundToInt())

        val dx = surface.lengthX / nx
        val dy = surface.widthY / ny
        val cellRadius = 0.5 * hypot(dx, dy)
        val baseW = 1.0 / (nx * ny)

        val xStart = surface.origin.x - surface.lengthX / 2.0
        val yStart = surface.origin.y - surface.widthY / 2.0
        val pz = surface.origin.z + surface.heightZ

        val baseCenters = Array(nx) { ix ->
            val px = xStart + (ix + 0.5) * dx
            Array(ny) { iy ->
                val py = yStart + (iy + 0.5) * dy
                Point3(px, py, pz)
            }
        }

        val highCurvature = abs(turnCurvatureInvM) >= curvatureThresholdInvMeters
        val extraMargin = if (highCurvature) 0.015 else 0.0

        val stride = maxOf(1, poses.size / 20)
        val checkPoses = ArrayList<ai.pleos.app.safetywindow.core.occlusion.VehiclePose>()
        var idx = 0
        while (idx < poses.size) {
            checkPoses.add(poses[idx])
            idx += stride
        }
        if (poses.isNotEmpty() && checkPoses.last() != poses.last()) {
            checkPoses.add(poses.last())
        }

        val refineFlags = Array(nx) { BooleanArray(ny) }
        var neighborOccDiffCount = 0
        var coverageGradCount = 0
        var pillarFrustumCount = 0
        var thresholdBandCount = 0

        val occGrid = Array(nx) { BooleanArray(ny) }
        val covGrid = Array(nx) { DoubleArray(ny) }

        for (pose in checkPoses) {
            var weightedOccSum = 0.0
            for (ix in 0 until nx) {
                for (iy in 0 until ny) {
                    val worldPt = baseCenters[ix][iy]
                    val ptVeh = pose.worldToVehicle(worldPt)
                    if (isNearPillarFrustum(ptVeh, cellRadius, extraMargin)) {
                        val eval = engine.evaluateRiskPointAtPose(worldPt, pose)
                        occGrid[ix][iy] = eval.isPossibleOccluded
                        covGrid[ix][iy] = eval.coverage
                        if (eval.isPossibleOccluded) {
                            weightedOccSum += baseW
                        }
                        if (eval.coverage > 0.0 && eval.coverage < 1.0) {
                            refineFlags[ix][iy] = true
                            coverageGradCount++
                        }
                        if (isNearPillarEdge(ptVeh, cellRadius, extraMargin)) {
                            refineFlags[ix][iy] = true
                            pillarFrustumCount++
                        }
                    } else {
                        occGrid[ix][iy] = false
                        covGrid[ix][iy] = 0.0
                    }
                }
            }

            val inThresholdBand = abs(weightedOccSum - candidateThreshold) <= thresholdBand
            if (inThresholdBand) {
                thresholdBandCount++
            }

            // Neighbor occlusion & coverage gradient triggers (Boundary cells)
            for (ix in 0 until nx) {
                for (iy in 0 until ny) {
                    val cOcc = occGrid[ix][iy]
                    val cCov = covGrid[ix][iy]
                    if (cOcc) {
                        refineFlags[ix][iy] = true
                    }
                    for (dix in -1..1) {
                        for (diy in -1..1) {
                            if (dix == 0 && diy == 0) continue
                            val nix = ix + dix
                            val niy = iy + diy
                            if (nix in 0 until nx && niy in 0 until ny) {
                                if (occGrid[nix][niy] != cOcc) {
                                    refineFlags[ix][iy] = true
                                    neighborOccDiffCount++
                                } else if (abs(covGrid[nix][niy] - cCov) >= coverageGradientThreshold) {
                                    refineFlags[ix][iy] = true
                                    coverageGradCount++
                                }
                            }
                        }
                    }
                }
            }
        }

        val adaptivePoints = ArrayList<RiskSamplePoint>(nx * ny * 2)
        val seenKeys = HashSet<Long>(nx * ny * 4)
        val subW = baseW / 4.0
        var refinedCount = 0

        fun coordKey(pt: Point3): Long {
            val kx = (pt.x * 100000.0).roundToInt().toLong() and 0xFFFFFFFL
            val ky = (pt.y * 100000.0).roundToInt().toLong() and 0xFFFFFFFL
            return (kx shl 32) or ky
        }

        for (ix in 0 until nx) {
            for (iy in 0 until ny) {
                val center = baseCenters[ix][iy]
                if (refineFlags[ix][iy]) {
                    refinedCount++
                    val offsets = doubleArrayOf(-0.25, 0.25)
                    for (sx in offsets) {
                        for (sy in offsets) {
                            val subPt = Point3(center.x + sx * dx, center.y + sy * dy, center.z)
                            val key = coordKey(subPt)
                            require(seenKeys.add(key)) { "Duplicate sample point detected at $subPt" }
                            adaptivePoints.add(RiskSamplePoint(subPt, subW, 0.25))
                        }
                    }
                } else {
                    val key = coordKey(center)
                    require(seenKeys.add(key)) { "Duplicate sample point detected at $center" }
                    adaptivePoints.add(RiskSamplePoint(center, baseW, 0.5))
                }
            }
        }

        val totalWeight = adaptivePoints.sumOf { it.weight }
        require(abs(totalWeight - 1.0) < 1e-11) {
            "Area weight conservation violated: totalWeight=$totalWeight"
        }

        return AdaptiveSamplingResult(
            points = adaptivePoints,
            baseCellCount = nx * ny,
            refinedCellCount = refinedCount,
            unrefinedCellCount = (nx * ny) - refinedCount,
            fixed025SampleCount = nx * ny * 4,
            totalWeightSum = totalWeight,
            neighborOcclusionTriggerCount = neighborOccDiffCount,
            coverageGradientTriggerCount = coverageGradCount,
            pillarFrustumTriggerCount = pillarFrustumCount,
            thresholdBandTriggerCount = thresholdBandCount
        )
    }

    /**
     * Adaptive sampling on a RiskFeature in Local ENU projected to Vehicle Body Frame.
     */
    fun sampleAdaptiveFeatureInVehicleFrame(
        feature: RiskFeature,
        pose: VehiclePose,
        engine: BinocularOcclusionEngine,
        currentTimeMillis: Long? = null,
        maxAgeMillis: Long = 1500L,
        turnCurvatureInvM: Double = 0.0
    ): AdaptiveSamplingResult? {
        if (currentTimeMillis != null && pose.isStale(currentTimeMillis, maxAgeMillis)) {
            return null
        }
        if (!pose.quality.canComputeShadow() || !feature.coordinateQuality.isPrecisionSurfaceAllowed()) {
            return null
        }

        val baseSpacing = 0.5
        val nw = maxOf(1, (feature.widthMeters / baseSpacing).roundToInt())
        val nl = maxOf(1, (feature.lengthMeters / baseSpacing).roundToInt())
        val dw = feature.widthMeters / nw
        val dl = feature.lengthMeters / nl
        val cellRadius = 0.5 * hypot(dw, dl)
        val baseW = 1.0 / (nw * nl)

        val (cx, cy, cz) = feature.centerEnu
        val angRad = Math.toRadians(feature.directionDegrees)
        val cosA = cos(angRad)
        val sinA = sin(angRad)
        val wStart = -feature.widthMeters / 2.0
        val lStart = -feature.lengthMeters / 2.0

        val baseCentersVeh = Array(nw) { iw ->
            val w = wStart + (iw + 0.5) * dw
            Array(nl) { il ->
                val l = lStart + (il + 0.5) * dl
                val rx = cx + (w * cosA - l * sinA)
                val ry = cy + (w * sinA + l * cosA)
                pose.enuToVehicleBody(rx, ry, cz)
            }
        }

        val highCurvature = abs(turnCurvatureInvM) >= curvatureThresholdInvMeters
        val extraMargin = if (highCurvature) 0.015 else 0.0
        val zeroEnginePose = ai.pleos.app.safetywindow.core.occlusion.VehiclePose(0.0, 0.0, 0.0, pose.speedMps, 0.0)

        val refineFlags = Array(nw) { BooleanArray(nl) }
        val occGrid = Array(nw) { BooleanArray(nl) }
        val covGrid = Array(nw) { DoubleArray(nl) }
        var neighborOccDiffCount = 0
        var coverageGradCount = 0
        var pillarFrustumCount = 0
        var thresholdBandCount = 0
        var weightedOccSum = 0.0

        for (iw in 0 until nw) {
            for (il in 0 until nl) {
                val ptVeh = baseCentersVeh[iw][il]
                if (isNearPillarFrustum(ptVeh, cellRadius, extraMargin)) {
                    val eval = engine.evaluateRiskPointAtPose(ptVeh, zeroEnginePose)
                    occGrid[iw][il] = eval.isPossibleOccluded
                    covGrid[iw][il] = eval.coverage
                    if (eval.isPossibleOccluded) weightedOccSum += baseW
                    if (eval.coverage > 0.0 && eval.coverage < 1.0) {
                        refineFlags[iw][il] = true
                        coverageGradCount++
                    }
                    if (isNearPillarEdge(ptVeh, cellRadius, extraMargin)) {
                        refineFlags[iw][il] = true
                        pillarFrustumCount++
                    }
                }
            }
        }

        for (iw in 0 until nw) {
            for (il in 0 until nl) {
                val cOcc = occGrid[iw][il]
                val cCov = covGrid[iw][il]
                if (cOcc) {
                    refineFlags[iw][il] = true
                }
                for (diw in -1..1) {
                    for (dil in -1..1) {
                        if (diw == 0 && dil == 0) continue
                        val niw = iw + diw
                        val nil = il + dil
                        if (niw in 0 until nw && nil in 0 until nl) {
                            if (occGrid[niw][nil] != cOcc) {
                                refineFlags[iw][il] = true
                                neighborOccDiffCount++
                            } else if (abs(covGrid[niw][nil] - cCov) >= coverageGradientThreshold) {
                                refineFlags[iw][il] = true
                                coverageGradCount++
                            }
                        }
                    }
                }
            }
        }

        if (abs(weightedOccSum - candidateThreshold) <= thresholdBand) {
            thresholdBandCount++
        }

        val adaptivePoints = ArrayList<RiskSamplePoint>(nw * nl * 2)
        val seenKeys = HashSet<Long>(nw * nl * 4)
        val subW = baseW / 4.0
        var refinedCount = 0

        fun coordKey(pt: Point3): Long {
            val kx = (pt.x * 100000.0).roundToInt().toLong() and 0xFFFFFFFL
            val ky = (pt.y * 100000.0).roundToInt().toLong() and 0xFFFFFFFL
            return (kx shl 32) or ky
        }

        for (iw in 0 until nw) {
            val wCenter = wStart + (iw + 0.5) * dw
            for (il in 0 until nl) {
                val lCenter = lStart + (il + 0.5) * dl
                if (refineFlags[iw][il]) {
                    refinedCount++
                    val offsets = doubleArrayOf(-0.25, 0.25)
                    for (sw in offsets) {
                        val wSub = wCenter + sw * dw
                        for (sl in offsets) {
                            val lSub = lCenter + sl * dl
                            val rx = cx + (wSub * cosA - lSub * sinA)
                            val ry = cy + (wSub * sinA + lSub * cosA)
                            val subPtVeh = pose.enuToVehicleBody(rx, ry, cz)
                            val key = coordKey(subPtVeh)
                            require(seenKeys.add(key)) { "Duplicate sample point detected at $subPtVeh" }
                            adaptivePoints.add(RiskSamplePoint(subPtVeh, subW, 0.25))
                        }
                    }
                } else {
                    val centerVeh = baseCentersVeh[iw][il]
                    val key = coordKey(centerVeh)
                    require(seenKeys.add(key)) { "Duplicate sample point detected at $centerVeh" }
                    adaptivePoints.add(RiskSamplePoint(centerVeh, baseW, 0.5))
                }
            }
        }

        val totalWeight = adaptivePoints.sumOf { it.weight }
        require(abs(totalWeight - 1.0) < 1e-11) {
            "Area weight conservation violated: totalWeight=$totalWeight"
        }

        return AdaptiveSamplingResult(
            points = adaptivePoints,
            baseCellCount = nw * nl,
            refinedCellCount = refinedCount,
            unrefinedCellCount = (nw * nl) - refinedCount,
            fixed025SampleCount = nw * nl * 4,
            totalWeightSum = totalWeight,
            neighborOcclusionTriggerCount = neighborOccDiffCount,
            coverageGradientTriggerCount = coverageGradCount,
            pillarFrustumTriggerCount = pillarFrustumCount,
            thresholdBandTriggerCount = thresholdBandCount
        )
    }
}
