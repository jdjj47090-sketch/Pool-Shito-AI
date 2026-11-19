package com.mahdi.poolshito.vision

import com.mahdi.poolshito.core.analysis.BallDetection
import com.mahdi.poolshito.core.vision.BallColorClassifier
import com.mahdi.poolshito.geometry.Point2
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Circle candidates come from OpenCV Hough gradients; only candidates inside calibrated felt survive. */
class OpenCvBallDetector(private val classifier: BallColorClassifier = BallColorClassifier()) : AutoCloseable {
    private val blurred = Mat(); private val circles = Mat(); private val saturation = Mat(); private val value = Mat(); private val colorMask = Mat()
    private val circleMask = Mat(); private val innerMask = Mat(); private val outerBallMask = Mat(); private val backgroundRingMask = Mat()
    private val insideColor = Mat(); private val centerColor = Mat(); private val outerColor = Mat()

    fun detect(hsv: Mat, gray: Mat, width: Int, height: Int, table: TableDetection): List<BallDetection> {
        val shortEdge = table.corners.indices.minOf { i ->
            val a = table.corners[i]; val b = table.corners[(i + 1) % 4]; hypot(a.x - b.x, a.y - b.y)
        }
        val radius = (shortEdge / 44.0).coerceIn(4.0, min(width, height).toDouble() * 0.06)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 1.0)
        val accumulatorThreshold = (radius * 2.4).coerceIn(14.0, 24.0)
        Imgproc.HoughCircles(blurred, circles, Imgproc.HOUGH_GRADIENT, 1.2, max(radius * 1.45, 6.0), 110.0, accumulatorThreshold, max(3.0, radius * 0.62).toInt(), max(5.0, radius * 1.42).toInt())
        Core.extractChannel(hsv, saturation, 1); Core.extractChannel(hsv, value, 2)
        Core.inRange(saturation, Scalar(48.0), Scalar(255.0), colorMask)
        val output = mutableListOf<BallDetection>()
        for (i in 0 until circles.cols()) {
            val c = circles.get(0, i) ?: continue
            val x = c[0]; val y = c[1]; val r = c[2]
            if (!insideTable(Point2(x, y), table.corners)) continue
            val margin = r * 1.35
            if (x - margin < 0 || y - margin < 0 || x + margin >= width || y + margin >= height) continue
            val left = max(0, (x - margin).toInt()); val top = max(0, (y - margin).toInt())
            val rect = Rect(left, top, min(width, (x + margin).toInt() + 1) - left, min(height, (y + margin).toInt() + 1) - top)
            if (rect.width <= 0 || rect.height <= 0) continue
            val localCenter = Point(x - rect.x, y - rect.y)
            resetMask(circleMask, rect.height, rect.width)
            resetMask(innerMask, rect.height, rect.width)
            resetMask(outerBallMask, rect.height, rect.width)
            resetMask(backgroundRingMask, rect.height, rect.width)
            val colors = colorMask.submat(rect)
            val values = value.submat(rect)
            val grayRoi = gray.submat(rect)
            val hsvRoi = hsv.submat(rect)
            try {
                Imgproc.circle(circleMask, localCenter, max(1, (r * 0.92).toInt()), Scalar(255.0), -1)
                Imgproc.circle(innerMask, localCenter, max(1, (r * 0.42).toInt()), Scalar(255.0), -1)
                Core.subtract(circleMask, innerMask, outerBallMask)
                Imgproc.circle(backgroundRingMask, localCenter, max(1, (r * 1.28).toInt()), Scalar(255.0), -1)
                Core.subtract(backgroundRingMask, circleMask, backgroundRingMask)

                Core.bitwise_and(colors, circleMask, insideColor)
                Core.bitwise_and(colors, innerMask, centerColor)
                Core.bitwise_and(colors, outerBallMask, outerColor)
                val diskPixels = max(1, Core.countNonZero(circleMask))
                val fraction = Core.countNonZero(insideColor).toDouble() / diskPixels
                val centerFraction = Core.countNonZero(centerColor).toDouble() / max(1, Core.countNonZero(innerMask))
                val outerFraction = Core.countNonZero(outerColor).toDouble() / max(1, Core.countNonZero(outerBallMask))
                val ballMean = Core.mean(hsvRoi, circleMask).`val`
                val ballKind = classifier.classify(
                    fraction, Core.mean(values, circleMask).`val`[0], centerFraction, outerFraction,
                )
                val ringMean = Core.mean(hsvRoi, backgroundRingMask).`val`
                val grayContrast = abs(Core.mean(grayRoi, innerMask).`val`[0] - Core.mean(grayRoi, backgroundRingMask).`val`[0]) / 255.0
                val hueDelta = abs(ballMean[0] - ringMean[0]).let { min(it, 180.0 - it) }
                val colorContrast = (abs(ballMean[1] - ringMean[1]) * 0.5 + abs(ballMean[2] - ringMean[2]) + hueDelta * 2.0) / 400.0
                val confidence = (0.38 + max(grayContrast, colorContrast) * 0.56).coerceIn(0.35, 0.94)
                if (confidence >= 0.42 && r in (radius * 0.60)..(radius * 1.42) && ballMean.size >= 3) {
                    output += BallDetection(Point2(x, y), r, confidence, ballKind)
                }
            } finally {
                colors.release(); values.release(); grayRoi.release(); hsvRoi.release()
            }
        }
        return suppressDuplicates(output, radius)
    }

    private fun resetMask(mask: Mat, rows: Int, cols: Int) {
        mask.create(rows, cols, CvType.CV_8UC1)
        mask.setTo(Scalar(0.0))
    }

    private fun insideTable(point: Point2, polygon: List<Point2>): Boolean {
        var inside = false; var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[j]
            val crosses = (a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun suppressDuplicates(input: List<BallDetection>, expectedRadius: Double): List<BallDetection> {
        val result = mutableListOf<BallDetection>()
        input.sortedByDescending { it.confidence }.forEach { candidate ->
            if (result.none { (it.center - candidate.center).length() < expectedRadius * 1.35 }) result += candidate
        }
        return result
    }

    override fun close() {
        listOf(
            blurred, circles, saturation, value, colorMask,
            circleMask, innerMask, outerBallMask, backgroundRingMask,
            insideColor, centerColor, outerColor,
        ).forEach { it.release() }
    }
}
