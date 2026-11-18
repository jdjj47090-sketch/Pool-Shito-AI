package com.mahdi.poolshito.vision

import com.mahdi.poolshito.core.analysis.BallDetection
import com.mahdi.poolshito.geometry.Point2
import org.opencv.core.Mat

data class TableDetection(val corners: List<Point2>, val confidence: Double)
data class DetectionFrame(val table: TableDetection?, val balls: List<BallDetection>)

/** Injectable boundary between camera preprocessing and concrete detection implementation. */
interface PoolFrameDetector : AutoCloseable {
    fun process(rgb: Mat, manualCorners: List<Point2>? = null): DetectionFrame
}
