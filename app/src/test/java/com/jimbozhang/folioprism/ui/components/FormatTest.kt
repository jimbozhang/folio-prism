package com.jimbozhang.folioprism.ui.components

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun cashCnyPadsToTwoDecimals() {
        assertEquals("¥4,350.00", formatCashCny(BigDecimal("4350")))
        assertEquals("¥4,350.00", formatCashCny(BigDecimal("4350.00")))
        assertEquals("¥38.70", formatCashCny(BigDecimal("38.7")))
        assertEquals("--", formatCashCny(null))
    }

    @Test
    fun cashNativePadsToTwoDecimals() {
        assertEquals("¥250.00", formatCashNative(BigDecimal("250"), "CNY"))
        assertEquals("¥250.00", formatCashNative(BigDecimal("250"), "CNH"))
        assertEquals("$150.00", formatCashNative(BigDecimal("150"), "USD"))
        assertEquals("HK$600.00", formatCashNative(BigDecimal("600"), "HKD"))
        assertEquals("--", formatCashNative(null, "CNY"))
    }

    @Test
    fun cashNumberHasNoPrefix() {
        assertEquals("4,350.00", formatCashNumber(BigDecimal("4350")))
        assertEquals("0.00", formatCashNumber(BigDecimal.ZERO))
    }

    @Test
    fun nonCashFormattersKeepExactDigits() {
        assertEquals("¥4,350", formatCny(BigDecimal("4350.00")))
        assertEquals("¥38.7", formatCny(BigDecimal("38.7")))
        assertEquals("$150", formatNative(BigDecimal("150"), "USD"))
        assertEquals("4,000.5", formatQuantity(BigDecimal("4000.5")))
    }
}
