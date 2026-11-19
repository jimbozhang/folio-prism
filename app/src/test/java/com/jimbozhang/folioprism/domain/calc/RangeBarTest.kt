package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.money.bd
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeBarTest {

    private fun window(share: Float, min: Float, max: Float) = RangeBarWindow.of(share, min, max)

    @Test
    fun shareInsideBandPadsBothSides() {
        // band [0.29, 0.35], share 0.30: span 0.06, pad 0.009
        val w = window(0.30f, 0.29f, 0.35f)
        assertEquals(0.281f, w.min, 1e-6f)
        assertEquals(0.359f, w.max, 1e-6f)
    }

    @Test
    fun shareBelowBandExtendsWindowDownToShare() {
        // band [0.30, 0.35], share 0.20: span 0.15, pad 0.0225
        val w = window(0.20f, 0.30f, 0.35f)
        assertEquals(0.1775f, w.min, 1e-6f)
        assertEquals(0.3725f, w.max, 1e-6f)
        // The pad extends beyond the share, so the marker sits one pad inside
        // the left edge rather than clamped to it: 0.0225 / 0.195.
        assertEquals(0.0225f / 0.195f, w.position(0.20f), 1e-6f)
        assertEquals(0.1725f / 0.195f, w.position(0.35f), 1e-6f)
    }

    @Test
    fun shareAboveBandKeepsBandSpan() {
        // band [0.18, 0.22], share 0.30: span = 0.22 - 0.18, pad 0.006
        val w = window(0.30f, 0.18f, 0.22f)
        assertEquals(0.174f, w.min, 1e-6f)
        assertEquals(0.306f, w.max, 1e-6f)
        // The pad extends beyond the share, so the marker sits one pad inside
        // the right edge rather than clamped to it: 0.126 / 0.132.
        assertEquals(0.126f / 0.132f, w.position(0.30f), 1e-6f)
    }

    @Test
    fun barMinFloorsAtZero() {
        // share 0.01 below band [0.02, 0.22]: span 0.21, pad 0.0315 -> barMin < 0, floored
        val w = window(0.01f, 0.02f, 0.22f)
        assertEquals(0f, w.min, 1e-6f)
        assertEquals(0.2515f, w.max, 1e-6f)
    }

    @Test
    fun minimumPadForNarrowBand() {
        // span 0.0005 * 0.15 = 0.000075 < 0.005 -> pad 0.005
        val w = window(0.10f, 0.10f, 0.1005f)
        assertEquals(0.095f, w.min, 1e-6f)
        assertEquals(0.1055f, w.max, 1e-6f)
    }

    @Test
    fun positionClampsOutsideWindow() {
        val w = window(0.20f, 0.30f, 0.35f)
        assertEquals(0f, w.position(0f), 1e-6f)
        assertEquals(1f, w.position(0.99f), 1e-6f)
        assertEquals(0.5f, w.position(0.275f), 1e-6f)
    }

    @Test
    fun degenerateWindowMapsToZero() {
        assertEquals(0f, RangeBarWindow(0.1f, 0.1f).position(0.1f), 1e-6f)
    }

    // --- overview scale ---

    private fun snapshot(
        visualShare: String,
        visualTarget: String,
        visualMin: String = "0",
        visualMax: String = "0",
    ) =
        CategorySnapshot(
            category = Category(
                id = "cat",
                name = "cat",
                order = 0,
                color = "#000000",
                targetPct = bd("10"),
                minPct = bd("0"),
                maxPct = bd("100"),
            ),
            valueCny = BigDecimal.ZERO,
            share = BigDecimal.ZERO,
            targetShare = BigDecimal.ZERO,
            targetValueCny = null,
            minValueCny = null,
            maxValueCny = null,
            inRange = false,
            gapToTargetCny = null,
            gapToMinCny = null,
            gapToMaxCny = null,
            incomplete = false,
            visualShare = bd(visualShare),
            visualTarget = bd(visualTarget),
            visualMin = bd(visualMin),
            visualMax = bd(visualMax),
        )

    @Test
    fun overviewScaleTakesMaxActualAndTarget() {
        val scale = SnapshotCalculator.overviewScale(
            listOf(
                snapshot("0.32", "0.30"),
                snapshot("0.10", "0.08"),
            ),
        )
        assertEquals(0.32f * 1.15f, scale, 1e-6f)
    }

    @Test
    fun overviewScaleIgnoresMinAndMaxMarks() {
        // Only actual and target feed the scale, not the min/max range marks.
        val scale = SnapshotCalculator.overviewScale(
            listOf(snapshot("0.05", "0.04", visualMin = "0.5", visualMax = "0.6")),
        )
        assertEquals(0.1f * 1.15f, scale, 1e-6f)
    }

    @Test
    fun overviewScaleFloorsAtTenPercent() {
        val scale = SnapshotCalculator.overviewScale(listOf(snapshot("0.0", "0.0")))
        assertEquals(0.1f * 1.15f, scale, 1e-6f)
    }

    @Test
    fun overviewScaleCapsAtHundredPercent() {
        val scale = SnapshotCalculator.overviewScale(listOf(snapshot("0.95", "0.90")))
        assertEquals(1f, scale, 1e-6f)
    }
}
