package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.domain.calc.EventValues
import com.jimbozhang.folioprism.domain.calc.TransferPricing
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toMoneyAmount
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Field-for-field mirror of the app editor's form state. The web UI posts one
 * of these; [WebEventBuilder] turns it into domain events.
 */
@Serializable
data class WebEventDraft(
    val type: String = "transfer",
    val date: String = "",
    val note: String = "",
    val fromInstitutionId: String? = null,
    val fromInstrumentId: String? = null,
    val fromQuantity: String = "",
    val fromPrice: String = "",
    /** True when [fromPrice] is typed in CNY instead of the instrument currency. */
    val fromPriceInCny: Boolean = false,
    val toInstitutionId: String? = null,
    val toInstrumentId: String? = null,
    val toQuantity: String = "",
    val toPrice: String = "",
    /** True when [toPrice] is typed in CNY instead of the instrument currency. */
    val toPriceInCny: Boolean = false,
    val feeLoss: String = "",
    val feeFromCash: Boolean = false,
    val feeInstitutionId: String? = null,
    val feeInstrumentId: String? = null,
    val incomeKind: String = "dividend",
    val incomeHasSource: Boolean = true,
    val sourceInstitutionId: String? = null,
    val sourceInstrumentId: String? = null,
    val incomeInstitutionId: String? = null,
    val incomeInstrumentId: String? = null,
    val incomeAmount: String = "",
    val immediateWithdrawal: Boolean = false,
    val externalLabel: String = "",
    val externalDirection: String = "out",
    val externalClassification: String = "capital",
    val externalInstitutionId: String? = null,
    val externalInstrumentId: String? = null,
    val externalAmount: String = "",
    val expenseNoPosition: Boolean = false,
    val expenseInstitutionId: String? = null,
    val expenseInstrumentId: String? = null,
    val expenseAmount: String = "",
    val expenseCategoryId: String = "",
    val openingInstitutionId: String? = null,
    val openingInstrumentId: String? = null,
    val openingValue: String = "",
    val adjustmentInstitutionId: String? = null,
    val adjustmentInstrumentId: String? = null,
    val adjustmentValue: String = "",
    val adjustmentReason: String = "",
)

/**
 * Mirrors [com.jimbozhang.folioprism.ui.transactions.buildEvents] so the web
 * API accepts and rejects exactly the same input as the app editor. The app
 * code is intentionally left untouched; keep these two in sync when editing
 * rules change.
 */
object WebEventBuilder {

    fun build(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        draft: WebEventDraft,
        initial: Event?,
    ): Result<List<Event>> = runCatching {
        runCatching { parseEventTime(draft.date) }.getOrElse { error("日期格式无效") }

        when (draft.type) {
            "transfer" -> buildTransfer(data, quotes, draft, initial)
            "income" -> buildIncome(data, quotes, draft, initial)
            "external" -> buildExternal(data, draft, initial)
            "expense" -> buildExpense(data, draft, initial)
            "opening" -> buildOpening(data, quotes, draft, initial)
            "adjustment" -> buildAdjustment(data, quotes, draft, initial)
            else -> error("未知事件类型")
        }
    }

