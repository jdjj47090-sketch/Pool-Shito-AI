package com.mahdi.poolshito.geometry

import kotlin.math.*

data class Point2(val x: Double, val y: Double) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Point2(x * scale, y * scale)
    fun length() = hypot(x, y)
    fun normalized(): Point2 {
        val len = length().coerceAtLeast(1e-9)
        return Point2(x / len, y / len)
    }
}

data class CushionSegment(val start: Point2, val end: Point2)
data class ShotPath(val points: List<Point2>, val description: String)

/**
 * Idealized 2D reflection geometry for a rectangular table.
 * This is a planning model, not a guarantee of real-world shot outcome.
 */
object PoolGeometry {
    fun reflectDirection(direction: Point2, normal: Point2): Point2 {
        val n = normal.normalized()
        return (direction - n * (2.0 * dot(direction, n))).normalized()
    }

    fun bankTarget(target: Point2, cushionX: Double, tableHeight: Double): Point2 {
        // Mirror the target across a vertical cushion for a one-bank construction.
        return Point2(2.0 * cushionX - target.x, target.y.coerceIn(0.0, tableHeight))
    }

    fun angleDegrees(a: Point2, vertex: Point2, c: Point2): Double {
        val u = (a - vertex).normalized()
        val v = (c - vertex).normalized()
        val cosine = dot(u, v).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun dot(a: Point2, b: Point2) = a.x * b.x + a.y * b.y
}
