package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.categoryOf
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

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

    /**
     * Suggests how much of the source instrument to transfer OUT so that, after
     * the transfer, the source category sits on its target share. The out leg's
     * CNY value is D and the receive leg receives the same D.
     *
     * The source category's denominator reflects this transfer's effect on the
     * dividend/portfolio split: a non-dividend source is measured against the
     * portfolio base, which shrinks by D when the destination is a dividend
     * category, so D solves (V0 - D) / (base - D) = p. In every other case the
     * base is unchanged and D = V0 - p * base.
     *
     * Only helps when the source is currently above target; when it is at or
     * below target the answer is zero. Returns null when the inputs or prices
     * are insufficient to value the portfolio.
     */
    fun suggestRebalanceOutQuantity(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
        provider: PriceProvider,
        date: LocalDate,
        fromInstitutionId: String?,
        fromInstrumentId: String?,
        toInstrumentId: String?,
    ): BigDecimal? {
        val fromInstrument = data.instrument(fromInstrumentId ?: return null) ?: return null
        val fromCategory = data.categoryOf(fromInstrument.id) ?: return null
        val toCategory = toInstrumentId?.let { data.categoryOf(it) }

        // The source must be valued before the zero shortcut below: without a
        // price its category value is unknown, not zero.
        val fromInstitution = fromInstitutionId ?: return null
        val fromKey = PositionKey(fromInstitution, fromInstrument.id)
        val holding = positions[fromKey] ?: return null
        if (holding.signum() <= 0) return null
        val holdingValue = Valuation.valuePosition(data, fromKey, holding, provider, date)
            ?: return null
        if (holdingValue.signum() <= 0) return null

        val snapshot = SnapshotCalculator.build(data, positions, provider, date)
        val totalValue = snapshot.totalValueCny
        val portfolioValue = snapshot.portfolioValueCny
        val sourceValue = snapshot.categories
            .firstOrNull { it.category.id == fromCategory.id }
            ?.valueCny ?: BigDecimal.ZERO

        val p = SnapshotCalculator.fraction(fromCategory.targetPct)
        val sourceIsDividend = fromCategory.specialRule == "dividend"
        val toIsDividend = toCategory?.specialRule == "dividend"
        val base = if (sourceIsDividend) totalValue else portfolioValue
        if (base.signum() <= 0) return null

        val numerator = sourceValue.subtract(p.multiply(base))
        var delta = if (!sourceIsDividend && toIsDividend) {
            val oneMinusP = BigDecimal.ONE.subtract(p)
            if (oneMinusP.signum() <= 0) {
                numerator
            } else {
                numerator.divide(oneMinusP, DIVISION_SCALE, RoundingMode.HALF_UP)
            }
        } else {
            numerator
        }
        if (delta.signum() <= 0) return BigDecimal.ZERO
        if (delta > holdingValue) delta = holdingValue

        return delta.multiply(holding)
            .divide(holdingValue, DIVISION_SCALE, RoundingMode.HALF_UP)
            .setScale(fromInstrument.precision.quantityDecimals, RoundingMode.DOWN)
    }

    /**
     * Suggests a transfer INTO the destination position that brings the
     * destination category to its target share. Returns both sides of the
     * trade: the source quantity to send and the receive quantity it buys.
     *
     * The value moving into a non-dividend destination grows the portfolio
     * base when the source is a dividend category, so that case solves
     * (Vc + D) / (P + D) = p. Every other case uses D = p * base - Vc. The
     * amount is capped at the source holding's value; the result is marked so
     * the UI can say the target cannot be reached with this holding.
     */
    fun suggestRebalanceIn(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
        provider: PriceProvider,
        date: LocalDate,
        fromInstitutionId: String?,
        fromInstrumentId: String?,
        toInstitutionId: String?,
        toInstrumentId: String?,
    ): RebalanceInSuggestion? {
        val fromInstrument = data.instrument(fromInstrumentId ?: return null) ?: return null
        val fromCategory = data.categoryOf(fromInstrument.id) ?: return null
        val toInstrument = data.instrument(toInstrumentId ?: return null) ?: return null
        val toCategory = data.categoryOf(toInstrument.id) ?: return null
        // Moving value inside one category does not change its share.
        if (fromCategory.id == toCategory.id) return null

        val fromInstitution = fromInstitutionId ?: return null
        val toInstitution = toInstitutionId ?: return null
        val fromKey = PositionKey(fromInstitution, fromInstrument.id)
        val holding = positions[fromKey] ?: return null
        if (holding.signum() <= 0) return null
        val holdingValue = Valuation.valuePosition(data, fromKey, holding, provider, date)
            ?: return null
        if (holdingValue.signum() <= 0) return null

        val snapshot = SnapshotCalculator.build(data, positions, provider, date)
        val totalValue = snapshot.totalValueCny
        val portfolioValue = snapshot.portfolioValueCny
        val toValue = snapshot.categories
            .firstOrNull { it.category.id == toCategory.id }
            ?.valueCny ?: BigDecimal.ZERO

        val p = SnapshotCalculator.fraction(toCategory.targetPct)
        val toIsDividend = toCategory.specialRule == "dividend"
        val fromIsDividend = fromCategory.specialRule == "dividend"
        val base = if (toIsDividend) totalValue else portfolioValue
        if (base.signum() <= 0) return null

        var required = if (!toIsDividend && fromIsDividend) {
            val oneMinusP = BigDecimal.ONE.subtract(p)
            if (oneMinusP.signum() <= 0) {
                return null
            }
            p.multiply(portfolioValue).subtract(toValue)
                .divide(oneMinusP, DIVISION_SCALE, RoundingMode.HALF_UP)
        } else {
            p.multiply(base).subtract(toValue)
        }
        // At or above target: no transfer needed.
        if (required.signum() <= 0) return null

        val cappedBySource = required > holdingValue
        if (cappedBySource) required = holdingValue

        // Value of one receive unit at the destination institution, so gold
        // premiums and FX are handled the same way as normal valuation.
        val toUnitValue = Valuation.valuePosition(
            data,
            PositionKey(toInstitution, toInstrument.id),
            BigDecimal.ONE,
            provider,
            date,
        ) ?: return null
        if (toUnitValue.signum() <= 0) return null

        val fromQuantity = required
            .multiply(holding)
            .divide(holdingValue, DIVISION_SCALE, RoundingMode.HALF_UP)
            .setScale(fromInstrument.precision.quantityDecimals, RoundingMode.DOWN)
        if (fromQuantity.signum() <= 0) return null
        // Recompute the value actually moved after rounding the source
        // quantity down, so the receive side matches it instead of showing a
        // spurious one-cent loss.
        val movedValue = fromQuantity
            .multiply(holdingValue)
            .divide(holding, DIVISION_SCALE, RoundingMode.HALF_UP)
        val toQuantity = movedValue
            .divide(toUnitValue, DIVISION_SCALE, RoundingMode.HALF_UP)
            .setScale(toInstrument.precision.quantityDecimals, RoundingMode.DOWN)
        if (toQuantity.signum() <= 0) return null
        return RebalanceInSuggestion(fromQuantity, toQuantity, cappedBySource)
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

    /**
     * Precedence rule for suggested prices: a suggestion fills a blank field
     * and keeps updating while the field still holds the previous
     * suggestion; once the user has typed anything else, their value wins.
     * Selecting a different instrument always takes its own quote.
     */
    fun applyPriceSuggestion(
        current: String,
        lastSuggested: String?,
        instrumentChanged: Boolean,
        suggested: String?,
    ): PriceSuggestion {
        return when {
            instrumentChanged -> PriceSuggestion(suggested ?: "", suggested)

            suggested == null -> {
                if (current.isBlank() || current == lastSuggested) {
                    PriceSuggestion("", null)
                } else {
                    PriceSuggestion(current, lastSuggested)
                }
            }

            current.isBlank() || current == lastSuggested ->
                PriceSuggestion(suggested, suggested)

            else -> PriceSuggestion(current, lastSuggested)
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

data class PriceSuggestion(
    val value: String,
    val lastSuggested: String?,
)

/** Both sides of a transfer that moves the destination category to target. */
data class RebalanceInSuggestion(
    val fromQuantity: BigDecimal,
    val toQuantity: BigDecimal,
    val cappedBySource: Boolean,
)
