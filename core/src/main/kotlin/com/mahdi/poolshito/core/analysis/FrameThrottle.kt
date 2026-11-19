package com.mahdi.poolshito.core.analysis

/** Simple monotonic-time gate; intended to run on the camera analysis executor. */
class FrameThrottle(private var minimumIntervalNs: Long) {
    @Synchronized fun setIntervalNs(value: Long) { minimumIntervalNs = value.coerceAtLeast(1L) }
    private var lastAcceptedNs = Long.MIN_VALUE
    @Synchronized fun accept(timestampNs: Long): Boolean {
        if (lastAcceptedNs != Long.MIN_VALUE) {
            val elapsedNs = timestampNs - lastAcceptedNs
            if (elapsedNs < 0L) {
                // Camera timestamps can restart when a device switches sensors or recreates its stream.
                lastAcceptedNs = timestampNs
                return true
            }
            if (elapsedNs < minimumIntervalNs) return false
        }
        lastAcceptedNs = timestampNs
        return true
    }
    @Synchronized fun reset() { lastAcceptedNs = Long.MIN_VALUE }
}
