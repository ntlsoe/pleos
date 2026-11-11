package ai.pleos.app.safetywindow.core.spatial

enum class SpatialQuality {
    SYNTHETIC_SCENARIO,
    MANUALLY_VERIFIED_MAP_GEOMETRY,
    PUBLIC_DATA_MANUALLY_CORRECTED,
    VERIFIED_DEMO,
    PUBLIC_POINT_WITH_MANUAL_DIRECTION,
    PUBLIC_POINT_DIRECTION_UNKNOWN,
    SCENARIO,
    STALE,
    INVALID;

    fun canComputeShadow(): Boolean {
        return this == SYNTHETIC_SCENARIO ||
               this == MANUALLY_VERIFIED_MAP_GEOMETRY ||
               this == PUBLIC_DATA_MANUALLY_CORRECTED ||
               this == VERIFIED_DEMO ||
               this == SCENARIO ||
               this == PUBLIC_POINT_WITH_MANUAL_DIRECTION
    }

    fun isPrecisionSurfaceAllowed(): Boolean {
        return this == SYNTHETIC_SCENARIO ||
               this == MANUALLY_VERIFIED_MAP_GEOMETRY ||
               this == PUBLIC_DATA_MANUALLY_CORRECTED ||
               this == VERIFIED_DEMO ||
               this == SCENARIO ||
               this == PUBLIC_POINT_WITH_MANUAL_DIRECTION
    }
}
