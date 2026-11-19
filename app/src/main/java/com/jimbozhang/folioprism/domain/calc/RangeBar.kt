package com.jimbozhang.folioprism.domain.calc

/**
 * The adaptive axis window of a category range bar.
 *
 * The bar does not start at zero: it spans [min, max], where the window
 * covers the rule band and the current share, padded by 15% of the span
 * (at least 0.5 percentage points) on each side, floored at zero.
 * Everything rendered on the bar maps linearly over this window.
 */
data class RangeBarWindow(
    val min: Float,
    val max: Float,
) {
    /** Linear position of [value] on the bar, clamped to [0, 1]. */
    fun position(value: Float): Float {
        val range = max - min
        if (range <= 0f) return 0f
        return ((value - min) / range).coerceIn(0f, 1f)
    }

    companion object {
        /** Builds the window around the rule band [minShare, maxShare] and the current [share]. */
        fun of(share: Float, minShare: Float, maxShare: Float): RangeBarWindow {
            val span = maxShare - minOf(minShare, share)
            val pad = maxOf(span * 0.15f, 0.005f)
            val barMin = maxOf(0f, minOf(minShare, share) - pad)
            val barMax = maxOf(maxShare, share) + pad
            return RangeBarWindow(barMin, barMax)
        }
    }
}
