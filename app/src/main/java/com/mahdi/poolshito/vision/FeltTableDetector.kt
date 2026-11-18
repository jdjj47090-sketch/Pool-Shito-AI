package com.mahdi.poolshito.vision

import com.mahdi.poolshito.geometry.Point2
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Finds large green/blue felt regions and fits a convex four-corner playing surface. */
class FeltTableDetector : AutoCloseable {
    private val greenMask = Mat(); private val blueMask = Mat(); private val tableMask = Mat(); private val hierarchy = Mat()
    private val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(11.0, 11.0))

    fun detect(hsv: Mat, width: Int, height: Int): TableDetection? {
        Core.inRange(hsv, Scalar(30.0, 38.0, 25.0), Scalar(99.0, 255.0, 255.0), greenMask)
        Core.inRange(hsv, Scalar(95.0, 38.0, 25.0), Scalar(137.0, 255.0, 255.0), blueMask)
        Core.bitwise_or(greenMask, blueMask, tableMask)
        Imgproc.morphologyEx(tableMask, tableMask, Imgproc.MORPH_CLOSE, kernel)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(tableMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val minArea = width.toDouble() * height * 0.12
        var best: TableDetection? = null
        contours.forEach { contour ->
            if (Imgproc.contourArea(contour) >= minArea) {
                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, 0.025 * Imgproc.arcLength(curve, true), true)
                val points = approx.toArray()
                if (points.size == 4) {
                    val ordered = orderCorners(points.map { Point2(it.x, it.y) })
                    if (isConvex(ordered)) {
                        val confidence = (Imgproc.contourArea(contour) / (width.toDouble() * height) * 1.8).coerceIn(0.35, 0.95)
                        if (best == null || confidence > best.confidence) best = TableDetection(ordered, confidence)
                    }
                }
                curve.release(); approx.release()
            }
            contour.release()
        }
        return best
    }

    private fun isConvex(corners: List<Point2>): Boolean {
        val signs = corners.indices.map { i ->
            val a = corners[i]; val b = corners[(i + 1) % 4]; val c = corners[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return signs.all { it > 1e-3 } || signs.all { it < -1e-3 }
    }

    private fun orderCorners(points: List<Point2>): List<Point2> {
        val cx = points.map { it.x }.average(); val cy = points.map { it.y }.average()
        val clockwise = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        val start = clockwise.indices.minByOrNull { i -> clockwise[i].x + clockwise[i].y } ?: 0
        var corners = (clockwise.drop(start) + clockwise.take(start)).toMutableList()
        if (corners[1].x < corners[3].x) corners = mutableListOf(corners[0], corners[3], corners[2], corners[1])
        return corners
    }

    override fun close() { listOf(greenMask, blueMask, tableMask, hierarchy, kernel).forEach { it.release() } }
}
