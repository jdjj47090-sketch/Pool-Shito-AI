package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableCornerSmootherTest {
    private val initial = listOf(Point2(100.0, 100.0), Point2(900.0, 100.0), Point2(900.0, 500.0), Point2(100.0, 500.0))

    @Test fun smoothsSmallDetectionJitter() {
        val smoother = TableCornerSmoother(newMeasurementWeight = 0.2)
        smoother.update(initial, 1000, 600)
        val jittered = initial.map { Point2(it.x + 4.0, it.y - 2.0) }
        val update = smoother.update(jittered, 1000, 600)
        assertFalse(update.discontinuity)
        assertEquals(100.8, update.corners[0].x, 1e-9)
        assertEquals(99.6, update.corners[0].y, 1e-9)
    }

    @Test fun reportsLargeGeometryJumpForTrackReset() {
        val smoother = TableCornerSmoother()
        smoother.update(initial, 1000, 600)
        val shifted = initial.map { Point2(it.x + 80.0, it.y + 50.0) }
        val update = smoother.update(shifted, 1000, 600)
        assertTrue(update.discontinuity)
        assertEquals(180.0, update.corners[0].x, 1e-9)
        assertEquals(150.0, update.corners[0].y, 1e-9)
    }
}
