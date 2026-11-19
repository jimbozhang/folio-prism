package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Input helpers for the transfer editor. Everything here only produces
 * suggested values: the user's own input always takes precedence.
 */
object TransferSuggestion {

    private const val DIVISION_SCALE = 16

    /**
     * Suggests the receive-side quantity by equal value: the out leg is
     * converted to CNY with the current quote FX rates, then into the receive
     * currency and quantity. Returns null when the inputs are not sufficient.
     */
    fun suggestToQuantity(
        data: PortfolioData,
        quotes: QuoteSnapshot,
        fromInstrumentId: String?,
        fromQuantity: BigDecimal?,
        fromPrice: BigDecimal?,
        toInstrumentId: String?,
        toPrice: BigDecimal?,
    ): BigDecimal? {
        val from = data.instrument(fromInstrumentId ?: return null) ?: return null
        val to = data.instrument(toInstrumentId ?: return null) ?: return null
        val quantity = fromQuantity ?: return null
        if (quantity.signum() <= 0) return null

        val fromNative = if (from.kind == InstrumentKind.CASH) {
            quantity
        } else {
            val price = fromPrice ?: return null
            if (price.signum() <= 0) return null
            quantity.multiply(price)
        }
        if (fromNative.signum() <= 0) return null

        // Moving the same instrument between institutions keeps the quantity.
        if (from.id == to.id) return quantity

        val fromFx = fxRate(from.currency, quotes) ?: return null
        val toFx = fxRate(to.currency, quotes) ?: return null
        val valueCny = fromNative.multiply(fromFx)
        val toNative = valueCny.divide(toFx, DIVISION_SCALE, RoundingMode.HALF_UP)
        val toQuantity = if (to.kind == InstrumentKind.CASH) {
            toNative
        } else {
            val price = toPrice ?: return null
            if (price.signum() <= 0) return null
            toNative.divide(price, DIVISION_SCALE, RoundingMode.HALF_UP)
        }
        return toQuantity.setScale(to.precision.quantityDecimals, RoundingMode.DOWN)
    }

    /** Current quote (or manual price) used to prefill a price field. */
    fun suggestPrice(quotes: QuoteSnapshot, instrument: Instrument?): String? =
        if (instrument?.kind == InstrumentKind.QUOTED) {
            (quotes.quotes[instrument.id]?.price ?: instrument.quote.manualPrice)
                ?.toPlainString()
        } else {
            null
        }

    /**
     * Precedence rule for suggested quantities: a suggestion fills a blank
     * field and keeps updating while the field still holds the previous
     * suggestion; once the user has typed anything else, their value wins.
     */
    fun applyQuantitySuggestion(
        current: String,
        lastSuggested: String?,
        suggested: BigDecimal?,
    ): QuantitySuggestion {
        val suggestedText = suggested?.toPlainString()
        return when {
            suggested == null -> {
                if (current.isBlank() || current == lastSuggested) {
                    QuantitySuggestion("", null)
                } else {
                    QuantitySuggestion(current, null)
                }
            }

            current.isBlank() || current == lastSuggested ->
                QuantitySuggestion(suggestedText!!, suggestedText)

            else -> QuantitySuggestion(current, lastSuggested)
        }
    }

    private fun fxRate(currency: String, quotes: QuoteSnapshot): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> quotes.fxRates[currency.uppercase()]
        }
}

data class QuantitySuggestion(
    val value: String,
    val lastSuggested: String?,
)