    private fun buildTransfer(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val fromInstitutionId = draft.fromInstitutionId ?: error("请选择转出机构")
        val toInstitutionId = draft.toInstitutionId ?: error("请选择转入机构")
        val fromInstrument = requireInstrument(data, draft.fromInstrumentId, "转出标的")
        val toInstrument = requireInstrument(data, draft.toInstrumentId, "转入标的")
        if (fromInstitutionId == toInstitutionId && fromInstrument.id == toInstrument.id) {
            error("转出与转入不能是同一持仓")
        }
        val fromQuantity = positive(draft.fromQuantity, "转出数量")
        val toQuantity = positive(draft.toQuantity, "转入数量")
        val fromEnteredPrice = if (fromInstrument.kind == InstrumentKind.QUOTED) {
            positive(draft.fromPrice, "${fromInstrument.code} 单价")
        } else {
            null
        }
        val toEnteredPrice = if (toInstrument.kind == InstrumentKind.QUOTED) {
            positive(draft.toPrice, "${toInstrument.code} 单价")
        } else {
            null
        }
        val fromPrice = fromEnteredPrice?.let {
            TransferPricing.nativePrice(it, draft.fromPriceInCny, fromInstrument, quotes.fxRates)
                ?: error("缺少 ${fromInstrument.currency} 当前汇率，请刷新行情")
        }
        val toPrice = toEnteredPrice?.let {
            TransferPricing.nativePrice(it, draft.toPriceInCny, toInstrument, quotes.fxRates)
                ?: error("缺少 ${toInstrument.currency} 当前汇率，请刷新行情")
        }
        val values = EventValues.transferValues(
            fromQuantity = fromQuantity,
            toQuantity = toQuantity,
            fromPrice = fromPrice,
            toPrice = toPrice,
        )
        val lossCurrency = fromInstrument.currency
        val sameCurrency =
            fromInstrument.currency.equals(toInstrument.currency, ignoreCase = true) ||
                (fromInstrument.currency.uppercase() in setOf("CNY", "CNH") &&
                    toInstrument.currency.uppercase() in setOf("CNY", "CNH"))
        val autoLoss = if (sameCurrency) {
            values.fromNative.subtract(values.toNative).toMoneyAmount()
        } else {
            val fromCny = TransferPricing.exactCnyValue(
                instrument = fromInstrument,
                quantity = fromQuantity,
                nativePrice = fromPrice,
                enteredPrice = fromEnteredPrice,
                inCny = draft.fromPriceInCny,
            )
            val toCny = TransferPricing.exactCnyValue(
                instrument = toInstrument,
                quantity = toQuantity,
                nativePrice = toPrice,
                enteredPrice = toEnteredPrice,
                inCny = draft.toPriceInCny,
            )
            if (fromCny != null && toCny != null) {
                TransferPricing.lossInCurrency(
                    lossCny = fromCny.subtract(toCny),
                    lossCurrency = lossCurrency,
                    fxRates = quotes.fxRates,
                ) ?: BigDecimal.ZERO
            } else {
                BigDecimal.ZERO
            }
        }
        val totalLoss = if (draft.feeLoss.isBlank()) {
            autoLoss
        } else {
            draft.feeLoss.toBigDecimalOrNull() ?: error("请输入有效的 手续费/损耗")
        }
        val feeCashInstrument = if (draft.feeFromCash) {
            val feeInstrumentId = draft.feeInstrumentId ?: error("请选择手续费账户")
            requireInstrument(data, feeInstrumentId, "手续费账户")
        } else {
            null
        }
        if (feeCashInstrument != null && feeCashInstrument.kind != InstrumentKind.CASH) {
            error("手续费账户必须是现金标的")
        }
        val feeCashInstitutionId = if (feeCashInstrument != null) {
            draft.feeInstitutionId ?: error("请选择手续费账户机构")
        } else {
            null
        }
        // The part not already embedded in the from/to values is paid from the
        // separate cash account. Convert at the current FX rate.
        val extraLoss = feeCashInstrument?.let { totalLoss.subtract(autoLoss) }
        val feeAmount = extraLoss?.let {
            val lossFx = currentFx(lossCurrency, quotes)
            val feeFx = currentFx(feeCashInstrument!!.currency, quotes)
            it.multiply(lossFx).divide(feeFx, 8, RoundingMode.HALF_UP).toMoneyAmount()
        }
        val feePosition =
            if (feeCashInstrument != null && feeCashInstitutionId != null &&
                feeAmount != null && feeAmount.signum() != 0
            ) {
                PositionRef(feeCashInstitutionId, feeCashInstrument.id)
            } else {
                null
            }
        return listOf(
            TransferEvent(
                id = if (initial is TransferEvent) initial.id else UUID.randomUUID().toString(),
                date = draft.date,
                note = draft.note,
                from = PositionRef(fromInstitutionId, fromInstrument.id),
                to = PositionRef(toInstitutionId, toInstrument.id),
                fromQuantity = fromQuantity,
                toQuantity = toQuantity,
                fromPrice = fromPrice,
                toPrice = toPrice,
                lossAmount = if (feeCashInstrument != null) autoLoss else totalLoss,
                lossCurrency = lossCurrency,
                feePosition = feePosition,
                feeAmount = feeAmount?.takeIf { it.signum() != 0 },
                feeCurrency = feeCashInstrument?.currency,
            ),
        )
    }

