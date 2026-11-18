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
