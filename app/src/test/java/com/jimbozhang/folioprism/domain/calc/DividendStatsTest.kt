package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GROWTH
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.money.bd
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DividendStatsTest {

    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 1, 31)
    private val fx = mapOf("USD" to bd("7"))

    private fun data(events: List<Event>): PortfolioData = portfolio(
        institutions = listOf(
            Institution(INST_A, "A"),
            Institution(INST_B, "B"),
        ),
        instruments = listOf(
            quotedInstrument(FUND, CategoryIds.DIVIDEND),
            quotedInstrument(GROWTH, CategoryIds.GROWTH),
            cashInstrument(CASH_CNY, "CNY"),
            cashInstrument(CASH_USD, "USD"),
        ),
        events = events,
    )

    private fun dividend(
        amount: String,
        date: String = "2026-01-05T00:00:00+08:00",
        currency: String = "CNY",
        source: PositionRef? = ref(INST_A, FUND),
        destination: PositionRef = ref(INST_A, CASH_CNY),
        withdrawn: Boolean = false,
        linkedEventId: String? = null,
    ): IncomeEvent = IncomeEvent(
        kind = IncomeKind.DIVIDEND,
        destination = destination,
        amount = bd(amount),
        currency = currency,
        date = date,
        source = source,
        immediateExternalWithdrawal = withdrawn,
        linkedEventId = linkedEventId,
    )

    private fun interest(
        amount: String,
        date: String = "2026-01-10T00:00:00+08:00",
    ): IncomeEvent = IncomeEvent(
        kind = IncomeKind.INTEREST,
        destination = ref(INST_A, CASH_CNY),
        amount = bd(amount),
        currency = "CNY",
        date = date,
        source = null,
    )

    @Test
    fun keepsOnlyDividendsInsideTheInclusiveRange() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100", date = "2026-01-01T00:00:00+08:00"),
                    dividend("50", date = "2026-01-31T23:00:00+08:00"),
                    dividend("999", date = "2026-02-01T00:00:00+08:00"),
                    interest("30"),
                ),
            ),
            fx,
            from,
            to,
        )

        assertDecimal("150", result.totalCny)
        assertEquals(2, result.count)
        assertFalse(result.incomplete)
    }

    @Test
    fun convertsForeignDividendsWithCurrentFx() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100"),
                    dividend("10", currency = "USD", destination = ref(INST_A, CASH_USD)),
                ),
            ),
            fx,
            from,
            to,
        )

        assertDecimal("170", result.totalCny)
        assertEquals(2, result.count)
        assertFalse(result.incomplete)
    }

    @Test
    fun missingFxSkipsEventAndMarksIncomplete() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100"),
                    dividend("10", currency = "GBP"),
                ),
            ),
            fx,
            from,
            to,
        )

        assertDecimal("100", result.totalCny)
        assertEquals(1, result.count)
        assertTrue(result.incomplete)
    }

    @Test
    fun groupsBySourceCategoryAndInstrument() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100"),
                    dividend("50"),
                    dividend("30", source = ref(INST_A, GROWTH)),
                    dividend("20", source = null),
                ),
            ),
            fx,
            from,
            to,
        )

        val dividendCategory = result.bySourceCategory.first { it.name == "红利" }
        assertDecimal("150", dividendCategory.amountCny)
        assertEquals(2, dividendCategory.count)

        val growthCategory = result.bySourceCategory.first { it.name == "成长" }
        assertDecimal("30", growthCategory.amountCny)

        val noSource = result.bySourceCategory.first { it.name == "无来源" }
        assertDecimal("20", noSource.amountCny)

        assertEquals("红利", result.bySourceCategory.first().name)
        assertDecimal("150", result.bySourceInstrument.first { it.name == FUND }.amountCny)
    }

    @Test
    fun splitsWithdrawnAndRetainedDividends() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100"),
                    dividend("50", withdrawn = true),
                    dividend("25", linkedEventId = "evt_out"),
                ),
            ),
            fx,
            from,
            to,
        )

        assertDecimal("100", result.retainedCny)
        assertEquals(1, result.retainedCount)
        assertDecimal("75", result.withdrawnCny)
        assertEquals(2, result.withdrawnCount)
    }

    @Test
    fun groupsByDestinationAccountAndMonthNewestFirst() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100", date = "2026-01-05T00:00:00+08:00"),
                    dividend(
                        "50",
                        date = "2026-02-05T00:00:00+08:00",
                        destination = ref(INST_B, CASH_CNY),
                    ),
                ),
            ),
            fx,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28),
        )

        assertEquals(2, result.monthly.size)
        assertEquals(YearMonth.of(2026, 2), result.monthly.first().month)
        assertDecimal("50", result.monthly.first().amountCny)
        assertDecimal("75", result.averagePerMonthCny)

        assertDecimal(
            "50",
            result.byDestinationAccount.first { it.name == "B · CNY cash" }.amountCny,
        )
        assertDecimal(
            "100",
            result.byDestinationAccount.first { it.name == "A · CNY cash" }.amountCny,
        )
    }

    @Test
    fun reportsLargestAndLatestDividends() {
        val result = DividendStatsCalculator.calculate(
            data(
                listOf(
                    dividend("100", date = "2026-01-05T00:00:00+08:00"),
                    dividend("300", date = "2026-01-20T00:00:00+08:00"),
                    dividend("50", date = "2026-01-25T00:00:00+08:00"),
                ),
            ),
            fx,
            from,
            to,
        )

        assertDecimal("300", result.largest?.amountCny)
        assertEquals(LocalDate.of(2026, 1, 25), result.latest?.date)
    }

    @Test
    fun emptyRangeReturnsZero() {
        val result = DividendStatsCalculator.calculate(
            data(listOf(dividend("100", date = "2026-02-05T00:00:00+08:00"))),
            fx,
            from,
            to,
        )

        assertDecimal("0", result.totalCny)
        assertEquals(0, result.count)
        assertTrue(result.monthly.isEmpty())
        assertFalse(result.incomplete)
    }
}
