package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.SATELLITE_RULE
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.category
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Direction of a rebalancing trade, by instrument kind. */
enum class RebalanceDirection {
    /** Cash was used to buy a quoted instrument. */
    CASH_TO_ASSET,

    /** A quoted instrument was sold into cash. */
    ASSET_TO_CASH,

    /** One quoted instrument was exchanged for another. */
    ASSET_TO_ASSET,
}

/** One rebalancing trade: a transfer between two different instruments. */
data class RebalanceRecord(
    val date: LocalDate,
    val direction: RebalanceDirection,
    val fromCategoryId: String,
    val fromCategoryName: String,
    val toCategoryId: String,
    val toCategoryName: String,
    /** CNY value of the sold leg; null when that leg cannot be priced. */
    val outCny: BigDecimal?,
    /** CNY value of the bought leg; null when that leg cannot be priced. */
    val inCny: BigDecimal?,
    val feeLossCny: BigDecimal,
)

/** One month of rebalancing activity, split by trade direction. */
data class RebalanceMonth(
    val month: YearMonth,
    val cashToAsset: Int,
    val assetToCash: Int,
    val assetToAsset: Int,
    val count: Int,
    val amountCny: BigDecimal,
)

/** Sold / bought amounts and counts of one category. */
data class RebalanceCategoryFlow(
    val categoryId: String,
    val categoryName: String,
    val outCny: BigDecimal,
    val inCny: BigDecimal,
    val outCount: Int,
    val inCount: Int,
)

/** How often money moved from one category to another. */
data class RebalancePair(
    val fromName: String,
    val toName: String,
    val count: Int,
    val amountCny: BigDecimal,
)

/**
 * Rebalancing statistics for a date range (inclusive).
 *
 * Only transfers between different instruments count as rebalancing; moving
 * the same instrument to another institution and cash-to-cash currency
 * exchange are reported separately and excluded from the frequency figures.
 * Amounts use the current FX rates; unpriceable legs leave the amount null but
 * the trade still counts.
 */
data class RebalanceStats(
    val count: Int,
    val dayCount: Int,
    /** Average days between rebalancing days; null with fewer than two days. */
    val averageIntervalDays: Int?,
    /** Rebalancing trades within the last 30 days of the range. */
    val recent30DaysCount: Int,
    val amountCny: BigDecimal,
    val feeLossCny: BigDecimal,
    val months: List<RebalanceMonth>,
    val categoryFlows: List<RebalanceCategoryFlow>,
    val topPairs: List<RebalancePair>,
    val accountMoveCount: Int,
    val fxSwapCount: Int,
    /** Transfers inside the cash category (cash to a money fund, ...). */
    val cashCategoryCount: Int,
    val incomplete: Boolean,
)

object RebalanceStatsCalculator {

    fun calculate(
        data: PortfolioData,
        fxRates: Map<String, BigDecimal>,
        from: LocalDate,
        to: LocalDate,
    ): RebalanceStats {
        val records = mutableListOf<RebalanceRecord>()
        var accountMoveCount = 0
        var fxSwapCount = 0
        var cashCategoryCount = 0
        var incomplete = false

        data.events
            .filterIsInstance<TransferEvent>()
            .filter { it.localDate() in from..to }
            .forEach { event ->
                val fromInstrument = data.instrument(event.from.instrumentId)
                val toInstrument = data.instrument(event.to.instrumentId)
                if (fromInstrument == null || toInstrument == null) {
                    incomplete = true
                    return@forEach
                }
                // Satellite assets stand outside the rebalancing strategy, so
                // trades into or out of them are not rebalancing activity.
                if (data.category(fromInstrument.categoryId)?.specialRule == SATELLITE_RULE ||
                    data.category(toInstrument.categoryId)?.specialRule == SATELLITE_RULE
                ) {
                    return@forEach
                }
                if (fromInstrument.id == toInstrument.id) {
                    accountMoveCount++
                    return@forEach
                }
                if (fromInstrument.kind == InstrumentKind.CASH &&
                    toInstrument.kind == InstrumentKind.CASH &&
                    !sameCurrency(fromInstrument.currency, toInstrument.currency)
                ) {
                    fxSwapCount++
                    return@forEach
                }
                // Moving money between positions that both sit in the cash
                // category (cash to a money fund, ...) is not a rebalance.
                if (fromInstrument.categoryId == CategoryIds.CASH &&
                    toInstrument.categoryId == CategoryIds.CASH
                ) {
                    cashCategoryCount++
                    return@forEach
                }

                val outFrequency = fxRate(fromInstrument.currency, fxRates)
                val inFrequency = fxRate(toInstrument.currency, fxRates)
                if (outFrequency == null || inFrequency == null) incomplete = true
                val outNative = legValue(fromInstrument, event.fromQuantity, event.fromPrice)
                val inNative = legValue(toInstrument, event.toQuantity, event.toPrice)
                if (outNative == null || inNative == null) incomplete = true

                val direction = when {
                    fromInstrument.categoryId == CategoryIds.CASH ->
                        RebalanceDirection.CASH_TO_ASSET

                    toInstrument.categoryId == CategoryIds.CASH ->
                        RebalanceDirection.ASSET_TO_CASH

                    else -> RebalanceDirection.ASSET_TO_ASSET
                }

                records += RebalanceRecord(
                    date = event.localDate(),
                    direction = direction,
                    fromCategoryId = fromInstrument.categoryId,
                    fromCategoryName = categoryName(data, fromInstrument.categoryId),
                    toCategoryId = toInstrument.categoryId,
                    toCategoryName = categoryName(data, toInstrument.categoryId),
                    outCny = outNative?.let { outFrequency?.let { fx -> it.multiply(fx) } }
                        ?.toCnyAmount(),
                    inCny = inNative?.let { inFrequency?.let { fx -> it.multiply(fx) } }
                        ?.toCnyAmount(),
                    feeLossCny = feeLossCny(event, fxRates),
                )
            }

        val days = records.map { it.date }.distinct().sorted()
        val months = monthlyActivity(records, from, to)
        val totalAmount = records.mapNotNull { it.outCny }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        return RebalanceStats(
            count = records.size,
            dayCount = days.size,
            averageIntervalDays = averageInterval(days),
            recent30DaysCount = records.count { !it.date.isBefore(to.minusDays(29)) },
            amountCny = totalAmount,
            feeLossCny = records.fold(BigDecimal.ZERO) { acc, record ->
                acc.add(record.feeLossCny)
            },
            months = months,
            categoryFlows = categoryFlows(data, records),
            topPairs = topPairs(records),
            accountMoveCount = accountMoveCount,
            fxSwapCount = fxSwapCount,
            cashCategoryCount = cashCategoryCount,
            incomplete = incomplete,
        )
    }

