package com.mahdi.poolshito.core.calibration

import com.mahdi.poolshito.geometry.Point2
import kotlin.math.hypot

/** Smooths small frame-to-frame table jitter and identifies camera/table jumps that invalidate tracks. */
class TableCornerSmoother(
    private val newMeasurementWeight: Double = 0.18,
    private val discontinuityFractionOfDiagonal: Double = 0.045,
) {
    data class Update(val corners: List<Point2>, val discontinuity: Boolean)

    private var stableCorners: List<Point2>? = null

    init {
        require(newMeasurementWeight in 0.0..1.0)
        require(discontinuityFractionOfDiagonal > 0.0)
    }

    fun update(corners: List<Point2>, frameWidth: Int, frameHeight: Int): Update {
        require(corners.size == 4) { "Table detection must have four corners" }
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive" }
        val previous = stableCorners
        val diagonal = hypot(frameWidth.toDouble(), frameHeight.toDouble())
        val displacement = previous?.zip(corners)?.map { (a, b) -> hypot(a.x - b.x, a.y - b.y) }?.average()
        val discontinuity = displacement != null && displacement / diagonal >= discontinuityFractionOfDiagonal
        val next = if (previous == null || discontinuity) corners.toList() else previous.zip(corners).map { (a, b) ->
            Point2(a.x * (1.0 - newMeasurementWeight) + b.x * newMeasurementWeight,
                a.y * (1.0 - newMeasurementWeight) + b.y * newMeasurementWeight)
        }
        stableCorners = next
        return Update(next, discontinuity)
    }

    fun reset() { stableCorners = null }
}
