package ai.pleos.app.safetywindow.batch.planning

import ai.pleos.app.safetywindow.batch.model.*

object ScenarioCatalog {
    fun all(): List<ScenarioDefinition> = listOf(
        ScenarioDefinition("ZONE_ENTRY", ScenarioFamily.BASE, RiskFeatureType.SCHOOL_ZONE_CONTEXT, listOf("CONST_20")),
        ScenarioDefinition("STRAIGHT_SILENCE", ScenarioFamily.BASE, RiskFeatureType.SCHOOL_ZONE_CONTEXT, listOf("CONST_10","CONST_20","CONST_30"), expectedSilence = true),
        ScenarioDefinition("STALE_SUPPRESSION", ScenarioFamily.BASE, RiskFeatureType.SCHOOL_ZONE_CONTEXT, listOf("CONST_20"), injectStaleAtMillis = 3000),
        ScenarioDefinition("LOW_QUALITY_FAIL_CLOSED", ScenarioFamily.BASE, RiskFeatureType.SCHOOL_ZONE_CONTEXT, listOf("CONST_20"), forceLowQuality = true),

        ScenarioDefinition("CURVE_LEFT", ScenarioFamily.CURVE, RiskFeatureType.CURVED_ROAD, listOf("CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("CURVE_RIGHT", ScenarioFamily.CURVE, RiskFeatureType.CURVED_ROAD, listOf("CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("CURVE_ENTRY", ScenarioFamily.CURVE, RiskFeatureType.CURVED_ROAD, listOf("CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("CURVE_EXIT", ScenarioFamily.CURVE, RiskFeatureType.CURVED_ROAD, listOf("CONST_10","CONST_20","CONST_30")),

        ScenarioDefinition("INTERSECTION_STRAIGHT", ScenarioFamily.INTERSECTION, RiskFeatureType.FOUR_WAY_INTERSECTION, listOf("CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("INTERSECTION_LEFT_TURN", ScenarioFamily.INTERSECTION, RiskFeatureType.FOUR_WAY_INTERSECTION, listOf("CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("INTERSECTION_RIGHT_TURN", ScenarioFamily.INTERSECTION, RiskFeatureType.FOUR_WAY_INTERSECTION, listOf("CONST_10","CONST_20","CONST_30")),

        ScenarioDefinition("T_JUNCTION_LEFT", ScenarioFamily.T_JUNCTION, RiskFeatureType.T_JUNCTION, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("T_JUNCTION_RIGHT", ScenarioFamily.T_JUNCTION, RiskFeatureType.T_JUNCTION, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("T_JUNCTION_DEPARTURE", ScenarioFamily.T_JUNCTION, RiskFeatureType.T_JUNCTION, listOf("DEPART_0_10","DEPART_0_20")),

        ScenarioDefinition("SCHOOL_GATE_APPROACH", ScenarioFamily.SCHOOL_GATE, RiskFeatureType.SCHOOL_GATE_CANDIDATE, listOf("CONST_10","CONST_20")),
        ScenarioDefinition("SCHOOL_GATE_STOP", ScenarioFamily.SCHOOL_GATE, RiskFeatureType.SCHOOL_GATE_CANDIDATE, listOf("DECEL_20_0")),
        ScenarioDefinition("SCHOOL_GATE_DEPARTURE_LEFT", ScenarioFamily.SCHOOL_GATE, RiskFeatureType.SCHOOL_GATE_CANDIDATE, listOf("DEPART_0_10","DEPART_0_20")),
        ScenarioDefinition("SCHOOL_GATE_DEPARTURE_RIGHT", ScenarioFamily.SCHOOL_GATE, RiskFeatureType.SCHOOL_GATE_CANDIDATE, listOf("DEPART_0_10","DEPART_0_20")),
        ScenarioDefinition("SCHOOL_GATE_DEPARTURE_STRAIGHT", ScenarioFamily.SCHOOL_GATE, RiskFeatureType.SCHOOL_GATE_CANDIDATE, listOf("DEPART_0_10","DEPART_0_20")),

        ScenarioDefinition("NARROW_ROAD_ENTRY", ScenarioFamily.NARROW_ROAD, RiskFeatureType.NARROW_ROAD_EXIT, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("NARROW_ROAD_EXIT_LEFT", ScenarioFamily.NARROW_ROAD, RiskFeatureType.NARROW_ROAD_EXIT, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("NARROW_ROAD_EXIT_RIGHT", ScenarioFamily.NARROW_ROAD, RiskFeatureType.NARROW_ROAD_EXIT, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("NARROW_ROAD_EXIT_STRAIGHT", ScenarioFamily.NARROW_ROAD, RiskFeatureType.NARROW_ROAD_EXIT, listOf("CONST_5","CONST_10","CONST_20")),

        ScenarioDefinition("CROSSWALK_STRAIGHT", ScenarioFamily.CROSSWALK, RiskFeatureType.CROSSWALK, listOf("CONST_5","CONST_10","CONST_20","CONST_30")),
        ScenarioDefinition("CROSSWALK_LEFT_TURN", ScenarioFamily.CROSSWALK, RiskFeatureType.CROSSWALK, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("CROSSWALK_RIGHT_TURN", ScenarioFamily.CROSSWALK, RiskFeatureType.CROSSWALK, listOf("CONST_5","CONST_10","CONST_20")),
        ScenarioDefinition("CROSSWALK_STOP_AND_GO", ScenarioFamily.CROSSWALK, RiskFeatureType.CROSSWALK, listOf("STOP_AND_GO_20"))
    )

    fun speedProfiles(): List<SpeedProfile> = listOf(
        constant("CONST_5",5.0), constant("CONST_10",10.0), constant("CONST_20",20.0), constant("CONST_30",30.0),
        ramp("DEPART_0_10",0.0,10.0), ramp("DEPART_0_20",0.0,20.0), ramp("DECEL_20_0",20.0,0.0),
        SpeedProfile("STOP_AND_GO_20", listOf(SpeedPoint(0,20.0/3.6),SpeedPoint(2500,0.0),SpeedPoint(5000,0.0),SpeedPoint(8000,20.0/3.6)), "TO_BE_COMPUTED")
    )

    private fun constant(id: String, kmh: Double) = SpeedProfile(id, listOf(SpeedPoint(0,kmh/3.6),SpeedPoint(10000,kmh/3.6)), "TO_BE_COMPUTED")
    private fun ramp(id: String, fromKmh: Double, toKmh: Double) = SpeedProfile(id, listOf(SpeedPoint(0,fromKmh/3.6),SpeedPoint(5000,toKmh/3.6),SpeedPoint(10000,toKmh/3.6)), "TO_BE_COMPUTED")
}
