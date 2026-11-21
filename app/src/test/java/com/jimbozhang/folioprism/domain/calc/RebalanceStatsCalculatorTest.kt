package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.transfer
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RebalanceStatsCalculatorTest {

    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 1, 31)
    private val fund2 = quotedInstrument("fund2", CategoryIds.GROWTH, code = "000002")

    private fun data(events: List<Event>) = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            cashInstrument(CASH_USD, "USD"),
            quotedInstrument("cashfund", CategoryIds.CASH, code = "000015"),
            quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163"),
            fund2,
        ),
        events = events,
    )

    private fun calculate(events: List<Event>, fx: Map<String, java.math.BigDecimal> = emptyMap()) =
        RebalanceStatsCalculator.calculate(data(events), fx, from, to)

    @Test
    fun cashToAssetTransferCountsAsRebalance() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, FUND),
                    fromQuantity = "100",
                    toQuantity = "10",
                    toPrice = "10",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(1, stats.count)
        assertEquals(1, stats.dayCount)
        assertDecimal("100", stats.amountCny)
        assertDecimal("0", stats.feeLossCny)
        assertEquals(1, stats.months.size)
        assertEquals(1, stats.months.first().cashToAsset)
        assertEquals(0, stats.months.first().assetToCash)
        assertEquals(0, stats.months.first().assetToAsset)
        assertEquals(1, stats.recent30DaysCount)
        assertNull(stats.averageIntervalDays)
        assertFalse(stats.incomplete)
    }

    @Test
    fun assetToAssetTransferCountsAsAssetSwap() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, FUND),
                    to = ref(INST_A, "fund2"),
                    fromQuantity = "10",
                    toQuantity = "10",
                    fromPrice = "10",
                    toPrice = "10",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(1, stats.count)
        assertDecimal("100", stats.amountCny)
        assertEquals(1, stats.months.first().assetToAsset)
        assertEquals(0, stats.months.first().cashToAsset)
    }

    @Test
    fun cashCategoryInternalTransferIsExcluded() {
        // CNY cash -> a money fund that also sits in the cash category is
        // parking cash, not rebalancing.
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, "cashfund"),
                    fromQuantity = "100",
                    toQuantity = "100",
                    toPrice = "1",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(0, stats.count)
        assertEquals(1, stats.cashCategoryCount)
        assertEquals(0, stats.accountMoveCount)
        assertEquals(0, stats.fxSwapCount)
    }

    @Test
    fun instrumentMoveAndCurrencySwapAreExcluded() {
        val stats = calculate(
            listOf(
                // Same cash instrument moved to another institution: no trade.
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_B, CASH_CNY),
                    fromQuantity = "100",
                    toQuantity = "100",
                    date = "2026-01-05T00:00:00+08:00",
                ),
                // CNY cash converted to USD cash: a currency swap, not a trade.
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, CASH_USD),
                    fromQuantity = "700",
                    toQuantity = "100",
                    date = "2026-01-06T00:00:00+08:00",
                ),
            ),
            fx = mapOf("USD" to bd("7.0")),
        )

        assertEquals(0, stats.count)
        assertEquals(1, stats.accountMoveCount)
        assertEquals(1, stats.fxSwapCount)
    }

    @Test
    fun sameDayTradesCountOnceAndIntervalUsesDistinctDays() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, FUND),
                    fromQuantity = "100",
                    toQuantity = "10",
                    toPrice = "10",
                    date = "2026-01-05T00:00:00+08:00",
                ),
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, "fund2"),
                    fromQuantity = "100",
                    toQuantity = "10",
                    toPrice = "10",
                    date = "2026-01-05T12:00:00+08:00",
                ),
                transfer(
                    from = ref(INST_A, FUND),
                    to = ref(INST_A, CASH_CNY),
                    fromQuantity = "10",
                    toQuantity = "95",
                    fromPrice = "9.5",
                    date = "2026-01-20T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(3, stats.count)
        assertEquals(2, stats.dayCount)
        assertEquals(15, stats.averageIntervalDays)
        val month = stats.months.first()
        assertEquals(2, month.cashToAsset)
        assertEquals(1, month.assetToCash)
        assertEquals(0, month.assetToAsset)
        assertEquals(3, month.count)
    }

    @Test
    fun currencySwapWithoutFxIsStillExcluded() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, CASH_USD),
                    fromQuantity = "700",
                    toQuantity = "100",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )
        assertEquals(0, stats.count)
        assertEquals(1, stats.fxSwapCount)
    }

    @Test
    fun missingFxStillCountsButMarksIncomplete() {
        val usFund = quotedInstrument("usfund", CategoryIds.US_EQUITY, currency = "USD", code = "VOO")
        val data = portfolio(
            instruments = listOf(
                cashInstrument(CASH_CNY, "CNY"),
                usFund,
            ),
            events = listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, "usfund"),
                    fromQuantity = "700",
                    toQuantity = "100",
                    toPrice = "1",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )
        val stats = RebalanceStatsCalculator.calculate(data, emptyMap(), from, to)

        assertEquals(1, stats.count)
        assertTrue(stats.incomplete)
        assertDecimal("700", stats.amountCny)
        val usEquity = stats.categoryFlows.first { it.categoryId == CategoryIds.US_EQUITY }
        assertDecimal("0", usEquity.inCny)
        assertEquals(1, usEquity.inCount)
    }

    @Test
    fun feesAndLossesAreAggregated() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, FUND),
                    fromQuantity = "100",
                    toQuantity = "10",
                    toPrice = "10",
                    loss = "5",
                    date = "2026-01-05T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(1, stats.count)
        assertDecimal("5", stats.feeLossCny)
    }

    @Test
    fun topPairAndCategoryFlowsAggregate() {
        val stats = calculate(
            listOf(
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, FUND),
                    fromQuantity = "100",
                    toQuantity = "10",
                    toPrice = "10",
                    date = "2026-01-05T00:00:00+08:00",
                ),
                transfer(
                    from = ref(INST_A, CASH_CNY),
                    to = ref(INST_A, "fund2"),
                    fromQuantity = "200",
                    toQuantity = "20",
                    toPrice = "10",
                    date = "2026-01-06T00:00:00+08:00",
                ),
            ),
        )

        assertEquals(1, stats.topPairs.size)
        val pair = stats.topPairs.first()
        assertEquals("现金", pair.fromName)
        assertEquals("成长", pair.toName)
        assertEquals(2, pair.count)
        assertDecimal("300", pair.amountCny)

        val cash = stats.categoryFlows.first { it.categoryId == CategoryIds.CASH }
        assertDecimal("300", cash.outCny)
        assertEquals(2, cash.outCount)
        assertEquals(0, cash.inCount)
        val growth = stats.categoryFlows.first { it.categoryId == CategoryIds.GROWTH }
        assertDecimal("300", growth.inCny)
        assertEquals(2, growth.inCount)
        assertTrue(growth.outCount == 0)
    }
}
