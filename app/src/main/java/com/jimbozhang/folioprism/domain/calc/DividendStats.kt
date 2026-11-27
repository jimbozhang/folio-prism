package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** One dividend event valued in CNY with the current FX rates. */
data class DividendRecord(
    val date: LocalDate,
    val amountCny: BigDecimal,
    val sourceCategoryId: String?,
    val sourceCategoryName: String,
    val sourceInstrumentId: String?,
    val sourceInstrumentName: String,
    /** Code of the paying instrument; null when the dividend has no source. */
    val sourceInstrumentCode: String?,
    val destinationInstitutionId: String,
    val destinationInstitutionName: String,
    val destinationInstrumentName: String,
    val withdrawn: Boolean,
)

/** A named, aggregated slice of the dividend total. */
data class DividendGroup(
    val id: String,
    val name: String,
    val amountCny: BigDecimal,
    val count: Int,
    /** The dividend events that make up this slice. */
    val records: List<DividendRecord>,
)

data class DividendMonth(
    val month: YearMonth,
    val amountCny: BigDecimal,
    val count: Int,
)

/**
 * Dividend statistics for a date range (inclusive).
 *
 * Interest income is not a dividend and is excluded. Amounts are converted
 * with the current FX rates, matching the rest of the app; a missing rate
 * excludes that event from the totals and marks the result incomplete.
 */
data class DividendStats(
    val totalCny: BigDecimal,
    val count: Int,
    val incomplete: Boolean,
    val monthly: List<DividendMonth>,
    val bySourceCategory: List<DividendGroup>,
    val bySourceInstrument: List<DividendGroup>,
    val byDestinationAccount: List<DividendGroup>,
    val withdrawnCny: BigDecimal,
    val withdrawnCount: Int,
    val retainedCny: BigDecimal,
    val retainedCount: Int,
    val largest: DividendRecord?,
    val latest: DividendRecord?,
    /** Every dividend inside the range, for the drill-down flow listings. */
    val records: List<DividendRecord>,
) {
    val averagePerMonthCny: BigDecimal?
        get() = if (monthly.isEmpty()) {
            null
        } else {
            totalCny.divide(BigDecimal(monthly.size), 2, RoundingMode.HALF_UP)
        }
}

object DividendStatsCalculator {

    fun calculate(
        data: PortfolioData,
        fxRates: Map<String, BigDecimal>,
        from: LocalDate,
        to: LocalDate,
    ): DividendStats {
        var incomplete = false
        val records = mutableListOf<DividendRecord>()

        data.events
            .filterIsInstance<IncomeEvent>()
            .filter { it.kind == IncomeKind.DIVIDEND }
            .forEach { event ->
                val date = event.localDate()
                if (date.isBefore(from) || date.isAfter(to)) return@forEach
                val rate = fxRate(event.currency, fxRates)
                if (rate == null) {
                    incomplete = true
                    return@forEach
                }
                val sourceInstrument = event.source?.let { data.instrument(it.instrumentId) }
                val sourceCategory = sourceInstrument?.let { ins ->
                    data.settings.categories.firstOrNull { it.id == ins.categoryId }
                }
                val destinationInstrument = data.instrument(event.destination.instrumentId)
                val destinationInstitution =
                    data.institutions.firstOrNull { it.id == event.destination.institutionId }
                records += DividendRecord(
                    date = date,
                    amountCny = event.amount.multiply(rate).toCnyAmount(),
                    sourceCategoryId = sourceCategory?.id,
                    sourceCategoryName = sourceCategory?.name ?: NO_SOURCE,
                    sourceInstrumentId = sourceInstrument?.id,
                    sourceInstrumentName = sourceInstrument?.name ?: NO_SOURCE,
                    sourceInstrumentCode = sourceInstrument?.code,
                    destinationInstitutionId = event.destination.institutionId,
                    destinationInstitutionName =
                        destinationInstitution?.name ?: event.destination.institutionId,
                    destinationInstrumentName =
                        destinationInstrument?.name ?: event.destination.instrumentId,
                    // Whether the dividend was immediately paid out to an
                    // external account. The flag is authoritative; the linked
                    // id covers data written by older versions.
                    withdrawn = event.immediateExternalWithdrawal ||
                        event.linkedEventId != null,
                )
            }

        val total = records.fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amountCny) }
        val monthly = records
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .map { (month, list) ->
                DividendMonth(
                    month = month,
                    amountCny = list.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amountCny) },
                    count = list.size,
                )
            }
        val withdrawn = records.filter { it.withdrawn }
        val retained = records.filter { !it.withdrawn }

        return DividendStats(
            totalCny = total,
            count = records.size,
            incomplete = incomplete,
            monthly = monthly.asReversed(),
            bySourceCategory = groups(
                records,
                id = { it.sourceCategoryId ?: NO_SOURCE },
                name = { it.sourceCategoryName },
            ),
            bySourceInstrument = groups(
                records,
                id = { it.sourceInstrumentId ?: NO_SOURCE },
                name = { it.sourceInstrumentName },
            ),
            byDestinationAccount = groups(
                records,
                id = { "${it.destinationInstitutionId}|${it.destinationInstrumentName}" },
                name = { "${it.destinationInstitutionName} · ${it.destinationInstrumentName}" },
            ),
            withdrawnCny = sum(withdrawn),
            withdrawnCount = withdrawn.size,
            retainedCny = sum(retained),
            retainedCount = retained.size,
            largest = records.maxByOrNull { it.amountCny },
            latest = records.maxByOrNull { it.date },
            records = records,
        )
    }

    private fun groups(
        records: List<DividendRecord>,
        id: (DividendRecord) -> String,
        name: (DividendRecord) -> String,
    ): List<DividendGroup> = records
        .groupBy(id)
        .map { (key, list) ->
            DividendGroup(
                id = key,
                name = name(list.first()),
                amountCny = sum(list),
                count = list.size,
                records = list,
            )
        }
        .sortedByDescending { it.amountCny }

    private fun sum(records: List<DividendRecord>): BigDecimal =
        records.fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amountCny) }

    private fun fxRate(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
        when (currency.uppercase()) {
            "CNY", "CNH" -> BigDecimal.ONE
            else -> fxRates[currency.uppercase()]
        }

    private const val NO_SOURCE = "无来源"
}
