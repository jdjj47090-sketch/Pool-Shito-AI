package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2

/** Four-tap calibration state used by the camera UI; reports a result only after all corners arrive. */
class ManualCalibrationInput {
    private val points = mutableListOf<Point2>()
    var isCollecting: Boolean = false
        private set
    val progress: Int get() = points.size

    fun begin() { points.clear(); isCollecting = true }
    fun cancel() { points.clear(); isCollecting = false }

    fun add(point: Point2): List<Point2>? {
        if (!isCollecting || points.size >= 4) return null
        points += point
        if (points.size < 4) return null
        isCollecting = false
        return points.toList()
    }
}
