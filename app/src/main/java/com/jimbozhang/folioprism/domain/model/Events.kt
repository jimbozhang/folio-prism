package com.jimbozhang.folioprism.domain.model

import com.jimbozhang.folioprism.domain.money.BigDecimalAsStringSerializer
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Event {
    abstract val id: String
    abstract val date: String
    abstract val createdAt: String
    abstract val updatedAt: String?
    abstract val note: String
}

@Serializable
@SerialName("transfer")
data class TransferEvent(
    val from: PositionRef,
    val to: PositionRef,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromQuantity: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toQuantity: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromValueCny: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toValueCny: BigDecimal,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromFxToCny: BigDecimal = BigDecimal.ONE,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toFxToCny: BigDecimal = BigDecimal.ONE,
    @Serializable(with = BigDecimalAsStringSerializer::class) val lossValueCny: BigDecimal = BigDecimal.ZERO,
) : Event()

@Serializable
enum class IncomeKind {
    @SerialName("dividend") DIVIDEND,
    @SerialName("interest") INTEREST,
}

@Serializable
@SerialName("income")
data class IncomeEvent(
    val kind: IncomeKind,
    val destination: PositionRef,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal,
    val currency: String,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amountCny: BigDecimal,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    val source: PositionRef? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fxToCny: BigDecimal = BigDecimal.ONE,
    val immediateExternalWithdrawal: Boolean = false,
    val externalAccountLabel: String? = null,
    val linkedEventId: String? = null,
) : Event()

@Serializable
@SerialName("expense")
data class ExpenseEvent(
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal,
    val currency: String,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amountCny: BigDecimal,
    val categoryId: String,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    val position: PositionRef? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fxToCny: BigDecimal = BigDecimal.ONE,
    val linkedTransferId: String? = null,
) : Event()

@Serializable
enum class ExternalDirection {
    @SerialName("in") IN,
    @SerialName("out") OUT,
}

@Serializable
enum class ExternalClassification {
    @SerialName("capital") CAPITAL,
    @SerialName("consumption") CONSUMPTION,
}

@Serializable
@SerialName("external")
data class ExternalEvent(
    val direction: ExternalDirection,
    val classification: ExternalClassification,
    val position: PositionRef,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal,
    val currency: String,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amountCny: BigDecimal,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val fxToCny: BigDecimal = BigDecimal.ONE,
    val counterpartyLabel: String? = null,
) : Event()

@Serializable
@SerialName("opening")
data class OpeningEvent(
    val position: PositionRef,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val quantity: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val price: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fxToCny: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val valueCny: BigDecimal? = null,
) : Event()

@Serializable
@SerialName("adjustment")
data class AdjustmentEvent(
    val position: PositionRef,
    val reason: String,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val updatedAt: String? = null,
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val deltaQuantity: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val deltaAmount: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val price: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fxToCny: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val valueCny: BigDecimal? = null,
) : Event()
