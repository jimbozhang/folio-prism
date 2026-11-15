package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.money.bd
import org.junit.Test

class EventValuesTest {

    @Test
    fun balancedTransferHasEqualNativeValues() {
        val values = EventValues.transferValues(
            fromQuantity = bd("1000"),
            toQuantity = bd("100"),
            fromPrice = null,
            toPrice = bd("10"),
        )

        assertDecimal("1000", values.fromNative)
        assertDecimal("1000", values.toNative)
    }

    @Test
    fun nativeLossIsFromMinusTo() {
        val values = EventValues.transferValues(
            fromQuantity = bd("1000"),
            toQuantity = bd("99"),
            fromPrice = null,
            toPrice = bd("10"),
        )

        assertDecimal("1000", values.fromNative)
        assertDecimal("990", values.toNative)
        assertDecimal("10", values.fromNative.subtract(values.toNative))
    }

    @Test
    fun negativeNativeLossIsAllowed() {
        val values = EventValues.transferValues(
            fromQuantity = bd("990"),
            toQuantity = bd("100"),
            fromPrice = null,
            toPrice = bd("10"),
        )

        assertDecimal("-10", values.fromNative.subtract(values.toNative))
    }
}
