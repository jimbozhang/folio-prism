package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.history.CachedSeries
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import java.math.BigDecimal
import java.time.LocalDate

class HistoricalPriceProvider(
    private val cache: PriceCacheData,
) : PriceProvider {

    override fun price(instrumentId: String, date: LocalDate): BigDecimal? =
        lookup(cache.quotes[instrumentId], date)

    override fun fxRate(currency: String, date: LocalDate): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> lookup(cache.fx[currency.uppercase()], date)
        }

    private fun lookup(series: CachedSeries?, date: LocalDate): BigDecimal? {
        if (series == null) return null
        series.manual[date.toString()]?.let { return it.toBigDecimalOrNull() }
        series.auto[date.toString()]?.let { return it.toBigDecimalOrNull() }

        // Use the most recent previous close (weekends / holidays / NAV gaps).
        for (daysBack in 1..30) {
            val previous = date.minusDays(daysBack.toLong())
            series.manual[previous.toString()]?.let { return it.toBigDecimalOrNull() }
            series.auto[previous.toString()]?.let { return it.toBigDecimalOrNull() }
        }
        return null
    }
}
