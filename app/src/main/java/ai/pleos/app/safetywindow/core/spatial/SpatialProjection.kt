package ai.pleos.app.safetywindow.core.spatial

import ai.pleos.app.safetywindow.core.occlusion.Point3

object SpatialProjection {

    fun wgs84ToEnu(point: GeoPoint, frame: LocalCoordinateFrame): Triple<Double, Double, Double> {
        return frame.geodeticToEnu(point)
    }

    fun enuToGeodetic(east: Double, north: Double, up: Double, frame: LocalCoordinateFrame): GeoPoint {
        return frame.enuToGeodetic(east, north, up)
    }

    fun enuToVehicleBody(east: Double, north: Double, up: Double, pose: VehiclePose): Point3 {
        return pose.enuToVehicleBody(east, north, up)
    }

    fun wgs84ToVehicleBody(point: GeoPoint, frame: LocalCoordinateFrame, pose: VehiclePose): Point3 {
        val (east, north, up) = frame.geodeticToEnu(point)
        return pose.enuToVehicleBody(east, north, up)
    }
}
