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

data class CategoryPnl(
    val categoryId: String,
    val categoryName: String,
    val pnlCny: BigDecimal?,
    val incomplete: Boolean,
)

data class HistoryResult(
    val from: LocalDate,
    val to: LocalDate,
    val curve: List<DailyPortfolioValue>,
    val categoryPnl: List<CategoryPnl>,
    val logs: List<String>,
)
