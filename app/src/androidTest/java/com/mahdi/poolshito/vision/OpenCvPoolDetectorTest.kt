package com.mahdi.poolshito.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.poolshito.core.analysis.BallKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class OpenCvPoolDetectorTest {
    @Test fun detectsTableAndActualBallPixelsFromFrame() {
        assertTrue("OpenCV native runtime must load", OpenCVLoader.initLocal())
        val frame = Mat(480, 640, CvType.CV_8UC3, Scalar(20.0, 20.0, 20.0))
        val feltCorners = MatOfPoint(
            Point(100.0, 80.0), Point(560.0, 120.0), Point(520.0, 390.0), Point(140.0, 360.0),
        )
        Imgproc.fillConvexPoly(frame, feltCorners, Scalar(35.0, 135.0, 45.0))
        feltCorners.release()
        Imgproc.circle(frame, Point(210.0, 220.0), 6, Scalar(242.0, 242.0, 242.0), -1)
        Imgproc.circle(frame, Point(330.0, 220.0), 6, Scalar(220.0, 35.0, 35.0), -1)
        Imgproc.circle(frame, Point(450.0, 220.0), 6, Scalar(242.0, 242.0, 242.0), -1)
        Imgproc.circle(frame, Point(450.0, 220.0), 6, Scalar(220.0, 35.0, 35.0), -1)
        Imgproc.circle(frame, Point(450.0, 220.0), 3, Scalar(242.0, 242.0, 242.0), -1)
        Imgproc.circle(frame, Point(620.0, 50.0), 9, Scalar(242.0, 242.0, 242.0), -1)
        val detector = OpenCvPoolDetector()
        try {
            val result = detector.process(frame)
            val table = result.table ?: throw AssertionError("Perspective felt quadrilateral should be found from the pixel mask")
            assertTrue("The detected top rail should retain perspective skew", kotlin.math.abs(table.corners[0].y - table.corners[1].y) > 10.0)
            assertTrue("Circle detector should find the rendered ball pixels", result.balls.isNotEmpty())
            val cue = result.balls.minByOrNull { kotlin.math.hypot(it.center.x - 210.0, it.center.y - 220.0) }
            assertNotNull("White ball should be found", cue)
            assertTrue(kotlin.math.hypot(cue!!.center.x - 210.0, cue.center.y - 220.0) < 8.0)
            assertEquals("Bright low-saturation pixels should classify as cue ball", BallKind.CUE, cue.kind)
            val solid = result.balls.minByOrNull { kotlin.math.hypot(it.center.x - 330.0, it.center.y - 220.0) }
            assertNotNull("Colored ball should be found", solid)
            assertTrue("Colored ball center should be localized; detections=${result.balls}", kotlin.math.hypot(solid!!.center.x - 330.0, solid.center.y - 220.0) < 8.0)
            assertEquals("Broad saturated color coverage should classify as solid", BallKind.SOLID, solid.kind)
            val stripe = result.balls.minByOrNull { kotlin.math.hypot(it.center.x - 450.0, it.center.y - 220.0) }
            assertNotNull("White ball with a colored band should be found", stripe)
            assertTrue("Striped ball center should be localized; detections=${result.balls}", kotlin.math.hypot(stripe!!.center.x - 450.0, stripe.center.y - 220.0) < 8.0)
            assertEquals("Outer colored band around a pale center should classify as stripe", BallKind.STRIPE, stripe.kind)
            assertTrue("A circle outside the detected felt boundary must be rejected", result.balls.none { kotlin.math.hypot(it.center.x - 620.0, it.center.y - 50.0) < 12.0 })
        } finally {
            detector.close(); frame.release()
        }
    }
}
