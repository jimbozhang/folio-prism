package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.money.bd
import org.junit.Test

class EventValuesTest {

    @Test
    fun balancedTransferHasNoLoss() {
        val values = EventValues.transferValues(
            fromQuantity = bd("1000"),
            toQuantity = bd("100"),
            fromPrice = null,
            toPrice = bd("10"),
            fromFxToCny = bd("1"),
            toFxToCny = bd("1"),
        )

        assertDecimal("1000", values.fromValueCny)
        assertDecimal("1000", values.toValueCny)
        assertDecimal("0", values.lossValueCny)
    }

    @Test
    fun lossIsFromMinusTo() {
        val values = EventValues.transferValues(
            fromQuantity = bd("1000"),
            toQuantity = bd("99"),
            fromPrice = null,
            toPrice = bd("10"),
            fromFxToCny = bd("1"),
            toFxToCny = bd("1"),
        )

        assertDecimal("1000", values.fromValueCny)
        assertDecimal("990", values.toValueCny)
        assertDecimal("10", values.lossValueCny)
    }

    @Test
    fun negativeLossIsRecordedAsNegativeExpense() {
        val values = EventValues.transferValues(
            fromQuantity = bd("990"),
            toQuantity = bd("100"),
            fromPrice = null,
            toPrice = bd("10"),
            fromFxToCny = bd("1"),
            toFxToCny = bd("1"),
        )

        assertDecimal("-10", values.lossValueCny)
    }

    @Test
    fun fxIsAppliedToBothSides() {
        val values = EventValues.transferValues(
            fromQuantity = bd("100"),
            toQuantity = bd("700"),
            fromPrice = null,
            toPrice = null,
            fromFxToCny = bd("7"),
            toFxToCny = bd("1"),
        )

        assertDecimal("700", values.fromValueCny)
        assertDecimal("700", values.toValueCny)
        assertDecimal("0", values.lossValueCny)
    }
}
