package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2
import kotlin.math.abs

/** Image-to-table homography. Table coordinates use a 2.0 by 1.0 normalized playing surface. */
class TableCalibration private constructor(
    val imageCorners: List<Point2>, private val forward: DoubleArray, private val inverse: DoubleArray,
) {
    fun imageToTable(point: Point2): Point2? = map(forward, point)
    fun tableToImage(point: Point2): Point2? = map(inverse, point)

    private fun map(h: DoubleArray, point: Point2): Point2? {
        val denominator = h[6] * point.x + h[7] * point.y + 1.0
        if (abs(denominator) < 1e-9) return null
        val x = (h[0] * point.x + h[1] * point.y + h[2]) / denominator
        val y = (h[3] * point.x + h[4] * point.y + h[5]) / denominator
        return if (x.isFinite() && y.isFinite()) Point2(x, y) else null
    }

    companion object {
        val TABLE_CORNERS = listOf(Point2(0.0, 0.0), Point2(2.0, 0.0), Point2(2.0, 1.0), Point2(0.0, 1.0))

        /** Corner order is top-left, top-right, bottom-right, bottom-left in the camera image. */
        fun fromCorners(corners: List<Point2>): TableCalibration? {
            if (corners.size != 4 || corners.any { !it.x.isFinite() || !it.y.isFinite() }) return null
            if (area(corners) < 1e-6 || !isConvex(corners)) return null
            val horizontal = distance(corners[0], corners[1]) + distance(corners[2], corners[3])
            val vertical = distance(corners[1], corners[2]) + distance(corners[3], corners[0])
            val ordered = if (horizontal >= vertical) corners.toList() else listOf(corners[1], corners[2], corners[3], corners[0])
            val forward = solveFor(ordered, TABLE_CORNERS) ?: return null
            val inverse = solveFor(TABLE_CORNERS, ordered) ?: return null
            return TableCalibration(ordered, forward, inverse)
        }

        private fun distance(a: Point2, b: Point2) = kotlin.math.hypot(a.x - b.x, a.y - b.y)

        private fun isConvex(points: List<Point2>): Boolean {
            val crossProducts = points.indices.map { i ->
                val a = points[i]; val b = points[(i + 1) % points.size]; val c = points[(i + 2) % points.size]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            }
            return crossProducts.all { it > 1e-7 } || crossProducts.all { it < -1e-7 }
        }

        private fun area(points: List<Point2>) = abs(points.indices.sumOf { i ->
            val a = points[i]; val b = points[(i + 1) % points.size]
            a.x * b.y - b.x * a.y
        }) / 2.0

        private fun solveFor(from: List<Point2>, to: List<Point2>): DoubleArray? {
            if (from.size != 4 || to.size != 4 || area(from) < 1e-9) return null
            val a = Array(8) { DoubleArray(9) }
            from.forEachIndexed { i, p ->
                val q = to[i]; val r = 2 * i
                a[r][0] = p.x; a[r][1] = p.y; a[r][2] = 1.0
                a[r][6] = -q.x * p.x; a[r][7] = -q.x * p.y; a[r][8] = q.x
                a[r + 1][3] = p.x; a[r + 1][4] = p.y; a[r + 1][5] = 1.0
                a[r + 1][6] = -q.y * p.x; a[r + 1][7] = -q.y * p.y; a[r + 1][8] = q.y
            }
            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) if (abs(a[row][column]) > abs(a[pivot][column])) pivot = row
                if (abs(a[pivot][column]) < 1e-10) return null
                val swap = a[column]; a[column] = a[pivot]; a[pivot] = swap
                val divisor = a[column][column]
                for (j in column..8) a[column][j] /= divisor
                for (row in 0 until 8) if (row != column) {
                    val factor = a[row][column]
                    for (j in column..8) a[row][j] -= factor * a[column][j]
                }
            }
            return DoubleArray(8) { a[it][8] }
        }
    }
}
