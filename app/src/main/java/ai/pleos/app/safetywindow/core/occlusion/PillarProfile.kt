package ai.pleos.app.safetywindow.core.occlusion

enum class PillarSide {
    LEFT,
    RIGHT
}

data class PillarVolume(
    val side: PillarSide,
    val vertices: List<Point3>,
    val source: String
) {
    val triangles: List<Triangle> = buildTriangles(vertices)

    fun isOccluded(eye: Point3, target: Point3): Boolean {
        val ox = eye.x
        val oy = eye.y
        val oz = eye.z
        val dx = target.x - ox
        val dy = target.y - oy
        val dz = target.z - oz
        for (i in 0 until triangles.size) {
            if (raySegmentHitsTrianglePrimitive(ox, oy, oz, dx, dy, dz, triangles[i])) {
                return true
            }
        }
        return false
    }

    companion object {
        fun buildTriangles(v: List<Point3>): List<Triangle> {
            require(v.size == 8) { "PillarVolume prism requires 8 vertices, got ${v.size}" }
            return listOf(
                // Bottom cap
                Triangle(v[0], v[1], v[2]),
                Triangle(v[0], v[2], v[3]),
                // Top cap
                Triangle(v[4], v[6], v[5]),
                Triangle(v[4], v[7], v[6]),
                // Front face
                Triangle(v[0], v[4], v[5]),
                Triangle(v[0], v[5], v[1]),
                // Inner face
                Triangle(v[1], v[5], v[6]),
                Triangle(v[1], v[6], v[2]),
                // Rear face
                Triangle(v[2], v[6], v[7]),
                Triangle(v[2], v[7], v[3]),
                // Outer face
                Triangle(v[3], v[7], v[4]),
                Triangle(v[3], v[4], v[0])
            )
        }
    }
}