    private fun buildIncome(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val destinationInstitutionId = draft.incomeInstitutionId ?: error("请选择入账机构")
        val destinationInstrument = requireInstrument(data, draft.incomeInstrumentId, "入账账户")
        if (destinationInstrument.kind != InstrumentKind.CASH) {
            error("入账账户必须是现金标的")
        }
        val source = if (draft.incomeHasSource) {
            val sourceInstitutionId = draft.sourceInstitutionId ?: error("请选择来源机构")
            val sourceInstrument = requireInstrument(data, draft.sourceInstrumentId, "来源标的")
            PositionRef(sourceInstitutionId, sourceInstrument.id)
        } else {
            null
        }
        if (draft.incomeKind == "dividend" && source == null) {
            error("分红必须选择来源持仓")
        }
        val amount = positive(draft.incomeAmount, "金额")
        val incomeId = if (initial is IncomeEvent) initial.id else UUID.randomUUID().toString()
        val externalId = if (draft.immediateWithdrawal) UUID.randomUUID().toString() else null
        val income = IncomeEvent(
            id = incomeId,
            date = draft.date,
            note = draft.note,
            kind = if (draft.incomeKind == "dividend") IncomeKind.DIVIDEND else IncomeKind.INTEREST,
            source = source,
            destination = PositionRef(destinationInstitutionId, destinationInstrument.id),
            amount = amount,
            currency = destinationInstrument.currency,
            immediateExternalWithdrawal = draft.immediateWithdrawal,
            externalAccountLabel = draft.externalLabel.takeIf { it.isNotBlank() },
            linkedEventId = externalId,
        )
        val external = externalId?.let {
            ExternalEvent(
                id = it,
                date = draft.date,
                note = "收益转出",
                direction = ExternalDirection.OUT,
                classification = ExternalClassification.CONSUMPTION,
                position = PositionRef(destinationInstitutionId, destinationInstrument.id),
                amount = amount,
                currency = destinationInstrument.currency,
                counterpartyLabel = draft.externalLabel.takeIf { it.isNotBlank() },
            )
        }
        return listOfNotNull(income, external)
    }

    private fun buildExternal(
        data: PortfolioData,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val institutionId = draft.externalInstitutionId ?: error("请选择现金账户机构")
        val instrument = requireInstrument(data, draft.externalInstrumentId, "现金账户")
        if (instrument.kind != InstrumentKind.CASH) error("外部出入金必须使用现金标的")
        val amount = positive(draft.externalAmount, "金额")
        val direction =
            if (draft.externalDirection == "in") ExternalDirection.IN else ExternalDirection.OUT
        val classification = if (direction == ExternalDirection.IN) {
            ExternalClassification.CAPITAL
        } else if (draft.externalClassification == "consumption") {
            ExternalClassification.CONSUMPTION
        } else {
            ExternalClassification.CAPITAL
        }
        return listOf(
            ExternalEvent(
                id = if (initial is ExternalEvent) initial.id else UUID.randomUUID().toString(),
                date = draft.date,
                note = draft.note,
                direction = direction,
                classification = classification,
                position = PositionRef(institutionId, instrument.id),
                amount = amount,
                currency = instrument.currency,
                counterpartyLabel = null,
            ),
        )
    }

