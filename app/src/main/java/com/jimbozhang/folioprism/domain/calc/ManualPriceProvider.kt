package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Temporary provider used until live quotes are wired in.
 * Only instruments with a configured manual price can be valued.
 */
class ManualPriceProvider(
    private val data: PortfolioData,
    private val fxRates: Map<String, BigDecimal> = emptyMap(),
) : PriceProvider {
    override fun price(instrumentId: String, date: LocalDate): BigDecimal? =
        data.instrument(instrumentId)?.quote?.manualPrice

    override fun fxRate(currency: String, date: LocalDate): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }
}
