package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFeeTest {

    private fun data() = portfolio(
        institutions = listOf(Institution(INST_A, "A")),
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
        ),
    )

    private fun dataWithUsd() = portfolio(
        institutions = listOf(Institution(INST_A, "A")),
        instruments = listOf(
            cashInstrument(CASH_USD, "USD"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
        ),
    )

    private fun formWithFee(fee: String): EventForm = EventForm(null, data()).apply {
        type = "transfer"
        date = "2026-01-01 10:00"
        fromInstitutionId = INST_A
        fromInstrumentId = FUND
        fromQuantity = "10"
        fromPrice = "2"
        fromFx = "1"
        toInstitutionId = INST_A
        toInstrumentId = CASH_CNY
        toQuantity = "20"
        toFx = "1"
        transferFee = fee
    }

    @Test
    fun feeCreatesLinkedExpense() {
        val form = formWithFee("1.50")
        val events = buildEvents(data(), QuoteSnapshot(), form, null).getOrThrow()

        assertEquals(2, events.size)
        val transfer = events.filterIsInstance<TransferEvent>().single()
        val fee = events.filterIsInstance<ExpenseEvent>().single()
        assertEquals("转账手续费", fee.note)
        assertDecimal("1.50", fee.amount)
        assertDecimal("1.50", fee.amountCny)
        assertEquals("CNY", fee.currency)
        assertEquals(PositionRef(INST_A, CASH_CNY), fee.position)
        assertEquals(CategoryIds.GROWTH, fee.categoryId)
        assertEquals(transfer.id, fee.linkedTransferId)
    }

    @Test
    fun zeroFeeCreatesNoExpense() {
        val form = formWithFee("0")
        val events = buildEvents(data(), QuoteSnapshot(), form, null).getOrThrow()

        assertEquals(1, events.size)
        assertTrue(events.single() is TransferEvent)
    }

    @Test
    fun foreignCashFeeUsesCashCurrencyAndRate() {
        val form = EventForm(null, dataWithUsd()).apply {
            type = "transfer"
            date = "2026-01-01 10:00"
            fromInstitutionId = INST_A
            fromInstrumentId = CASH_USD
            fromQuantity = "10"
            fromFx = "7"
            toInstitutionId = INST_A
            toInstrumentId = FUND
            toQuantity = "35"
            toPrice = "2"
            toFx = "1"
            transferFee = "1.50"
        }
        val events = buildEvents(dataWithUsd(), QuoteSnapshot(), form, null).getOrThrow()

        assertEquals(2, events.size)
        val fee = events.filterIsInstance<ExpenseEvent>().single()
        assertEquals("USD", fee.currency)
        assertDecimal("1.50", fee.amount)
        assertDecimal("10.50", fee.amountCny)
        assertEquals(PositionRef(INST_A, CASH_USD), fee.position)
        assertEquals(CategoryIds.GROWTH, fee.categoryId)
    }

    @Test
    fun editingTransferLoadsLinkedFee() {
        val form = formWithFee("1.50")
        val events = buildEvents(data(), QuoteSnapshot(), form, null).getOrThrow()
        val transfer = events.filterIsInstance<TransferEvent>().single()
        val loaded = EventForm(transfer, data().copy(events = events))

        assertEquals("1.50", loaded.transferFee)
    }
}
