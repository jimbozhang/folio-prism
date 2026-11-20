package com.jimbozhang.folioprism.ui.components

import com.jimbozhang.folioprism.domain.money.bd
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun compactCnyKeepsSmallAmountsAsIntegers() {
        assertEquals("--", formatCnyCompact(null))
        assertEquals("¥0", formatCnyCompact(bd("0")))
        assertEquals("¥8,320", formatCnyCompact(bd("8320")))
        assertEquals("¥9,999", formatCnyCompact(bd("9999.4")))
    }

    @Test
    fun compactCnyUsesWanAndYiUnits() {
        assertEquals("¥1万", formatCnyCompact(bd("10000")))
        assertEquals("¥28.8万", formatCnyCompact(bd("288000")))
        assertEquals("¥316万", formatCnyCompact(bd("3160000")))
        assertEquals("¥1亿", formatCnyCompact(bd("100000000")))
        assertEquals("¥1.23亿", formatCnyCompact(bd("123456789")))
    }

    @Test
    fun compactCnyKeepsTheSign() {
        assertEquals("-¥3.7万", formatCnyCompact(bd("-36838")))
        assertEquals("-¥520", formatCnyCompact(bd("-520")))
    }

    @Test
    fun cnyKeepsEveryDecimalDigit() {
        assertEquals("--", formatCny(null))
        assertEquals("¥39.169", formatCny(bd("39.169")))
        assertEquals("¥-0.001", formatCny(bd("-0.001")))
        assertEquals("¥252,076.27", formatCny(bd("252076.27")))
    }

    @Test
    fun cnyDoesNotPadTrailingZeros() {
        assertEquals("¥598.7", formatCny(bd("598.70")))
        assertEquals("¥100", formatCny(bd("100.00")))
    }

    @Test
    fun nativeKeepsEveryDecimalDigit() {
        assertEquals("HK$39.169", formatNative(bd("39.169"), "HKD"))
        assertEquals("$5.0001", formatNative(bd("5.0001"), "USD"))
    }

    @Test
    fun quantityKeepsEveryDecimalDigit() {
        assertEquals("39.169", formatQuantity(bd("39.169")))
        assertEquals("100", formatQuantity(bd("100.00")))
    }
}
