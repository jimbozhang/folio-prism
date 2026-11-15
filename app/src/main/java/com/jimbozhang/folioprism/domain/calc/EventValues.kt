package com.jimbozhang.folioprism.domain.calc

import java.math.BigDecimal

data class TransferValues(
    val fromNative: BigDecimal,
    val toNative: BigDecimal,
)

object EventValues {

    /**
     * Computes native values for a transfer. A null price means the position is
     * cash-like and its quantity is already the native-currency amount.
     */
    fun transferValues(
        fromQuantity: BigDecimal,
        toQuantity: BigDecimal,
        fromPrice: BigDecimal?,
        toPrice: BigDecimal?,
    ): TransferValues {
        val fromNative = fromQuantity.multiply(fromPrice ?: BigDecimal.ONE)
        val toNative = toQuantity.multiply(toPrice ?: BigDecimal.ONE)
        return TransferValues(
            fromNative = fromNative,
            toNative = toNative,
        )
    }

    fun nativeValue(quantity: BigDecimal, price: BigDecimal?): BigDecimal =
        quantity.multiply(price ?: BigDecimal.ONE)
}
