package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.DATE1
import com.jimbozhang.folioprism.domain.DATE2
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.transfer
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.money.bd
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EventReplayTest {

    private fun data(events: List<Event>) = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
        ),
        events = events,
    )

    @Test
    fun buyTransferMovesCashIntoFund() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, CASH_CNY), amount = "1000"),
                    transfer(
                        from = ref(INST_A, CASH_CNY),
                        to = ref(INST_A, FUND),
                        fromQuantity = "1000",
                        toQuantity = "100",
                        toPrice = "10",
                    ),
                ),
            ).events,
        )

        assertDecimal("0", positions[PositionKey(INST_A, CASH_CNY)])
        assertDecimal("100", positions[PositionKey(INST_A, FUND)])
    }

    @Test
    fun sellTransferMovesFundIntoCash() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, FUND), quantity = "100"),
                    transfer(
                        from = ref(INST_A, FUND),
                        to = ref(INST_A, CASH_CNY),
                        fromQuantity = "40",
                        toQuantity = "400",
                        fromPrice = "10",
                    ),
                ),
            ).events,
        )

        assertDecimal("60", positions[PositionKey(INST_A, FUND)])
        assertDecimal("400", positions[PositionKey(INST_A, CASH_CNY)])
    }

    @Test
    fun dividendIncreasesCashButNotShares() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, FUND), quantity = "100"),
                    IncomeEvent(
                        kind = IncomeKind.DIVIDEND,
                        source = ref(INST_A, FUND),
                        destination = ref(INST_A, CASH_CNY),
                        amount = bd("50"),
                        currency = "CNY",
                        amountCny = bd("50"),
                        date = DATE1,
                    ),
                ),
            ).events,
        )

        assertDecimal("50", positions[PositionKey(INST_A, CASH_CNY)])
        assertDecimal("100", positions[PositionKey(INST_A, FUND)])
    }

    @Test
    fun cashExpenseReducesCash() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, CASH_CNY), amount = "100"),
                    ExpenseEvent(
                        amount = bd("10"),
                        currency = "CNY",
                        amountCny = bd("10"),
                        categoryId = CategoryIds.CASH,
                        position = ref(INST_A, CASH_CNY),
                        date = DATE1,
                    ),
                ),
            ).events,
        )

        assertDecimal("90", positions[PositionKey(INST_A, CASH_CNY)])
    }

    @Test
    fun nonCashExpenseDoesNotChangePositions() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, CASH_CNY), amount = "100"),
                    ExpenseEvent(
                        amount = bd("10"),
                        currency = "CNY",
                        amountCny = bd("10"),
                        categoryId = CategoryIds.GROWTH,
                        position = null,
                        date = DATE1,
                    ),
                ),
            ).events,
        )

        assertDecimal("100", positions[PositionKey(INST_A, CASH_CNY)])
    }

    @Test
    fun externalFlowsChangeCash() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, CASH_CNY), amount = "100"),
                    ExternalEvent(
                        direction = ExternalDirection.IN,
                        classification = ExternalClassification.CAPITAL,
                        position = ref(INST_A, CASH_CNY),
                        amount = bd("20"),
                        currency = "CNY",
                        amountCny = bd("20"),
                        date = DATE1,
                    ),
                    ExternalEvent(
                        direction = ExternalDirection.OUT,
                        classification = ExternalClassification.CONSUMPTION,
                        position = ref(INST_A, CASH_CNY),
                        amount = bd("30"),
                        currency = "CNY",
                        amountCny = bd("30"),
                        date = DATE1,
                    ),
                ),
            ).events,
        )

        assertDecimal("90", positions[PositionKey(INST_A, CASH_CNY)])
    }

    @Test
    fun adjustmentsCanBeNegativeOrPositive() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, CASH_CNY), amount = "100"),
                    opening(ref(INST_A, FUND), quantity = "10"),
                    AdjustmentEvent(
                        position = ref(INST_A, CASH_CNY),
                        reason = "reconcile",
                        deltaAmount = bd("-20"),
                        date = DATE1,
                    ),
                    AdjustmentEvent(
                        position = ref(INST_A, FUND),
                        reason = "reconcile",
                        deltaQuantity = bd("5"),
                        date = DATE1,
                    ),
                ),
            ).events,
        )

        assertDecimal("80", positions[PositionKey(INST_A, CASH_CNY)])
        assertDecimal("15", positions[PositionKey(INST_A, FUND)])
    }

    @Test
    fun negativePositionsAreAllowed() {
        val positions = EventReplay.replay(
            data(
                listOf(
                    opening(ref(INST_A, FUND), quantity = "10"),
                    transfer(
                        from = ref(INST_A, FUND),
                        to = ref(INST_A, CASH_CNY),
                        fromQuantity = "20",
                        toQuantity = "200",
                        fromPrice = "10",
                    ),
                ),
            ).events,
        )

        assertDecimal("-10", positions[PositionKey(INST_A, FUND)])
        assertDecimal("200", positions[PositionKey(INST_A, CASH_CNY)])
    }

    @Test
    fun replayCanStopAtDate() {
        val events = listOf(
            opening(ref(INST_A, CASH_CNY), amount = "100", date = DATE1),
            opening(ref(INST_A, CASH_CNY), amount = "50", date = DATE2),
        )

        val january = EventReplay.replay(events, upTo = LocalDate.of(2026, 1, 31))
        val february = EventReplay.replay(events, upTo = LocalDate.of(2026, 2, 1))

        assertDecimal("100", january[PositionKey(INST_A, CASH_CNY)])
        assertDecimal("150", february[PositionKey(INST_A, CASH_CNY)])
        assertEquals(1, january.size)
    }
}
