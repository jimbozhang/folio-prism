package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.DATE1
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GROWTH
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.transfer
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.money.bd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFilterTest {

    private val moved = transfer(
        from = ref(INST_A, FUND),
        to = ref(INST_B, CASH_CNY),
        fromQuantity = "1",
        toQuantity = "100",
    )
    private val opened = opening(ref(INST_A, GROWTH), amount = "500")
    private val dividend = IncomeEvent(
        kind = IncomeKind.DIVIDEND,
        destination = ref(INST_B, CASH_CNY),
        amount = bd("5"),
        currency = "CNY",
        date = DATE1,
        source = ref(INST_A, FUND),
    )
    private val interest = IncomeEvent(
        kind = IncomeKind.INTEREST,
        destination = ref(INST_B, CASH_CNY),
        amount = bd("2"),
        currency = "CNY",
        date = DATE1,
    )
    private val feePaying = transfer(
        from = ref(INST_A, FUND),
        to = ref(INST_A, GROWTH),
        fromQuantity = "1",
        toQuantity = "1",
    ).copy(
        feePosition = ref(INST_B, CASH_CNY),
        feeAmount = bd("1"),
        feeCurrency = "CNY",
    )

    @Test
    fun emptyFilterMatchesEverything() {
        val filter = TransactionFilter()

        assertTrue(filter.isEmpty)
        assertEquals(0, filter.activeCount)
        assertTrue(filter.matches(moved))
        assertTrue(filter.matches(opened))
    }

    @Test
    fun typeFilterMatchesOnlyThatType() {
        val filter = TransactionFilter(type = "transfer")

        assertTrue(filter.matches(moved))
        assertFalse(filter.matches(opened))
        assertFalse(filter.matches(dividend))
    }

    @Test
    fun typeFilterGroupsDividendAndInterestAsIncome() {
        val filter = TransactionFilter(type = "income")

        assertTrue(filter.matches(dividend))
        assertTrue(filter.matches(interest))
        assertFalse(filter.matches(moved))
    }

    @Test
    fun institutionFilterMatchesEitherLeg() {
        val filter = TransactionFilter(institutionId = INST_B)

        assertTrue(filter.matches(moved))
        assertFalse(filter.matches(opened))
    }

    @Test
    fun instrumentFilterMatchesEitherLeg() {
        val filter = TransactionFilter(instrumentId = FUND)

        assertTrue(filter.matches(moved))
        assertTrue(filter.matches(dividend))
        assertFalse(filter.matches(opened))
    }

    @Test
    fun institutionAndInstrumentMustMatchTheSameLeg() {
        // FUND belongs to INST_A and CASH_CNY to INST_B: the combination exists
        // only on the out leg, so it is matched; crossing the legs is not.
        assertTrue(TransactionFilter(institutionId = INST_A, instrumentId = FUND).matches(moved))
        assertFalse(TransactionFilter(institutionId = INST_B, instrumentId = FUND).matches(moved))
    }

    @Test
    fun incomeWithoutSourceMatchesDestinationOnly() {
        assertTrue(TransactionFilter(institutionId = INST_B).matches(interest))
        assertFalse(TransactionFilter(institutionId = INST_A).matches(interest))
    }

    @Test
    fun transferFeeAccountCountsAsTouchedPosition() {
        assertTrue(TransactionFilter(institutionId = INST_B).matches(feePaying))
        assertTrue(TransactionFilter(instrumentId = CASH_CNY).matches(feePaying))
        // The two legs still win when both filters are set together.
        assertFalse(
            TransactionFilter(institutionId = INST_B, instrumentId = FUND).matches(feePaying),
        )
    }

    @Test
    fun activeCountCountsSetDimensions() {
        val filter = TransactionFilter(
            type = "transfer",
            institutionId = INST_A,
            instrumentId = FUND,
        )

        assertEquals(3, filter.activeCount)
        assertFalse(filter.isEmpty)
    }
}
