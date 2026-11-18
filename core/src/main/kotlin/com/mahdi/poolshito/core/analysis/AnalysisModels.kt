package com.mahdi.poolshito.core.analysis

import com.mahdi.poolshito.geometry.Point2

enum class BallKind { CUE, SOLID, STRIPE, UNKNOWN }
enum class CalibrationStatus { NOT_FOUND, MANUAL_REQUIRED, CALIBRATED }

data class BallDetection(val center: Point2, val radiusPx: Double, val confidence: Double, val kind: BallKind)
data class BallTrack(
    val id: Int, val position: Point2, val velocity: Point2, val radiusPx: Double,
    val kind: BallKind, val confidence: Double, val ageFrames: Int, val missedFrames: Int,
) {
    val speed get() = velocity.length()
    val isMoving get() = speed >= 4.0
}
data class AnalysisSnapshot(
    val imageWidth: Int, val imageHeight: Int, val tableCorners: List<Point2>,
    val calibrationStatus: CalibrationStatus, val calibrationConfidence: Double,
    val detections: List<BallDetection>, val tracks: List<BallTrack>,
    val trajectories: List<TrajectorySegment>, val latencyMs: Long, val statusMessage: String,
)
data class TrajectorySegment(val points: List<Point2>, val kind: TrajectoryKind, val ballId: Int, val description: String)
enum class TrajectoryKind { CUE_BALL, OBJECT_BALL }
