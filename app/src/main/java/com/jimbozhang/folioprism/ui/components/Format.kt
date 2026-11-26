package com.jimbozhang.folioprism.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val chinaIntegerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)

private val TEN_THOUSAND = BigDecimal(10_000)
private val ONE_HUNDRED_MILLION = BigDecimal(100_000_000)

/**
 * Cached NumberFormat per exact decimal scale: minimumFractionDigits 0 so
 * nothing is padded, maximumFractionDigits equal to the value's own scale so
 * nothing is rounded.
 */
private val exactFormats = HashMap<Int, NumberFormat>()

private fun exactFormat(scale: Int): NumberFormat =
    synchronized(exactFormats) {
        exactFormats.getOrPut(scale) {
            NumberFormat.getNumberInstance(Locale.CHINA).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = scale
            }
        }
    }

/**
 * Renders a value with exactly the decimal digits it carries — no rounding,
 * no zero padding. Small residues (e.g. -0.001) stay visible so users can
 * trace them back to the transactions that produced them.
 */
private fun exactText(value: BigDecimal): String {
    val v = value.stripTrailingZeros()
    return exactFormat(v.scale().coerceAtLeast(0)).format(v)
}

/**
 * Cash amounts are displayed bank-style: always exactly two decimal places,
 * so a whole-amount balance renders as 4,350.00 instead of 4,350.
 */
private val cashFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun cashText(value: BigDecimal): String = cashFormat.format(value)

fun formatCny(value: BigDecimal?): String =
    if (value == null) "--"
    else "¥" + exactText(value)

/** Cash amount in CNY, always with two decimal places. */
fun formatCashCny(value: BigDecimal?): String =
    if (value == null) "--"
    else "¥" + cashText(value)

/** Cash amount in the position's own currency, always two decimal places. */
fun formatCashNative(value: BigDecimal?, currency: String): String {
    if (value == null) return "--"
    return currencyPrefix(currency) + cashText(value)
}

/** Plain padded number (no currency prefix) for cash amounts with a unit suffix. */
fun formatCashNumber(value: BigDecimal): String = cashText(value)

/**
 * Compact CNY amount for chart annotations: 亿 / 万 units keep labels short
 * enough to sit side by side on a range bar.
 */
fun formatCnyCompact(value: BigDecimal?): String {
    if (value == null) return "--"
    val sign = if (value.signum() < 0) "-" else ""
    val abs = value.abs()
    val body = when {
        abs >= ONE_HUNDRED_MILLION ->
            abs.divide(ONE_HUNDRED_MILLION, 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "亿"

        abs >= TEN_THOUSAND ->
            abs.divide(TEN_THOUSAND, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "万"

        else -> chinaIntegerFormat.format(abs.setScale(0, RoundingMode.HALF_UP))
    }
    return "$sign¥$body"
}

fun formatNative(value: BigDecimal?, currency: String): String {
    if (value == null) return "--"
    return currencyPrefix(currency) + exactText(value)
}

private fun currencyPrefix(currency: String): String = when (currency.uppercase()) {
    "USD" -> "$"
    "HKD" -> "HK$"
    else -> "¥"
}

fun formatPercent(fraction: BigDecimal?): String {
    if (fraction == null) return "--"
    return fraction
        .multiply(BigDecimal(100))
        .setScale(1, RoundingMode.HALF_UP)
        .toPlainString() + "%"
}

fun formatQuantity(value: BigDecimal): String = exactText(value)
