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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun priceSuggestionPrefersUserInput() {
        // A blank field takes the suggestion.
        val blank = TransferSuggestion.applyPriceSuggestion("", null, false, "2")
        assertEquals("2", blank.value)
        assertEquals("2", blank.lastSuggested)

        // A field still holding the previous suggestion follows new quotes.
        val followsQuote = TransferSuggestion.applyPriceSuggestion("2", "2", false, "3.5")
        assertEquals("3.5", followsQuote.value)
        assertEquals("3.5", followsQuote.lastSuggested)

        // A user-typed value wins and the last suggestion is remembered.
        val userWins = TransferSuggestion.applyPriceSuggestion("999", "2", false, "3.5")
        assertEquals("999", userWins.value)
        assertEquals("2", userWins.lastSuggested)

        // A field the user cleared is filled by the suggestion again.
        val cleared = TransferSuggestion.applyPriceSuggestion("", "2", false, "3.5")
        assertEquals("3.5", cleared.value)
        assertEquals("3.5", cleared.lastSuggested)

        // A different instrument always takes its own quote.
        val switched = TransferSuggestion.applyPriceSuggestion("999", "2", true, "4")
        assertEquals("4", switched.value)
        assertEquals("4", switched.lastSuggested)

        // Without a quote, a stale derived value is dropped...
        val staleDropped = TransferSuggestion.applyPriceSuggestion("2", "2", false, null)
        assertEquals("", staleDropped.value)
        assertNull(staleDropped.lastSuggested)

        // ...but user input survives.
        val userKept = TransferSuggestion.applyPriceSuggestion("999", "2", false, null)
        assertEquals("999", userKept.value)
        assertEquals("2", userKept.lastSuggested)

        // Switching to an instrument without a quote clears the field.
        val switchedNoQuote = TransferSuggestion.applyPriceSuggestion("999", "2", true, null)
        assertEquals("", switchedNoQuote.value)
        assertNull(switchedNoQuote.lastSuggested)
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

    // --- suggestRebalanceIn ---

    @Test
    fun rebalanceInFillsBothSidesToTarget() {
        // total 4000, growth 3000, cash 1000; cash target 50% -> +1000.
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
            toInstrumentId = CASH_CNY,
        )

        assertDecimal("500", suggested?.fromQuantity)
        assertDecimal("1000", suggested?.toQuantity)
        assertFalse(suggested?.cappedBySource ?: true)
    }

    @Test
    fun rebalanceInAccountsForShrinkingBaseWhenSourceIsDividend() {
        // total 4000, dividend 2000, cash 500, growth 1500; portfolio 2000.
        // cash target 50%: D = (0.5*2000 - 500) / 0.5 = 1000.
        val data = dataWithCategories(
            instruments = listOf(divInstrument, fund, cashCny),
            categories = listOf(
                category(CategoryIds.DIVIDEND, "30", dividend = true),
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, "ins_dividend") to bd("1000"),
                PositionKey(INST_A, FUND) to bd("750"),
                PositionKey(INST_A, CASH_CNY) to bd("500"),
            ),
            provider = rebalanceProvider(
                mapOf("ins_dividend" to bd("2"), FUND to bd("2")),
            ),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = "ins_dividend",
            toInstitutionId = INST_A,
            toInstrumentId = CASH_CNY,
        )

        assertDecimal("500", suggested?.fromQuantity)
        assertDecimal("1000", suggested?.toQuantity)
    }

    @Test
    fun rebalanceInToDividendUsesTotalValueAsBase() {
        // total 5000, dividend 1000, growth 3000, cash 1000.
        // dividend target 30%: D = 0.3*5000 - 1000 = 500.
        val data = dataWithCategories(
            instruments = listOf(divInstrument, fund, cashCny),
            categories = listOf(
                category(CategoryIds.DIVIDEND, "30", dividend = true),
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, "ins_dividend") to bd("500"),
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(
                mapOf("ins_dividend" to bd("2"), FUND to bd("2")),
            ),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
            toInstrumentId = "ins_dividend",
        )

        assertDecimal("250", suggested?.fromQuantity)
        assertDecimal("250", suggested?.toQuantity)
    }

    @Test
    fun rebalanceInIsCappedAtSourceHoldingValue() {
        // total 3000, growth 1000, cn bond 2000, cash 0; cash target 80%
        // needs 2400 but only 1000 is available from the growth holding.
        val cnBond = quotedInstrument("ins_cn_bond", CategoryIds.CN_BOND, "CNY", "511130")
        val data = dataWithCategories(
            instruments = listOf(fund, cnBond, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CN_BOND, "50"),
                category(CategoryIds.CASH, "80"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("500"),
                PositionKey(INST_A, "ins_cn_bond") to bd("1000"),
            ),
            provider = rebalanceProvider(
                mapOf(FUND to bd("2"), "ins_cn_bond" to bd("2")),
            ),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
            toInstrumentId = CASH_CNY,
        )

        assertDecimal("500", suggested?.fromQuantity)
        assertDecimal("1000", suggested?.toQuantity)
        assertTrue(suggested?.cappedBySource ?: false)
    }

    @Test
    fun rebalanceInReturnsNullWhenDestinationAtTarget() {
        // cash target 10% but cash already 1000/4000 = 25%.
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "10"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(mapOf(FUND to bd("2"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
            toInstrumentId = CASH_CNY,
        )

        assertNull(suggested)
    }

    @Test
    fun rebalanceInReturnsNullForSameCategory() {
        val growth2Local = quotedInstrument("ins_growth_local", CategoryIds.GROWTH, "CNY", "000009")
        val data = dataWithCategories(
            instruments = listOf(fund, growth2Local, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, "ins_growth_local") to bd("100"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(
                mapOf(FUND to bd("2"), "ins_growth_local" to bd("3")),
            ),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
            toInstrumentId = "ins_growth_local",
        )

        assertNull(suggested)
    }

    @Test
    fun rebalanceInReturnsNullWhenPricesMissing() {
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
            ),
        )
        val suggested = TransferSuggestion.suggestRebalanceIn(
            data = data,
            positions = mapOf(
                PositionKey(INST_A, FUND) to bd("1500"),
                PositionKey(INST_A, CASH_CNY) to bd("1000"),
            ),
            provider = rebalanceProvider(emptyMap()),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstitutionId = INST_A,
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

    @Test
    fun satelliteLegsGetNoRebalanceSuggestion() {
        val satellite = quotedInstrument("ins_sat", "cat_sat", "CNY", "000005")
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny, satellite),
            categories = listOf(
                category(CategoryIds.GROWTH, "40"),
                category(CategoryIds.CASH, "40"),
                category("cat_sat", "0").copy(specialRule = "satellite"),
            ),
        )
        val positions = mapOf(
            PositionKey(INST_A, FUND) to bd("1000"),
            PositionKey(INST_A, CASH_CNY) to bd("1000"),
            PositionKey(INST_A, "ins_sat") to bd("1000"),
        )
        val provider = rebalanceProvider(mapOf(FUND to bd("2"), "ins_sat" to bd("1")))

        assertNull(
            TransferSuggestion.suggestRebalanceOutQuantity(
                data = data,
                positions = positions,
                provider = provider,
                date = LocalDate.of(2026, 1, 1),
                fromInstitutionId = INST_A,
                fromInstrumentId = FUND,
                toInstrumentId = "ins_sat",
            ),
        )
        assertNull(
            TransferSuggestion.suggestRebalanceIn(
                data = data,
                positions = positions,
                provider = provider,
                date = LocalDate.of(2026, 1, 1),
                fromInstitutionId = INST_A,
                fromInstrumentId = "ins_sat",
                toInstitutionId = INST_A,
                toInstrumentId = FUND,
            ),
        )
        assertNull(
            TransferSuggestion.suggestRebalanceIn(
                data = data,
                positions = positions,
                provider = provider,
                date = LocalDate.of(2026, 1, 1),
                fromInstitutionId = INST_A,
                fromInstrumentId = FUND,
                toInstitutionId = INST_A,
                toInstrumentId = "ins_sat",
            ),
        )
    }

    @Test
    fun satelliteValueDoesNotDiluteTargetBases() {
        val satellite = quotedInstrument("ins_sat", "cat_sat", "CNY", "000005")
        val data = dataWithCategories(
            instruments = listOf(fund, cashCny, satellite),
            categories = listOf(
                category(CategoryIds.GROWTH, "50"),
                category(CategoryIds.CASH, "50"),
                category("cat_sat", "0").copy(specialRule = "satellite"),
            ),
        )
        // Strategy assets: growth 1500 + cash 500 = 2000; satellite 1000 excluded.
        // D = 1500 - 50% * 2000 = 500 -> 500 / 1500 * 750 = 250 units.
        val positions = mapOf(
            PositionKey(INST_A, FUND) to bd("750"),
            PositionKey(INST_A, CASH_CNY) to bd("500"),
            PositionKey(INST_A, "ins_sat") to bd("1000"),
        )
        val suggested = TransferSuggestion.suggestRebalanceOutQuantity(
            data = data,
            positions = positions,
            provider = rebalanceProvider(mapOf(FUND to bd("2"), "ins_sat" to bd("1"))),
            date = LocalDate.of(2026, 1, 1),
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            toInstrumentId = CASH_CNY,
        )
        assertDecimal("250", suggested)
    }
}
