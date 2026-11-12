package com.jimbozhang.folioprism.data.price

import com.jimbozhang.folioprism.domain.calc.PriceProvider
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.time.LocalDate

class LivePriceProvider(
    private val quotes: QuoteSnapshot,
) : PriceProvider {
    override fun price(instrumentId: String, date: LocalDate): BigDecimal? =
        quotes.quotes[instrumentId]?.price

    override fun fxRate(currency: String, date: LocalDate): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> quotes.fxRates[currency.uppercase()]
        }
}
