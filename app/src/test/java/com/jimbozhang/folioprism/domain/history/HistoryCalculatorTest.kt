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
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.calc.HistoryCalculator
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
