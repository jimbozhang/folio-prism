package com.jimbozhang.folioprism.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val chinaNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatCny(value: BigDecimal?): String =
    if (value == null) "--"
    else "¥" + chinaNumberFormat.format(value.setScale(2, RoundingMode.HALF_UP))

fun formatNative(value: BigDecimal?, currency: String): String {
    if (value == null) return "--"
    val prefix = when (currency.uppercase()) {
        "USD" -> "$"
        "HKD" -> "HK$"
        else -> "¥"
    }
    return prefix + chinaNumberFormat.format(value.setScale(2, RoundingMode.HALF_UP))
}

fun formatPercent(fraction: BigDecimal?): String {
    if (fraction == null) return "--"
    return fraction
        .multiply(BigDecimal(100))
        .setScale(1, RoundingMode.HALF_UP)
        .toPlainString() + "%"
}

fun formatQuantity(value: BigDecimal): String =
    value.setScale(2, RoundingMode.HALF_UP).toPlainString()