data class VehiclePillarGeometry(
    val profileId: String,
    val source: String,
    val leftPillar: PillarVolume,
    val rightPillar: PillarVolume,
    val driverEyeNominal: Point3,
    val wheelbase: Double,
    val trackWidth: Double
) {
    fun isOccludedByEitherPillar(eye: Point3, target: Point3): Boolean {
        return leftPillar.isOccluded(eye, target) || rightPillar.isOccluded(eye, target)
    }

    companion object {
        fun createRepresentativeSedanV0(): VehiclePillarGeometry {
            val leftVerts = listOf(
                Point3(2.15, 0.70, 0.88), Point3(2.17, 0.62, 0.88),
                Point3(2.07, 0.61, 0.88), Point3(2.05, 0.69, 0.88),
                Point3(1.52, 0.58, 1.38), Point3(1.54, 0.50, 1.38),
                Point3(1.44, 0.49, 1.38), Point3(1.42, 0.57, 1.38)
            )
            val rightVerts = listOf(
                Point3(2.15, -0.70, 0.88), Point3(2.17, -0.62, 0.88),
                Point3(2.07, -0.61, 0.88), Point3(2.05, -0.69, 0.88),
                Point3(1.52, -0.58, 1.38), Point3(1.54, -0.50, 1.38),
                Point3(1.44, -0.49, 1.38), Point3(1.42, -0.57, 1.38)
            )
            return VehiclePillarGeometry(
                profileId = "RepresentativeSedanV0",
                source = "SYNTHETIC_TEST_ONLY",
                leftPillar = PillarVolume(PillarSide.LEFT, leftVerts, "SYNTHETIC_TEST_ONLY"),
                rightPillar = PillarVolume(PillarSide.RIGHT, rightVerts, "SYNTHETIC_TEST_ONLY"),
                driverEyeNominal = Point3(1.30, 0.38, 1.20),
                wheelbase = 2.80,
                trackWidth = 1.60
            )
        }

        fun createSymmetricTestProfile(): VehiclePillarGeometry {
            val leftVerts = listOf(
                Point3(2.15, 0.70, 0.88), Point3(2.17, 0.62, 0.88),
                Point3(2.07, 0.61, 0.88), Point3(2.05, 0.69, 0.88),
                Point3(1.52, 0.58, 1.38), Point3(1.54, 0.50, 1.38),
                Point3(1.44, 0.49, 1.38), Point3(1.42, 0.57, 1.38)
            )
            val rightVerts = listOf(
                Point3(2.15, -0.70, 0.88), Point3(2.17, -0.62, 0.88),
                Point3(2.07, -0.61, 0.88), Point3(2.05, -0.69, 0.88),
                Point3(1.52, -0.58, 1.38), Point3(1.54, -0.50, 1.38),
                Point3(1.44, -0.49, 1.38), Point3(1.42, -0.57, 1.38)
            )
            return VehiclePillarGeometry(
                profileId = "SymmetricVehicleTestProfile",
                source = "SYNTHETIC_TEST_ONLY",
                leftPillar = PillarVolume(PillarSide.LEFT, leftVerts, "SYNTHETIC_TEST_ONLY"),
                rightPillar = PillarVolume(PillarSide.RIGHT, rightVerts, "SYNTHETIC_TEST_ONLY"),
                driverEyeNominal = Point3(1.30, 0.00, 1.20),
                wheelbase = 2.80,
                trackWidth = 1.60
            )
        }

        fun createRepresentativeSuvV0(): VehiclePillarGeometry {
            val leftVerts = listOf(
                Point3(2.25, 0.73, 1.00), Point3(2.27, 0.64, 1.00),
                Point3(2.17, 0.63, 1.00), Point3(2.15, 0.72, 1.00),
                Point3(1.68, 0.60, 1.55), Point3(1.70, 0.51, 1.55),
                Point3(1.60, 0.50, 1.55), Point3(1.58, 0.59, 1.55)
            )
            val rightVerts = listOf(
                Point3(2.25, -0.73, 1.00), Point3(2.27, -0.64, 1.00),
                Point3(2.17, -0.63, 1.00), Point3(2.15, -0.72, 1.00),
                Point3(1.68, -0.60, 1.55), Point3(1.70, -0.51, 1.55),
                Point3(1.60, -0.50, 1.55), Point3(1.58, -0.59, 1.55)
            )
            return VehiclePillarGeometry(
                profileId = "RepresentativeSuvV0",
                source = "SYNTHETIC_TEST_ONLY",
                leftPillar = PillarVolume(PillarSide.LEFT, leftVerts, "SYNTHETIC_TEST_ONLY"),
                rightPillar = PillarVolume(PillarSide.RIGHT, rightVerts, "SYNTHETIC_TEST_ONLY"),
                driverEyeNominal = Point3(1.35, 0.39, 1.35),
                wheelbase = 2.85,
                trackWidth = 1.65
            )
        }

        fun createRepresentativeMpvV0(): VehiclePillarGeometry {
            val leftVerts = listOf(
                Point3(2.45, 0.78, 1.10), Point3(2.47, 0.69, 1.10),
                Point3(2.37, 0.68, 1.10), Point3(2.35, 0.77, 1.10),
                Point3(1.85, 0.64, 1.68), Point3(1.87, 0.55, 1.68),
                Point3(1.77, 0.54, 1.68), Point3(1.75, 0.63, 1.68)
            )
            val rightVerts = listOf(
                Point3(2.45, -0.78, 1.10), Point3(2.47, -0.69, 1.10),
                Point3(2.37, -0.68, 1.10), Point3(2.35, -0.77, 1.10),
                Point3(1.85, -0.64, 1.68), Point3(1.87, -0.55, 1.68),
                Point3(1.77, -0.54, 1.68), Point3(1.75, -0.63, 1.68)
            )
            return VehiclePillarGeometry(
                profileId = "RepresentativeMpvV0",
                source = "SYNTHETIC_TEST_ONLY",
                leftPillar = PillarVolume(PillarSide.LEFT, leftVerts, "SYNTHETIC_TEST_ONLY"),
                rightPillar = PillarVolume(PillarSide.RIGHT, rightVerts, "SYNTHETIC_TEST_ONLY"),
                driverEyeNominal = Point3(1.40, 0.42, 1.45),
                wheelbase = 3.00,
                trackWidth = 1.70
            )
        }
    }
}
