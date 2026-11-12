package com.jimbozhang.folioprism.domain.history

import com.jimbozhang.folioprism.domain.CASH_CNY
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
        val result = HistoryCalculator.build(data(baseEvents()), cache(), from, to)

        assertDecimal("1100", result.curve.first().totalCny)
        assertDecimal("1120", result.curve.last().totalCny)
    }

    @Test
    fun categoryPnlReflectsMarketGain() {
        val result = HistoryCalculator.build(data(baseEvents()), cache(), from, to)
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
        val result = HistoryCalculator.build(data(events), cache(), from, to)
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
            amountCny = bd("500"),
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), from, to)
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("0", cash.pnlCny)
        assertDecimal("1620", result.curve.last().totalCny)
    }

    @Test
    fun cashExpenseReducesPnl() {
        val events = baseEvents() + ExpenseEvent(
            amount = bd("10"),
            currency = "CNY",
            amountCny = bd("10"),
            categoryId = CategoryIds.CASH,
            position = ref(INST_A, CASH_CNY),
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), from, to)
        val cash = result.categoryPnl.first { it.categoryId == CategoryIds.CASH }

        assertDecimal("-10", cash.pnlCny)
    }

    @Test
    fun transferLossIsDeductedFromCategory() {
        val buyTransfer = transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "100",
            toQuantity = "9",
            toPrice = "10",
            date = "2026-01-10T00:00:00+08:00",
        )
        val events = baseEvents() + buyTransfer + ExpenseEvent(
            amount = bd("10"),
            currency = "CNY",
            amountCny = bd("10"),
            categoryId = CategoryIds.GROWTH,
            position = null,
            linkedTransferId = buyTransfer.id,
            date = "2026-01-10T00:00:00+08:00",
        )
        val result = HistoryCalculator.build(data(events), cache(), from, to)
        val growth = result.categoryPnl.first { it.categoryId == CategoryIds.GROWTH }

        assertDecimal("28", growth.pnlCny)
    }

    @Test
    fun missingEndPriceMarksIncomplete() {
        val result = HistoryCalculator.build(
            data(baseEvents()),
            cache(includeEnd = false),
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
