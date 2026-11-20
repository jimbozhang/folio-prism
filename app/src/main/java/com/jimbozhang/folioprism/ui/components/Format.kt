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

fun formatCny(value: BigDecimal?): String =
    if (value == null) "--"
    else "¥" + exactText(value)

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
    val prefix = when (currency.uppercase()) {
        "USD" -> "$"
        "HKD" -> "HK$"
        else -> "¥"
    }
    return prefix + exactText(value)
}

fun formatPercent(fraction: BigDecimal?): String {
    if (fraction == null) return "--"
    return fraction
        .multiply(BigDecimal(100))
        .setScale(1, RoundingMode.HALF_UP)
        .toPlainString() + "%"
}

fun formatQuantity(value: BigDecimal): String = exactText(value)
