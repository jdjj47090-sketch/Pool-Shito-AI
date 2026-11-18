package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2
import org.junit.Assert.*
import org.junit.Test

class TableCalibrationTest {
    @Test fun perspectiveCornersMapToNormalizedTable() {
        val source = listOf(Point2(120.0, 80.0), Point2(920.0, 130.0), Point2(810.0, 610.0), Point2(180.0, 570.0))
        val calibration = TableCalibration.fromCorners(source)!!
        TableCalibration.TABLE_CORNERS.zip(source).forEach { (expected, actual) ->
            val mapped = calibration.imageToTable(actual)!!
            assertEquals(expected.x, mapped.x, 1e-6); assertEquals(expected.y, mapped.y, 1e-6)
            val reversed = calibration.tableToImage(expected)!!
            assertEquals(actual.x, reversed.x, 1e-6); assertEquals(actual.y, reversed.y, 1e-6)
        }
    }

    @Test fun rejectsDegenerateCorners() {
        assertNull(TableCalibration.fromCorners(List(4) { Point2(10.0, 10.0) }))
        assertNull(TableCalibration.fromCorners(listOf(Point2(0.0, 0.0), Point2(100.0, 100.0), Point2(0.0, 100.0), Point2(100.0, 0.0))))
    }
}
