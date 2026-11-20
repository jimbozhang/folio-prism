package com.jimbozhang.folioprism.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Derived CNY amounts (position values, flows, stats) are rounded to the fen.
 * Stored native-currency amounts and quantities keep their own precision.
 */
const val CNY_SCALE = 2

/** A money amount in some currency: rounded to the currency's minor unit (2 decimals). */
fun BigDecimal.toMoneyAmount(): BigDecimal = setScale(CNY_SCALE, RoundingMode.HALF_UP)

fun BigDecimal.toCnyAmount(): BigDecimal = toMoneyAmount()
