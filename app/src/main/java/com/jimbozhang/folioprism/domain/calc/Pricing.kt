package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.goldPremium
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import java.math.BigDecimal
import java.time.LocalDate

interface PriceProvider {
    fun price(instrumentId: String, date: LocalDate): BigDecimal?

    /** Current FX rate only. Historical FX is intentionally not supported. */
    fun fxRate(currency: String): BigDecimal?
}

/**
 * Simple provider used by tests and previews. It ignores dates; production code
 * will provide date-aware implementations backed by live quotes / the price cache.
 */
class MapPriceProvider(
    private val prices: Map<String, BigDecimal> = emptyMap(),
    private val fxRates: Map<String, BigDecimal> = emptyMap(),
) : PriceProvider {
    override fun price(instrumentId: String, date: LocalDate): BigDecimal? =
        prices[instrumentId]

    override fun fxRate(currency: String): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }
}

object Valuation {

    fun isGold(instrument: Instrument): Boolean =
        instrument.code.equals("AU9999", ignoreCase = true)

    fun fxToCny(
        currency: String,
        provider: PriceProvider,
    ): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> provider.fxRate(currency.uppercase())
        }

    /**
     * Returns the position value in CNY, or null when a required price/FX is missing.
     * The value is a CNY amount and is rounded to the fen (2 decimals); quantity and
     * native-currency amounts stay exact at their own precision.
     */
    fun valuePosition(
        data: PortfolioData,
        key: PositionKey,
        quantity: BigDecimal,
        provider: PriceProvider,
        date: LocalDate,
    ): BigDecimal? {
        val instrument = data.instrument(key.instrumentId) ?: return null
        val fx = fxToCny(instrument.currency, provider) ?: return null

        val raw = when (instrument.kind) {
            InstrumentKind.CASH -> quantity.multiply(fx)

            InstrumentKind.QUOTED -> {
                val price = provider.price(instrument.id, date) ?: return null
                val effectivePrice =
                    if (isGold(instrument)) price.add(data.goldPremium(key.institutionId))
                    else price
                quantity.multiply(effectivePrice).multiply(fx)
            }
        }
        return raw.toCnyAmount()
    }
}
