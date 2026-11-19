package com.mahdi.poolshito.core.vision

import com.mahdi.poolshito.core.analysis.BallKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BallColorClassifierTest {
    private val classifier = BallColorClassifier()

    @Test fun detectsCueSolidAndSpatiallyStripedPixelPatterns() {
        assertEquals(BallKind.CUE, classifier.classify(0.05, 220.0, 0.03, 0.06))
        assertEquals(BallKind.SOLID, classifier.classify(0.92, 140.0, 0.88, 0.95))
        assertEquals(BallKind.STRIPE, classifier.classify(0.42, 160.0, 0.08, 0.91))
    }

    @Test fun partialColorWithoutBandEvidenceRemainsUnknown() {
        assertEquals(BallKind.UNKNOWN, classifier.classify(0.42, 160.0, 0.39, 0.45))
        assertEquals(BallKind.UNKNOWN, classifier.classify(0.10, 95.0, 0.05, 0.12))
        assertEquals(BallKind.UNKNOWN, classifier.classify(Double.NaN, 200.0, 0.0, 0.0))
    }
}
