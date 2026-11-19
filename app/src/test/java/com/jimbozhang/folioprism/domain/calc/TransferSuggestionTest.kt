package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.Settings
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.quote.Quote
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.time.LocalDate
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

    // --- suggestRebalanceOutQuantity ---

    private val divInstrument = quotedInstrument("ins_dividend", CategoryIds.DIVIDEND, "CNY", "000003")
    private val growth2 = quotedInstrument("ins_growth2", CategoryIds.GROWTH, "CNY", "000004")

    private fun category(id: String, targetPct: String, dividend: Boolean = false) =
        Category(
            id = id,
            name = id,
            order = 0,
            color = "#000000",
            targetPct = bd(targetPct),
            minPct = bd("0"),
            maxPct = bd("100"),
            specialRule = if (dividend) "dividend" else null,
        )

    private fun dataWithCategories(instruments: List<Instrument>, categories: List<Category>) =
        portfolio(instruments = instruments).copy(settings = Settings(categories = categories))

    private fun rebalanceProvider(prices: Map<String, BigDecimal>) =
        LivePriceProvider(
            QuoteSnapshot(
                quotes = prices.mapValues { (id, price) ->
                    Quote(id, price, "CNY", id, "", "test")
                },
            ),
        )

    @Test
    fun rebalanceOutMovesSourceToTargetWhenDestIsNonDividend() {
        // total 4000, growth 3000, target 50% -> D = 3000 - 0.5 * 4000 = 1000
        // quantity = 1000 / 3000 * 1500 = 500
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertDecimal("500", suggested)
    }

    @Test
    fun rebalanceOutAccountsForShrinkingPortfolioBaseWhenDestIsDividend() {
        // total 3000, no dividend value -> portfolio base 3000, growth 2000, target 30%
        // D = (2000 - 0.3 * 3000) / (1 - 0.3) = 1100 / 0.7 = 1571.4285...
        // quantity = 1571.4285... / 2000 * 1000 = 785.71
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny, divInstrument),
            categories = listOf(
                category(CategoryIds.GROWTH, "30"),
                category(CategoryIds.CASH, "70"),
                category(CategoryIds.DIVIDEND, "32", dividend = true),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1000"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = "ins_dividend",
        )
        assertDecimal("785.71", suggested)
    }

    @Test
    fun rebalanceOutDividendSourceUsesTotalValueAsBase() {
        // total 5000 (dividend 2000 + growth 3000), dividend target 30%
        // D = 2000 - 0.3 * 5000 = 500 -> quantity = 500 / 2000 * 500 = 125
        val data = dataWithCategories(
            instruments = listOf(fund, divInstrument),
            categories = listOf(
                category(CategoryIds.DIVIDEND, "30", dividend = true),
                category(CategoryIds.GROWTH, "100"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, "ins_dividend") to bd("500"),
                PositionKey(INST_A, FUND) to bd("1500"),
            ),
            provider = rebalanceProvider(mapOf("ins_dividend" to bd("4"), FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = "ins_dividend",
            toInstrumentId = FUND,
        )
        assertDecimal("125", suggested)
    }

    @Test
    fun rebalanceOutIsZeroWhenSourceAtOrBelowTarget() {
        // total 2500, growth 1000, target 50% -> D = 1000 - 1250 < 0 -> fill 0
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("500"),
                PositionKey(INST_A, CASH_CNY) to bd("1500"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertDecimal("0", suggested)
    }

    @Test
    fun rebalanceOutIsCappedAtHoldingValue() {
        // growth category = 3000 (fund) + 1000 (growth2), total 5000, target 10%
        // D = 4000 - 500 = 3500 > fund value 3000 -> capped, quantity = all 1500
        val data = dataWithCategories(
            instruments = listOf(fund, growth2, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "10"),
                category(CategoryIds.CASH, "90"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, "ins_growth2") to bd("100"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"), "ins_growth2" to bd("10"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertDecimal("1500", suggested)
    }

    @Test
    fun rebalanceOutReturnsNullWhenSourcePriceMissing() {
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(emptyMap()),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertNull(suggested)
    }

    @Test
    fun rebalanceOutReturnsNullForMissingInputs() {
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val positions = mapOf(
            PositionKey(INST_A, FUND) to bd("1500"),
            PositionKey(INST_A, CASH_CNY) to bd("1000"),
        )
        val provider = rebalanceProvider(mapOf(FUND to bd("2")))

        val noInstitution = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = positions,
            provider = provider,
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = null,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertNull(noInstitution)

        val unknownInstrument = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = positions,
            provider = provider,
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = "ins_unknown",
            toInstrumentId = CASH_CNY,
        )
        assertNull(unknownInstrument)

        val noFromInstrument = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = positions,
            provider = provider,
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = null,
            toInstrumentId = CASH_CNY,
        )
        assertNull(noFromInstrument)
    }
}
