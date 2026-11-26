package com.jimbozhang.folioprism.data.web

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire format for the LAN web UI. All decimal values travel as strings so the
 * browser never introduces floating-point drift into money values; the UI
 * converts to numbers only for drawing.
 */
val WEB_JSON = Json {
    encodeDefaults = true
    prettyPrint = false
}

internal fun BigDecimal.plain(): String = toPlainString()

internal fun BigDecimal?.plainOrNull(): String? = this?.toPlainString()

@Serializable
data class ErrorDto(val error: String)

@Serializable
data class VersionDto(val version: String)

@Serializable
data class ApkUploadDto(
    val fileName: String,
    val sizeBytes: Long,
)

@Serializable
data class CountsDto(
    val institutions: Int,
    val instruments: Int,
    val events: Int,
)

@Serializable
data class QuoteFailureDto(
    val instrumentId: String? = null,
    val code: String? = null,
    val name: String? = null,
    val kind: String,
    val source: String,
    val message: String,
)

@Serializable
data class QuoteStatusDto(
    val fetchedAt: String? = null,
    val failures: List<QuoteFailureDto> = emptyList(),
)

@Serializable
data class CategoryViewDto(
    val id: String,
    val name: String,
    val color: String,
    val order: Int,
    val specialRule: String? = null,
    val valueCny: String,
    /** Fraction, e.g. "0.32". */
    val share: String,
    /** Percent points, e.g. "32.0". */
    val targetPct: String,
    val minPct: String,
    val maxPct: String,
    val inRange: Boolean,
    val incomplete: Boolean,
    val targetValueCny: String? = null,
    val minValueCny: String? = null,
    val maxValueCny: String? = null,
    val gapToTargetCny: String? = null,
    val gapToMinCny: String? = null,
    val gapToMaxCny: String? = null,
    /** Fractions against total assets, used to draw the allocation bar. */
    val visualShare: String,
    val visualTarget: String,
    val visualMin: String,
    val visualMax: String,
    val goldMinPriceCnyPerGram: String? = null,
    val goldMaxPriceCnyPerGram: String? = null,
)

@Serializable
data class PositionViewDto(
    val institutionId: String,
    val institutionName: String,
    val instrumentId: String,
    val instrumentCode: String,
    val instrumentName: String,
    val categoryId: String,
    /** "cash" or "quoted". */
    val kind: String,
    val currency: String,
    val unit: String,
    val quantity: String,
    val price: String? = null,
    val valueCny: String? = null,
    val incomplete: Boolean,
)

@Serializable
data class DistributionDto(
    val id: String,
    val name: String,
    val valueCny: String,
    val share: String,
)

@Serializable
data class OverviewDto(
    val version: String,
    val updatedAt: String,
    val asOf: String,
    val counts: CountsDto,
    val totalValueCny: String,
    val dividendValueCny: String,
    val dividendShare: String,
    val portfolioValueCny: String,
    val portfolioShare: String,
    /** Sum of satellite categories; omitted when the data has none. */
    val satelliteValueCny: String? = null,
    val satelliteShare: String? = null,
    val hasMissingPrices: Boolean,
    val categories: List<CategoryViewDto>,
    val positions: List<PositionViewDto>,
    val institutions: List<DistributionDto>,
    val currencies: List<DistributionDto>,
    val quote: QuoteStatusDto,
)

@Serializable
data class ChartPointDto(
    val date: String,
    val value: String? = null,
    val incomplete: Boolean = false,
)

/** One day with a non-market change; positive = money into the chart's scope. */
@Serializable
data class ChartFlowDto(
    val date: String,
    val netCny: String,
)

@Serializable
data class ChartSummaryDto(
    val startValue: String? = null,
    val endValue: String? = null,
    val totalPnl: String? = null,
    /** Gross non-market inflow within the range. */
    val inflowCny: String? = null,
    /** Gross non-market outflow within the range (positive magnitude). */
    val outflowCny: String? = null,
    /** endValue - startValue - (inflow - outflow). */
    val marketPnlCny: String? = null,
)

@Serializable
data class CategoryRefDto(val id: String, val name: String, val color: String)

@Serializable
data class CategoryPnlDto(
    val id: String,
    val name: String,
    val color: String,
    val pnlCny: String? = null,
    val incomplete: Boolean,
)

@Serializable
data class ChartDto(
    val type: String,
    val from: String,
    val to: String,
    val points: List<ChartPointDto> = emptyList(),
    val summary: ChartSummaryDto? = null,
    val category: CategoryRefDto? = null,
    val categoryPnl: List<CategoryPnlDto> = emptyList(),
    val logs: List<String> = emptyList(),
    /** Days with non-market changes, for the flow markers. */
    val flows: List<ChartFlowDto> = emptyList(),
    /** The curve restated without capital flows. */
    val adjustedPoints: List<ChartPointDto> = emptyList(),
)

@Serializable
data class FlowMonthDto(
    val month: String,
    val inflowCny: String,
    val outflowCny: String,
)

@Serializable
data class FlowsDto(val months: List<FlowMonthDto>)

@Serializable
data class RebalanceMonthDto(
    val month: String,
    val cashToAsset: Int,
    val assetToCash: Int,
    val assetToAsset: Int,
    val count: Int,
    val amountCny: String,
)

@Serializable
data class RebalanceFlowDto(
    val categoryId: String,
    val name: String,
    val outCny: String,
    val inCny: String,
    val outCount: Int,
    val inCount: Int,
)

@Serializable
data class RebalancePairDto(
    val fromName: String,
    val toName: String,
    val count: Int,
    val amountCny: String,
)

@Serializable
data class RebalanceStatsDto(
    val count: Int,
    val dayCount: Int,
    val averageIntervalDays: Int? = null,
    val recent30DaysCount: Int,
    val amountCny: String,
    val feeLossCny: String,
    val months: List<RebalanceMonthDto> = emptyList(),
    val categoryFlows: List<RebalanceFlowDto> = emptyList(),
    val topPairs: List<RebalancePairDto> = emptyList(),
    val accountMoveCount: Int,
    val fxSwapCount: Int,
    val cashCategoryCount: Int,
    val incomplete: Boolean,
)

@Serializable
data class DividendGroupDto(
    val id: String,
    val name: String,
    val amountCny: String,
    val count: Int,
)

@Serializable
data class DividendMonthDto(
    val month: String,
    val amountCny: String,
    val count: Int,
)

@Serializable
data class DividendRecordDto(
    val date: String,
    val amountCny: String,
)

@Serializable
data class DividendsDto(
    val totalCny: String,
    val count: Int,
    val incomplete: Boolean,
    val averagePerMonthCny: String?,
    val withdrawnCny: String,
    val withdrawnCount: Int,
    val retainedCny: String,
    val retainedCount: Int,
    val monthly: List<DividendMonthDto> = emptyList(),
    val bySourceCategory: List<DividendGroupDto> = emptyList(),
    val bySourceInstrument: List<DividendGroupDto> = emptyList(),
    val byDestinationAccount: List<DividendGroupDto> = emptyList(),
    val largest: DividendRecordDto? = null,
    val latest: DividendRecordDto? = null,
)
