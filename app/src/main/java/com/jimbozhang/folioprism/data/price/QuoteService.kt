package com.jimbozhang.folioprism.data.price

import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.Quote
import com.jimbozhang.folioprism.domain.quote.QuoteFailure
import com.jimbozhang.folioprism.domain.quote.QuoteFailureKind
import com.jimbozhang.folioprism.domain.quote.QuoteRoute
import com.jimbozhang.folioprism.domain.quote.QuoteRouter
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class QuoteService(
    private val fetcher: HttpFetcher,
    private val retryCount: Int = 1,
    private val now: () -> String = { OffsetDateTime.now().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
    ): QuoteSnapshot = coroutineScope {
        val logs = mutableListOf<String>()
        val failures = mutableListOf<QuoteFailure>()
        val quotes = linkedMapOf<String, Quote>()
        val fxRates = linkedMapOf<String, BigDecimal>()

        val required = requiredInstruments(data, positions, logs)
        if (required.isEmpty()) {
            return@coroutineScope QuoteSnapshot(logs = logs)
        }

        val goldSource = data.settings.gold.defaultSource
        val routes = required.associate { it.instrument.id to QuoteRouter.route(it.instrument, goldSource) }

        val fxCurrencies = sortedSetOf<String>()
        required.forEach { req ->
            val currency = req.instrument.currency.uppercase()
            if (currency !in setOf("CNY", "CNH") &&
                (req.instrument.kind == InstrumentKind.CASH || req.instrument.kind == InstrumentKind.QUOTED)
            ) {
                fxCurrencies += currency
            }
        }
        if (required.any {
                it.instrument.code.equals("AU9999", ignoreCase = true) &&
                    routes[it.instrument.id] == QuoteRoute.GoldXau
            }
        ) {
            fxCurrencies += "USD"
        }

        if (fxCurrencies.isNotEmpty()) {
            try {
                val rates = fetchFx()
                fxCurrencies.forEach { currency ->
                    val rate = rates[currency]
                    if (rate == null) {
                        failures += fxFailure(currency, "未返回该币种汇率", now())
                        logs += "fx missing: $currency"
                    } else {
                        fxRates[currency] = rate
                    }
                }
                if (failures.none { it.kind == QuoteFailureKind.FX }) {
                    logs += "fx ok: ${fxCurrencies.joinToString(",")}"
                }
            } catch (t: Throwable) {
                fxCurrencies.forEach { currency ->
                    failures += fxFailure(currency, t.message ?: "汇率请求失败", now())
                }
                logs += "fx failed: ${t.message}"
            }
        }

        // Manual prices.
        required.filter { routes[it.instrument.id] == QuoteRoute.Manual }.forEach { req ->
            val price = req.instrument.quote.manualPrice
            if (price == null || price.signum() <= 0) {
                failures += failure(
                    req,
                    QuoteFailureKind.MANUAL_PRICE_MISSING,
                    "manual",
                    "未配置手工价格",
                    now(),
                )
                logs += "manual missing: ${req.instrument.code}"
            } else {
                quotes[req.instrument.id] = Quote(
                    instrumentId = req.instrument.id,
                    price = price,
                    currency = req.instrument.currency,
                    name = req.instrument.name,
                    updateTime = "",
                    source = "manual",
                )
                logs += "manual ok: ${req.instrument.code}"
            }
        }

        // Fund batch: one request for all funds.
        val funds = required.filter { routes[it.instrument.id] == QuoteRoute.FundBatch }
        if (funds.isNotEmpty()) {
            try {
                val byCode = fetchFundBatch(funds.map { it.instrument.code }.distinct())
                funds.forEach { req ->
                    val item = byCode[req.instrument.code]
                    if (item == null) {
                        failures += failure(
                            req,
                            QuoteFailureKind.PRICE,
                            "eastmoney fundmobapi",
                            "批量接口未返回该基金价格",
                            now(),
                        )
                        logs += "fund missing: ${req.instrument.code}"
                    } else {
                        quotes[req.instrument.id] = Quote(
                            instrumentId = req.instrument.id,
                            price = item.price,
                            currency = "CNY",
                            name = item.name,
                            updateTime = item.updateTime,
                            source = "eastmoney fundmobapi (batch)",
                        )
                        logs += "fund ok: ${req.instrument.code}"
                    }
                }
            } catch (t: Throwable) {
                funds.forEach {
                    failures += failure(
                        it,
                        QuoteFailureKind.PRICE,
                        "eastmoney fundmobapi",
                        t.message ?: "基金批量请求失败",
                        now(),
                    )
                }
                logs += "fund batch failed: ${t.message}"
            }
        }

        // ETF / US / unknown sources: parallel per-instrument requests.
        val individual = required.filter {
            routes[it.instrument.id] == QuoteRoute.TencentEtf ||
                routes[it.instrument.id] == QuoteRoute.TencentUs ||
                routes[it.instrument.id] is QuoteRoute.Unknown
        }
        individual
            .map { req ->
                async {
                    runCatching {
                        when (val route = routes[req.instrument.id]) {
                            QuoteRoute.TencentEtf -> fetchEtf(req.instrument)
                            QuoteRoute.TencentUs -> fetchUs(req.instrument)
                            is QuoteRoute.Unknown -> error("未知行情源: ${route.source}")
                            else -> error("unexpected route")
                        }
                    } to req
                }
            }
            .awaitAll()
            .forEach { (result, req) ->
                result.onSuccess { quote ->
                    quotes[req.instrument.id] = quote
                    logs += "quote ok: ${req.instrument.code} (${quote.source})"
                }.onFailure { t ->
                    val route = routes[req.instrument.id]
                    failures += failure(
                        req,
                        QuoteFailureKind.PRICE,
                        route?.let(::sourceLabel) ?: "unknown",
                        t.message ?: "行情请求失败",
                        now(),
                    )
                    logs += "quote failed: ${req.instrument.code}: ${t.message}"
                }
            }

        // Gold sources: one request per source, applied to all gold instruments.
        fetchGoldQuotes(
            required = required,
            routes = routes,
            fxRates = fxRates,
            quotes = quotes,
            failures = failures,
            logs = logs,
        )

        QuoteSnapshot(
            quotes = quotes,
            fxRates = fxRates,
            failures = failures,
            logs = logs,
        )
    }

    private suspend fun fetchGoldQuotes(
        required: List<RequiredInstrument>,
        routes: Map<String, QuoteRoute>,
        fxRates: Map<String, BigDecimal>,
        quotes: MutableMap<String, Quote>,
        failures: MutableList<QuoteFailure>,
        logs: MutableList<String>,
    ) {
        val sge = required.filter { routes[it.instrument.id] == QuoteRoute.GoldSge }
        if (sge.isNotEmpty()) {
            try {
                val quote = fetchGoldSge()
                sge.forEach { req ->
                    quotes[req.instrument.id] = quote.copy(instrumentId = req.instrument.id)
                }
                logs += "gold ok: SGE"
            } catch (t: Throwable) {
                sge.forEach {
                    failures += failure(
                        it,
                        QuoteFailureKind.GOLD,
                        "eastmoney AU9999",
                        t.message ?: "黄金价格请求失败",
                        now(),
                    )
                }
                logs += "gold failed: SGE: ${t.message}"
            }
        }

        val xau = required.filter { routes[it.instrument.id] == QuoteRoute.GoldXau }
        if (xau.isNotEmpty()) {
            val usdCny = fxRates["USD"]
            if (usdCny == null) {
                xau.forEach {
                    failures += failure(
                        it,
                        QuoteFailureKind.GOLD,
                        "gold-api XAU",
                        "缺少 USD/CNY 汇率",
                        now(),
                    )
                }
                logs += "gold failed: XAU: missing USD/CNY"
            } else {
                try {
                    val quote = fetchGoldXau(usdCny)
                    xau.forEach { req ->
                        quotes[req.instrument.id] = quote.copy(instrumentId = req.instrument.id)
                    }
                    logs += "gold ok: XAU"
                } catch (t: Throwable) {
                    xau.forEach {
                        failures += failure(
                            it,
                            QuoteFailureKind.GOLD,
                            "gold-api XAU",
                            t.message ?: "XAU 价格请求失败",
                            now(),
                        )
                    }
                    logs += "gold failed: XAU: ${t.message}"
                }
            }
        }
    }

    private fun requiredInstruments(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
        logs: MutableList<String>,
    ): List<RequiredInstrument> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        positions.forEach { (key, quantity) ->
            if (quantity.signum() == 0) return@forEach
            val instrument = data.instrument(key.instrumentId)
            if (instrument == null) {
                logs += "missing instrument: ${key.instrumentId}"
                return@forEach
            }
            grouped.getOrPut(instrument.id) { mutableListOf() }.add(key.institutionId)
        }
        return grouped.map { (instrumentId, institutionIds) ->
            RequiredInstrument(
                instrument = data.instrument(instrumentId)!!,
                institutionIds = institutionIds.distinct(),
            )
        }
    }

    private suspend fun fetchFx(): Map<String, BigDecimal> {
        val text = getWithRetry("https://open.er-api.com/v6/latest/USD")
        val root = parseObject(text)
        val rates = root.obj("rates") ?: error("rates missing")
        val cny = rates.decimal("CNY") ?: error("CNY rate missing")
        val hkd = rates.decimal("HKD") ?: error("HKD rate missing")
        val hkdCny = cny.divide(hkd, 8, RoundingMode.HALF_UP)
        return mapOf("USD" to cny, "HKD" to hkdCny)
    }

    private suspend fun fetchFundBatch(codes: List<String>): Map<String, FundQuote> {
        val url = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo" +
            "?pageIndex=1&pageSize=${codes.size}&plat=Android&appType=ttjj" +
            "&product=EFund&Version=6.3.3&deviceid=0&Fcodes=${codes.joinToString(",")}"
        val root = parseObject(getWithRetry(url))
        val success = (root["Success"] as? JsonPrimitive)?.booleanOrNull == true
        if (!success) error("Success=false")
        val datas = root.array("Datas") ?: error("Datas missing")
        val result = linkedMapOf<String, FundQuote>()
        datas.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val code = item.string("FCODE") ?: return@forEach
            val newPrice = item.decimal("NEWPRICE")
            val nav = item.decimal("NAV")
            val price = if (newPrice != null && newPrice.signum() > 0) newPrice else nav
            if (price != null && price.signum() > 0) {
                result[code] = FundQuote(
                    price = price,
                    name = item.string("SHORTNAME") ?: code,
                    updateTime = item.string("HQDATE") ?: item.string("PDATE") ?: "",
                )
            }
        }
        return result
    }

    private suspend fun fetchEtf(instrument: Instrument): Quote {
        val prefix = if (instrument.code.startsWith("1")) "sz" else "sh"
        val key = "$prefix${instrument.code}"
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$key,day,,,1,qfq"
        val root = parseObject(getWithRetry(url))
        val data = root.obj("data") ?: error("data missing")
        val item = data.obj(key) ?: error("$key missing")
        val qt = item.obj("qt") ?: error("qt missing")
        val arr = qt.array(key) ?: error("qt[$key] missing")
        val price = arr.getOrNull(3)?.stringValue()?.toBigDecimalOrNull()
            ?: error("price missing")
        if (price.signum() <= 0) error("price <= 0")
        return Quote(
            instrumentId = instrument.id,
            price = price,
            currency = "CNY",
            name = arr.getOrNull(1)?.stringValue() ?: instrument.code,
            updateTime = arr.getOrNull(30)?.stringValue() ?: "",
            source = "tencent $key",
        )
    }

    private suspend fun fetchUs(instrument: Instrument): Quote {
        val key = "us${instrument.code}"
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$key,day,,,1,qfq"
        val root = parseObject(getWithRetry(url))
        val data = root.obj("data") ?: error("data missing")
        val item = data.obj(key) ?: error("$key missing")
        val qt = item.obj("qt") ?: error("qt missing")
        val arr = qt.array(key) ?: error("qt[$key] missing")
        var price = arr.getOrNull(3)?.stringValue()?.toBigDecimalOrNull()
            ?: error("price missing")
        var updateTime = arr.getOrNull(30)?.stringValue() ?: ""
        val name = arr.getOrNull(1)?.stringValue() ?: instrument.code

        val pan = item.obj("pandata")
        val panPrice = pan?.decimal("last")
        if (panPrice != null && panPrice.signum() > 0) {
            price = panPrice
            val tag = pan.string("tag")
            updateTime = (pan.string("time") ?: "") +
                if (tag == "before") " (盘前)" else " (盘后)"
        }
        if (price.signum() <= 0) error("price <= 0")
        return Quote(
            instrumentId = instrument.id,
            price = price,
            currency = "USD",
            name = name,
            updateTime = updateTime,
            source = "tencent $key",
        )
    }

    private suspend fun fetchGoldSge(): Quote {
        val url = "https://push2.eastmoney.com/api/qt/stock/get" +
            "?secid=118.AU9999&fields=f43,f58,f59"
        val root = parseObject(getWithRetry(url))
        val data = root.obj("data") ?: error("data missing")
        val f43 = data.decimal("f43") ?: error("f43 missing")
        val f59 = data.int("f59") ?: 2
        val price = f43.movePointLeft(f59)
        if (price.signum() <= 0) error("price <= 0")
        return Quote(
            instrumentId = "",
            price = price,
            currency = "CNY",
            name = data.string("f58") ?: "AU9999",
            updateTime = "",
            source = "eastmoney AU9999",
        )
    }

    private suspend fun fetchGoldXau(usdCny: BigDecimal): Quote {
        val root = parseObject(getWithRetry("https://api.gold-api.com/price/XAU"))
        val usdPerOz = root.decimal("price") ?: error("price missing")
        val price = usdPerOz
            .divide(BigDecimal("31.1035"), 10, RoundingMode.HALF_UP)
            .multiply(usdCny)
        if (price.signum() <= 0) error("price <= 0")
        return Quote(
            instrumentId = "",
            price = price,
            currency = "CNY",
            name = "XAU",
            updateTime = "",
            source = "gold-api XAU",
        )
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

    private fun fxFailure(currency: String, message: String, timestamp: String) =
        QuoteFailure(
            instrumentId = null,
            instrumentCode = null,
            instrumentName = "$currency/CNY",
            institutionIds = emptyList(),
            kind = QuoteFailureKind.FX,
            source = "open.er-api.com",
            message = message,
            timestamp = timestamp,
        )

    private fun failure(
        req: RequiredInstrument,
        kind: QuoteFailureKind,
        source: String,
        message: String,
        timestamp: String,
    ) = QuoteFailure(
        instrumentId = req.instrument.id,
        instrumentCode = req.instrument.code,
        instrumentName = req.instrument.name,
        institutionIds = req.institutionIds,
        kind = kind,
        source = source,
        message = message,
        timestamp = timestamp,
    )

    private fun sourceLabel(route: QuoteRoute): String = when (route) {
        QuoteRoute.None -> "none"
        QuoteRoute.Manual -> "manual"
        QuoteRoute.FundBatch -> "eastmoney fundmobapi"
        QuoteRoute.TencentEtf -> "tencent etf"
        QuoteRoute.TencentUs -> "tencent us"
        QuoteRoute.GoldSge -> "eastmoney AU9999"
        QuoteRoute.GoldXau -> "gold-api XAU"
        is QuoteRoute.Unknown -> route.source
    }

    private data class RequiredInstrument(
        val instrument: Instrument,
        val institutionIds: List<String>,
    )

    private data class FundQuote(
        val price: BigDecimal,
        val name: String,
        val updateTime: String,
    )
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.decimal(key: String): BigDecimal? =
    string(key)?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()

private fun JsonObject.int(key: String): Int? =
    string(key)?.toIntOrNull()

private fun JsonElement.stringValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull
