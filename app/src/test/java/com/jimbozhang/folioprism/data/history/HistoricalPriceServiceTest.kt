package com.jimbozhang.folioprism.data.history

import com.jimbozhang.folioprism.data.price.HttpFetcher
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GOLD
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.money.bd
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalPriceServiceTest {

    private val fundJs =
        """var fS_name="Fund";var Data_netWorthTrend = [{"x":1767225600000,"y":1.2},{"x":1767312000000,"y":1.3}];var x=1;"""
    private val etfJson =
        """{"data":{"sh513500":{"qfqday":[["2026-01-02","2.0","2.1","2.2","1.9","100"]]}}}"""
    private val usJson =
        """var x=([{"d":"2026-01-02","c":"101"}]);"""
    private val goldJson =
        """var _AU0=([{"d":"2026-01-02","c":"901"}]);"""
    private val fxJson =
        """{"amount":1,"base":"USD","start_date":"2026-01-01","end_date":"2026-01-03","rates":{"2026-01-02":{"CNY":7.0,"HKD":7.8}}}"""

    private class FakeFetcher(private val handler: (String) -> String?) : HttpFetcher {
        var requestCount = 0
            private set

        override suspend fun get(url: String): String {
            requestCount++
            return handler(url) ?: error("no fake response for $url")
        }
    }

    @Test
    fun fetchesAndCachesAllSupportedSources() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                cashInstrument(CASH_USD, "USD"),
                quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163"),
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
                quotedInstrument(US_EQ, CategoryIds.US_EQUITY, currency = "USD", code = "VOO"),
                quotedInstrument(GOLD, CategoryIds.GOLD, code = "AU9999"),
            ),
            events = listOf(
                opening(ref(INST_A, FUND), quantity = "10", date = "2026-01-01T00:00:00+08:00"),
                opening(ref(INST_A, "etf"), quantity = "10", date = "2026-01-01T00:00:00+08:00"),
                opening(ref(INST_A, US_EQ), quantity = "1", date = "2026-01-01T00:00:00+08:00"),
                opening(ref(INST_A, GOLD), quantity = "1", date = "2026-01-01T00:00:00+08:00"),
                opening(ref(INST_A, CASH_USD), amount = "100", date = "2026-01-01T00:00:00+08:00"),
            ),
        )
        val fetcher = FakeFetcher { url ->
            when {
                url.contains("pingzhongdata/008163") -> fundJs
                url.contains("param=sh513500") -> etfJson
                url.contains("US_MinKService.getDailyK") -> usJson
                url.contains("InnerFuturesNewService.getDailyKLine") -> goldJson
                url.contains("frankfurter") -> fxJson
                else -> null
            }
        }
        val dir = Files.createTempDirectory("history-cache").toFile()
        val repo = PriceCacheRepository(java.io.File(dir, "price-cache.json"))
        val service = HistoricalPriceService(fetcher, repo, now = { "now" })

        val result = service.fetchAndCache(
            data = data,
            from = LocalDate.of(2026, 1, 2),
            to = LocalDate.of(2026, 1, 3),
        )

        assertTrue(result.logs.any { it.contains("history ok: 008163") })
        assertTrue(result.cache.quotes.containsKey(FUND))
        assertTrue(result.cache.quotes.containsKey("etf"))
        assertTrue(result.cache.quotes.containsKey(US_EQ))
        assertTrue(result.cache.quotes.containsKey(GOLD))
        assertTrue(result.cache.fx.containsKey("USD"))
        assertDecimal("1.2", result.cache.quotes[FUND]?.auto?.get("2026-01-01")?.let(::bd))
        assertDecimal("2.1", result.cache.quotes["etf"]?.auto?.get("2026-01-02")?.let(::bd))
        assertDecimal("101", result.cache.quotes[US_EQ]?.auto?.get("2026-01-02")?.let(::bd))
        assertDecimal("901", result.cache.quotes[GOLD]?.auto?.get("2026-01-02")?.let(::bd))
        assertDecimal("7.0", result.cache.fx["USD"]?.auto?.get("2026-01-02")?.let(::bd))

        val requestsAfterFirstFetch = fetcher.requestCount
        val second = service.fetchAndCache(
            data = data,
            from = LocalDate.of(2026, 1, 2),
            to = LocalDate.of(2026, 1, 3),
        )
        assertTrue(second.logs.any { it.contains("history cached: 008163") })
        assertTrue(fetcher.requestCount == requestsAfterFirstFetch)

        dir.deleteRecursively()
        Unit
    }
}

private fun assertDecimal(expected: String, actual: java.math.BigDecimal?) {
    val value = actual ?: throw AssertionError("Expected $expected but was null")
    if (java.math.BigDecimal(expected).compareTo(value) != 0) {
        throw AssertionError("Expected $expected but was $value")
    }
}
