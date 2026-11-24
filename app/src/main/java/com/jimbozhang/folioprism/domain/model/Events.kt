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

    /**
     * Legacy: when the row was recorded, not when the event happened. The
     * ledger only cares about [date], so it is no longer written; the
     * property stays so files written by older versions still decode, and it
     * disappears from the file on the next save.
     */
    abstract val createdAt: String
    abstract val note: String
}

@Serializable
@SerialName("transfer")
data class TransferEvent(
    val from: PositionRef,
    val to: PositionRef,
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromQuantity: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toQuantity: BigDecimal,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val fromPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val toPrice: BigDecimal? = null,
    /**
     * Embedded fee/loss in [lossCurrency]. Positive = cost, negative = gain.
     * A separate cash fee is stored in [feeAmount] and is not included here.
     */
    @Serializable(with = BigDecimalAsStringSerializer::class) val lossAmount: BigDecimal = BigDecimal.ZERO,
    val lossCurrency: String = "CNY",
    /** Cash account that pays the fee, only when the fee is not embedded in the transfer values. */
    val feePosition: PositionRef? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val feeAmount: BigDecimal? = null,
    val feeCurrency: String? = null,
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
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    val source: PositionRef? = null,
    val immediateExternalWithdrawal: Boolean = false,
    val externalAccountLabel: String? = null,
    val linkedEventId: String? = null,
) : Event()

@Serializable
@SerialName("expense")
data class ExpenseEvent(
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal,
    val currency: String,
    val categoryId: String,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    val position: PositionRef? = null,
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
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    val counterpartyLabel: String? = null,
) : Event()

@Serializable
@SerialName("opening")
data class OpeningEvent(
    val position: PositionRef,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val quantity: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val amount: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val price: BigDecimal? = null,
) : Event()

@Serializable
@SerialName("adjustment")
data class AdjustmentEvent(
    val position: PositionRef,
    val reason: String,
    override val date: String,
    override val id: String = UUID.randomUUID().toString(),
    override val createdAt: String = "",
    override val note: String = "",
    @Serializable(with = BigDecimalAsStringSerializer::class) val deltaQuantity: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val deltaAmount: BigDecimal? = null,
    @Serializable(with = BigDecimalAsStringSerializer::class) val price: BigDecimal? = null,
) : Event()

/**
 * Clears the legacy [Event.createdAt] so the whole file, not only edited
 * events, loses the field on the next save.
 */
fun Event.withoutLegacyCreatedAt(): Event = when (this) {
    is TransferEvent -> copy(createdAt = "")
    is IncomeEvent -> copy(createdAt = "")
    is ExpenseEvent -> copy(createdAt = "")
    is ExternalEvent -> copy(createdAt = "")
    is OpeningEvent -> copy(createdAt = "")
    is AdjustmentEvent -> copy(createdAt = "")
}
