package com.jimbozhang.folioprism.domain.calc

import java.math.BigDecimal

data class TransferValues(
    val fromValueCny: BigDecimal,
    val toValueCny: BigDecimal,
    val lossValueCny: BigDecimal,
)

object EventValues {

    /**
     * Computes CNY values for a transfer. A null price means the position is cash-like
     * and its quantity is already the native-currency amount.
     */
    fun transferValues(
        fromQuantity: BigDecimal,
        toQuantity: BigDecimal,
        fromPrice: BigDecimal?,
        toPrice: BigDecimal?,
        fromFxToCny: BigDecimal,
        toFxToCny: BigDecimal,
    ): TransferValues {
        val fromNative = fromQuantity.multiply(fromPrice ?: BigDecimal.ONE)
        val toNative = toQuantity.multiply(toPrice ?: BigDecimal.ONE)
        val fromValue = fromNative.multiply(fromFxToCny)
        val toValue = toNative.multiply(toFxToCny)
        return TransferValues(
            fromValueCny = fromValue,
            toValueCny = toValue,
            lossValueCny = fromValue.subtract(toValue),
        )
    }

    fun amountCny(amount: BigDecimal, fxToCny: BigDecimal): BigDecimal =
        amount.multiply(fxToCny)
}
