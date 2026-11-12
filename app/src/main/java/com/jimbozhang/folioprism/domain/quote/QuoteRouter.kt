package com.jimbozhang.folioprism.domain.quote

import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.QuoteMode

sealed interface QuoteRoute {
    data object None : QuoteRoute
    data object Manual : QuoteRoute
    data object FundBatch : QuoteRoute
    data object TencentEtf : QuoteRoute
    data object TencentUs : QuoteRoute
    data object GoldSge : QuoteRoute
    data object GoldXau : QuoteRoute
    data class Unknown(val source: String) : QuoteRoute
}

object QuoteRouter {

    private val sixDigits = Regex("^\\d{6}$")
    private val usTicker = Regex("^[A-Z]{2,5}$")
    private val onExchangeCode = Regex("^[15]\\d{5}$")

    fun route(instrument: Instrument, goldSource: String): QuoteRoute {
        if (instrument.kind == InstrumentKind.CASH) return QuoteRoute.None
        if (instrument.quote.mode == QuoteMode.MANUAL) return QuoteRoute.Manual

        val explicit = instrument.quote.source.trim().lowercase()
        when (explicit) {
            "", "auto" -> Unit
            "manual" -> return QuoteRoute.Manual
            "eastmoneyfund", "fund", "fundbatch" -> return QuoteRoute.FundBatch
            "tencentetf", "etf", "bond" -> return QuoteRoute.TencentEtf
            "tencentus", "us" -> return QuoteRoute.TencentUs
            "au9999", "sge" -> return QuoteRoute.GoldSge
            "xau" -> return QuoteRoute.GoldXau
            else -> return QuoteRoute.Unknown(explicit)
        }

        if (instrument.code.equals("AU9999", ignoreCase = true)) {
            return if (goldSource.equals("AU9999", ignoreCase = true) ||
                goldSource.equals("sge", ignoreCase = true)
            ) {
                QuoteRoute.GoldSge
            } else {
                QuoteRoute.GoldXau
            }
        }

        if (sixDigits.matches(instrument.code)) {
            return if (instrument.categoryId == CategoryIds.CN_BOND ||
                onExchangeCode.matches(instrument.code)
            ) {
                QuoteRoute.TencentEtf
            } else {
                QuoteRoute.FundBatch
            }
        }

        if (usTicker.matches(instrument.code)) {
            return QuoteRoute.TencentUs
        }

        return QuoteRoute.Manual
    }
}
