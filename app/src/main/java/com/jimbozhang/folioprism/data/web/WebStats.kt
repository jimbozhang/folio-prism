package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.TransferEvent
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Monthly flow/expense aggregation mirrors the app's charts (which live in a
 * Compose file and are not directly reusable). Keep the rules in sync.
 */
object WebStats {

    private const val FEE_LOSS_LABEL = "手续费/损耗"

    fun monthlyFlows(
        data: PortfolioData,
        fxRates: Map<String, BigDecimal>,
        range: DateRange,
    ): List<FlowMonthDto> {
        val map = linkedMapOf<YearMonth, MutableList<ExternalEvent>>()
        data.events.filterIsInstance<ExternalEvent>().forEach { event ->
            val date = event.localDate()
            if (date.isBefore(range.from) || date.isAfter(range.to)) return@forEach
            val month = YearMonth.from(parseEventTime(event.date))
            map.getOrPut(month) { mutableListOf() }.add(event)
        }
        return map.entries
            .sortedBy { it.key }
            .map { (month, events) ->
                var inflow = BigDecimal.ZERO
                var outflow = BigDecimal.ZERO
                events.forEach { event ->
                    val amountCny = fxToCny(event.currency, fxRates)
                        ?.let { event.amount.multiply(it) } ?: return@forEach
                    if (event.direction == ExternalDirection.IN) {
                        inflow = inflow.add(amountCny)
                    } else {
                        outflow = outflow.add(amountCny)
                    }
                }
                FlowMonthDto(month.toString(), inflow.plain(), outflow.plain())
            }
    }

    fun monthlyExpenses(
        data: PortfolioData,
        categoryNames: Map<String, String>,
        fxRates: Map<String, BigDecimal>,
        range: DateRange,
    ): List<ExpenseMonthDto> {
        val map = linkedMapOf<YearMonth, MutableMap<String, BigDecimal>>()
        val linkedFeeTransfers = data.events
            .filterIsInstance<ExpenseEvent>()
            .filter { it.linkedTransferId != null && it.note in setOf("转账手续费", "转账损耗") }
            .mapNotNull { it.linkedTransferId }
            .toSet()
        data.events.filterIsInstance<ExpenseEvent>().forEach { event ->
            val date = event.localDate()
            if (date.isBefore(range.from) || date.isAfter(range.to)) return@forEach
            val amountCny = fxToCny(event.currency, fxRates)
                ?.let { event.amount.multiply(it) } ?: return@forEach
            val month = YearMonth.from(parseEventTime(event.date))
            val categories = map.getOrPut(month) { linkedMapOf() }
            val name = categoryNames[event.categoryId] ?: event.categoryId
            categories[name] = (categories[name] ?: BigDecimal.ZERO).add(amountCny)
        }
        data.events.filterIsInstance<TransferEvent>().forEach { event ->
            if (event.lossAmount.signum() == 0 && (event.feeAmount?.signum() ?: 0) == 0) {
                return@forEach
            }
            // Old data records these as linked expenses; do not count them twice.
            if (event.id in linkedFeeTransfers) return@forEach
            val date = event.localDate()
            if (date.isBefore(range.from) || date.isAfter(range.to)) return@forEach
            var feeLossCny = fxToCny(event.lossCurrency, fxRates)
                ?.let { event.lossAmount.multiply(it) } ?: BigDecimal.ZERO
            val feeAmount = event.feeAmount
            val feeCurrency = event.feeCurrency
            if (feeAmount != null && feeCurrency != null) {
                fxToCny(feeCurrency, fxRates)?.let {
                    feeLossCny = feeLossCny.add(feeAmount.multiply(it))
                }
            }
            if (feeLossCny.signum() == 0) return@forEach
            val month = YearMonth.from(parseEventTime(event.date))
            val categories = map.getOrPut(month) { linkedMapOf() }
            categories[FEE_LOSS_LABEL] =
                (categories[FEE_LOSS_LABEL] ?: BigDecimal.ZERO).add(feeLossCny)
        }
        return map.entries
            .sortedBy { it.key }
            .map { (month, categories) ->
                ExpenseMonthDto(
                    month = month.toString(),
                    totalCny = categories.values
                        .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
                        .plain(),
                    byCategory = categories.mapValues { it.value.plain() },
                )
            }
    }

    private fun fxToCny(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }
}
