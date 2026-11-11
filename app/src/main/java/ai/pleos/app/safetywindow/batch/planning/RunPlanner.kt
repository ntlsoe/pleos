package ai.pleos.app.safetywindow.batch.planning

import ai.pleos.app.safetywindow.batch.model.*
import java.security.MessageDigest
import java.time.Instant

class RunPlanner {
    fun plan(
        zones: List<ZoneRecord>,
        vehicles: List<VehicleProfile>,
        eyeModel: EyeModel,
        scenarios: List<ScenarioDefinition>,
        engineVersion: String,
        gitCommit: String,
        now: Instant = Instant.now()
    ): List<BatchRunPlan> {
        require(zones.size == 100) { "Final zone count must equal 100" }
        require(vehicles.size == 5) { "Vehicle profile count must equal 5" }
        val plans = mutableListOf<BatchRunPlan>()
        for (zone in zones.sortedBy { it.zoneId }) {
            require(zone.allowContextSimulation) { "Every final candidate must permit context simulation: ${zone.zoneId}" }
            val contextFeature = RiskFeatureRecord("${zone.zoneId}_ZONE_CONTEXT", RiskFeatureType.SCHOOL_ZONE_CONTEXT, zone.sourceGeometrySha256 ?: "NO_GEOMETRY", zone.evidenceClass.name, true)
            val features = listOf(contextFeature) + zone.riskFeatures.filter { it.batchEligible }
            for (feature in features) {
                for (scenario in scenarios.filter { applicable(zone, feature, it) }) {
                    for (speedId in scenario.speedProfileIds) {
                        for (vehicle in vehicles.sortedBy { it.vehicleProfileId }) {
                            val seed = listOf(zone.zoneId,feature.featureId,vehicle.vehicleProfileId,eyeModel.eyeModelId,scenario.scenarioId,speedId,engineVersion,gitCommit).joinToString("|")
                            plans += BatchRunPlan(
                                runId = "RUN_" + sha256(seed).take(20).uppercase(),
                                zoneId = zone.zoneId,
                                featureId = feature.featureId,
                                riskFeatureType = feature.type,
                                vehicleProfileId = vehicle.vehicleProfileId,
                                pillarProfileId = vehicle.syntheticPillarProfileId,
                                eyeModelId = eyeModel.eyeModelId,
                                scenarioId = scenario.scenarioId,
                                speedProfileId = speedId,
                                inputManifestSha256 = sha256(seed),
                                engineVersion = engineVersion,
                                gitCommit = gitCommit,
                                plannedAt = now
                            )
                        }
                    }
                }
            }
        }
        require(plans.map { it.runId }.toSet().size == plans.size) { "Duplicate runId detected" }
        return plans.sortedWith(compareBy(BatchRunPlan::zoneId, BatchRunPlan::scenarioId, BatchRunPlan::vehicleProfileId, BatchRunPlan::speedProfileId))
    }

    private fun applicable(zone: ZoneRecord, feature: RiskFeatureRecord, scenario: ScenarioDefinition): Boolean {
        if (!feature.batchEligible) return false
        if (scenario.requiredFeatureType != feature.type) return false
        if (feature.type == RiskFeatureType.CROSSWALK && !zone.allowPreciseOcclusion) return false
        if (feature.geometrySha256.isBlank() || feature.geometrySha256 == "NO_GEOMETRY") {
            return scenario.family == ScenarioFamily.BASE
        }
        return true
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
