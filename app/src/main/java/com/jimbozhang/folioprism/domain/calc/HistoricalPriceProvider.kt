package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.history.CachedSeries
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PricePoint(
    val date: LocalDate,
    val price: BigDecimal,
)

class HistoricalPriceProvider(
    private val cache: PriceCacheData,
    private val currentFxRates: Map<String, BigDecimal> = emptyMap(),
    private val eventPrices: Map<String, List<PricePoint>> = emptyMap(),
    /** Live prices (native currency) that override the cache at [currentAsOf]. */
    private val currentPrices: Map<String, BigDecimal> = emptyMap(),
    /** The single date for which [currentPrices] apply, or null. */
    private val currentAsOf: LocalDate? = null,
) : PriceProvider {

    /** True when at least one date had no online/cache price and used interpolation. */
    var usedInterpolation: Boolean = false
        private set

    override fun price(instrumentId: String, date: LocalDate): BigDecimal? {
        // On the "now" date, prefer the live quote so the curve end matches
        // the home screen; the cache holds the last daily close.
        if (date == currentAsOf) {
            currentPrices[instrumentId]?.let { return it }
        }
        lookup(cache.quotes[instrumentId], date)?.let { return it }
        return interpolate(eventPrices[instrumentId], date)?.also {
            usedInterpolation = true
        }
    }

    /** Current FX only; the date is irrelevant by design. */
    override fun fxRate(currency: String): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> currentFxRates[currency.uppercase()]
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

    /**
     * Manual-price assets have no intermediate quotes. Connect the known
     * transaction/opening/current manual prices with straight lines.
     */
    private fun interpolate(points: List<PricePoint>?, date: LocalDate): BigDecimal? {
        if (points.isNullOrEmpty()) return null
        points.firstOrNull { it.date == date }?.let { return it.price }

        val previous = points.lastOrNull { it.date.isBefore(date) }
        val next = points.firstOrNull { it.date.isAfter(date) }
        return when {
            previous != null && next != null -> {
                val totalDays = ChronoUnit.DAYS.between(previous.date, next.date)
                val offsetDays = ChronoUnit.DAYS.between(previous.date, date)
                if (totalDays <= 0) {
                    previous.price
                } else {
                    val delta = next.price.subtract(previous.price)
                    val fraction = BigDecimal(offsetDays)
                        .divide(BigDecimal(totalDays), 10, RoundingMode.HALF_UP)
                    previous.price.add(delta.multiply(fraction))
                }
            }
            previous != null -> previous.price
            next != null -> next.price
            else -> null
        }
    }
}
