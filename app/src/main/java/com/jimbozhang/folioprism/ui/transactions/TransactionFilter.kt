package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.positionRefs

internal val eventTypeFilterOptions = listOf(
    "transfer" to "转账",
    "income" to "利息/分红",
    "external" to "外部",
    "expense" to "支出",
    "opening" to "期初",
    "adjustment" to "调整",
)

/**
 * Filter applied to the transaction list. Every dimension that is set must
 * match; account and instrument must match the SAME position reference, so a
 * transfer whose two legs touch different positions is not matched by a
 * combination that no single leg satisfies.
 */
internal data class TransactionFilter(
    val type: String? = null,
    val institutionId: String? = null,
    val instrumentId: String? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(type, institutionId, instrumentId).size

    val isEmpty: Boolean
        get() = activeCount == 0

    fun matches(event: Event): Boolean {
        if (type != null && event.typeKey() != type) return false
        if (institutionId == null && instrumentId == null) return true
        return event.filterRefs().any { ref ->
            (institutionId == null || ref.institutionId == institutionId) &&
                (instrumentId == null || ref.instrumentId == instrumentId)
        }
    }
}

/**
 * Positions an event touches for filtering purposes. Unlike the referential
 * check, a transfer's separately paid fee is included: that cash account took
 * part in the event even though it is not one of the two legs.
 */
private fun Event.filterRefs(): List<PositionRef> = when (this) {
    is TransferEvent -> feePosition?.let { positionRefs() + it } ?: positionRefs()
    else -> positionRefs()
}

internal fun Event.typeKey(): String = when (this) {
    is TransferEvent -> "transfer"
    is IncomeEvent -> "income"
    is ExternalEvent -> "external"
    is ExpenseEvent -> "expense"
    is OpeningEvent -> "opening"
    is AdjustmentEvent -> "adjustment"
}
