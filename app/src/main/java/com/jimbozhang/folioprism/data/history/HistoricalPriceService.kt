package com.jimbozhang.folioprism.data.history

import com.jimbozhang.folioprism.data.price.HttpFetcher
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.history.CachedSeries
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.model.positionRefs
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class HistoricalFetchResult(
    val cache: PriceCacheData,
    val logs: List<String>,
)

class HistoricalPriceService(
    private val fetcher: HttpFetcher,
    private val cacheRepository: PriceCacheRepository,
    private val retryCount: Int = 1,
    private val now: () -> String = { OffsetDateTime.now().toString() },
    // How long to refrain from retrying an instrument after a failed fetch, so
    // a failing/rate-limited source isn't hammered on every range change.
    private val failureBackoffMs: Long = 60_000L,
    // After a successful fetch that still cannot cover the range end (the
    // source has no data near the end, e.g. a delisted instrument), refrain
    // from re-fetching it for this long so it cannot drive a network request
    // and a "fetching" status on every load.
    private val exhaustionTtlMs: Long = 24 * 60 * 60 * 1000L,
    // Longest retry window for a source whose fetches keep failing: the
    // window doubles with each consecutive failure (starting from
    // exhaustionTtlMs), so a permanently failing source is probed at most
    // once every few weeks instead of on every app start.
    private val failureRetryCapMs: Long = 30 * 24 * 60 * 60 * 1000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val chinaZone = ZoneId.of("Asia/Shanghai")
    private var memoryCache: PriceCacheData? = null
    // InstrumentId -> wall-clock millis of its last failed fetch. Backs the
    // negative cache: a recently failed instrument is not retried within the
    // backoff window. Kept in memory only; a fresh session retries freely.
    private val lastFailedAt = linkedMapOf<String, Long>()
    private val sixDigits = Regex("^\\d{6}$")
    private val onExchange = Regex("^[15]\\d{5}$")
    private val usTicker = Regex("^[A-Z]{2,5}$")

    suspend fun fetchAndCache(
        data: PortfolioData,
        from: LocalDate,
        to: LocalDate,
    ): HistoricalFetchResult {
        val logs = mutableListOf<String>()
        var cache = memoryCache ?: cacheRepository.load().also { memoryCache = it }
        var cacheChanged = false
        val fetchFrom = from.minusDays(10)

        val instruments = instrumentsPresentIn(data, to)

        instruments.forEach { instrument ->
            val source = route(instrument)
            when (source) {
                HistorySource.CASH -> return@forEach
                HistorySource.MANUAL -> {
                    logs += "history skipped (manual): ${instrument.code}"
                    return@forEach
                }

                HistorySource.UNKNOWN -> {
                    logs += "history skipped (unknown source): ${instrument.code}"
                    return@forEach
                }

                else -> Unit
            }

            if (isCovered(cache.quotes[instrument.id], from, to)) {
                logs += "history cached: ${instrument.code}"
                return@forEach
            }

            if (inBackoff(instrument.id)) {
                logs += "history backoff: ${instrument.code}"
                return@forEach
            }

            if (isExhausted(cache, instrument.id)) {
                // Distinguish the two ways an instrument can be exhausted: a
                // source that keeps failing (lastFailedAtMs set) vs. a source
                // that answers but has no data near the range end.
                logs += if (cache.quotes[instrument.id]?.lastFailedAtMs != null) {
                    "history backoff (failed source): ${instrument.code}"
                } else {
                    "history exhausted: ${instrument.code}"
                }
                return@forEach
            }

            val result = try {
                Result.success(
                    when (source) {
                        HistorySource.FUND -> fetchFundHistory(instrument, fetchFrom, to)
                        HistorySource.TENCENT -> fetchTencentHistory(instrument, fetchFrom, to)
                        HistorySource.US_SINA -> fetchSinaUsHistory(instrument, fetchFrom, to)
                        HistorySource.GOLD_SINA -> fetchSinaGoldHistory(fetchFrom, to)
                        else -> error("unexpected history source: $source")
                    },
                )
            } catch (e: CancellationException) {
                // A superseded load was cancelled; stop fetching instead of
                // iterating the remaining instruments.
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.onSuccess { series ->
                cache = cacheRepository.mergeSeries(
                    cache = cache,
                    instrumentId = instrument.id,
                    prices = series.prices,
                    source = series.source,
                    fetchedAt = now(),
                )
                cacheChanged = true
                lastFailedAt.remove(instrument.id)
                logs += "history ok: ${instrument.code} (${series.source})"
                val merged = cache.quotes[instrument.id]
                if (merged != null) {
                    if (!isCovered(merged, from, to)) {
                        // The source answered but still has no data within the
                        // requested end: it is exhausted (delisted, retired,
                        // ...). Record that, so neither a network request nor a
                        // "fetching" status repeats for it on every load until
                        // the window elapses and one more probe is made.
                        cache = cache.copy(
                            quotes = cache.quotes + (
                                instrument.id to merged.copy(
                                    lastFailedAtMs = null,
                                    exhaustedUntilMs = (clockMs() + exhaustionTtlMs).toString(),
                                )
                            ),
                        )
                        logs += "history exhausted: ${instrument.code} (data ends ${maxCachedDate(merged)})"
                    } else if (merged.lastFailedAtMs != null || merged.exhaustedUntilMs != null) {
                        // The range is covered again: drop the records so they
                        // cannot suppress a later genuine refetch.
                        cache = cache.copy(
                            quotes = cache.quotes + (
                                instrument.id to merged.copy(
                                    lastFailedAtMs = null,
                                    exhaustedUntilMs = null,
                                )
                            ),
                        )
                    }
                }
            }.onFailure {
                // A real (non-cancellation) failure: back off retries for this
                // instrument so a failing source isn't hammered.
                lastFailedAt[instrument.id] = clockMs()
                logs += "history failed: ${instrument.code}: ${it.message}"
                // Also record the failure in the persisted cache: the in-memory
                // backoff is wiped on every restart, so without this a source
                // that always fails would drive a fetch — and a "fetching"
                // status — on every app start. While failures keep happening
                // the retry window is remembered across sessions and escalates
                // (doubling, capped at failureRetryCapMs), so a permanently
                // failing source is probed at most every few weeks.
                cache = cache.copy(
                    quotes = cache.quotes + (
                        instrument.id to newFailureRecord(
                            cache.quotes[instrument.id] ?: CachedSeries(),
                            clockMs(),
                        )
                    ),
                )
                cacheChanged = true
            }
        }

        if (cacheChanged) {
            cache = cacheRepository.save(cache)
            memoryCache = cache
        }
        return HistoricalFetchResult(cache, logs)
    }

    /**
     * A cached series is sufficient when it has a close within 30 days before
     * the requested start (the provider's look-back window) and a close within
     * 15 days of the requested end (covers weekends and long holidays).
     */
    private fun isCovered(
        series: CachedSeries?,
        from: LocalDate,
        to: LocalDate,
    ): Boolean {
        if (series == null) return false
        val dates = (series.auto.keys + series.manual.keys)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (dates.isEmpty()) return false
        val hasStart = dates.any { !it.isBefore(from.minusDays(30)) && !it.isAfter(from) }
        val hasEnd = dates.any { !it.isBefore(to.minusDays(15)) }
        return hasStart && hasEnd
    }

    /**
     * Instruments referenced by events on or before [to] that the portfolio
     * holds, in stable order.
     */
    private fun instrumentsPresentIn(data: PortfolioData, to: LocalDate): List<Instrument> {
        val instrumentIds = data.events
            .filter { !it.localDate().isAfter(to) }
            .flatMap { it.positionRefs() }
            .map { it.instrumentId }
            .toSet()
        return instrumentIds.mapNotNull { data.instrument(it) }
    }

    /**
     * True when [instrumentId] failed a recent fetch and is still inside the
     * backoff window, so it should not be retried yet.
     */
    private fun inBackoff(instrumentId: String): Boolean {
        val failedAt = lastFailedAt[instrumentId] ?: return false
        return clockMs() - failedAt < failureBackoffMs
    }

    /**
     * Whether a recent successful fetch proved that the source of
     * [instrumentId] has no data near the requested range end (e.g. a
     * delisted or retired instrument): until the recorded moment another
     * fetch would only return the same stale data, so it must neither be
     * issued nor announced as "fetching".
     */
    private fun isExhausted(cache: PriceCacheData, instrumentId: String): Boolean {
        val until = cache.quotes[instrumentId]?.exhaustedUntilMs?.toLongOrNull() ?: return false
        return clockMs() < until
    }

    /**
     * The persisted failure record after a failed fetch: [prev]'s
     * lastFailedAtMs is updated to now, and a REPEATED failure (a record
     * already existed) arms an escalating retry window in
     * [CachedSeries.exhaustedUntilMs] — doubling from exhaustionTtlMs on each
     * consecutive failure, capped at failureRetryCapMs. The first recorded
     * failure arms no window: the next start still retries once, so a
     * transient failure self-heals without a full day of quiet.
     */
    private fun newFailureRecord(prev: CachedSeries, nowMs: Long): CachedSeries {
        if (prev.lastFailedAtMs == null) {
            return prev.copy(lastFailedAtMs = nowMs.toString())
        }
        val prevWindowMs = prev.exhaustedUntilMs?.toLongOrNull()
            ?.minus(prev.lastFailedAtMs.toLongOrNull() ?: 0L)
            ?: 0L
        val nextWindowMs = minOf(failureRetryCapMs, maxOf(exhaustionTtlMs, prevWindowMs * 2))
        return prev.copy(
            lastFailedAtMs = nowMs.toString(),
            exhaustedUntilMs = (nowMs + nextWindowMs).toString(),
        )
    }

    private fun maxCachedDate(series: CachedSeries?): LocalDate? {
        if (series == null) return null
        return (series.auto.keys + series.manual.keys)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .maxOrNull()
    }

    /**
     * Whether loading [from, to] for [data] would issue any network request:
     * some held instrument is neither covered by the cache nor in backoff.
     * Used to pick a truthful status message (fetching vs. just computing).
     */
    suspend fun needsNetwork(
        data: PortfolioData,
        from: LocalDate,
        to: LocalDate,
    ): Boolean {
        val cache = memoryCache ?: cacheRepository.load().also { memoryCache = it }
        return instrumentsPresentIn(data, to).any { instrument ->
            route(instrument) !in setOf(
                HistorySource.CASH,
                HistorySource.MANUAL,
                HistorySource.UNKNOWN,
            ) &&
                !isCovered(cache.quotes[instrument.id], from, to) &&
                !inBackoff(instrument.id) &&
                !isExhausted(cache, instrument.id)
        }
    }

    private fun route(instrument: Instrument): HistorySource = when {
        instrument.kind == InstrumentKind.CASH -> HistorySource.CASH
        instrument.quote.mode == QuoteMode.MANUAL || instrument.quote.source == "manual" ->
            HistorySource.MANUAL
        instrument.code.equals("AU9999", ignoreCase = true) -> HistorySource.GOLD_SINA
        sixDigits.matches(instrument.code) &&
            (instrument.categoryId == CategoryIds.CN_BOND ||
                onExchange.matches(instrument.code)) -> HistorySource.TENCENT
        sixDigits.matches(instrument.code) -> HistorySource.FUND
        usTicker.matches(instrument.code) -> HistorySource.US_SINA
        else -> HistorySource.UNKNOWN
    }

    private suspend fun fetchFundHistory(
        instrument: Instrument,
        from: LocalDate,
        to: LocalDate,
    ): HistoricalSeries {
        val text = getWithRetry(
            "https://fund.eastmoney.com/pingzhongdata/${instrument.code}.js",
        )
        val marker = "Data_netWorthTrend = "
        val markerIndex = text.indexOf(marker)
        if (markerIndex < 0) error("Data_netWorthTrend not found")
        val start = text.indexOf('[', markerIndex)
        val end = text.indexOf(']', start)
        if (start < 0 || end < 0) error("Data_netWorthTrend array not found")
        val array = json.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray
            ?: error("Data_netWorthTrend is not an array")
        val prices = linkedMapOf<LocalDate, BigDecimal>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val timestamp = (obj["x"] as? JsonPrimitive)?.longOrNull ?: return@forEach
            val nav = ((obj["y"] as? JsonPrimitive)?.contentOrNull)?.toBigDecimalOrNull()
                ?: return@forEach
            val date = Instant.ofEpochMilli(timestamp).atZone(chinaZone).toLocalDate()
            if (!date.isBefore(from) && !date.isAfter(to)) prices[date] = nav
        }
        if (prices.isEmpty()) error("no fund NAV in range")
        return HistoricalSeries(prices, "eastmoney fund history")
    }

    private suspend fun fetchTencentHistory(
        instrument: Instrument,
        from: LocalDate,
        to: LocalDate,
    ): HistoricalSeries {
        val prefix = if (instrument.code.startsWith("1")) "sz" else "sh"
        val key = "$prefix${instrument.code}"
        val urls = listOf(
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get" +
                "?param=$key,day,$from,$to,1000,qfq",
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get" +
                "?param=$key,day,,,1000,qfq",
        )
        var last: Throwable? = null
        urls.forEach { url ->
            try {
                val root = parseObject(getWithRetry(url))
                val data = root.obj("data")?.obj(key) ?: error("$key missing")
                val rows = (data["qfqday"] as? JsonArray) ?: (data["day"] as? JsonArray)
                    ?: error("kline array missing")
                val prices = linkedMapOf<LocalDate, BigDecimal>()
                rows.forEach { row ->
                    val arr = row as? JsonArray ?: return@forEach
                    val dateText = arr.getOrNull(0)?.stringValue() ?: return@forEach
                    val close = arr.getOrNull(2)?.stringValue()?.toBigDecimalOrNull()
                        ?: return@forEach
                    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                        ?: return@forEach
                    if (!date.isBefore(from) && !date.isAfter(to)) prices[date] = close
                }
                if (prices.isNotEmpty()) {
                    return HistoricalSeries(prices, "tencent kline ($key)")
                }
                last = IllegalStateException("no kline data in range")
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("no kline data")
    }

    private suspend fun fetchSinaUsHistory(
        instrument: Instrument,
        from: LocalDate,
        to: LocalDate,
    ): HistoricalSeries {
        val url = "https://stock.finance.sina.com.cn/usstock/api/jsonp.php/" +
            "var%20x=/US_MinKService.getDailyK?symbol=${instrument.code}&___qn=3"
        val prices = parseSinaSeries(getWithRetry(url), from, to)
        if (prices.isEmpty()) error("no Sina US history in range")
        return HistoricalSeries(prices, "sina US daily (${instrument.code})")
    }

    /**
     * Historical gold uses Sina's Shanghai gold futures continuous contract AU0
     * as a proxy for AU9999 spot; XAU history is not available without a key.
     */
    private suspend fun fetchSinaGoldHistory(
        from: LocalDate,
        to: LocalDate,
    ): HistoricalSeries {
        val url = "https://stock.finance.sina.com.cn/futures/api/jsonp.php/" +
            "var%20_AU0=/InnerFuturesNewService.getDailyKLine?symbol=AU0"
        val prices = parseSinaSeries(getWithRetry(url), from, to)
        if (prices.isEmpty()) error("no Sina AU0 history in range")
        return HistoricalSeries(prices, "sina AU0 futures (proxy for AU9999)")
    }

    private fun parseSinaSeries(
        text: String,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) error("Sina array not found")
        val array = json.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray
            ?: error("Sina payload is not an array")
        val prices = linkedMapOf<LocalDate, BigDecimal>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val dateText = obj.string("d") ?: return@forEach
            val close = obj.decimal("c") ?: return@forEach
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                ?: return@forEach
            if (!date.isBefore(from) && !date.isAfter(to)) prices[date] = close
        }
        return prices
    }

    private suspend fun getWithRetry(url: String): String {
        var last: Throwable? = null
        repeat(retryCount + 1) { attempt ->
            try {
                return fetcher.get(url)
            } catch (t: Throwable) {
                last = t
                if (attempt < retryCount) delay(500)
            }
        }
        throw last ?: IllegalStateException("request failed: $url")
    }

    private fun parseObject(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { error("JSON parse failed: ${it.message}") }

    private enum class HistorySource {
        CASH,
        MANUAL,
        FUND,
        TENCENT,
        US_SINA,
        GOLD_SINA,
        UNKNOWN,
    }

    private data class HistoricalSeries(
        val prices: Map<LocalDate, BigDecimal>,
        val source: String,
    )
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.decimal(key: String): BigDecimal? =
    ((this[key] as? JsonPrimitive)?.contentOrNull)?.toBigDecimalOrNull()
private fun kotlinx.serialization.json.JsonElement.stringValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull
