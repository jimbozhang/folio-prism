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
)

@Serializable
data class PriceCacheData(
    val schemaVersion: Int = 1,
    val updatedAt: String = "",
    val quotes: Map<String, CachedSeries> = emptyMap(),
    val fx: Map<String, CachedSeries> = emptyMap(),
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
