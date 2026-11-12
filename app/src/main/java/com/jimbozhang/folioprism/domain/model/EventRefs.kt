package com.jimbozhang.folioprism.domain.model

/**
 * Position references touched by an event. Used for referential checks in settings.
 */
fun Event.positionRefs(): List<PositionRef> = when (this) {
    is OpeningEvent -> listOf(position)
    is TransferEvent -> listOf(from, to)
    is IncomeEvent -> listOfNotNull(source, destination)
    is ExpenseEvent -> listOfNotNull(position)
    is ExternalEvent -> listOf(position)
    is AdjustmentEvent -> listOf(position)
}

fun PortfolioData.eventsReferencingInstitution(institutionId: String): Boolean =
    events.any { event -> event.positionRefs().any { it.institutionId == institutionId } }

fun PortfolioData.eventsReferencingInstrument(instrumentId: String): Boolean =
    events.any { event -> event.positionRefs().any { it.instrumentId == instrumentId } }
