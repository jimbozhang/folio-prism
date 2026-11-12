package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import java.math.BigDecimal
import java.time.LocalDate

object HistoryCalculator {

    fun build(
        data: PortfolioData,
        cache: PriceCacheData,
        from: LocalDate,
        to: LocalDate,
    ): HistoryResult {
        val provider = HistoricalPriceProvider(cache)
        val logs = mutableListOf<String>()
        val days = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .toList()
        val sortedEvents = data.events.sortedWith(eventComparator())
        val positions = linkedMapOf<PositionKey, BigDecimal>()
        var eventIndex = 0
        while (eventIndex < sortedEvents.size &&
            sortedEvents[eventIndex].localDate().isBefore(from)
        ) {
            EventReplay.applyEvent(positions, sortedEvents[eventIndex])
            eventIndex++
        }
        val curve = days.map { day ->
            while (eventIndex < sortedEvents.size &&
                !sortedEvents[eventIndex].localDate().isAfter(day)
            ) {
                EventReplay.applyEvent(positions, sortedEvents[eventIndex])
                eventIndex++
            }
            dailyValue(data, provider, day, positions)
        }
        val fromPoint = curve.firstOrNull()
        val toPoint = curve.lastOrNull()

        val flows = mutableMapOf<String, BigDecimal>()
        val losses = mutableMapOf<String, BigDecimal>()
        val attribution = mutableMapOf<String, BigDecimal>()

        fun add(map: MutableMap<String, BigDecimal>, key: String, value: BigDecimal) {
            map[key] = (map[key] ?: BigDecimal.ZERO).add(value)
        }

        data.events.forEach { event ->
            val date = event.localDate()
            if (!date.isAfter(from) || date.isAfter(to)) return@forEach
            when (event) {
                is TransferEvent -> {
                    val fromCategory = data.instrument(event.from.instrumentId)?.categoryId
                    val toCategory = data.instrument(event.to.instrumentId)?.categoryId
                    if (fromCategory != null && toCategory != null && fromCategory != toCategory) {
                        add(flows, fromCategory, event.fromValueCny.negate())
                        add(flows, toCategory, event.toValueCny)
                    }
                }

                is IncomeEvent -> {
                    val sourceCategory = event.source
                        ?.let { data.instrument(it.instrumentId)?.categoryId }
                    val destinationCategory =
                        data.instrument(event.destination.instrumentId)?.categoryId
                    if (sourceCategory != null && destinationCategory != null &&
                        sourceCategory != destinationCategory
                    ) {
                        add(flows, sourceCategory, event.amountCny.negate())
                        add(flows, destinationCategory, event.amountCny)
                    }
                }

                is ExternalEvent -> {
                    val category = data.instrument(event.position.instrumentId)?.categoryId
                    if (category != null) {
                        val signed = if (event.direction == ExternalDirection.IN) {
                            event.amountCny
                        } else {
                            event.amountCny.negate()
                        }
                        add(flows, category, signed)
                    }
                }

                is ExpenseEvent -> {
                    if (event.position == null) {
                        add(losses, event.categoryId, event.amountCny)
                    } else {
                        val cashCategory =
                            data.instrument(event.position.instrumentId)?.categoryId
                        if (cashCategory != null && cashCategory != event.categoryId) {
                            add(attribution, cashCategory, event.amountCny)
                            add(attribution, event.categoryId, event.amountCny.negate())
                        }
                    }
                }

                is AdjustmentEvent -> {
                    val category = data.instrument(event.position.instrumentId)?.categoryId
                    val value = event.valueCny
                    if (category != null && value != null) {
                        add(flows, category, value)
                    }
                }

                is OpeningEvent -> Unit
            }
        }

        val categoryPnl = data.settings.categories
            .sortedBy { it.order }
            .map { category ->
                val fromValue = fromPoint?.categoryValues?.get(category.id)
                val toValue = toPoint?.categoryValues?.get(category.id)
                val incomplete = fromPoint == null || toPoint == null ||
                    fromValue == null || toValue == null
                val pnl = if (fromValue != null && toValue != null) {
                    toValue
                        .subtract(fromValue)
                        .subtract(flows[category.id] ?: BigDecimal.ZERO)
                        .subtract(losses[category.id] ?: BigDecimal.ZERO)
                        .subtract(attribution[category.id] ?: BigDecimal.ZERO)
                } else {
                    null
                }
                CategoryPnl(
                    categoryId = category.id,
                    categoryName = category.name,
                    pnlCny = pnl,
                    incomplete = incomplete,
                )
            }

        if (curve.any { it.incomplete }) {
            logs += "部分日期缺少历史价格，曲线/盈亏可能不完整"
        }
        return HistoryResult(
            from = from,
            to = to,
            curve = curve,
            categoryPnl = categoryPnl,
            logs = logs,
        )
    }

    private fun dailyValue(
        data: PortfolioData,
        provider: PriceProvider,
        date: LocalDate,
        positions: Map<PositionKey, BigDecimal>,
    ): DailyPortfolioValue {
        var total = BigDecimal.ZERO
        var incomplete = false
        val categoryValues = linkedMapOf<String, BigDecimal?>()
        data.settings.categories.forEach { categoryValues[it.id] = BigDecimal.ZERO }

        positions.forEach { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId)
            if (instrument == null) {
                if (quantity.signum() != 0) incomplete = true
                return@forEach
            }
            val value = Valuation.valuePosition(data, key, quantity, provider, date)
            if (value == null) {
                if (quantity.signum() != 0) {
                    incomplete = true
                    categoryValues[instrument.categoryId] = null
                }
                return@forEach
            }
            total = total.add(value)
            val current = categoryValues[instrument.categoryId]
            if (current != null) {
                categoryValues[instrument.categoryId] = current.add(value)
            }
        }

        return DailyPortfolioValue(
            date = date,
            totalCny = if (incomplete) null else total,
            categoryValues = categoryValues,
            incomplete = incomplete,
        )
    }
}
