package com.jimbozhang.folioprism.data.price

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GOLD
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.GoldSettings
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.quote.QuoteFailureKind
import com.jimbozhang.folioprism.domain.calc.PositionKey
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteServiceTest {

    private val fxJson = """{"rates":{"CNY":7.0,"HKD":7.8}}"""
    private val fundJson =
        """{"Success":true,"Datas":[{"FCODE":"008163","SHORTNAME":"Fund","NAV":"1.0791","NEWPRICE":null,"PDATE":"2026-11-11"}]}"""
    private val etfJson =
        """{"data":{"sh513500":{"qt":{"sh513500":["1","ETF","513500","2.65","2.60","2.55"]}}}}"""
    private val usJson =
        """{"data":{"usVOO":{"qt":{"usVOO":["real","VOO","VOO.AM","698.86"]},"pandata":{"last":"700.50","tag":"after","time":"2026-11-12 16:00"}}}}"""
    private val goldSgeJson = """{"data":{"f43":87298,"f59":2,"f58":"黄金9999"}}"""
    private val goldXauJson = """{"price":2000}"""

    private class FakeFetcher(
        private val handler: (String) -> String?,
    ) : HttpFetcher {
        val requests = mutableListOf<String>()

        override suspend fun get(url: String): String {
            requests += url
            return handler(url) ?: error("no fake response for $url")
        }
    }

    private fun allData(goldSource: String = "AU9999"): Pair<PortfolioData, Map<PositionKey, BigDecimal>> {
        val instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            cashInstrument(CASH_USD, "USD"),
            cashInstrument("ins_cash_hkd", "HKD"),
            quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163"),
            quotedInstrument(US_EQ, CategoryIds.US_EQUITY, currency = "USD", code = "VOO"),
            quotedInstrument("etf", CategoryIds.US_EQUITY, code = "513500"),
            quotedInstrument(GOLD, CategoryIds.GOLD, code = "AU9999"),
        )
        val data = portfolio(instruments = instruments).let {
            it.copy(settings = it.settings.copy(gold = GoldSettings(defaultSource = goldSource)))
        }
        val positions = mapOf(
            PositionKey(INST_A, CASH_CNY) to bd("1000"),
            PositionKey(INST_A, CASH_USD) to bd("100"),
            PositionKey(INST_A, "ins_cash_hkd") to bd("100"),
            PositionKey(INST_A, FUND) to bd("10"),
            PositionKey(INST_A, US_EQ) to bd("2"),
            PositionKey(INST_A, "etf") to bd("5"),
            PositionKey(INST_A, GOLD) to bd("3"),
        )
        return data to positions
    }

    private fun fetcherFor(
        fx: String = fxJson,
        fund: String = fundJson,
        etf: String = etfJson,
        us: String = usJson,
        goldSge: String = goldSgeJson,
        goldXau: String = goldXauJson,
    ): FakeFetcher = FakeFetcher { url ->
        when {
            url.contains("open.er-api.com") -> fx
            url.contains("fundmobapi.eastmoney.com") -> fund
            url.contains("param=sh513500") -> etf
            url.contains("param=usVOO") -> us
            url.contains("secid=118.AU9999") -> goldSge
            url.contains("gold-api.com/price/XAU") -> goldXau
            else -> null
        }
    }

    @Test
    fun fetchesAllPrimarySources() = runBlocking {
        val (data, positions) = allData()
        val fetcher = fetcherFor()
        val snapshot = QuoteService(fetcher, now = { "now" }).fetch(data, positions)

        assertTrue(snapshot.failures.isEmpty())
        assertDecimal("1.0791", snapshot.quotes[FUND]?.price)
        assertDecimal("2.65", snapshot.quotes["etf"]?.price)
        assertDecimal("700.50", snapshot.quotes[US_EQ]?.price)
        assertDecimal("872.98", snapshot.quotes[GOLD]?.price)
        assertDecimal("7.0", snapshot.fxRates["USD"])
        assertDecimal("0.89743590", snapshot.fxRates["HKD"])
    }

    @Test
    fun fundBatchMissingCodeProducesFailure() = runBlocking {
        val (data, positions) = allData()
        val emptyFund = """{"Success":true,"Datas":[]}"""
        val snapshot = QuoteService(fetcherFor(fund = emptyFund), now = { "now" }).fetch(data, positions)

        assertEquals(1, snapshot.failures.size)
        assertEquals(QuoteFailureKind.PRICE, snapshot.failures.single().kind)
        assertEquals(FUND, snapshot.failures.single().instrumentId)
    }

    @Test
    fun fundBatchSuccessFalseProducesFailure() = runBlocking {
        val (data, positions) = allData()
        val badFund = """{"Success":false}"""
        val snapshot = QuoteService(fetcherFor(fund = badFund), now = { "now" }).fetch(data, positions)

        assertTrue(snapshot.failures.any { it.instrumentId == FUND && it.kind == QuoteFailureKind.PRICE })
    }

    @Test
    fun fxFailureIsReported() = runBlocking {
        val data = portfolio(instruments = listOf(cashInstrument(CASH_USD, "USD")))
        val positions = mapOf(PositionKey(INST_A, CASH_USD) to bd("100"))
        val fetcher = FakeFetcher { error("network down") }
        val snapshot = QuoteService(fetcher, now = { "now" }).fetch(data, positions)

        assertEquals(1, snapshot.failures.size)
        assertEquals(QuoteFailureKind.FX, snapshot.failures.single().kind)
        assertTrue(snapshot.failureMessage().contains("USD"))
    }

    @Test
    fun xauUsesUsdCnyConversion() = runBlocking {
        val (data, positions) = allData(goldSource = "XAU")
        val snapshot = QuoteService(fetcherFor(), now = { "now" }).fetch(data, positions)

        assertTrue(snapshot.failures.isEmpty())
        val expected = bd("2000")
            .divide(bd("31.1035"), 10, RoundingMode.HALF_UP)
            .multiply(bd("7.0"))
        assertDecimal(expected.toPlainString(), snapshot.quotes[GOLD]?.price)
    }

    @Test
    fun manualQuoteIsUsedWithoutNetwork() = runBlocking {
        val manualInstrument = quotedInstrument("manual", CategoryIds.GROWTH, code = "IPFD2333").copy(
            quote = QuoteConfig(mode = QuoteMode.MANUAL, manualPrice = bd("97.82")),
        )
        val data = portfolio(instruments = listOf(manualInstrument))
        val positions = mapOf(PositionKey(INST_A, "manual") to bd("1"))
        val fetcher = FakeFetcher { error("should not request $it") }
        val snapshot = QuoteService(fetcher, now = { "now" }).fetch(data, positions)

        assertTrue(snapshot.failures.isEmpty())
        assertDecimal("97.82", snapshot.quotes["manual"]?.price)
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun missingManualPriceProducesFailure() = runBlocking {
        val manualInstrument = quotedInstrument("manual", CategoryIds.GROWTH, code = "IPFD2333").copy(
            quote = QuoteConfig(mode = QuoteMode.MANUAL, manualPrice = null),
        )
        val data = portfolio(instruments = listOf(manualInstrument))
        val positions = mapOf(PositionKey(INST_A, "manual") to bd("1"))
        val snapshot = QuoteService(FakeFetcher { null }, now = { "now" }).fetch(data, positions)

        assertEquals(QuoteFailureKind.MANUAL_PRICE_MISSING, snapshot.failures.single().kind)
    }

    @Test
    fun zeroQuantityNeedsNoQuote() = runBlocking {
        val (data, _) = allData()
        val fetcher = FakeFetcher { error("should not request $it") }
        val snapshot = QuoteService(fetcher, now = { "now" }).fetch(
            data,
            mapOf(PositionKey(INST_A, FUND) to BigDecimal.ZERO),
        )

        assertTrue(snapshot.failures.isEmpty())
        assertTrue(snapshot.quotes.isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun sameInstrumentInTwoInstitutionsFetchesOnce() = runBlocking {
        val data = portfolio(
            instruments = listOf(quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163")),
        )
        val positions = mapOf(
            PositionKey(INST_A, FUND) to bd("10"),
            PositionKey(INST_B, FUND) to bd("20"),
        )
        val fetcher = fetcherFor()
        val snapshot = QuoteService(fetcher, now = { "now" }).fetch(data, positions)

        assertTrue(snapshot.failures.isEmpty())
        assertDecimal("1.0791", snapshot.quotes[FUND]?.price)
        assertEquals(1, fetcher.requests.count { it.contains("fundmobapi") })
    }

    @Test
    fun failureCarriesAllHoldingInstitutions() = runBlocking {
        val data = portfolio(
            instruments = listOf(quotedInstrument(FUND, CategoryIds.GROWTH, code = "008163")),
        )
        val positions = mapOf(
            PositionKey(INST_A, FUND) to bd("10"),
            PositionKey(INST_B, FUND) to bd("20"),
        )
        val snapshot = QuoteService(
            fetcherFor(fund = """{"Success":false}"""),
            now = { "now" },
        ).fetch(data, positions)

        val failure = snapshot.failures.single()
        assertTrue(failure.institutionIds.containsAll(listOf(INST_A, INST_B)))
    }
}

private fun com.jimbozhang.folioprism.domain.quote.QuoteSnapshot.failureMessage(): String =
    failures.joinToString { it.instrumentName ?: it.message }
