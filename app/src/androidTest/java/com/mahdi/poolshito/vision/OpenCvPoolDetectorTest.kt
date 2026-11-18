package com.mahdi.poolshito.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class OpenCvPoolDetectorTest {
    @Test fun detectsTableAndActualBallPixelsFromFrame() {
        assertTrue("OpenCV native runtime must load", OpenCVLoader.initLocal())
        val frame = Mat(480, 640, CvType.CV_8UC3, Scalar(20.0, 20.0, 20.0))
        Imgproc.rectangle(frame, Point(50.0, 70.0), Point(590.0, 410.0), Scalar(35.0, 135.0, 45.0), -1)
        Imgproc.circle(frame, Point(210.0, 220.0), 9, Scalar(242.0, 242.0, 242.0), -1)
        Imgproc.circle(frame, Point(330.0, 220.0), 9, Scalar(220.0, 35.0, 35.0), -1)
        Imgproc.circle(frame, Point(620.0, 50.0), 9, Scalar(242.0, 242.0, 242.0), -1)
        val detector = OpenCvPoolDetector()
        try {
            val result = detector.process(frame)
            assertNotNull("Felt quadrilateral should be found from the pixel mask", result.table)
            assertTrue("Circle detector should find the rendered ball pixels", result.balls.isNotEmpty())
            assertTrue(result.balls.any { kotlin.math.hypot(it.center.x - 210.0, it.center.y - 220.0) < 8.0 })
            assertTrue("A circle outside the detected felt boundary must be rejected", result.balls.none { kotlin.math.hypot(it.center.x - 620.0, it.center.y - 50.0) < 12.0 })
        } finally {
            detector.close(); frame.release()
        }
    }
}
