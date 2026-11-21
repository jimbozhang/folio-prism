package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Monthly external-flow aggregation for the web charts.
 */
object WebStats {

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
                        ?.let { event.amount.multiply(it).toCnyAmount() } ?: return@forEach
                    if (event.direction == ExternalDirection.IN) {
                        inflow = inflow.add(amountCny)
                    } else {
                        outflow = outflow.add(amountCny)
                    }
                }
                FlowMonthDto(month.toString(), inflow.plain(), outflow.plain())
            }
    }

    private fun fxToCny(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }
}
