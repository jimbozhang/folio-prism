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
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.money.bd
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertDecimal("1.2", result.cache.quotes[FUND]?.auto?.get("2026-01-01")?.let(::bd))
        assertDecimal("2.1", result.cache.quotes["etf"]?.auto?.get("2026-01-02")?.let(::bd))
        assertDecimal("101", result.cache.quotes[US_EQ]?.auto?.get("2026-01-02")?.let(::bd))
        assertDecimal("901", result.cache.quotes[GOLD]?.auto?.get("2026-01-02")?.let(::bd))

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

    /**
     * Reproduces the chart range arrows: load [from, to], step back one
     * whole month (shiftBack, fetches genuinely new data), then step forward
     * one month again (shiftForward lands on the full month after, which the
     * cache now covers) and verify that window is served from cache with no
     * new requests.
     */
    @Test
    fun shiftBackThenShiftForwardServedFromCache() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            ),
            events = listOf(
                opening(ref(INST_A, "etf"), quantity = "10", date = "2025-06-01T00:00:00+08:00"),
            ),
        )
        // Answer any tencent kline request with a close for every calendar day
        // in the requested range.
        val fetcher = FakeFetcher { url ->
            val match = Regex("day,([^,]+),([^,]+),").find(url) ?: return@FakeFetcher null
            val from = LocalDate.parse(match.groupValues[1])
            val to = LocalDate.parse(match.groupValues[2])
            val rows = generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .joinToString(",") { day ->
                    "[\"${day}\",\"1.0\",\"${day.dayOfMonth}.5\",\"1.0\",\"1.0\",\"1\"]"
                }
            """{"data":{"sh513500":{"qfqday":[$rows]}}}"""
        }
        val dir = Files.createTempDirectory("history-cache-arrows").toFile()
        val repo = PriceCacheRepository(File(dir, "price-cache.json"))
        val service = HistoricalPriceService(fetcher, repo, now = { "now" })

        val today = LocalDate.of(2026, 1, 31)
        val range = DateRange(LocalDate.of(2026, 1, 2), today)
        val first = service.fetchAndCache(data, range.from, range.to)
        assertTrue(first.logs.any { it.contains("history ok: 513500") })
        val afterFirst = fetcher.requestCount

        val shifted = range.shiftBack()
        service.fetchAndCache(data, shifted.from, shifted.to)
        val afterShifted = fetcher.requestCount

        val restored = shifted.shiftForward(today)
        assertEquals(DateRange(LocalDate.of(2026, 1, 1), today), restored)
        val third = service.fetchAndCache(data, restored.from, restored.to)

        assertTrue(third.logs.any { it.contains("history cached: 513500") })
        assertEquals(afterShifted, fetcher.requestCount)

        dir.deleteRecursively()
        Unit
    }

    /**
     * A failed fetch must back off: a second load inside the window issues no
     * new request; after the window elapses it retries; a repeated failure
     * arms a persisted window that outlives the session; and a successful
     * fetch clears the backoff entry, so the next uncovered range retries
     * right away even though the stale failure would still be inside the
     * window.
     */
    @Test
    fun failedFetchBacksOffUntilWindowElapses() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            ),
            events = listOf(
                opening(ref(INST_A, "etf"), quantity = "10", date = "2025-06-01T00:00:00+08:00"),
            ),
        )
        var respond = false
        val fetcher = FakeFetcher { url ->
            if (!respond) return@FakeFetcher null
            val match = Regex("day,([^,]+),([^,]+),").find(url) ?: return@FakeFetcher null
            val from = LocalDate.parse(match.groupValues[1])
            val to = LocalDate.parse(match.groupValues[2])
            val rows = generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .joinToString(",") { day ->
                    "[\"${day}\",\"1.0\",\"${day.dayOfMonth}.5\",\"1.0\",\"1.0\",\"1\"]"
                }
            """{"data":{"sh513500":{"qfqday":[$rows]}}}"""
        }
        val dir = Files.createTempDirectory("history-cache-backoff").toFile()
        val repo = PriceCacheRepository(File(dir, "price-cache.json"))
        var nowMs = 0L
        val service = HistoricalPriceService(
            fetcher,
            repo,
            retryCount = 0,
            now = { "now" },
            failureBackoffMs = 60_000L,
            // Tiny persisted window: the repeated failure below arms one, and
            // the success is allowed to outrun it within the test.
            exhaustionTtlMs = 1_000L,
            clockMs = { nowMs },
        )
        val from = LocalDate.of(2026, 1, 2)
        val to = LocalDate.of(2026, 1, 31)

        // A failed tencent fetch tries two URL variants, so it costs 2 requests.
        val first = service.fetchAndCache(data, from, to)
        assertTrue(first.logs.any { it.contains("history failed: 513500") })
        assertEquals(2, fetcher.requestCount)

        // Inside the backoff window: no new request is made.
        val second = service.fetchAndCache(data, from, to)
        assertTrue(second.logs.any { it.contains("history backoff: 513500") })
        assertEquals(2, fetcher.requestCount)

        // After the in-memory window elapses: it retries and fails again. The
        // repeated failure also arms a persisted window (1s in this test) that
        // outlives the session.
        nowMs += 60_000L
        val third = service.fetchAndCache(data, from, to)
        assertTrue(third.logs.any { it.contains("history failed: 513500") })
        assertEquals(4, fetcher.requestCount)
        assertTrue(third.cache.quotes["etf"]?.exhaustedUntilMs != null)

        // A successful fetch clears the backoff entry and the persisted
        // window: wait out both windows, then the fetch succeeds.
        respond = true
        nowMs += 60_000L
        val fourth = service.fetchAndCache(data, from, to)
        assertTrue(fourth.logs.any { it.contains("history ok: 513500") })
        assertEquals(5, fetcher.requestCount)

        // Step the fake clock back 1s so the failure from `third` would still
        // be inside its in-memory window if it had not been cleared by the
        // success. A new, uncovered range is then retried immediately instead
        // of being skipped.
        respond = false
        nowMs -= 1_000L
        val fifth = service.fetchAndCache(
            data,
            LocalDate.of(2025, 12, 1),
            LocalDate.of(2025, 12, 31),
        )
        assertTrue(fifth.logs.any { it.contains("history failed: 513500") })
        assertEquals(7, fetcher.requestCount)

        dir.deleteRecursively()
        Unit
    }

    /**
     * [HistoricalPriceService.needsNetwork] drives the truthful loading message:
     * true only while some held instrument is uncovered AND not in backoff.
     */
    @Test
    fun needsNetworkReflectsCacheAndBackoff() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            ),
            events = listOf(
                opening(ref(INST_A, "etf"), quantity = "10", date = "2025-06-01T00:00:00+08:00"),
            ),
        )
        val fetcher = FakeFetcher { null }
        val dir = Files.createTempDirectory("history-cache-needs-network").toFile()
        val repo = PriceCacheRepository(File(dir, "price-cache.json"))
        var nowMs = 0L
        val service = HistoricalPriceService(
            fetcher,
            repo,
            retryCount = 0,
            now = { "now" },
            failureBackoffMs = 60_000L,
            clockMs = { nowMs },
        )
        val from = LocalDate.of(2026, 1, 2)
        val to = LocalDate.of(2026, 1, 31)
        val earlierFrom = LocalDate.of(2025, 12, 1)
        val earlierTo = LocalDate.of(2025, 12, 31)

        // Nothing cached yet: the load needs the network.
        assertTrue(service.needsNetwork(data, from, to))

        // The fetch fails, putting the instrument in backoff: even a range the
        // cache does not cover must not hit the network while backoffed.
        service.fetchAndCache(data, from, to)
        assertFalse(service.needsNetwork(data, from, to))
        assertFalse(service.needsNetwork(data, earlierFrom, earlierTo))

        // After the backoff window: the uncovered range needs the network again.
        nowMs += 60_000L
        assertTrue(service.needsNetwork(data, earlierFrom, earlierTo))

        dir.deleteRecursively()
        Unit
    }

    /**
     * A source that answers but has no data near the range end (e.g. a
     * delisted instrument) must not be chased on every load: the first
     * successful fetch that still cannot cover the end records exhaustion,
     * which suppresses both the network request and the "fetching" status
     * until the window elapses; then one more probe is made and, still
     * stale, the instrument is re-armed instead of being fetched forever.
     */
    @Test
    fun exhaustedSourceIsNotChasedOnEveryLoad() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            ),
            events = listOf(
                opening(ref(INST_A, "etf"), quantity = "10", date = "2025-06-01T00:00:00+08:00"),
            ),
        )
        // The provider only ever has closes up to 2026-01-10, far before the
        // range end (a stale/exhausted source).
        val rows = (2..10)
            .joinToString(",") { day -> """["2026-01-${day}","1.0","${day}.5","1.0","1.0","1"]""" }
        val fetcher = FakeFetcher { """{"data":{"sh513500":{"qfqday":[$rows]}}}""" }
        val dir = Files.createTempDirectory("history-cache-exhausted").toFile()
        val repo = PriceCacheRepository(File(dir, "price-cache.json"))
        var nowMs = 0L
        val service = HistoricalPriceService(
            fetcher,
            repo,
            retryCount = 0,
            now = { "now" },
            clockMs = { nowMs },
            exhaustionTtlMs = 3_600_000L,
        )
        val from = LocalDate.of(2026, 1, 2)
        val to = LocalDate.of(2026, 1, 31)

        // First load: the fetch succeeds but leaves the end uncovered, so the
        // source is recorded as exhausted.
        val first = service.fetchAndCache(data, from, to)
        assertTrue(first.logs.any { it.contains("history ok: 513500") })
        assertTrue(first.logs.any { it.contains("history exhausted: 513500") })
        assertEquals(1, fetcher.requestCount)

        // While exhausted: no further request is issued and the load does not
        // need the network, so no "fetching" status is shown for it.
        val second = service.fetchAndCache(data, from, to)
        assertTrue(second.logs.any { it.contains("history exhausted: 513500") })
        assertEquals(1, fetcher.requestCount)
        assertFalse(service.needsNetwork(data, from, to))

        // After the window elapses: the source is probed once more, is still
        // stale, and is re-armed instead of being fetched on every load.
        nowMs += 3_600_000L
        assertTrue(service.needsNetwork(data, from, to))
        val third = service.fetchAndCache(data, from, to)
        assertEquals(2, fetcher.requestCount)
        assertTrue(third.logs.any { it.contains("history exhausted: 513500") })
        assertFalse(service.needsNetwork(data, from, to))

        dir.deleteRecursively()
        Unit
    }

    /**
     * A source whose fetches keep failing must not drive a fetch — and a
     * "fetching" status — on every app start: each failure is persisted with
     * the cache, the retry window escalates (doubling, capped) while
     * failures continue across restarts, and a later successful fetch clears
     * the records so the instrument behaves normally again. Fresh service
     * instances sharing one cache file simulate app restarts.
     */
    @Test
    fun failedSourceBackoffSurvivesRestartAndEscalates() = runBlocking {
        val data = portfolio(
            instruments = listOf(
                quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            ),
            events = listOf(
                opening(ref(INST_A, "etf"), quantity = "10", date = "2025-06-01T00:00:00+08:00"),
            ),
        )
        var respond = false
        val fetcher = FakeFetcher { url ->
            if (!respond) return@FakeFetcher null
            val match = Regex("day,([^,]+),([^,]+),").find(url) ?: return@FakeFetcher null
            val from = LocalDate.parse(match.groupValues[1])
            val to = LocalDate.parse(match.groupValues[2])
            val rows = generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .joinToString(",") { day ->
                    "[\"${day}\",\"1.0\",\"${day.dayOfMonth}.5\",\"1.0\",\"1.0\",\"1\"]"
                }
            """{"data":{"sh513500":{"qfqday":[$rows]}}}"""
        }
        val dir = Files.createTempDirectory("history-cache-failure").toFile()
        val repo = PriceCacheRepository(File(dir, "price-cache.json"))
        var nowMs = 0L
        // A fresh service per "app start": in-memory state is wiped, only the
        // cache file survives.
        val newService = {
            HistoricalPriceService(
                fetcher,
                repo,
                retryCount = 0,
                now = { "now" },
                failureBackoffMs = 60_000L,
                exhaustionTtlMs = 3_600_000L,
                clockMs = { nowMs },
            )
        }
        val from = LocalDate.of(2026, 1, 2)
        val to = LocalDate.of(2026, 1, 31)

        // Start 1: the fetch fails; the failure is recorded, but no window
        // yet, so the next start still retries once.
        val first = newService().fetchAndCache(data, from, to)
        assertTrue(first.logs.any { it.contains("history failed: 513500") })
        assertEquals(2, fetcher.requestCount)

        // Start 2 after the in-memory window: it retries, fails again, and the
        // repeated failure arms a persisted window (3h in this test) that
        // survives further restarts.
        nowMs += 60_000L
        val s2 = newService()
        assertTrue(s2.needsNetwork(data, from, to))
        val second = s2.fetchAndCache(data, from, to)
        assertTrue(second.logs.any { it.contains("history failed: 513500") })
        assertEquals(4, fetcher.requestCount)
        assertEquals(60_000L + 3_600_000L, second.cache.quotes["etf"]?.exhaustedUntilMs?.toLong())

        // Start 3 inside the window: no request, and no "fetching" status.
        val s3 = newService()
        assertFalse(s3.needsNetwork(data, from, to))
        val third = s3.fetchAndCache(data, from, to)
        assertTrue(third.logs.any { it.contains("history backoff (failed source): 513500") })
        assertEquals(4, fetcher.requestCount)

        // After the window elapses: one more probe, still failing, and the
        // window escalates (3h -> 6h in this test).
        nowMs += 3_600_000L
        val s4 = newService()
        assertTrue(s4.needsNetwork(data, from, to))
        val fourth = s4.fetchAndCache(data, from, to)
        assertTrue(fourth.logs.any { it.contains("history failed: 513500") })
        assertEquals(6, fetcher.requestCount)
        assertEquals(3_660_000L + 7_200_000L, fourth.cache.quotes["etf"]?.exhaustedUntilMs?.toLong())
        val s5 = newService()
        assertFalse(s5.needsNetwork(data, from, to))

        // The source recovers: the fetch succeeds, covers the range, and the
        // failure records are cleared, so later starts behave normally.
        respond = true
        nowMs += 7_200_000L
        val s6 = newService()
        val fifth = s6.fetchAndCache(data, from, to)
        assertTrue(fifth.logs.any { it.contains("history ok: 513500") })
        assertEquals(7, fetcher.requestCount)
        assertFalse(s6.needsNetwork(data, from, to))
        val s7 = newService()
        val sixth = s7.fetchAndCache(data, from, to)
        assertTrue(sixth.logs.any { it.contains("history cached: 513500") })
        assertNull(sixth.cache.quotes["etf"]?.exhaustedUntilMs)
        assertNull(sixth.cache.quotes["etf"]?.lastFailedAtMs)
        assertEquals(7, fetcher.requestCount)

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
