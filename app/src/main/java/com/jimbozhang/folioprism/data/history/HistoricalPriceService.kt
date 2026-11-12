package com.jimbozhang.folioprism.data.history

import com.jimbozhang.folioprism.data.price.HttpFetcher
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.model.positionRefs
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
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
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val chinaZone = ZoneId.of("Asia/Shanghai")
    private var memoryCache: PriceCacheData? = null
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

        val instrumentIds = data.events
            .filter { !it.localDate().isAfter(to) }
            .flatMap { it.positionRefs() }
            .map { it.instrumentId }
            .toSet()
        val instruments = instrumentIds.mapNotNull { data.instrument(it) }

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

            val result = runCatching {
                when (source) {
                    HistorySource.FUND -> fetchFundHistory(instrument, fetchFrom, to)
                    HistorySource.TENCENT -> fetchTencentHistory(instrument, fetchFrom, to)
                    HistorySource.US_SINA -> fetchSinaUsHistory(instrument, fetchFrom, to)
                    HistorySource.GOLD_SINA -> fetchSinaGoldHistory(fetchFrom, to)
                    else -> error("unexpected history source: $source")
                }
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
                logs += "history ok: ${instrument.code} (${series.source})"
            }.onFailure {
                logs += "history failed: ${instrument.code}: ${it.message}"
            }
        }

        val currencies = instruments
            .map { it.currency.uppercase() }
            .filter { it !in setOf("CNY", "CNH") }
            .toSet()
        val missingCurrencies = currencies.filterNot {
            isCovered(cache.fx[it], from, to)
        }
        currencies.filterNot { it in missingCurrencies }.forEach {
            logs += "fx cached: $it"
        }
        if (missingCurrencies.isNotEmpty()) {
            runCatching { fetchFx(fetchFrom, to) }
                .onSuccess { fx ->
                    missingCurrencies.forEach { currency ->
                        val series = fx[currency]
                        if (series.isNullOrEmpty()) {
                            logs += "fx history missing: $currency"
                        } else {
                            cache = cacheRepository.mergeFx(
                                cache = cache,
                                currency = currency,
                                rates = series,
                                source = "frankfurter",
                                fetchedAt = now(),
                            )
                            cacheChanged = true
                            logs += "fx history ok: $currency"
                        }
                    }
                }
                .onFailure { logs += "fx history failed: ${it.message}" }
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
        series: com.jimbozhang.folioprism.domain.history.CachedSeries?,
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

    private suspend fun fetchFx(
        from: LocalDate,
        to: LocalDate,
    ): Map<String, Map<LocalDate, BigDecimal>> {
        val url = "https://api.frankfurter.app/$from..$to?from=USD&to=CNY,HKD"
        val root = parseObject(getWithRetry(url))
        val rates = root.obj("rates") ?: error("rates missing")
        val usd = linkedMapOf<LocalDate, BigDecimal>()
        val hkd = linkedMapOf<LocalDate, BigDecimal>()
        rates.forEach { (dateText, element) ->
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                ?: return@forEach
            if (date.isBefore(from) || date.isAfter(to)) return@forEach
            val obj = element as? JsonObject ?: return@forEach
            val cny = obj.decimal("CNY") ?: return@forEach
            usd[date] = cny
            val hkdRate = obj.decimal("HKD")
            if (hkdRate != null && hkdRate.signum() > 0) {
                hkd[date] = cny.divide(hkdRate, 8, RoundingMode.HALF_UP)
            }
        }
        return mapOf("USD" to usd, "HKD" to hkd)
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
