package com.mahdi.poolshito.core.analysis

import org.junit.Assert.*
import org.junit.Test

class FrameThrottleTest {
    @Test fun acceptsOnlyFramesBeyondConfiguredInterval() {
        val throttle = FrameThrottle(100L)
        assertTrue(throttle.accept(1_000L))
        assertFalse(throttle.accept(1_050L))
        assertTrue(throttle.accept(1_100L))
    }
}
