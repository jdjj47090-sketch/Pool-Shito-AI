package com.mahdi.poolshito.vision

import com.mahdi.poolshito.core.analysis.AnalysisSnapshot
import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.core.analysis.CalibrationStatus
import com.mahdi.poolshito.core.calibration.TableCalibration
import com.mahdi.poolshito.core.calibration.TableCornerSmoother
import com.mahdi.poolshito.core.physics.TrajectoryAnalyzer
import com.mahdi.poolshito.core.tracking.BallTracker
import com.mahdi.poolshito.geometry.Point2
import org.opencv.core.Mat
import kotlin.math.hypot

/** Single-threaded real-frame analysis: detect -> calibrate -> track -> physics -> image-space overlay. */
class PoolAnalysisEngine(
    private val detector: PoolFrameDetector = OpenCvPoolDetector(),
    private val tracker: BallTracker = BallTracker(),
    private val trajectoryAnalyzer: TrajectoryAnalyzer = TrajectoryAnalyzer(),
) : AutoCloseable {
    private var manualCorners: List<Point2>? = null
    private var calibration: TableCalibration? = null
    private val autoCornerSmoother = TableCornerSmoother()
    private var calibrationConfidence = 0.0
    private var tableMisses = 0

    fun setManualCalibration(corners: List<Point2>) {
        require(corners.size == 4) { "Manual calibration requires four corners" }
        val next = TableCalibration.fromCorners(corners) ?: throw IllegalArgumentException("The selected table corners are degenerate")
        manualCorners = next.imageCorners; calibration = next; calibrationConfidence = 1.0; tableMisses = 0
        autoCornerSmoother.reset(); tracker.reset()
    }

    fun clearCalibration() {
        manualCorners = null; autoCornerSmoother.reset(); calibration = null
        calibrationConfidence = 0.0; tableMisses = 0; tracker.reset()
    }

    fun process(rgb: Mat, timestampNs: Long): AnalysisSnapshot {
        val frame = detector.process(rgb, manualCorners)
        val detectedTable = frame.table
        if (manualCorners == null && detectedTable != null) {
            tableMisses = 0
            val update = autoCornerSmoother.update(detectedTable.corners, rgb.cols(), rgb.rows())
            if (update.discontinuity) tracker.reset()
            calibration = TableCalibration.fromCorners(update.corners)
            calibrationConfidence = detectedTable.confidence
        } else if (manualCorners == null) {
            tableMisses++
            if (tableMisses > 80) { calibration = null; autoCornerSmoother.reset(); calibrationConfidence = 0.0 }
        }

        val currentCalibration = calibration
        val tracks = tracker.update(frame.balls, timestampNs)
        val motions = if (currentCalibration != null) tracks.filter { it.missedFrames == 0 }.mapNotNull { track ->
            val p0 = currentCalibration.imageToTable(track.position) ?: return@mapNotNull null
            val p1 = currentCalibration.imageToTable(track.position + track.velocity * 0.1) ?: return@mapNotNull null
            TrajectoryAnalyzer.MotionBall(track.id, p0, (p1 - p0) * 10.0, track.kind)
        } else emptyList()
        val trajectory = trajectoryAnalyzer.analyze(motions)
        val imagePaths = trajectory?.segments.orEmpty().map { segment ->
            segment.copy(points = segment.points.mapNotNull { currentCalibration?.tableToImage(it) })
        }.filter { it.points.size >= 2 }
        val status = when {
            currentCalibration == null -> "Table not found — tap Calibrate and mark its four corners"
            manualCorners != null -> "Manual calibration · ${frame.balls.size} balls"
            tableMisses > 0 -> "Table temporarily lost · using held calibration · ${frame.balls.size} balls"
            else -> "Table calibrated · ${frame.balls.size} balls"
        }
        return AnalysisSnapshot(
            rgb.cols(), rgb.rows(), currentCalibration?.imageCorners.orEmpty(),
            when { currentCalibration == null -> CalibrationStatus.MANUAL_REQUIRED; else -> CalibrationStatus.CALIBRATED },
            calibrationConfidence, frame.balls, tracks, imagePaths, 0L, status,
        )
    }

    override fun close() { detector.close(); tracker.reset() }
}
