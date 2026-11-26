package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GROWTH
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.money.bd
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

    private fun fundData() = portfolio(
        institutions = listOf(Institution(INST_A, "A")),
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
            quotedInstrument(GROWTH, CategoryIds.DIVIDEND),
        ),
    )

    private fun cashToFundForm(feeLoss: String = "1.50"): EventForm =
        EventForm(null, data()).apply {
            type = "transfer"
            date = "2026-01-01 10:00"
            fromInstitutionId = INST_A
            fromInstrumentId = CASH_CNY
            fromQuantity = "20"
            toInstitutionId = INST_A
            toInstrumentId = FUND
            toQuantity = "9.25"
            toPrice = "2"
            this.feeLoss = feeLoss
        }

    private fun fundConversionForm(feeLoss: String = "1.50"): EventForm =
        EventForm(null, fundData()).apply {
            type = "transfer"
            date = "2026-01-01 10:00"
            fromInstitutionId = INST_A
            fromInstrumentId = FUND
            fromQuantity = "10"
            fromPrice = "2"
            toInstitutionId = INST_A
            toInstrumentId = GROWTH
            toQuantity = "18.5"
            toPrice = "1"
            this.feeLoss = feeLoss
        }

    @Test
    fun cashToFundFeeIsStoredOnTransferOnly() {
        val events = buildEvents(data(), QuoteSnapshot(), cashToFundForm(), null).getOrThrow()

        assertEquals(1, events.size)
        val transfer = events.single() as TransferEvent
        assertDecimal("1.50", transfer.lossAmount)
        assertTrue(events.none { it is ExpenseEvent })
        assertEquals(null, transfer.feePosition)
        assertEquals(null, transfer.feeAmount)
    }

    @Test
    fun fundConversionFeeIsStoredOnTransferOnly() {
        val events = buildEvents(fundData(), QuoteSnapshot(), fundConversionForm(), null).getOrThrow()

        assertEquals(1, events.size)
        val transfer = events.single() as TransferEvent
        assertDecimal("1.50", transfer.lossAmount)
        assertTrue(events.none { it is ExpenseEvent })
        assertEquals(null, transfer.feePosition)
    }

    @Test
    fun thirdPartyCashFeeRecordsCashPositionAndNativeAmount() {
        val form = fundConversionForm("2.50").apply {
            fromQuantity = "10"
            fromPrice = "2"
            toQuantity = "10"
            toPrice = "2"
            feeFromCash = true
            feeInstitutionId = INST_A
            feeInstrumentId = CASH_CNY
        }
        val events = buildEvents(fundData(), QuoteSnapshot(), form, null).getOrThrow()

        assertEquals(1, events.size)
        val transfer = events.single() as TransferEvent
        assertDecimal("0", transfer.lossAmount)
        assertEquals(PositionRef(INST_A, CASH_CNY), transfer.feePosition)
        assertDecimal("2.50", transfer.feeAmount)
        assertEquals("CNY", transfer.feeCurrency)
        assertTrue(events.none { it is ExpenseEvent })
    }

    @Test
    fun negativeFeeLossIsAllowed() {
        val form = fundConversionForm("-1.00").apply {
            toQuantity = "10"
            toPrice = "2.1"
        }
        val events = buildEvents(fundData(), QuoteSnapshot(), form, null).getOrThrow()

        val transfer = events.single() as TransferEvent
        assertDecimal("-1.00", transfer.lossAmount)
    }

    @Test
    fun editingTransferLoadsFeeLoss() {
        val events = buildEvents(data(), QuoteSnapshot(), cashToFundForm(), null).getOrThrow()
        val transfer = events.single() as TransferEvent
        val loaded = EventForm(transfer, data().copy(events = events))

        assertEquals("1.50", loaded.feeLoss)
    }

    @Test
    fun noFeeHasNoExpense() {
        val form = cashToFundForm("").apply {
            fromQuantity = "20"
            toQuantity = "10"
            toPrice = "2"
        }
        val events = buildEvents(data(), QuoteSnapshot(), form, null).getOrThrow()

        assertEquals(1, events.size)
        val transfer = events.single() as TransferEvent
        assertDecimal("0", transfer.lossAmount)
        assertTrue(events.none { it is ExpenseEvent })
    }

    private val hkStock = "ins_hk"

    private fun hkData() = portfolio(
        institutions = listOf(Institution(INST_A, "A")),
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(hkStock, CategoryIds.GROWTH, currency = "HKD", code = "01810"),
        ),
    )

    private fun hkQuotes() = QuoteSnapshot(fxRates = mapOf("HKD" to bd("0.9")))

    private fun cnyPriceForm(feeLoss: String = ""): EventForm =
        EventForm(null, hkData()).apply {
            type = "transfer"
            date = "2026-01-01 10:00"
            fromInstitutionId = INST_A
            fromInstrumentId = CASH_CNY
            fromQuantity = "910"
            toInstitutionId = INST_A
            toInstrumentId = hkStock
            toQuantity = "10"
            toPrice = "90"
            toPriceInCny = true
            this.feeLoss = feeLoss
        }

    @Test
    fun cnyPriceEntryConvertsToNativeAndDerivesExactLoss() {
        val transfer = buildEvents(hkData(), hkQuotes(), cnyPriceForm(), null)
            .getOrThrow().single() as TransferEvent

        // 90 CNY per share / 0.9 = 100 HKD, stored native.
        assertDecimal("100", transfer.toPrice)
        // 910 CNY paid - 10 * 90 CNY = 10 CNY loss, exact at entry.
        assertDecimal("10", transfer.lossAmount)
        assertEquals("CNY", transfer.lossCurrency)
    }

    @Test
    fun nativePriceEntryKeepsCrossCurrencyLossManual() {
        val form = cnyPriceForm().apply {
            toPriceInCny = false
            toPrice = "100"
        }
        val transfer = buildEvents(hkData(), hkQuotes(), form, null)
            .getOrThrow().single() as TransferEvent

        assertDecimal("100", transfer.toPrice)
        // No exact CNY leg: today's FX would distort a historical trade, so
        // the loss stays manual/zero.
        assertDecimal("0", transfer.lossAmount)
    }
}
