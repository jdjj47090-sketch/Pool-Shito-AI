package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2
import org.junit.Assert.*
import org.junit.Test

class ManualCalibrationInputTest {
    @Test fun calibrationOnlyCompletesAfterFourOrderedTapsAndCanCancel() {
        val input = ManualCalibrationInput()
        assertNull(input.add(Point2(1.0, 1.0)))
        input.begin()
        val corners = listOf(Point2(0.0, 0.0), Point2(200.0, 0.0), Point2(200.0, 100.0), Point2(0.0, 100.0))
        corners.take(3).forEach { assertNull(input.add(it)) }
        assertEquals(3, input.progress)
        assertEquals(corners, input.add(corners.last()))
        assertFalse(input.isCollecting)
        input.begin(); input.add(corners[0]); input.cancel()
        assertEquals(0, input.progress)
        assertFalse(input.isCollecting)
    }
}
