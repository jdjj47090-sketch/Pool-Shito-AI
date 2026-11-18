package com.mahdi.poolshito.core.tracking

import com.mahdi.poolshito.core.analysis.BallDetection
import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.geometry.Point2
import org.junit.Assert.*
import org.junit.Test

class BallTrackerTest {
    private fun d(x: Double) = BallDetection(Point2(x, 100.0), 12.0, 0.9, BallKind.CUE)

    @Test fun keepsPersistentIdAndEstimatesDirection() {
        val tracker = BallTracker(gateDistancePx = 100.0)
        val first = tracker.update(listOf(d(100.0)), 1_000_000_000L).single()
        val next = tracker.update(listOf(d(110.0)), 1_100_000_000L).single()
        assertEquals(first.id, next.id)
        assertTrue("expected rightward velocity, got ${next.velocity.x}", next.velocity.x > 0.0)
        assertEquals(2, next.ageFrames)
    }

    @Test fun survivesBriefDetectionLossAndExpiresOldTrack() {
        val tracker = BallTracker(maxMissedFrames = 2, gateDistancePx = 100.0)
        val first = tracker.update(listOf(d(100.0)), 1_000_000_000L).single()
        assertEquals(1, tracker.update(emptyList(), 1_033_000_000L).single().missedFrames)
        val reacquired = tracker.update(listOf(d(101.0)), 1_066_000_000L).single()
        assertEquals(first.id, reacquired.id)
        tracker.update(emptyList(), 1_099_000_000L)
        tracker.update(emptyList(), 1_132_000_000L)
        assertTrue(tracker.update(emptyList(), 1_165_000_000L).isEmpty())
    }
}
