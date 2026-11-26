package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.money.toMoneyAmount
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unit-price entry helpers for the transfer editor. The event always stores
 * the instrument's native price; a price typed in CNY is converted here with
 * the current FX rate. A cross-currency loss is auto-computed only when both
 * legs can be expressed in CNY exactly (CNY cash, a CNY-native instrument, or
 * a foreign price typed in CNY); otherwise it stays manual because the app
 * has no historical FX rates.
 */
object TransferPricing {

    /** CNY display/suggestion prices keep 4 decimals after conversion. */
    private const val CNY_PRICE_SCALE = 4

    /** Converted foreign-currency losses are derived amounts; keep them precise. */
    private const val NATIVE_LOSS_SCALE = 8

    fun isCny(currency: String): Boolean =
        currency.uppercase() in setOf("CNY", "CNH")

    fun fxOf(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
        if (isCny(currency)) BigDecimal.ONE else fxRates[currency.uppercase()]

    /** Converts an entered unit price into the instrument's native currency. */
    fun nativePrice(
        entered: BigDecimal,
        inCny: Boolean,
        instrument: Instrument,
        fxRates: Map<String, BigDecimal>,
    ): BigDecimal? {
        if (!inCny || isCny(instrument.currency)) return entered
        val fx = fxOf(instrument.currency, fxRates) ?: return null
        if (fx.signum() <= 0) return null
        return entered.divide(fx, instrument.precision.priceDecimals, RoundingMode.HALF_UP)
    }

    /** Converts a native quote into the entry currency, for suggestions/hints. */
    fun enteredPrice(
        native: BigDecimal,
        inCny: Boolean,
        instrument: Instrument,
        fxRates: Map<String, BigDecimal>,
    ): BigDecimal? {
        if (!inCny || isCny(instrument.currency)) return native
        val fx = fxOf(instrument.currency, fxRates) ?: return null
        if (fx.signum() <= 0) return null
        return native.multiply(fx).setScale(CNY_PRICE_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * The leg's value in CNY at entry time when it is exact: CNY cash, a
     * CNY-native quoted instrument, or a foreign price typed in CNY. Null when
     * the value would depend on today's FX rate for a historical trade.
     */
    fun exactCnyValue(
        instrument: Instrument,
        quantity: BigDecimal,
        nativePrice: BigDecimal?,
        enteredPrice: BigDecimal?,
        inCny: Boolean,
    ): BigDecimal? {
        if (instrument.kind == InstrumentKind.CASH) {
            return if (isCny(instrument.currency)) quantity else null
        }
        if (inCny) {
            return enteredPrice?.let { quantity.multiply(it) }
        }
        if (isCny(instrument.currency)) {
            return nativePrice?.let { quantity.multiply(it) }
        }
        return null
    }

    /** Converts an exact CNY loss into the loss currency used by the event. */
    fun lossInCurrency(
        lossCny: BigDecimal,
        lossCurrency: String,
        fxRates: Map<String, BigDecimal>,
    ): BigDecimal? {
        if (isCny(lossCurrency)) return lossCny.toMoneyAmount()
        val fx = fxOf(lossCurrency, fxRates) ?: return null
        if (fx.signum() <= 0) return null
        return lossCny.divide(fx, NATIVE_LOSS_SCALE, RoundingMode.HALF_UP)
    }
}