    private fun buildExpense(
        data: PortfolioData,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val amount = nonZero(draft.expenseAmount, "金额")
        val position = if (draft.expenseNoPosition) {
            null
        } else {
            val institutionId = draft.expenseInstitutionId ?: error("请选择现金账户机构")
            val instrument = requireInstrument(data, draft.expenseInstrumentId, "现金账户")
            if (instrument.kind != InstrumentKind.CASH) error("现金损耗必须使用现金标的")
            PositionRef(institutionId, instrument.id)
        }
        if (draft.expenseCategoryId.isBlank()) error("请选择类别")
        val currency = if (position == null) {
            "CNY"
        } else {
            data.instrument(position.instrumentId)!!.currency
        }
        return listOf(
            ExpenseEvent(
                id = if (initial is ExpenseEvent) initial.id else UUID.randomUUID().toString(),
                date = draft.date,
                note = draft.note,
                position = position,
                amount = amount,
                currency = currency,
                categoryId = draft.expenseCategoryId,
                linkedTransferId = (initial as? ExpenseEvent)?.linkedTransferId,
            ),
        )
    }

    private fun buildOpening(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val institutionId = draft.openingInstitutionId ?: error("请选择机构")
        val instrument = requireInstrument(data, draft.openingInstrumentId, "标的")
        val existingOpening = data.events
            .filterIsInstance<OpeningEvent>()
            .firstOrNull {
                it.position == PositionRef(institutionId, instrument.id) &&
                    it.id != (initial as? OpeningEvent)?.id
            }
        if (existingOpening != null) {
            error("该持仓已于 ${existingOpening.localDate()} 有一笔期初交易")
        }
        val cash = instrument.kind == InstrumentKind.CASH
        val value = nonZero(draft.openingValue, if (cash) "期初金额" else "期初份额")
        return listOf(
            OpeningEvent(
                id = if (initial is OpeningEvent) initial.id else UUID.randomUUID().toString(),
                date = draft.date,
                note = draft.note,
                position = PositionRef(institutionId, instrument.id),
                quantity = if (cash) null else value,
                amount = if (cash) value else null,
                price = if (cash) null else quotePrice(quotes, instrument),
            ),
        )
    }

    private fun buildAdjustment(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        draft: WebEventDraft,
        initial: Event?,
    ): List<Event> {
        val institutionId = draft.adjustmentInstitutionId ?: error("请选择机构")
        val instrument = requireInstrument(data, draft.adjustmentInstrumentId, "标的")
        if (draft.adjustmentReason.isBlank()) error("请填写调整原因")
        val cash = instrument.kind == InstrumentKind.CASH
        val value = nonZero(draft.adjustmentValue, if (cash) "金额增减" else "份额增减")
        return listOf(
            AdjustmentEvent(
                id = if (initial is AdjustmentEvent) initial.id else UUID.randomUUID().toString(),
                date = draft.date,
                note = draft.note,
                position = PositionRef(institutionId, instrument.id),
                deltaQuantity = if (cash) null else value,
                deltaAmount = if (cash) value else null,
                price = if (cash) null else quotePrice(quotes, instrument),
                reason = draft.adjustmentReason,
            ),
        )
    }

    private fun requireInstrument(data: PortfolioData, id: String?, role: String): Instrument =
        data.instrument(id ?: error("请选择$role")) ?: error("$role 不存在")

    private fun positive(text: String, label: String): BigDecimal =
        text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: error("请输入有效的$label")

    private fun nonZero(text: String, label: String): BigDecimal =
        text.toBigDecimalOrNull()?.takeIf { it.signum() != 0 } ?: error("请输入有效的$label")

    private fun currentFx(currency: String, quotes: QuoteSnapshot): BigDecimal =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> quotes.fxRates[currency.uppercase()]
                ?: error("缺少 $currency 当前汇率，请刷新行情")
        }

    private fun quotePrice(quotes: QuoteSnapshot, instrument: Instrument?): BigDecimal? =
        instrument?.let { quotes.quotes[it.id]?.price ?: it.quote.manualPrice }
}
