package com.mahdi.poolshito.core.vision

import com.mahdi.poolshito.core.analysis.BallKind

/** Conservative color-fraction classifier; uncertain pixels remain UNKNOWN rather than guessed. */
class BallColorClassifier {
    fun classify(saturatedPixelFraction: Double, meanValue: Double): BallKind = when {
        saturatedPixelFraction < 0.16 && meanValue > 135.0 -> BallKind.CUE
        saturatedPixelFraction in 0.16..0.58 && meanValue > 85.0 -> BallKind.STRIPE
        saturatedPixelFraction > 0.58 -> BallKind.SOLID
        else -> BallKind.UNKNOWN
    }
}
