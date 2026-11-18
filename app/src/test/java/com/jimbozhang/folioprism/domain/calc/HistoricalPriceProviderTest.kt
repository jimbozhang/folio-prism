package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.history.CachedSeries
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import com.jimbozhang.folioprism.domain.money.bd
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalPriceProviderTest {

    private val instrument = "ins_test"
    private val day1 = LocalDate.of(2026, 1, 1)
    private val day2 = LocalDate.of(2026, 1, 2)
    private val day6 = LocalDate.of(2026, 1, 6)
    private val day11 = LocalDate.of(2026, 1, 11)
    private val points = mapOf(
        instrument to listOf(
            PricePoint(day1, bd("100")),
            PricePoint(day11, bd("120")),
        ),
    )

    @Test
    fun onlineCacheWinsOverInterpolation() {
        val cache = PriceCacheData(
            quotes = mapOf(
                instrument to CachedSeries(auto = mapOf("2026-01-06" to "200")),
            ),
        )
        val provider = HistoricalPriceProvider(cache, emptyMap(), points)

        assertDecimal("200", provider.price(instrument, day6))
        // The cache's previous close is preferred over interpolating.
        assertDecimal("200", provider.price(instrument, day11))
        // No cache near day2: fall back to the known points.
        assertDecimal("102", provider.price(instrument, day2))
        assertTrue(provider.usedInterpolation)
    }

    @Test
    fun currentPriceOverridesCacheAtAsOfDateOnly() {
        val cache = PriceCacheData(
            quotes = mapOf(
                instrument to CachedSeries(auto = mapOf("2026-01-06" to "200")),
            ),
        )
        val provider = HistoricalPriceProvider(
            cache,
            emptyMap(),
            points,
            currentPrices = mapOf(instrument to bd("250")),
            currentAsOf = day6,
        )

        assertDecimal("250", provider.price(instrument, day6))
        // Other dates keep the historical behavior.
        assertDecimal("200", provider.price(instrument, day11))
        assertFalse(provider.usedInterpolation)
    }

    @Test
    fun interpolationFillsOnlyMissingDates() {
        val provider = HistoricalPriceProvider(PriceCacheData(), emptyMap(), points)

        assertDecimal("102", provider.price(instrument, day2))
        assertDecimal("110", provider.price(instrument, day6))
        assertDecimal("120", provider.price(instrument, day11))
    }
}
