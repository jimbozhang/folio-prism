package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.quote.Quote
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferSuggestionTest {

    private val fund = quotedInstrument(FUND, CategoryIds.GROWTH, "CNY", "000001")
    private val fund2 = quotedInstrument(US_EQ, CategoryIds.US_EQUITY, "CNY", "000002")
    private val cashCny = cashInstrument(CASH_CNY, "CNY")
    private val cashUsd = cashInstrument(CASH_USD, "USD")
    private val data = portfolio(instruments = listOf(fund, fund2, cashCny, cashUsd))
    private val quotes = QuoteSnapshot(
        quotes = mapOf(
            FUND to Quote(FUND, bd("2"), "CNY", fund.name, "", "test"),
            US_EQ to Quote(US_EQ, bd("4"), "CNY", fund2.name, "", "test"),
        ),
        fxRates = mapOf("USD" to bd("7.2")),
    )

    @Test
    fun sameCurrencyFundSwitchUsesEqualValue() {
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = FUND,
            fromQuantity = bd("100"),
            fromPrice = bd("2"),
            toInstrumentId = US_EQ,
            toPrice = bd("4"),
        )
        assertDecimal("50", suggested)
    }

    @Test
    fun cashToFundUsesCashAmount() {
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = CASH_CNY,
            fromQuantity = bd("1000"),
            fromPrice = null,
            toInstrumentId = US_EQ,
            toPrice = bd("4"),
        )
        assertDecimal("250", suggested)
    }

    @Test
    fun fundToCashUsesProceeds() {
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = FUND,
            fromQuantity = bd("100"),
            fromPrice = bd("2"),
            toInstrumentId = CASH_CNY,
            toPrice = null,
        )
        assertDecimal("200", suggested)
    }

    @Test
    fun crossCurrencyIsConvertedWithCurrentFx() {
        val usdToCny = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = CASH_USD,
            fromQuantity = bd("100"),
            fromPrice = null,
            toInstrumentId = US_EQ,
            toPrice = bd("4"),
        )
        assertDecimal("180", usdToCny)

        val cnyToUsd = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = CASH_CNY,
            fromQuantity = bd("720"),
            fromPrice = null,
            toInstrumentId = CASH_USD,
            toPrice = null,
        )
        assertDecimal("100", cnyToUsd)
    }

    @Test
    fun missingFxReturnsNull() {
        val noFx = QuoteSnapshot(quotes = quotes.quotes)
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = noFx,
            fromInstrumentId = CASH_USD,
            fromQuantity = bd("100"),
            fromPrice = null,
            toInstrumentId = CASH_CNY,
            toPrice = null,
        )
        assertNull(suggested)
    }

    @Test
    fun missingPricesReturnNull() {
        val missingFromPrice = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = FUND,
            fromQuantity = bd("100"),
            fromPrice = null,
            toInstrumentId = CASH_CNY,
            toPrice = null,
        )
        assertNull(missingFromPrice)

        val missingToPrice = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = CASH_CNY,
            fromQuantity = bd("100"),
            fromPrice = null,
            toInstrumentId = US_EQ,
            toPrice = null,
        )
        assertNull(missingToPrice)
    }

    @Test
    fun sameInstrumentKeepsQuantity() {
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = FUND,
            fromQuantity = bd("100.5"),
            fromPrice = bd("2"),
            toInstrumentId = FUND,
            toPrice = bd("2"),
        )
        assertDecimal("100.5", suggested)
    }

    @Test
    fun derivedQuantityRoundsDownToPrecision() {
        val suggested = TransferSuggestion.suggestToQuantity(
            data = data,
            quotes = quotes,
            fromInstrumentId = CASH_CNY,
            fromQuantity = bd("100"),
            fromPrice = null,
            toInstrumentId = US_EQ,
            toPrice = bd("3"),
        )
        assertDecimal("33.33", suggested)
    }

    @Test
    fun nonPositiveQuantityReturnsNull() {
        for (quantity in listOf(bd("0"), bd("-5"))) {
            val suggested = TransferSuggestion.suggestToQuantity(
                data = data,
                quotes = quotes,
                fromInstrumentId = CASH_CNY,
                fromQuantity = quantity,
                fromPrice = null,
                toInstrumentId = US_EQ,
                toPrice = bd("4"),
            )
            assertNull(suggested)
        }
    }

    @Test
    fun suggestionPrefersUserInput() {
        val blank = TransferSuggestion.applyQuantitySuggestion("", null, bd("50"))
        assertEquals("50", blank.value)
        assertEquals("50", blank.lastSuggested)

        val followsSuggestion = TransferSuggestion.applyQuantitySuggestion("50", "50", bd("60"))
        assertEquals("60", followsSuggestion.value)
        assertEquals("60", followsSuggestion.lastSuggested)

        val userWins = TransferSuggestion.applyQuantitySuggestion("55", "50", bd("60"))
        assertEquals("55", userWins.value)
        assertEquals("50", userWins.lastSuggested)

        val cleared = TransferSuggestion.applyQuantitySuggestion("50", "50", null)
        assertEquals("", cleared.value)
        assertNull(cleared.lastSuggested)

        val kept = TransferSuggestion.applyQuantitySuggestion("55", "50", null)
        assertEquals("55", kept.value)
        assertNull(kept.lastSuggested)
    }

    @Test
    fun priceSuggestionUsesQuoteThenManual() {
        assertEquals("2", TransferSuggestion.suggestPrice(quotes, fund))

        val manual = fund.copy(
            quote = QuoteConfig(
                mode = QuoteMode.MANUAL,
                source = "manual",
                manualPrice = bd("3.5"),
            ),
        )
        assertEquals("3.5", TransferSuggestion.suggestPrice(QuoteSnapshot(), manual))

        assertNull(TransferSuggestion.suggestPrice(quotes, cashCny))
    }
}
