package com.jimbozhang.folioprism.domain.quote

import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteRouterTest {

    @Test
    fun cashIsNotQuoted() {
        val cash = cashInstrument("cash", "CNY")
        assertEquals(QuoteRoute.None, QuoteRouter.route(cash, "XAU"))
    }

    @Test
    fun sixDigitFundUsesFundBatch() {
        val fund = quotedInstrument("f", CategoryIds.GROWTH, code = "008163")
        assertEquals(QuoteRoute.FundBatch, QuoteRouter.route(fund, "XAU"))
    }

    @Test
    fun shanghaiEtfUsesTencentEtf() {
        val etf = quotedInstrument("e", CategoryIds.US_EQUITY, code = "513500")
        assertEquals(QuoteRoute.TencentEtf, QuoteRouter.route(etf, "XAU"))
    }

    @Test
    fun shenzhenEtfUsesTencentEtf() {
        val etf = quotedInstrument("e", CategoryIds.GROWTH, code = "159915")
        assertEquals(QuoteRoute.TencentEtf, QuoteRouter.route(etf, "XAU"))
    }

    @Test
    fun cnBondCategoryForcesTencentEtf() {
        val bond = quotedInstrument("b", CategoryIds.CN_BOND, code = "019838")
        assertEquals(QuoteRoute.TencentEtf, QuoteRouter.route(bond, "XAU"))
    }

    @Test
    fun usTickerUsesTencentUs() {
        val us = quotedInstrument("u", CategoryIds.US_EQUITY, currency = "USD", code = "VOO")
        assertEquals(QuoteRoute.TencentUs, QuoteRouter.route(us, "XAU"))
    }

    @Test
    fun goldHonorsConfiguredSource() {
        val gold = quotedInstrument("g", CategoryIds.GOLD, code = "AU9999")
        assertEquals(QuoteRoute.GoldXau, QuoteRouter.route(gold, "XAU"))
        assertEquals(QuoteRoute.GoldSge, QuoteRouter.route(gold, "AU9999"))
    }

    @Test
    fun manualModeWins() {
        val manual = quotedInstrument("m", CategoryIds.GROWTH, code = "IPFD2333").copy(
            quote = QuoteConfig(mode = QuoteMode.MANUAL),
        )
        assertEquals(QuoteRoute.Manual, QuoteRouter.route(manual, "XAU"))
    }

    @Test
    fun explicitSourceIsHonored() {
        val etf = quotedInstrument("e", CategoryIds.GROWTH, code = "008163").copy(
            quote = QuoteConfig(mode = QuoteMode.AUTO, source = "tencentEtf"),
        )
        assertEquals(QuoteRoute.TencentEtf, QuoteRouter.route(etf, "XAU"))
    }

    @Test
    fun unknownExplicitSourceIsReported() {
        val weird = quotedInstrument("w", CategoryIds.GROWTH, code = "008163").copy(
            quote = QuoteConfig(mode = QuoteMode.AUTO, source = "mystery"),
        )
        assertEquals(QuoteRoute.Unknown("mystery"), QuoteRouter.route(weird, "XAU"))
    }

    @Test
    fun unknownCodeFallsBackToManual() {
        val unknown = quotedInstrument("x", CategoryIds.GROWTH, code = "ABC123")
        assertEquals(QuoteRoute.Manual, QuoteRouter.route(unknown, "XAU"))
    }
}
