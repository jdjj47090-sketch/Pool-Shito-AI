package com.mahdi.poolshito.core.vision

import com.mahdi.poolshito.core.analysis.BallKind

/** Conservative HSV coverage classifier. A stripe requires a spatial color band, not just partial color. */
class BallColorClassifier {
    fun classify(
        saturatedPixelFraction: Double,
        meanValue: Double,
        centerSaturatedFraction: Double,
        outerSaturatedFraction: Double,
    ): BallKind {
        if (listOf(saturatedPixelFraction, meanValue, centerSaturatedFraction, outerSaturatedFraction).any { !it.isFinite() }) {
            return BallKind.UNKNOWN
        }
        if (saturatedPixelFraction < 0.16 && meanValue > 135.0) return BallKind.CUE

        val radialBandEvidence =
            saturatedPixelFraction in 0.16..0.62 &&
                centerSaturatedFraction <= 0.42 &&
                outerSaturatedFraction >= 0.58 &&
                outerSaturatedFraction - centerSaturatedFraction >= 0.22 &&
                meanValue > 85.0
        if (radialBandEvidence) return BallKind.STRIPE

        if (saturatedPixelFraction >= 0.58 && centerSaturatedFraction >= 0.48) return BallKind.SOLID
        return BallKind.UNKNOWN
    }
}
