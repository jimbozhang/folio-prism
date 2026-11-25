package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.DATE1
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.quote.Quote
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEventDraftTest {

    private val fund = quotedInstrument(FUND, CategoryIds.GROWTH, "CNY", "000001")
    private val cash = cashInstrument(CASH_CNY, "CNY")
    private val usdCash = cashInstrument(CASH_USD, "USD")
    private val data = portfolio(
        institutions = listOf(Institution(INST_A, "A"), Institution(INST_B, "B")),
        instruments = listOf(fund, cash, usdCash),
    )
    private val quotes = QuoteSnapshot(
        quotes = mapOf(FUND to Quote(FUND, bd("2"), "CNY", fund.name, "", "test")),
        fxRates = mapOf("USD" to bd("7.2")),
    )

    private fun build(draft: WebEventDraft, initial: com.jimbozhang.folioprism.domain.model.Event? = null) =
        WebEventBuilder.build(data, quotes, draft, initial)

    private fun failure(draft: WebEventDraft): String {
        val result = build(draft)
        assertTrue("expected failure for $draft", result.isFailure)
        return result.exceptionOrNull()!!.message.orEmpty()
    }

    @Test
    fun invalidDateIsRejected() {
        assertEquals("日期格式无效", failure(WebEventDraft(type = "opening", date = "not-a-date")))
    }

    @Test
    fun transferRequiresInstitutionsAndDistinctPositions() {
        assertEquals(
            "请选择转出机构",
            failure(WebEventDraft(type = "transfer", date = DATE1)),
        )
        val samePosition = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            fromQuantity = "1",
            fromPrice = "2",
            toInstitutionId = INST_A,
            toInstrumentId = FUND,
            toQuantity = "1",
            toPrice = "2",
        )
        assertEquals("转出与转入不能是同一持仓", failure(samePosition))
    }

    @Test
    fun transferRequiresPriceForQuotedInstrument() {
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = CASH_CNY,
            fromQuantity = "100",
            toInstitutionId = INST_A,
            toInstrumentId = FUND,
            toQuantity = "50",
        )
        assertEquals("请输入有效的000001 单价", failure(draft))
    }

    @Test
    fun transferBuildsAndComputesLoss() {
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = CASH_CNY,
            fromQuantity = "100",
            toInstitutionId = INST_B,
            toInstrumentId = FUND,
            toQuantity = "50",
            toPrice = "2",
        )
        val transfer = build(draft).getOrThrow().single() as TransferEvent
        assertEquals(PositionRef(INST_B, FUND), transfer.to)
        assertDecimal("0", transfer.lossAmount)
        assertNull(transfer.feePosition)
    }

    @Test
    fun transferAutoLossRoundsToTheFen() {
        // 33768 * 1.4082 - 54326.62 * 0.8753 = 0.007114: a derived money
        // amount is stored rounded to the fen, never with sub-cent digits.
        val fund2 = quotedInstrument("fund2", CategoryIds.GROWTH, "CNY", "000002")
        val data2 = portfolio(
            institutions = listOf(Institution(INST_A, "A"), Institution(INST_B, "B")),
            instruments = listOf(fund, fund2),
        )
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = FUND,
            fromQuantity = "33768",
            fromPrice = "1.4082",
            toInstitutionId = INST_B,
            toInstrumentId = "fund2",
            toQuantity = "54326.62",
            toPrice = "0.8753",
        )
        val transfer = WebEventBuilder.build(data2, quotes, draft, null)
            .getOrThrow().single() as TransferEvent
        assertDecimal("0.01", transfer.lossAmount)
    }

    @Test
    fun transferFeeFromCashAccount() {
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = CASH_CNY,
            fromQuantity = "100",
            toInstitutionId = INST_B,
            toInstrumentId = FUND,
            toQuantity = "50",
            toPrice = "2",
            feeLoss = "10",
            feeFromCash = true,
            feeInstitutionId = INST_B,
            feeInstrumentId = CASH_CNY,
        )
        val transfer = build(draft).getOrThrow().single() as TransferEvent
        assertEquals(PositionRef(INST_B, CASH_CNY), transfer.feePosition)
        assertDecimal("10", transfer.feeAmount)
        assertEquals("CNY", transfer.feeCurrency)
        assertDecimal("0", transfer.lossAmount)
    }

    @Test
    fun transferFeeAccountMustBeCash() {
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = CASH_CNY,
            fromQuantity = "100",
            toInstitutionId = INST_B,
            toInstrumentId = FUND,
            toQuantity = "50",
            toPrice = "2",
            feeFromCash = true,
            feeInstitutionId = INST_B,
            feeInstrumentId = FUND,
        )
        assertEquals("手续费账户必须是现金标的", failure(draft))
    }

    @Test
    fun transferFeeRequiresFxRate() {
        val noFxQuotes = QuoteSnapshot(quotes = quotes.quotes)
        val draft = WebEventDraft(
            type = "transfer",
            date = DATE1,
            fromInstitutionId = INST_A,
            fromInstrumentId = CASH_CNY,
            fromQuantity = "100",
            toInstitutionId = INST_B,
            toInstrumentId = FUND,
            toQuantity = "50",
            toPrice = "2",
            feeLoss = "10",
            feeFromCash = true,
            feeInstitutionId = INST_B,
            feeInstrumentId = CASH_USD,
        )
        val result = WebEventBuilder.build(data, noFxQuotes, draft, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("缺少 USD 当前汇率"))
    }

    @Test
    fun incomeDestinationMustBeCash() {
        val draft = WebEventDraft(
            type = "income",
            date = DATE1,
            incomeKind = "interest",
            incomeHasSource = false,
            incomeInstitutionId = INST_A,
            incomeInstrumentId = FUND,
            incomeAmount = "5",
        )
        assertEquals("入账账户必须是现金标的", failure(draft))
    }

    @Test
    fun dividendRequiresSource() {
        val draft = WebEventDraft(
            type = "income",
            date = DATE1,
            incomeKind = "dividend",
            incomeHasSource = false,
            incomeInstitutionId = INST_A,
            incomeInstrumentId = CASH_CNY,
            incomeAmount = "5",
        )
        assertEquals("分红必须选择来源持仓", failure(draft))
    }

    @Test
    fun interestWithoutSourceBuilds() {
        val draft = WebEventDraft(
            type = "income",
            date = DATE1,
            incomeKind = "interest",
            incomeHasSource = false,
            incomeInstitutionId = INST_A,
            incomeInstrumentId = CASH_CNY,
            incomeAmount = "5",
        )
        val income = build(draft).getOrThrow().single() as IncomeEvent
        assertEquals(IncomeKind.INTEREST, income.kind)
        assertNull(income.source)
        assertEquals("CNY", income.currency)
        assertNull(income.linkedEventId)
    }

    @Test
    fun immediateWithdrawalCreatesLinkedExternal() {
        val draft = WebEventDraft(
            type = "income",
            date = DATE1,
            incomeKind = "interest",
            incomeHasSource = false,
            incomeInstitutionId = INST_A,
            incomeInstrumentId = CASH_CNY,
            incomeAmount = "5",
            immediateWithdrawal = true,
            externalLabel = "银行卡",
        )
        val events = build(draft).getOrThrow()
        assertEquals(2, events.size)
        val income = events[0] as IncomeEvent
        val external = events[1] as com.jimbozhang.folioprism.domain.model.ExternalEvent
        assertEquals(external.id, income.linkedEventId)
        assertEquals(ExternalDirection.OUT, external.direction)
        assertEquals(ExternalClassification.CONSUMPTION, external.classification)
        assertEquals("收益转出", external.note)
        assertEquals("银行卡", external.counterpartyLabel)
    }

    @Test
    fun externalUsesCashOnlyAndInForcesCapital() {
        val bad = WebEventDraft(
            type = "external",
            date = DATE1,
            externalDirection = "in",
            externalInstitutionId = INST_A,
            externalInstrumentId = FUND,
            externalAmount = "5",
        )
        assertEquals("外部出入金必须使用现金标的", failure(bad))

        val draft = WebEventDraft(
            type = "external",
            date = DATE1,
            externalDirection = "in",
            externalClassification = "consumption",
            externalInstitutionId = INST_A,
            externalInstrumentId = CASH_CNY,
            externalAmount = "5",
        )
        val event = build(draft).getOrThrow().single()
            as com.jimbozhang.folioprism.domain.model.ExternalEvent
        assertEquals(ExternalDirection.IN, event.direction)
        assertEquals(ExternalClassification.CAPITAL, event.classification)
    }

    @Test
    fun expenseWithoutPositionUsesCnyAndAllowsNegative() {
        val draft = WebEventDraft(
            type = "expense",
            date = DATE1,
            expenseNoPosition = true,
            expenseAmount = "-3",
            expenseCategoryId = CategoryIds.CASH,
        )
        val event = build(draft).getOrThrow().single()
            as com.jimbozhang.folioprism.domain.model.ExpenseEvent
        assertNull(event.position)
        assertEquals("CNY", event.currency)
        assertDecimal("-3", event.amount)
    }

    @Test
    fun expenseRequiresNonZeroAmountAndCashPosition() {
        val zero = WebEventDraft(
            type = "expense",
            date = DATE1,
            expenseNoPosition = true,
            expenseAmount = "0",
            expenseCategoryId = CategoryIds.CASH,
        )
        assertEquals("请输入有效的金额", failure(zero))

        val nonCash = WebEventDraft(
            type = "expense",
            date = DATE1,
            expenseInstitutionId = INST_A,
            expenseInstrumentId = FUND,
            expenseAmount = "1",
            expenseCategoryId = CategoryIds.CASH,
        )
        assertEquals("现金损耗必须使用现金标的", failure(nonCash))
    }

    @Test
    fun openingRejectsDuplicatePosition() {
        val existing = portfolio(
            institutions = listOf(Institution(INST_A, "A")),
            instruments = listOf(cash),
            events = listOf(opening(ref(INST_A, CASH_CNY), amount = "100")),
        )
        val draft = WebEventDraft(
            type = "opening",
            date = DATE1,
            openingInstitutionId = INST_A,
            openingInstrumentId = CASH_CNY,
            openingValue = "200",
        )
        val result = WebEventBuilder.build(existing, quotes, draft, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("有一笔期初交易"))
    }

    @Test
    fun openingUsesAmountForCashAndQuantityForQuoted() {
        val cashDraft = WebEventDraft(
            type = "opening",
            date = DATE1,
            openingInstitutionId = INST_A,
            openingInstrumentId = CASH_CNY,
            openingValue = "120",
        )
        val cashEvent = build(cashDraft).getOrThrow().single()
            as com.jimbozhang.folioprism.domain.model.OpeningEvent
        assertDecimal("120", cashEvent.amount)
        assertNull(cashEvent.quantity)
        assertNull(cashEvent.price)

        val fundDraft = WebEventDraft(
            type = "opening",
            date = DATE1,
            openingInstitutionId = INST_A,
            openingInstrumentId = FUND,
            openingValue = "10",
        )
        val fundEvent = build(fundDraft).getOrThrow().single()
            as com.jimbozhang.folioprism.domain.model.OpeningEvent
        assertDecimal("10", fundEvent.quantity)
        assertNull(fundEvent.amount)
        assertDecimal("2", fundEvent.price)
    }

    @Test
    fun adjustmentRequiresReasonAndNonZeroDelta() {
        val noReason = WebEventDraft(
            type = "adjustment",
            date = DATE1,
            adjustmentInstitutionId = INST_A,
            adjustmentInstrumentId = CASH_CNY,
            adjustmentValue = "1",
        )
        assertEquals("请填写调整原因", failure(noReason))

        val zero = WebEventDraft(
            type = "adjustment",
            date = DATE1,
            adjustmentInstitutionId = INST_A,
            adjustmentInstrumentId = CASH_CNY,
            adjustmentValue = "0",
            adjustmentReason = "修正",
        )
        assertEquals("请输入有效的金额增减", failure(zero))

        val draft = WebEventDraft(
            type = "adjustment",
            date = DATE1,
            adjustmentInstitutionId = INST_A,
            adjustmentInstrumentId = FUND,
            adjustmentValue = "-2",
            adjustmentReason = "份额修正",
        )
        val event = build(draft).getOrThrow().single()
            as com.jimbozhang.folioprism.domain.model.AdjustmentEvent
        assertDecimal("-2", event.deltaQuantity)
        assertNull(event.deltaAmount)
        assertDecimal("2", event.price)
    }
}
