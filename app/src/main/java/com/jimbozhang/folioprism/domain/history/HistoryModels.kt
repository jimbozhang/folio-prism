package com.jimbozhang.folioprism.domain.history

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CachedSeries(
    val auto: Map<String, String> = emptyMap(),
    val manual: Map<String, String> = emptyMap(),
    val lastAutoFetchAt: String? = null,
    val source: String? = null,
    /**
     * Epoch millis until which this series must not be re-fetched: either a
     * successful fetch proved the source has no data near the requested range
     * end (e.g. a delisted or retired instrument), or consecutive fetch
     * failures armed an escalating retry window — so the source is not chased
     * on every app start. Null: no exhaustion record.
     */
    val exhaustedUntilMs: String? = null,
    /**
     * Epoch millis of the last failed fetch, persisted so the failure is not
     * forgotten on restart (the in-memory backoff alone is wiped on every
     * start). Null: no recorded failure.
     */
    val lastFailedAtMs: String? = null,
)

@Serializable
data class PriceCacheData(
    val schemaVersion: Int = 1,
    val updatedAt: String = "",
    val quotes: Map<String, CachedSeries> = emptyMap(),
)

data class DailyPortfolioValue(
    val date: LocalDate,
    /** Null when any held position could not be priced on this day. */
    val totalCny: BigDecimal?,
    /** Null when any position in that category could not be priced. */
    val categoryValues: Map<String, BigDecimal?>,
    val incomplete: Boolean,
)

/**
 * Non-market value changes of one day, split by category.
 *
 * [externalByCategory] is money entering or leaving the portfolio from the
 * outside (deposits, withdrawals, openings, adjustments); [internalByCategory]
 * is movement between categories (transfers, dividends into cash, expenses
 * paid from another category). Internal entries sum to zero across categories.
 */
data class DailyFlow(
    val date: LocalDate,
    val externalByCategory: Map<String, BigDecimal> = emptyMap(),
    val internalByCategory: Map<String, BigDecimal> = emptyMap(),
) {
    /** Net flow of one category, positive = into the category. */
    fun netFor(categoryId: String): BigDecimal =
        (externalByCategory[categoryId] ?: BigDecimal.ZERO) +
            (internalByCategory[categoryId] ?: BigDecimal.ZERO)

    /** Net external flow of the whole portfolio; internal movement cancels out. */
    val netTotal: BigDecimal
        get() = externalByCategory.values.fold(BigDecimal.ZERO, BigDecimal::add)
}

data class CategoryPnl(
    val categoryId: String,
    val categoryName: String,
    val pnlCny: BigDecimal?,
    val incomplete: Boolean,
)

/**
 * P&L of a single instrument over the range, used to drill into a category's
 * P&L. [pnlCny] = end value − start value − instrument flows − embedded
 * transfer losses attributed to the instrument, so the P&Ls of a category's
 * instruments sum to the category P&L (except for cross-category expenses,
 * which keep the category-level attribution convention).
 */
data class InstrumentPnl(
    val categoryId: String,
    val instrumentId: String,
    val name: String,
    val code: String,
    val startValue: BigDecimal?,
    val pnlCny: BigDecimal?,
    val incomplete: Boolean,
)

data class HistoryResult(
    val from: LocalDate,
    val to: LocalDate,
    val curve: List<DailyPortfolioValue>,
    val categoryPnl: List<CategoryPnl>,
    /** Per-instrument P&L for the range, grouped by [InstrumentPnl.categoryId]. */
    val instrumentPnl: List<InstrumentPnl> = emptyList(),
    val logs: List<String>,
    /** Days with non-market value changes, for the flow markers on the curve. */
    val dailyFlows: List<DailyFlow> = emptyList(),
    /**
     * The same curve restated so that capital flows no longer move it: each
     * day's market return is applied to the previous adjusted value, so the
     * line only rises and falls with prices, dividends and fees.
     */
    val adjustedCurve: List<DailyPortfolioValue> = emptyList(),
)
