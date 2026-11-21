package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DailyFlow
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val RATIO_SCALE = 10

object HistoryCalculator {

    /**
     * [currentPrices] are live quote prices in each instrument's native
     * currency. When [to] is today, the final curve point uses them so the
     * curve ends at the value the home screen shows; for earlier range ends
     * they are ignored to keep the curve purely historical.
     */
    fun build(
        data: PortfolioData,
        cache: PriceCacheData,
        currentFxRates: Map<String, BigDecimal>,
        from: LocalDate,
        to: LocalDate,
        currentPrices: Map<String, BigDecimal> = emptyMap(),
    ): HistoryResult {
        val eventPrices = buildEventPrices(data, to)
        val livePrices = if (to == LocalDate.now()) currentPrices else emptyMap()
        val provider = HistoricalPriceProvider(
            cache = cache,
            currentFxRates = currentFxRates,
            eventPrices = eventPrices,
            currentPrices = livePrices,
            currentAsOf = if (livePrices.isNotEmpty()) to else null,
        )
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
        if (provider.usedInterpolation) {
            logs += "部分日期缺少线上历史价格，已用已知价格点线性插值"
        }
        val fromPoint = curve.firstOrNull()
        val toPoint = curve.lastOrNull()

        val flows = mutableMapOf<String, BigDecimal>()
        val losses = mutableMapOf<String, BigDecimal>()
        val attribution = mutableMapOf<String, BigDecimal>()
        // Old data records transfer fees/losses as linked expenses. Do not
        // count the embedded value difference twice when those exist.
        val linkedFeeTransfers = data.events
            .filterIsInstance<ExpenseEvent>()
            .filter {
                it.linkedTransferId != null &&
                    it.note in setOf("转账手续费", "转账损耗")
            }
            .mapNotNull { it.linkedTransferId }
            .toSet()

        fun add(map: MutableMap<String, BigDecimal>, key: String, value: BigDecimal) {
            map[key] = (map[key] ?: BigDecimal.ZERO).add(value)
        }

        // Per-day non-market changes back the curve's flow markers and the
        // flow-adjusted ("market only") curve.
        val dailyExternal = linkedMapOf<LocalDate, MutableMap<String, BigDecimal>>()
        val dailyInternal = linkedMapOf<LocalDate, MutableMap<String, BigDecimal>>()

        fun addDaily(
            target: MutableMap<LocalDate, MutableMap<String, BigDecimal>>,
            date: LocalDate,
            categoryId: String,
            value: BigDecimal,
        ) {
            val byCategory = target.getOrPut(date) { linkedMapOf() }
            byCategory[categoryId] = (byCategory[categoryId] ?: BigDecimal.ZERO).add(value)
        }

        data.events.forEach { event ->
            val date = event.localDate()
            if (!date.isAfter(from) || date.isAfter(to)) return@forEach
            when (event) {
                is TransferEvent -> {
                    val fromInstrument = data.instrument(event.from.instrumentId)
                    val toInstrument = data.instrument(event.to.instrumentId)
                    val fromFx = fromInstrument?.let { fx(it.currency, provider) }
                    val toFx = toInstrument?.let { fx(it.currency, provider) }
                    val values = EventValues.transferValues(
                        fromQuantity = event.fromQuantity,
                        toQuantity = event.toQuantity,
                        fromPrice = event.fromPrice,
                        toPrice = event.toPrice,
                    )
                    val fromValueCny = fromFx?.let { values.fromNative.multiply(it).toCnyAmount() }
                    val toValueCny = toFx?.let { values.toNative.multiply(it).toCnyAmount() }
                    val fromCategory = fromInstrument?.categoryId
                    val toCategory = toInstrument?.categoryId
                    if (fromValueCny != null && toValueCny != null &&
                        fromCategory != null && toCategory != null &&
                        fromCategory != toCategory
                    ) {
                        add(flows, fromCategory, fromValueCny.negate())
                        add(flows, toCategory, toValueCny)
                        addDaily(dailyInternal, date, fromCategory, fromValueCny.negate())
                        addDaily(dailyInternal, date, toCategory, toValueCny)
                    }
                    // The embedded fee/loss is stored in its native currency.
                    // An extra fee paid from a separate cash account is already
                    // reflected in that cash balance, so it is not added here.
                    val lossFx = fx(event.lossCurrency, provider)
                    val embeddedLoss = lossFx?.let { event.lossAmount.multiply(it).toCnyAmount() }
                    if (embeddedLoss != null && event.id !in linkedFeeTransfers &&
                        embeddedLoss.signum() != 0
                    ) {
                        add(
                            losses,
                            transferLossCategory(
                                data,
                                event.from.instrumentId,
                                event.to.instrumentId,
                            ),
                            embeddedLoss,
                        )
                    }
                }

                is IncomeEvent -> {
                    val sourceCategory = event.source
                        ?.let { data.instrument(it.instrumentId)?.categoryId }
                    val destinationCategory =
                        data.instrument(event.destination.instrumentId)?.categoryId
                    val amountCny = fx(event.currency, provider)
                        ?.let { event.amount.multiply(it).toCnyAmount() }
                    if (amountCny != null && sourceCategory != null &&
                        destinationCategory != null && sourceCategory != destinationCategory
                    ) {
                        add(flows, sourceCategory, amountCny.negate())
                        add(flows, destinationCategory, amountCny)
                        addDaily(dailyInternal, date, sourceCategory, amountCny.negate())
                        addDaily(dailyInternal, date, destinationCategory, amountCny)
                    }
                }

                is ExternalEvent -> {
                    val category = data.instrument(event.position.instrumentId)?.categoryId
                    val amountCny = fx(event.currency, provider)
                        ?.let { event.amount.multiply(it).toCnyAmount() }
                    if (category != null && amountCny != null) {
                        val signed = if (event.direction == ExternalDirection.IN) {
                            amountCny
                        } else {
                            amountCny.negate()
                        }
                        add(flows, category, signed)
                        addDaily(dailyExternal, date, category, signed)
                    }
                }

                is ExpenseEvent -> {
                    val amountCny = fx(event.currency, provider)
                        ?.let { event.amount.multiply(it).toCnyAmount() }
                    if (amountCny == null) {
                        return@forEach
                    }
                    if (event.position == null) {
                        add(losses, event.categoryId, amountCny)
                    } else {
                        val cashCategory =
                            data.instrument(event.position.instrumentId)?.categoryId
                        if (cashCategory != null && cashCategory != event.categoryId) {
                            add(attribution, cashCategory, amountCny)
                            add(attribution, event.categoryId, amountCny.negate())
                            addDaily(dailyInternal, date, cashCategory, amountCny)
                            addDaily(dailyInternal, date, event.categoryId, amountCny.negate())
                        }
                    }
                }

                is AdjustmentEvent -> {
                    val instrument = data.instrument(event.position.instrumentId)
                    val category = instrument?.categoryId
                    val rate = instrument?.let { fx(it.currency, provider) }
                    val value = when {
                        rate == null -> null
                        event.deltaAmount != null -> event.deltaAmount.multiply(rate).toCnyAmount()
                        event.deltaQuantity != null && event.price != null ->
                            event.deltaQuantity.multiply(event.price).multiply(rate).toCnyAmount()
                        else -> null
                    }
                    if (category != null && value != null) {
                        add(flows, category, value)
                        addDaily(dailyExternal, date, category, value)
                    }
                }

                is OpeningEvent -> {
                    // An opening inside the range adds holdings out of
                    // nowhere; treat its value as capital inflow so it never
                    // shows up as profit.
                    val instrument = data.instrument(event.position.instrumentId)
                    val category = instrument?.categoryId
                    val rate = instrument?.let { fx(it.currency, provider) }
                    if (instrument != null && category != null && rate != null) {
                        val native = when {
                            instrument.kind == InstrumentKind.CASH ->
                                event.amount ?: event.quantity

                            event.quantity != null -> {
                                val price = event.price ?: provider.price(instrument.id, date)
                                price?.let { event.quantity.multiply(it) }
                            }

                            else -> event.amount
                        }
                        if (native != null) {
                            val value = native.multiply(rate).toCnyAmount()
                            add(flows, category, value)
                            addDaily(dailyExternal, date, category, value)
                        }
                    }
                }
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

        val dailyFlows = days.mapNotNull { day ->
            val external = dailyExternal[day]
            val internal = dailyInternal[day]
            if (external == null && internal == null) {
                null
            } else {
                DailyFlow(
                    date = day,
                    externalByCategory = external.orEmpty(),
                    internalByCategory = internal.orEmpty(),
                )
            }
        }
        val adjustedCurve = adjustedCurve(
            curve = curve,
            dailyFlows = dailyFlows,
            categoryIds = data.settings.categories.map { it.id },
        )

        if (curve.any { it.incomplete }) {
            logs += "部分日期缺少历史价格，曲线/盈亏可能不完整"
        }
        return HistoryResult(
            from = from,
            to = to,
            curve = curve,
            categoryPnl = categoryPnl,
            logs = logs,
            dailyFlows = dailyFlows,
            adjustedCurve = adjustedCurve,
        )
    }

    /**
     * Restates [curve] without the effect of capital flows: each day's market
     * return (value change minus that day's flow) is applied to the previous
     * adjusted value. The adjusted line therefore only reflects prices,
     * dividends and fees.
     */
    private fun adjustedCurve(
        curve: List<DailyPortfolioValue>,
        dailyFlows: List<DailyFlow>,
        categoryIds: List<String>,
    ): List<DailyPortfolioValue> {
        val flowByDate = dailyFlows.associateBy { it.date }
        val total = adjustStream(
            values = curve.map { it.totalCny },
            flows = curve.map { flowByDate[it.date]?.netTotal ?: BigDecimal.ZERO },
        )
        val categories = categoryIds.associateWith { categoryId ->
            adjustStream(
                values = curve.map { it.categoryValues[categoryId] },
                flows = curve.map { flowByDate[it.date]?.netFor(categoryId) ?: BigDecimal.ZERO },
            )
        }
        return curve.mapIndexed { index, point ->
            DailyPortfolioValue(
                date = point.date,
                totalCny = total[index],
                categoryValues = categoryIds.associateWith { categories.getValue(it)[index] },
                incomplete = point.incomplete,
            )
        }
    }

    /**
     * Time-weighted restatement of one value stream. A stream that is still
     * empty is seeded by its first positive value; missing (null) days are kept
     * as gaps and do not change the running index.
     */
    private fun adjustStream(
        values: List<BigDecimal?>,
        flows: List<BigDecimal>,
    ): List<BigDecimal?> {
        var previous: BigDecimal? = null
        var index: BigDecimal? = null
        return values.mapIndexed { day, value ->
            if (value == null) return@mapIndexed null
            val current = index
            index = when {
                current == null -> value.takeIf { it.signum() > 0 }
                previous != null && previous.signum() > 0 -> {
                    val growth = value
                        .subtract(previous)
                        .subtract(flows[day])
                        .divide(previous, RATIO_SCALE, RoundingMode.HALF_UP)
                    current.multiply(BigDecimal.ONE.add(growth))
                }
                // A stream that starts from zero: seed it where it appears.
                value.signum() > 0 -> value
                else -> current
            }
            previous = value
            index?.setScale(2, RoundingMode.HALF_UP)
        }
    }

    /**
     * Known native prices from events. Manual-price instruments also get their
     * current manual price at the range end, so the curve ends at the value the
     * home screen shows.
     */
    private fun buildEventPrices(
        data: PortfolioData,
        to: LocalDate,
    ): Map<String, List<PricePoint>> {
        val byInstrument = linkedMapOf<String, MutableMap<LocalDate, BigDecimal>>()

        fun put(instrumentId: String, date: LocalDate, price: BigDecimal) {
            byInstrument.getOrPut(instrumentId) { linkedMapOf() }[date] = price
        }

        data.events.forEach { event ->
            when (event) {
                is OpeningEvent -> event.price?.let {
                    put(event.position.instrumentId, event.localDate(), it)
                }

                is TransferEvent -> {
                    event.fromPrice?.let {
                        put(event.from.instrumentId, event.localDate(), it)
                    }
                    event.toPrice?.let {
                        put(event.to.instrumentId, event.localDate(), it)
                    }
                }

                is AdjustmentEvent -> event.price?.let {
                    put(event.position.instrumentId, event.localDate(), it)
                }

                else -> Unit
            }
        }

        data.instruments.forEach { instrument ->
            if (instrument.quote.mode == QuoteMode.MANUAL) {
                instrument.quote.manualPrice?.let { put(instrument.id, to, it) }
            }
        }

        return byInstrument.mapValues { (_, points) ->
            points.entries
                .sortedBy { it.key }
                .map { PricePoint(it.key, it.value) }
        }
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

    private fun fx(currency: String, provider: PriceProvider): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> provider.fxRate(currency.uppercase())
        }

    private fun transferLossCategory(
        data: PortfolioData,
        fromInstrumentId: String,
        toInstrumentId: String,
    ): String {
        val from = data.instrument(fromInstrumentId)
        val to = data.instrument(toInstrumentId)
        return when {
            to?.kind != InstrumentKind.CASH -> to?.categoryId ?: CategoryIds.CASH
            from?.kind != InstrumentKind.CASH -> from?.categoryId ?: CategoryIds.CASH
            else -> CategoryIds.CASH
        }
    }
}
