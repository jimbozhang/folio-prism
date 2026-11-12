package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import java.math.BigDecimal
import java.time.LocalDate

data class PositionKey(
    val institutionId: String,
    val instrumentId: String,
)

object EventReplay {

    /**
     * Replays events in chronological order and returns position quantities.
     * For cash instruments the quantity is the native-currency amount.
     * Negative quantities are intentionally allowed.
     */
    fun replay(
        events: List<Event>,
        upTo: LocalDate? = null,
    ): Map<PositionKey, BigDecimal> {
        val positions = linkedMapOf<PositionKey, BigDecimal>()
        events
            .sortedWith(eventComparator())
            .forEach { event ->
                if (upTo == null || !event.localDate().isAfter(upTo)) {
                    applyEvent(positions, event)
                }
            }
        return positions
    }

    fun applyEvent(
        positions: MutableMap<PositionKey, BigDecimal>,
        event: Event,
    ) {
        when (event) {
            is OpeningEvent -> add(
                positions,
                event.position,
                event.quantity ?: event.amount ?: BigDecimal.ZERO,
            )

            is TransferEvent -> {
                add(positions, event.from, event.fromQuantity.negate())
                add(positions, event.to, event.toQuantity)
            }

            is IncomeEvent -> add(positions, event.destination, event.amount)

            is ExpenseEvent -> event.position?.let {
                add(positions, it, event.amount.negate())
            }

            is ExternalEvent -> add(
                positions,
                event.position,
                if (event.direction == ExternalDirection.IN) event.amount
                else event.amount.negate(),
            )

            is AdjustmentEvent -> add(
                positions,
                event.position,
                event.deltaQuantity ?: event.deltaAmount ?: BigDecimal.ZERO,
            )
        }
    }

    private fun add(
        positions: MutableMap<PositionKey, BigDecimal>,
        ref: PositionRef,
        delta: BigDecimal,
    ) {
        val key = PositionKey(ref.institutionId, ref.instrumentId)
        positions[key] = (positions[key] ?: BigDecimal.ZERO).add(delta)
    }
}
