package com.jimbozhang.folioprism.domain.history

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.transfer
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.calc.HistoryCalculator
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCalculatorTest {

    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 1, 31)

    private fun data(events: List<Event>): PortfolioData = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163"),
        ),
        events = events,
    )

    private fun cache(includeEnd: Boolean = true): PriceCacheData =
        PriceCacheData(
            quotes = mapOf(
                FUND to CachedSeries(
                    auto = buildMap {
                        put("2026-01-01", "10")
                        if (includeEnd) put("2026-01-31", "12")
                    },
                ),
            ),
        )

    private fun baseEvents(): List<Event> = listOf(
        opening(ref(INST_A, CASH_CNY), amount = "1000", date = "2026-01-01T00:00:00+08:00"),
        opening(ref(INST_A, FUND), quantity = "10", date = "2026-01-01T00:00:00+08:00"),
    )

    @Test
    fun curveUsesNearestPreviousPrice() {
        val result = HistoryCalculator.build(data(baseEvents()), cache(), emptyMap(), from, to)

        assertDecimal("1100", result.curve.first().totalCny)
        assertDecimal("1120", result.curve.last().totalCny)
    }

    @Test
    fun categoryPnlReflectsMarketGain() {
        val result = HistoryCalculator.build(data(baseEvents()), cache(), emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("20", growth.pnlCny)
        assertDecimal("0", cash.pnlCny)
    }

    @Test
    fun buyTransferIsCapitalFlowNotPnl() {
        val events = baseEvents() + transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "100",
            toQuantity = "10",
            toPrice = "10",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("40", growth.pnlCny)
        assertDecimal("0", cash.pnlCny)
    }

    @Test
    fun externalDepositIsCapitalFlow() {
        val events = baseEvents() + ExternalEvent(
            direction = ExternalDirection.IN,
            classification = ExternalClassification.CAPITAL,
            position = ref(INST_A, CASH_CNY),
            amount = bd("500"),
            currency = "CNY",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("0", cash.pnlCny)
        assertDecimal("1620", result.curve.last().totalCny)
    }

    @Test
    fun externalDepositShowsAsDailyFlowButNotInAdjustedCurve() {
        val events = baseEvents() + ExternalEvent(
            direction = ExternalDirection.IN,
            classification = ExternalClassification.CAPITAL,
            position = ref(INST_A, CASH_CNY),
            amount = bd("500"),
            currency = "CNY",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)

        val depositDay = result.dailyFlows.first { it.date == LocalDate.of(2026, 1, 10) }
        assertDecimal("500", depositDay.netTotal)
        assertDecimal("500", depositDay.externalByCategory[CategoryIds.CASH])

        // The actual curve jumps by the deposit, the adjusted curve does not:
        // it only carries the fund's 10 -> 12 price move (1100 -> 1113.75).
        assertDecimal("1620", result.curve.last().totalCny)
        assertDecimal("1100", result.adjustedCurve[9].totalCny)
        assertDecimal("1113.75", result.adjustedCurve.last().totalCny)
    }

    @Test
    fun transferFlowIsInternalAndLeavesCurvesUnmoved() {
        val events = baseEvents() + transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "100",
            toQuantity = "10",
            toPrice = "10",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)

        val day = result.dailyFlows.first { it.date == LocalDate.of(2026, 1, 10) }
        assertDecimal("0", day.netTotal)
        assertDecimal("-100", day.internalByCategory[CategoryIds.CASH])
        assertDecimal("100", day.internalByCategory[CategoryIds.GROWTH])

        // A reallocation day leaves both adjusted category curves flat, and
        // the adjusted total only grows with the fund's price.
        val dayPoint = result.adjustedCurve[9]
        assertDecimal("1000", dayPoint.categoryValues[CategoryIds.CASH])
        assertDecimal("100", dayPoint.categoryValues[CategoryIds.GROWTH])
        assertDecimal("1140", result.adjustedCurve.last().totalCny)
    }

    @Test
    fun openingInsideRangeIsCapitalFlowNotPnl() {
        val events = baseEvents() + opening(
            ref(INST_A, FUND),
            quantity = "5",
            price = "12",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }

        // 15 shares × 12 at the end vs 10 shares × 10 at the start, minus
        // the 5 × 12 opening inflow: only the market gain remains.
        assertDecimal("20", growth.pnlCny)
    }

    @Test
    fun cashOpeningInsideRangeIsCapitalFlowNotPnl() {
        val events = baseEvents() + opening(
            ref(INST_A, CASH_CNY),
            amount = "500",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("0", cash.pnlCny)
        assertDecimal("1620", result.curve.last().totalCny)
    }

    @Test
    fun cashExpenseReducesPnl() {
        val events = baseEvents() + ExpenseEvent(
            amount = bd("10"),
            currency = "CNY",
            categoryId = CategoryIds.CASH,
            position = ref(INST_A, CASH_CNY),
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("-10", cash.pnlCny)
    }

    @Test
    fun transferEmbeddedLossIsDeductedFromCategory() {
        val buyTransfer = transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "100",
            toQuantity = "9",
            toPrice = "10",
            date = "2026-01-10T00:00:00+08:00",
        ).copy(lossAmount = bd("10"))
        val events = baseEvents() + buyTransfer
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }

        assertDecimal("28", growth.pnlCny)
    }

    @Test
    fun sameCategoryTransferLossIsNotDoubleCounted() {
        val fundA = quotedInstrument("ins_fund_a", CategoryIds.GROWTH, code = "008163")
        val fundB = quotedInstrument("ins_fund_b", CategoryIds.GROWTH, code = "007028")
        val data = portfolio(
            instruments = listOf(fundA, fundB),
            events = listOf(
                opening(
                    ref(INST_A, "ins_fund_a"),
                    quantity = "10",
                    price = "10",
                    date = "2026-01-01T00:00:00+08:00",
                ),
                transfer(
                    from = ref(INST_A, "ins_fund_a"),
                    to = ref(INST_A, "ins_fund_b"),
                    fromQuantity = "10",
                    toQuantity = "9",
                    fromPrice = "10",
                    toPrice = "10",
                    loss = "10",
                    date = "2026-01-10T00:00:00+08:00",
                ),
            ),
        )
        val cache = PriceCacheData(
            quotes = mapOf(
                "ins_fund_a" to CachedSeries(
                    auto = buildMap {
                        put("2026-01-01", "10")
                        put("2026-01-31", "10")
                    },
                ),
                "ins_fund_b" to CachedSeries(
                    auto = buildMap {
                        put("2026-01-01", "10")
                        put("2026-01-31", "10")
                    },
                ),
            ),
        )
        val result = HistoryCalculator.build(data, cache, emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }

        // The 10 fee shows up once, as the category's value drop (100 -> 90);
        // it must not be subtracted a second time from the category P&L.
        assertDecimal("-10", growth.pnlCny)

        // The instruments of the category sum to the category P&L: fund_a
        // breaks even (fully redeemed) and fund_b carries the -10 loss.
        val sum = result.instrumentPnl
            .filter { it.categoryId == CategoryIds.GROWTH }
            .map { it.pnlCny ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        assertDecimal("-10", sum)
    }

    @Test
    fun crossCategoryTransferLossLandsOnDestinationInstrument() {
        val buyTransfer = transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "100",
            toQuantity = "9",
            toPrice = "10",
            date = "2026-01-10T00:00:00+08:00",
        ).copy(lossAmount = bd("10"))
        val events = baseEvents() + buyTransfer
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }
        val fund = result.instrumentPnl.first { it.instrumentId == FUND }

        // The embedded fee is attributed to the destination instrument, so it
        // matches the category P&L exactly.
        assertDecimal("28", growth.pnlCny)
        assertDecimal("28", fund.pnlCny)
    }

    @Test
    fun incomeCreditsSourceInstrument() {
        val events = baseEvents() + IncomeEvent(
            kind = IncomeKind.DIVIDEND,
            source = ref(INST_A, FUND),
            destination = ref(INST_A, CASH_CNY),
            amount = bd("100"),
            currency = "CNY",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val fund = result.instrumentPnl.first { it.instrumentId == FUND }
        val cash = result.instrumentPnl.first { it.instrumentId == CASH_CNY }
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }
        val cashCategory = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        // The 100 dividend is income generated by the fund: it is a gain on the
        // fund (20 market gain + 100 dividend) and a zero-P&L receipt on the
        // cash. Both match the category-level P&L.
        assertDecimal("120", fund.pnlCny)
        assertDecimal("0", cash.pnlCny)
        assertDecimal("120", growth.pnlCny)
        assertDecimal("0", cashCategory.pnlCny)
    }

    @Test
    fun interestWithoutSourceCreditsDestinationInstrument() {
        val events = baseEvents() + IncomeEvent(
            kind = IncomeKind.INTEREST,
            destination = ref(INST_A, CASH_CNY),
            amount = bd("3.1"),
            currency = "CNY",
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), emptyMap(), from, to)
        val cash = result.instrumentPnl.first { it.instrumentId == CASH_CNY }
        val cashCategory = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        // Interest credited to cash with no source instrument is a P&L gain on
        // the cash, matching the category level which records no flow for it.
        assertDecimal("3.1", cash.pnlCny)
        assertDecimal("3.1", cashCategory.pnlCny)
    }

    @Test
    fun foreignCashUsesCurrentFxForEveryDate() {
        val data = portfolio(
            instruments = listOf(cashInstrument(CASH_USD, "USD")),
            events = listOf(
                opening(
                    ref(INST_A, CASH_USD),
                    amount = "100",
                    date = "2026-01-01T00:00:00+08:00",
                ),
            ),
        )
        val result = HistoryCalculator.build(
            data,
            PriceCacheData(),
            mapOf("USD" to bd("7")),
            from,
            to,
        )

        assertDecimal("700", result.curve.first().totalCny)
        assertDecimal("700", result.curve.last().totalCny)
    }

    @Test
    fun missingCurrentFxMarksIncomplete() {
        val data = portfolio(
            instruments = listOf(cashInstrument(CASH_USD, "USD")),
            events = listOf(opening(ref(INST_A, CASH_USD), amount = "100")),
        )
        val result = HistoryCalculator.build(data, PriceCacheData(), emptyMap(), from, to)

        assertTrue(result.curve.last().incomplete)
        assertNull(result.curve.last().totalCny)
    }

    @Test
    fun manualPriceInterpolatesBetweenKnownPoints() {
        val manualFund = quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163")
            .copy(
                quote = QuoteConfig(
                    mode = QuoteMode.MANUAL,
                    source = "manual",
                    manualPrice = bd("120"),
                ),
            )
        val data = portfolio(
            instruments = listOf(manualFund),
            events = listOf(
                opening(
                    ref(INST_A, FUND),
                    quantity = "1",
                    price = "100",
                    date = "2026-01-01T00:00:00+08:00",
                ),
            ),
        )
        val end = LocalDate.of(2026, 1, 11)
        val result = HistoryCalculator.build(
            data,
            PriceCacheData(),
            emptyMap(),
            LocalDate.of(2026, 1, 1),
            end,
        )

        assertDecimal("100", result.curve.first().totalCny)
        assertDecimal("110", result.curve[5].totalCny)
        assertDecimal("120", result.curve.last().totalCny)
    }

    @Test
    fun livePriceSetsCurveEndWhenRangeEndsToday() {
        val today = LocalDate.now()
        val cache = PriceCacheData(
            quotes = mapOf(
                FUND to CachedSeries(
                    auto = mapOf(
                        today.minusDays(30).toString() to "10",
                        today.minusDays(1).toString() to "12",
                    ),
                ),
            ),
        )
        val result = HistoryCalculator.build(
            data(baseEvents()),
            cache,
            emptyMap(),
            today.minusDays(2),
            today,
            currentPrices = mapOf(FUND to bd("13")),
        )

        assertDecimal("1130", result.curve.last().totalCny)
        // Earlier points keep their historical values.
        assertDecimal("1100", result.curve.first().totalCny)
    }

    @Test
    fun livePriceIgnoredWhenRangeEndsBeforeToday() {
        val end = LocalDate.now().minusDays(1)
        val cache = PriceCacheData(
            quotes = mapOf(
                FUND to CachedSeries(
                    auto = mapOf(
                        end.minusDays(1).toString() to "11",
                        end.toString() to "12",
                    ),
                ),
            ),
        )
        val result = HistoryCalculator.build(
            data(baseEvents()),
            cache,
            emptyMap(),
            end.minusDays(1),
            end,
            currentPrices = mapOf(FUND to bd("13")),
        )

        assertDecimal("1120", result.curve.last().totalCny)
    }

    @Test
    fun missingEndPriceMarksIncomplete() {
        val result = HistoryCalculator.build(
            data(baseEvents()),
            cache(includeEnd = false),
            emptyMap(),
            from,
            LocalDate.of(2026, 3, 31),
        )
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }

        assertTrue(result.curve.last().incomplete)
        assertNull(result.curve.last().totalCny)
        assertNull(growth.pnlCny)
        assertTrue(growth.incomplete)
    }
}
