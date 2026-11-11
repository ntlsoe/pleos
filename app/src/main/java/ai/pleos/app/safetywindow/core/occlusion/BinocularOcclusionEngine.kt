package ai.pleos.app.safetywindow.core.occlusion

class BinocularOcclusionEngine(
    val vehiclePillars: VehiclePillarGeometry,
    val eyePopulation: EyePopulationModel
) {
    val eyeSamples: List<WeightedEyePair> = eyePopulation.generateSamples()
    val totalEyeWeight: Double = eyeSamples.sumOf { it.weight }

    init {
        require(totalEyeWeight > 0.0) { "Total eye population weight must be positive" }
    }

    fun evaluateRiskPointAtPose(
        worldPt: Point3,
        pose: VehiclePose
    ): PointOcclusionEvaluation {
        val ptVeh = pose.worldToVehicle(worldPt)

        var occludedEyeWeight = 0.0
        var numOccludedEyes = 0

        for (eyeSample in eyeSamples) {
            val leftEye = eyeSample.eyePair.left
            val rightEye = eyeSample.eyePair.right

            val leftOcc = vehiclePillars.isOccludedByEitherPillar(leftEye, ptVeh)
            val rightOcc = vehiclePillars.isOccludedByEitherPillar(rightEye, ptVeh)

            // Binocular occlusion requires BOTH eyes to be occluded
            if (leftOcc && rightOcc) {
                occludedEyeWeight += eyeSample.weight
                numOccludedEyes++
            }
        }

        val totalEyes = eyeSamples.size
        val coverage = occludedEyeWeight / totalEyeWeight
        val isUniversal = (numOccludedEyes == totalEyes)
        val isPossible = (numOccludedEyes > 0)

        return PointOcclusionEvaluation(
            positionVeh = ptVeh,
            coverage = coverage,
            isUniversalOccluded = isUniversal,
            isPossibleOccluded = isPossible
        )
    }

    fun evaluateTrajectory(
        scenarioId: String,
        riskPoints: List<RiskSamplePoint>,
        trajectoryPoses: List<VehiclePose>,
        dt: Double
    ): SimulationTrajectoryMetrics {
        val totalRiskWeight = riskPoints.sumOf { it.weight }
        require(totalRiskWeight > 0.0) { "Risk point weights must sum to > 0" }

        val timesteps = ArrayList<TimestepOcclusionResult>(trajectoryPoses.size)

        for (pose in trajectoryPoses) {
            var univCount = 0
            var possCount = 0
            var univWeightedSum = 0.0
            var possWeightedSum = 0.0
            val ptStates = ArrayList<PointOcclusionEvaluation>(riskPoints.size)

            for (pt in riskPoints) {
                val res = evaluateRiskPointAtPose(pt.position, pose)
                ptStates.add(res)
                if (res.isUniversalOccluded) {
                    univCount++
                    univWeightedSum += pt.weight
                }
                if (res.isPossibleOccluded) {
                    possCount++
                    possWeightedSum += pt.weight
                }
            }

            val univRatio = univWeightedSum / totalRiskWeight
            val possRatio = possWeightedSum / totalRiskWeight

            timesteps.add(
                TimestepOcclusionResult(
                    timeSec = pose.timeSec,
                    pose = pose,
                    sampleCount = riskPoints.size,
                    universalOccludedCount = univCount,
                    possibleOccludedCount = possCount,
                    universalWeightedRatio = univRatio,
                    possibleWeightedRatio = possRatio,
                    pointStates = ptStates
                )
            )
        }

        return SimulationTrajectoryMetrics.calculateFromTimesteps(
            scenarioId = scenarioId,
            profileId = vehiclePillars.profileId,
            eyeModelId = eyePopulation.modelId,
            timestepSec = dt,
            timesteps = timesteps
        )
    }
}
