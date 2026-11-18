package com.mahdi.poolshito.core.vision

import com.mahdi.poolshito.core.analysis.BallKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BallColorClassifierTest {
    private val classifier = BallColorClassifier()

    @Test fun classifiesCueSolidStripeAndLeavesAmbiguousPixelsUnknown() {
        assertEquals(BallKind.CUE, classifier.classify(0.05, 220.0))
        assertEquals(BallKind.SOLID, classifier.classify(0.92, 140.0))
        assertEquals(BallKind.STRIPE, classifier.classify(0.42, 160.0))
        assertEquals(BallKind.UNKNOWN, classifier.classify(0.10, 95.0))
    }
}
