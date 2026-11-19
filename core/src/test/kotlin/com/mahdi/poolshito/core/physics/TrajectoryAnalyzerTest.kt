package com.mahdi.poolshito.core.physics

import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.core.analysis.TrajectoryKind
import com.mahdi.poolshito.geometry.Point2
import org.junit.Assert.*
import org.junit.Test

class TrajectoryAnalyzerTest {
    @Test fun detectsForwardCollisionAndPredictsObjectBallPath() {
        val result = TrajectoryAnalyzer().analyze(listOf(
            TrajectoryAnalyzer.MotionBall(1, Point2(0.30, 0.50), Point2(0.8, 0.0), BallKind.CUE),
            TrajectoryAnalyzer.MotionBall(2, Point2(1.00, 0.50), Point2(0.0, 0.0), BallKind.SOLID),
        ))!!
        assertEquals(2, result.segments.size)
        assertEquals(TrajectoryKind.CUE_BALL, result.segments[0].kind)
        assertEquals(TrajectoryKind.OBJECT_BALL, result.segments[1].kind)
        assertTrue(result.segments[0].points.last().x < 1.0)
    }

    @Test fun thinCutUsesCentersAtContactToSetObjectBallDirection() {
        val cue = Point2(0.40, 0.50)
        val target = Point2(0.90, 0.54)
        val result = TrajectoryAnalyzer().analyze(listOf(
            TrajectoryAnalyzer.MotionBall(1, cue, Point2(0.8, 0.0), BallKind.CUE),
            TrajectoryAnalyzer.MotionBall(2, target, Point2(0.0, 0.0), BallKind.SOLID),
        ))!!
        val objectPath = result.segments.single { it.kind == TrajectoryKind.OBJECT_BALL }.points
        val direction = (objectPath[1] - objectPath[0]).normalized()
        val expectedX = kotlin.math.sqrt(0.045 * 0.045 - 0.04 * 0.04) / 0.045
        val expectedY = 0.04 / 0.045
        assertEquals(expectedX, direction.x, 1e-5)
        assertEquals(expectedY, direction.y, 1e-5)
    }

    @Test fun suggestsClearPocketRouteWithoutClaimingAimWasObserved() {
        val result = TrajectoryAnalyzer().analyze(listOf(
            TrajectoryAnalyzer.MotionBall(1, Point2(0.45, 0.5), Point2(0.0, 0.0), BallKind.CUE),
            TrajectoryAnalyzer.MotionBall(2, Point2(1.0, 0.5), Point2(0.0, 0.0), BallKind.SOLID),
        ))!!
        assertEquals(2, result.segments.size)
        assertTrue(result.description.contains("not a shot prediction"))
    }

    @Test fun rejectsMissingCueAndReflectsAtCushion() {
        assertNull(TrajectoryAnalyzer().analyze(listOf(TrajectoryAnalyzer.MotionBall(2, Point2(1.0, 0.5), Point2(0.0, 0.0), BallKind.SOLID))))
        val path = TrajectoryAnalyzer().analyze(listOf(TrajectoryAnalyzer.MotionBall(1, Point2(1.8, 0.5), Point2(0.8, 0.0), BallKind.CUE)))!!.segments.single().points
        assertEquals(2.0 - 0.0225, path[1].x, 1e-6)
        assertTrue(path.last().x < 2.0)
    }
}