    private fun monthlyActivity(
        records: List<RebalanceRecord>,
        from: LocalDate,
        to: LocalDate,
    ): List<RebalanceMonth> {
        val byMonth = records.groupBy { YearMonth.from(it.date) }
        return generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(to)) }
            .map { month ->
                val monthRecords = byMonth[month].orEmpty()
                RebalanceMonth(
                    month = month,
                    cashToAsset = monthRecords.count {
                        it.direction == RebalanceDirection.CASH_TO_ASSET
                    },
                    assetToCash = monthRecords.count {
                        it.direction == RebalanceDirection.ASSET_TO_CASH
                    },
                    assetToAsset = monthRecords.count {
                        it.direction == RebalanceDirection.ASSET_TO_ASSET
                    },
                    count = monthRecords.size,
                    amountCny = monthRecords.mapNotNull { it.outCny }
                        .fold(BigDecimal.ZERO, BigDecimal::add),
                )
            }
            .toList()
            .asReversed()
    }

    private fun averageInterval(days: List<LocalDate>): Int? {
        if (days.size < 2) return null
        val span = ChronoUnit.DAYS.between(days.first(), days.last())
        return BigDecimal(span)
            .divide(BigDecimal(days.size - 1), 0, RoundingMode.HALF_UP)
            .toInt()
    }

    private fun categoryFlows(
        data: PortfolioData,
        records: List<RebalanceRecord>,
    ): List<RebalanceCategoryFlow> {
        val ids = records.flatMap { listOf(it.fromCategoryId, it.toCategoryId) }.distinct()
        return ids.map { categoryId ->
            val out = records.filter { it.fromCategoryId == categoryId }
            val into = records.filter { it.toCategoryId == categoryId }
            RebalanceCategoryFlow(
                categoryId = categoryId,
                categoryName = categoryName(data, categoryId),
                outCny = out.mapNotNull { it.outCny }.fold(BigDecimal.ZERO, BigDecimal::add),
                inCny = into.mapNotNull { it.inCny }.fold(BigDecimal.ZERO, BigDecimal::add),
                outCount = out.size,
                inCount = into.size,
            )
        }.sortedByDescending { it.outCny.add(it.inCny) }
    }

    private fun topPairs(records: List<RebalanceRecord>): List<RebalancePair> =
        records
            .groupBy { it.fromCategoryId to it.toCategoryId }
            .map { (key, list) ->
                RebalancePair(
                    fromName = list.first().fromCategoryName,
                    toName = list.first().toCategoryName,
                    count = list.size,
                    amountCny = list.mapNotNull { it.outCny }
                        .fold(BigDecimal.ZERO, BigDecimal::add),
                )
            }
            .sortedWith(compareByDescending<RebalancePair> { it.count }.thenByDescending {
                it.amountCny
            })
            .take(3)

    private fun feeLossCny(event: TransferEvent, fxRates: Map<String, BigDecimal>): BigDecimal {
        var total = BigDecimal.ZERO
        if (event.lossAmount.signum() != 0) {
            fxRate(event.lossCurrency, fxRates)?.let {
                total = total.add(event.lossAmount.multiply(it).toCnyAmount())
            }
        }
        val feeAmount = event.feeAmount
        val feeCurrency = event.feeCurrency
        if (feeAmount != null && feeAmount.signum() != 0 && feeCurrency != null) {
            fxRate(feeCurrency, fxRates)?.let {
                total = total.add(feeAmount.multiply(it).toCnyAmount())
            }
        }
        return total
    }

    /** Native value of one leg; null when a quoted leg has no usable price. */
    private fun legValue(
        instrument: Instrument,
        quantity: BigDecimal,
        price: BigDecimal?,
    ): BigDecimal? = when (instrument.kind) {
        InstrumentKind.CASH -> quantity
        InstrumentKind.QUOTED -> price?.let { quantity.multiply(it) }
    }

    private fun sameCurrency(first: String, second: String): Boolean {
        val a = first.uppercase()
        val b = second.uppercase()
        return a == b || (a in CNY_LIKE && b in CNY_LIKE)
    }

    private fun categoryName(data: PortfolioData, categoryId: String): String =
        data.settings.categories.firstOrNull { it.id == categoryId }?.name ?: categoryId

    private fun fxRate(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }

    private val CNY_LIKE = setOf("CNY", "CNH")
}
