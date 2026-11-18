package com.mahdi.poolshito.vision

import com.mahdi.poolshito.geometry.Point2
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** OpenCV preprocessing and composition of table finding, ball detection, and classification. */
class OpenCvPoolDetector(
    private val tableDetector: FeltTableDetector = FeltTableDetector(),
    private val ballDetector: OpenCvBallDetector = OpenCvBallDetector(),
) : PoolFrameDetector {
    private val hsv = Mat(); private val gray = Mat()

    fun process(rgb: Mat): DetectionFrame = process(rgb, null)

    override fun process(rgb: Mat, manualCorners: List<Point2>?): DetectionFrame {
        if (rgb.empty()) return DetectionFrame(null, emptyList())
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val table = manualCorners?.takeIf { it.size == 4 }?.let { TableDetection(it, 1.0) }
            ?: tableDetector.detect(hsv, rgb.cols(), rgb.rows())
        val balls = table?.let { ballDetector.detect(hsv, gray, rgb.cols(), rgb.rows(), it) }.orEmpty()
        return DetectionFrame(table, balls)
    }

    override fun close() { hsv.release(); gray.release(); tableDetector.close(); ballDetector.close() }
}
