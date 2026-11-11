package ai.pleos.app.safetywindow.core.occlusion

import kotlin.math.abs
import kotlin.math.sqrt

data class Point3(val x: Double, val y: Double, val z: Double) {
    fun toTuple(): Triple<Double, Double, Double> = Triple(x, y, z)
}

data class Vector3(val x: Double, val y: Double, val z: Double) {
    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3 {
        val l = length()
        return if (l < 1e-12) Vector3(0.0, 0.0, 0.0) else Vector3(x / l, y / l, z / l)
    }

    fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
}

fun pointDiff(p1: Point3, p2: Point3): Vector3 = Vector3(p1.x - p2.x, p1.y - p2.y, p1.z - p2.z)

fun pointAddVec(p: Point3, v: Vector3): Point3 = Point3(p.x + v.x, p.y + v.y, p.z + v.z)

data class Ray(val origin: Point3, val target: Point3) {
    val direction: Vector3 = pointDiff(target, origin)
}

data class Triangle(val v0: Point3, val v1: Point3, val v2: Point3) {
    val e1x: Double = v1.x - v0.x
    val e1y: Double = v1.y - v0.y
    val e1z: Double = v1.z - v0.z
    val e2x: Double = v2.x - v0.x
    val e2y: Double = v2.y - v0.y
    val e2z: Double = v2.z - v0.z

    fun normal(): Vector3 {
        val edge1 = pointDiff(v1, v0)
        val edge2 = pointDiff(v2, v0)
        return edge1.cross(edge2).normalize()
    }
}

/**
 * Zero-allocation primitive Möller–Trumbore ray-triangle segment intersection for Android ART runtime.
 */
fun raySegmentHitsTrianglePrimitive(
    ox: Double,
    oy: Double,
    oz: Double,
    dx: Double,
    dy: Double,
    dz: Double,
    tri: Triangle,
    epsilon: Double = 1e-7
): Boolean {
    val pvecX = dy * tri.e2z - dz * tri.e2y
    val pvecY = dz * tri.e2x - dx * tri.e2z
    val pvecZ = dx * tri.e2y - dy * tri.e2x

    val det = tri.e1x * pvecX + tri.e1y * pvecY + tri.e1z * pvecZ
    if (abs(det) < epsilon) {
        return false
    }

    val invDet = 1.0 / det
    val tvecX = ox - tri.v0.x
    val tvecY = oy - tri.v0.y
    val tvecZ = oz - tri.v0.z

    val u = (tvecX * pvecX + tvecY * pvecY + tvecZ * pvecZ) * invDet
    if (u < -epsilon || u > 1.0 + epsilon) {
        return false
    }

    val qvecX = tvecY * tri.e1z - tvecZ * tri.e1y
    val qvecY = tvecZ * tri.e1x - tvecX * tri.e1z
    val qvecZ = tvecX * tri.e1y - tvecY * tri.e1x

    val v = (dx * qvecX + dy * qvecY + dz * qvecZ) * invDet
    if (v < -epsilon || u + v > 1.0 + epsilon) {
        return false
    }

    val t = (tri.e2x * qvecX + tri.e2y * qvecY + tri.e2z * qvecZ) * invDet
    return t > epsilon && t < (1.0 - epsilon)
}

/**
 * Möller–Trumbore ray-triangle intersection algorithm for segment [0, 1].
 * Returns fractional distance t in (epsilon, 1.0 - epsilon) if the segment intersects triangle interior, else null.
 */
fun rayTriangleIntersectSegment(ray: Ray, triangle: Triangle, epsilon: Double = 1e-7): Double? {
    val v0 = triangle.v0
    val v1 = triangle.v1
    val v2 = triangle.v2

    val edge1 = pointDiff(v1, v0)
    val edge2 = pointDiff(v2, v0)
    val dirVec = ray.direction

    val pvec = dirVec.cross(edge2)
    val det = edge1.dot(pvec)

    if (abs(det) < epsilon) {
        return null
    }

    val invDet = 1.0 / det
    val tvec = pointDiff(ray.origin, v0)
    val u = tvec.dot(pvec) * invDet
    if (u < -epsilon || u > 1.0 + epsilon) {
        return null
    }

    val qvec = tvec.cross(edge1)
    val v = dirVec.dot(qvec) * invDet
    if (v < -epsilon || u + v > 1.0 + epsilon) {
        return null
    }

    val t = edge2.dot(qvec) * invDet
    return if (t > epsilon && t < (1.0 - epsilon)) t else null
}
