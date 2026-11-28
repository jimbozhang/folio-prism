package com.jimbozhang.folioprism.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.StackedLineChart
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.DividendRecord
import com.jimbozhang.folioprism.domain.calc.DividendStats
import com.jimbozhang.folioprism.domain.calc.DividendStatsCalculator
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.calc.RebalanceCategoryFlow
import com.jimbozhang.folioprism.domain.calc.RebalanceMonth
import com.jimbozhang.folioprism.domain.calc.RebalanceRecord
import com.jimbozhang.folioprism.domain.calc.RebalancePair
import com.jimbozhang.folioprism.domain.calc.RebalanceStats
import com.jimbozhang.folioprism.domain.calc.RebalanceStatsCalculator
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DailyFlow
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.history.InstrumentPnl
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.HistoryUiState
import com.jimbozhang.folioprism.ui.components.DateRangePickerDialog
import com.jimbozhang.folioprism.ui.components.HoldingRow
import com.jimbozhang.folioprism.ui.components.RangeArrowButton
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatCashCny
import com.jimbozhang.folioprism.ui.components.formatCashNative
import com.jimbozhang.folioprism.ui.components.formatPercent
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.transactions.shortPosition
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

private enum class ChartType(
    val label: String,
    val icon: ImageVector,
    val needsHistory: Boolean,
    val usesRange: Boolean,
) {
    ASSETS("总资产", Icons.AutoMirrored.Outlined.ShowChart, needsHistory = true, usesRange = true),
    CATEGORY_PNL("类别盈亏", Icons.Outlined.PieChart, needsHistory = true, usesRange = true),
    FLOWS("投入取出", Icons.Outlined.Savings, needsHistory = false, usesRange = true),
    REBALANCE("调仓统计", Icons.Outlined.SwapVert, needsHistory = false, usesRange = true),
    DIVIDENDS("分红统计", Icons.Outlined.Payments, needsHistory = false, usesRange = true),
    INSTITUTIONS("机构分布", Icons.Outlined.AccountBalance, needsHistory = false, usesRange = false),
    CURRENCIES("币种分布", Icons.Outlined.CurrencyExchange, needsHistory = false, usesRange = false),
}

/**
 * The range a chart opens with: category P&L starts from the first day of
 * the current month, all other charts from the first day of the current year.
 */
private fun ChartType.defaultRange(today: LocalDate): DateRange =
    if (this == ChartType.CATEGORY_PNL) {
        DateRange.monthToDate(today)
    } else {
        DateRange.yearToDate(today)
    }

private data class MonthFlow(
    val month: YearMonth,
    val inflow: BigDecimal,
    val outflow: BigDecimal,
    /** Every external event in this month; drives the drill-down card. */
    val events: List<ExternalEvent> = emptyList(),
)

private data class DistributionRowData(
    val id: String,
    val name: String,
    val value: BigDecimal,
    val color: Color,
)

private data class InstitutionHolding(
    val name: String,
    val quantity: BigDecimal,
    val unit: String,
    val valueCny: BigDecimal?,
    val isCash: Boolean,
)

private val dateRangeSaver = listSaver<DateRange, Long>(
    save = { listOf(it.from.toEpochDay(), it.to.toEpochDay()) },
    restore = { DateRange(LocalDate.ofEpochDay(it[0]), LocalDate.ofEpochDay(it[1])) },
)

/** Survives the merge of the old CATEGORY_ASSETS tab into ASSETS. */
private val chartTypeSaver = Saver<ChartType, String>(
    save = { it.name },
    restore = { name ->
        ChartType.entries.firstOrNull { it.name == name }
            ?: if (name == "CATEGORY_ASSETS") ChartType.ASSETS else ChartType.CATEGORY_PNL
    },
)

@Composable
fun ChartsScreen(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    historyState: HistoryUiState,
    onLoadHistory: (LocalDate, LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    val positions = remember(data) { EventReplay.replay(data.events) }
    val snapshot = remember(data, positions, quotes) {
        SnapshotCalculator.build(data, positions, LivePriceProvider(quotes), LocalDate.now())
    }
    val categoriesById = remember(data.settings.categories) {
        data.settings.categories.associateBy { it.id }
    }
    val today = LocalDate.now()
    val earliestEvent = remember(data.events) {
        data.events.minOfOrNull { it.localDate() }
    }

    var chart by rememberSaveable(stateSaver = chartTypeSaver) {
        mutableStateOf(ChartType.CATEGORY_PNL)
    }
    var range by rememberSaveable(stateSaver = dateRangeSaver) {
        mutableStateOf(chart.defaultRange(today))
    }
    var showRangePicker by remember { mutableStateOf(false) }
    val orderedCategories = remember(data.settings.categories) {
        data.settings.categories.sortedBy { it.order }
    }
    // "" means the merged page shows the whole portfolio; otherwise the
    // selected category's own curve.
    var assetScopeId by rememberSaveable { mutableStateOf("") }
    val selectedCategory = orderedCategories.firstOrNull { it.id == assetScopeId }

    val monthlyFlows = remember(data.events, range, quotes.fxRates) {
        monthlyFlows(data, quotes.fxRates, range)
    }
    val dividendStats = remember(data, quotes.fxRates, range) {
        DividendStatsCalculator.calculate(data, quotes.fxRates, range.from, range.to)
    }
    val rebalanceStats = remember(data, quotes.fxRates, range) {
        RebalanceStatsCalculator.calculate(data, quotes.fxRates, range.from, range.to)
    }
    val dividendInstrumentColors = remember(data.instruments, data.settings.categories) {
        data.instruments.associate { ins ->
            val category = data.settings.categories.firstOrNull { it.id == ins.categoryId }
            ins.id to (category?.let(::categoryColor) ?: FolioGold)
        }
    }
    val institutionRows = remember(snapshot.brokerValues, data.institutions) {
        institutionRows(snapshot, data)
    }
    val institutionHoldings = remember(positions, snapshot.positionValues, data.instruments) {
        holdingsByInstitution(data, positions, snapshot.positionValues)
    }
    val currencyRows = remember(snapshot.currencyValues) { currencyRows(snapshot) }

    val quotesFingerprint = remember(quotes) { quotesFingerprint(quotes) }
    var historyQuotesFingerprint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chart, range, data.events.size) {
        if (!chart.needsHistory) return@LaunchedEffect
        val ready = historyState as? HistoryUiState.Ready
        if (ready == null || ready.result.from != range.from || ready.result.to != range.to) {
            historyQuotesFingerprint = quotesFingerprint
            onLoadHistory(range.from, range.to)
        }
    }

    // After a quote refresh, if prices or FX rates changed since the displayed
    // result was built, rebuild it for the same range so the chart reflects the
    // new prices. No other setting (chart type, range, ...) is touched.
    LaunchedEffect(quotesFingerprint, chart) {
        if (quotesFingerprint == historyQuotesFingerprint) return@LaunchedEffect
        if (!chart.needsHistory) return@LaunchedEffect
        val ready = historyState as? HistoryUiState.Ready
        if (ready == null ||
            ready.result.from != range.from ||
            ready.result.to != range.to
        ) {
            return@LaunchedEffect
        }
        historyQuotesFingerprint = quotesFingerprint
        onLoadHistory(range.from, range.to)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ChartHeader(
            chart = chart,
            contentPadding = contentPadding,
            onSelected = { selected ->
                // Each chart carries its own default range. When the current
                // range is still the open chart's default (i.e. the user has
                // not customized it), adopt the selected chart's default;
                // otherwise keep the user's choice.
                if (range == chart.defaultRange(today)) {
                    range = selected.defaultRange(today)
                }
                chart = selected
            },
        )
        if (chart == ChartType.ASSETS) {
            CategorySelector(
                categories = orderedCategories,
                selectedId = selectedCategory?.id,
                onSelected = { assetScopeId = it },
            )
        }

        // A fresh scroll position per chart (and per selected scope) so a new
        // page never opens halfway down with its first card clipped.
        val listState = remember(chart, assetScopeId) { LazyListState() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (chart) {
                ChartType.ASSETS -> assetsContent(
                    historyState = historyState,
                    range = range,
                    snapshot = snapshot,
                    data = data,
                    quotes = quotes,
                    positions = positions,
                    categoryId = selectedCategory?.id,
                    categoryName = selectedCategory?.name,
                    onRetry = { onLoadHistory(range.from, range.to) },
                )

                ChartType.CATEGORY_PNL -> categoryPnlContent(
                    historyState = historyState,
                    range = range,
                    onRetry = { onLoadHistory(range.from, range.to) },
                )

                ChartType.FLOWS -> flowsContent(
                    monthly = monthlyFlows,
                    data = data,
                    fxRates = quotes.fxRates,
                    range = range,
                )

                ChartType.REBALANCE -> rebalanceContent(
                    stats = rebalanceStats,
                    range = range,
                )

                ChartType.DIVIDENDS -> dividendsContent(
                    stats = dividendStats,
                    range = range,
                    instrumentColors = dividendInstrumentColors,
                )

                ChartType.INSTITUTIONS -> distributionContent(
                    title = "机构分布",
                    rows = institutionRows,
                    total = snapshot.totalValueCny,
                    holdings = institutionHoldings,
                )

                ChartType.CURRENCIES -> distributionContent(
                    title = "币种分布",
                    rows = currencyRows,
                    total = snapshot.totalValueCny,
                )
            }
        }

        if (chart.usesRange) {
            ChartRangeBar(
                range = range,
                today = today,
                onShift = { range = it },
                onPick = { showRangePicker = true },
            )
        }
    }

    if (showRangePicker) {
        DateRangePickerDialog(
            initial = range,
            today = today,
            earliest = earliestEvent,
            onDismiss = { showRangePicker = false },
            onConfirm = {
                range = it
                showRangePicker = false
            },
        )
    }
}

@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val selectedName = selected?.name ?: "总资产"
    val selectedColor = selected?.let(::categoryColor) ?: FolioGold
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "类别",
                style = MaterialTheme.typography.labelMedium,
                color = FolioMuted,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(selectedColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = selectedName,
                style = MaterialTheme.typography.titleSmall,
                color = FolioInk,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "切换类别",
                tint = FolioMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(FolioGold),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "总资产",
                            color = if (selectedId == null) FolioGold else FolioInk,
                        )
                    }
                },
                trailingIcon = {
                    if (selectedId == null) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = FolioGold,
                        )
                    }
                },
                onClick = {
                    onSelected("")
                    expanded = false
                },
            )
            categories.forEach { category ->
                val isSelected = category.id == selectedId
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor(category)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category.name,
                                color = if (isSelected) FolioGold else FolioInk,
                            )
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = FolioGold,
                            )
                        }
                    },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun categoryColor(category: Category): Color =
    runCatching { Color(android.graphics.Color.parseColor(category.color)) }
        .getOrElse { FolioGold }

@Composable
private fun ChartHeader(
    chart: ChartType,
    contentPadding: PaddingValues,
    onSelected: (ChartType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 14.dp,
                bottom = 4.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chart.label,
                style = MaterialTheme.typography.headlineSmall,
                color = FolioInk,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "切换图表",
                tint = FolioGold,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ChartType.entries.forEach { type ->
                val selected = type == chart
                DropdownMenuItem(
                    text = {
                        Text(
                            text = type.label,
                            color = if (selected) FolioGold else FolioInk,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            tint = if (selected) FolioGold else FolioMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = FolioGold,
                            )
                        }
                    },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ChartRangeBar(
    range: DateRange,
    today: LocalDate,
    onShift: (DateRange) -> Unit,
    onPick: () -> Unit,
) {
    Surface(
        color = Color.White,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RangeArrowButton(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "上一区间",
                enabled = true,
                onClick = { onShift(range.shiftBack()) },
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPick)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = range.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "选择日期区间",
                    tint = FolioMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            RangeArrowButton(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "下一区间",
                enabled = range.canShiftForward(today),
                onClick = { onShift(range.shiftForward(today)) },
            )
        }
    }
}

/**
 * The merged 总资产 page. With no category selected it shows the whole
 * portfolio; with a category selected it shows that category's curve, P&L,
 * current amount and holdings. Both scopes share the range and chart modes.
 */
private fun LazyListScope.assetsContent(
    historyState: HistoryUiState,
    range: DateRange,
    snapshot: PortfolioSnapshot,
    data: PortfolioData,
    quotes: QuoteSnapshot,
    positions: Map<PositionKey, BigDecimal>,
    categoryId: String?,
    categoryName: String?,
    onRetry: () -> Unit,
) {
    val ready = (historyState as? HistoryUiState.Ready)
        ?.takeIf { it.result.from == range.from && it.result.to == range.to }

    when {
        ready != null -> {
            val pnlRows: List<CategoryPnl>
            val curve: List<DailyPortfolioValue>
            val adjusted: List<DailyPortfolioValue>
            if (categoryId == null) {
                pnlRows = ready.result.categoryPnl
                curve = ready.result.curve
                adjusted = ready.result.adjustedCurve
            } else {
                val pnl = ready.result.categoryPnl.firstOrNull { it.categoryId == categoryId }
                    ?: CategoryPnl(categoryId, categoryName ?: categoryId, null, true)
                fun categoryStream(points: List<DailyPortfolioValue>) = points.map { point ->
                    DailyPortfolioValue(
                        date = point.date,
                        totalCny = point.categoryValues[categoryId],
                        categoryValues = emptyMap(),
                        incomplete = point.categoryValues[categoryId] == null && point.incomplete,
                    )
                }
                pnlRows = listOf(pnl)
                curve = categoryStream(ready.result.curve)
                adjusted = categoryStream(ready.result.adjustedCurve)
            }
            item {
                HistorySummary(
                    curve = curve,
                    flows = ready.result.dailyFlows,
                    categoryId = categoryId,
                    categoryPnl = pnlRows,
                )
            }
            item {
                AssetChartSection(
                    curve = curve,
                    adjustedCurve = adjusted,
                    flows = ready.result.dailyFlows,
                    categoryId = categoryId,
                )
            }
        }

        historyState is HistoryUiState.Error -> item { ErrorBlock(historyState.message, onRetry) }
        historyState is HistoryUiState.Loading -> item { LoadingBlock(historyState.message) }
        else -> item { LoadingBlock() }
    }
    item { CategoryTable(snapshot, onlyCategoryId = categoryId) }
    if (categoryId != null) {
        item {
            CategoryHoldingsBlock(
                data = data,
                quotes = quotes,
                snapshot = snapshot,
                positions = positions,
                categoryId = categoryId,
            )
        }
    }
}

@Composable
private fun CategoryHoldingsBlock(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    snapshot: PortfolioSnapshot,
    positions: Map<PositionKey, BigDecimal>,
    categoryId: String,
) {
    val holdings = remember(categoryId, positions, data.instruments, snapshot) {
        positions.entries
            .filter { (key, quantity) ->
                quantity.signum() != 0 &&
                    data.instrument(key.instrumentId)?.categoryId == categoryId
            }
            .sortedByDescending { (key, _) -> snapshot.positionValues[key] ?: BigDecimal.ZERO }
    }
    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle("持仓明细")
            if (holdings.isEmpty()) {
                Text(
                    text = "该类别暂无持仓",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            } else {
                holdings.forEachIndexed { index, (key, quantity) ->
                    if (index > 0) {
                        HorizontalDivider(color = FolioDivider)
                    }
                    HoldingRow(
                        data = data,
                        quotes = quotes,
                        snapshot = snapshot,
                        key = key,
                        quantity = quantity,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.categoryPnlContent(
    historyState: HistoryUiState,
    range: DateRange,
    onRetry: () -> Unit,
) {
    val ready = (historyState as? HistoryUiState.Ready)
        ?.takeIf { it.result.from == range.from && it.result.to == range.to }

    when {
        ready != null -> {
            val pnl = ready.result.categoryPnl
            val incomplete = pnl.any { it.incomplete || it.pnlCny == null }
            val totalPnl = if (incomplete) {
                null
            } else {
                pnl.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.pnlCny ?: BigDecimal.ZERO) }
            }
            // Value of each category at the start of the range, used as the
            // denominator of the per-category range return shown below.
            val startValues = pnl.associate { row ->
                row.categoryId to ready.result.curve
                    .firstOrNull { point -> point.categoryValues[row.categoryId] != null }
                    ?.categoryValues
                    ?.get(row.categoryId)
            }
            item { BigPnlHeader("类别盈亏合计", totalPnl, incomplete) }
            item {
                CategoryPnlBlock(
                    rows = pnl,
                    startValues = startValues,
                    instruments = ready.result.instrumentPnl,
                    range = range,
                )
            }
        }

        historyState is HistoryUiState.Error -> item { ErrorBlock(historyState.message, onRetry) }
        historyState is HistoryUiState.Loading -> item { LoadingBlock(historyState.message) }
        else -> item { LoadingBlock() }
    }
}

private fun LazyListScope.flowsContent(
    monthly: List<MonthFlow>,
    data: PortfolioData,
    fxRates: Map<String, BigDecimal>,
    range: DateRange,
) {
    val inflow = monthly.fold(BigDecimal.ZERO) { acc, flow -> acc.add(flow.inflow) }
    val outflow = monthly.fold(BigDecimal.ZERO) { acc, flow -> acc.add(flow.outflow) }
    val net = inflow.subtract(outflow)

    item {
        BigValueHeader(
            label = "区间净投入",
            value = formatCashCny(net),
            color = if (net.signum() < 0) FolioGreen else FolioInk,
        )
    }
    item {
        FlowsMonthlyBlock(
            monthly = monthly,
            inflow = inflow,
            outflow = outflow,
            net = net,
            data = data,
            fxRates = fxRates,
            range = range,
        )
    }
}

@Composable
private fun FlowsMonthlyBlock(
    monthly: List<MonthFlow>,
    inflow: BigDecimal,
    outflow: BigDecimal,
    net: BigDecimal,
    data: PortfolioData,
    fxRates: Map<String, BigDecimal>,
    range: DateRange,
) {
    // Which month is expanded; reset whenever the range changes.
    var expandedId by remember(range) { mutableStateOf<String?>(null) }
    if (monthly.isEmpty()) {
        EmptyBlock("所选区间内没有外部本金流入/流出事件。")
        return
    }
    WhiteBlock {
        SummaryLine("累计投入", formatCashCny(inflow))
        HorizontalDivider(color = FolioDivider)
        SummaryLine("累计取出", formatCashCny(outflow))
        HorizontalDivider(color = FolioDivider)
        SummaryLine("净投入", formatCashCny(net), emphasize = true)
        monthly.asReversed().forEach { flow ->
            HorizontalDivider(color = FolioDivider)
            val id = flow.month.toString()
            val expanded = expandedId == id
            FlowRowItem(
                flow = flow,
                expanded = expanded,
                onToggle = { expandedId = if (expanded) null else id },
            )
            if (expanded && flow.events.isNotEmpty()) {
                ExternalFlowsCard(flow.events, data, fxRates)
            }
        }
    }
}

private data class DividendBarRow(
    val id: String,
    val name: String,
    val amountCny: BigDecimal,
    val count: Int,
    val color: Color,
    /** The dividend flows that make up this row. */
    val flows: List<DividendRecord>,
)

private fun LazyListScope.dividendsContent(
    stats: DividendStats,
    range: DateRange,
    instrumentColors: Map<String, Color>,
) {
    item {
        BigValueHeader(
            label = "区间分红合计",
            value = formatCashCny(stats.totalCny),
            color = FolioInk,
        )
    }
    if (stats.count == 0) {
        item { EmptyBlock("所选区间内没有分红事件。") }
        return
    }
    item {
        WhiteBlock {
            SummaryLine("分红笔数", "${stats.count} 笔")
            HorizontalDivider(color = FolioDivider)
            stats.averagePerMonthCny?.let { average ->
                SummaryLine("月均分红", formatCashCny(average))
                HorizontalDivider(color = FolioDivider)
            }
            stats.largest?.let { largest ->
                SummaryLine("单笔最大", "${formatCashCny(largest.amountCny)} · ${shortDate(largest.date)}")
                HorizontalDivider(color = FolioDivider)
            }
            stats.latest?.let { latest ->
                SummaryLine("最近一笔", "${shortDate(latest.date)} · ${formatCashCny(latest.amountCny)}")
            }
            if (stats.incomplete) {
                HorizontalDivider(color = FolioDivider)
                Text(
                    text = "部分分红因缺少汇率未计入",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioCoral,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    item {
        DividendBarsBlock(
            title = "来源标的",
            range = range,
            rows = stats.bySourceInstrument.map { group ->
                DividendBarRow(
                    id = group.id,
                    name = group.name,
                    amountCny = group.amountCny,
                    count = group.count,
                    color = instrumentColors[group.id] ?: FolioMuted,
                    flows = group.records,
                )
            },
            total = stats.totalCny,
        )
    }
    item {
        DividendBarsBlock(
            title = "月度分红",
            range = range,
            rows = stats.monthly.map { month ->
                DividendBarRow(
                    id = month.month.toString(),
                    name = "${month.month.year} 年 ${month.month.monthValue} 月",
                    amountCny = month.amountCny,
                    count = month.count,
                    color = FolioGold,
                    flows = stats.records.filter { record ->
                        YearMonth.from(record.date) == month.month
                    },
                )
            },
            total = stats.totalCny,
        )
    }
    item {
        DividendBarsBlock(
            title = "去向分布",
            range = range,
            rows = listOf(
                DividendBarRow(
                    id = "withdrawn",
                    name = "立即转出",
                    amountCny = stats.withdrawnCny,
                    count = stats.withdrawnCount,
                    color = FolioBlue,
                    flows = stats.records.filter { it.withdrawn },
                ),
                DividendBarRow(
                    id = "retained",
                    name = "留存现金",
                    amountCny = stats.retainedCny,
                    count = stats.retainedCount,
                    color = FolioGreen,
                    flows = stats.records.filter { !it.withdrawn },
                ),
            ).filter { it.count > 0 },
            total = stats.totalCny,
        )
    }
}

@Composable
private fun DividendBarsBlock(
    title: String,
    range: DateRange,
    rows: List<DividendBarRow>,
    total: BigDecimal,
) {
    // Which row is expanded; reset whenever the range changes.
    var expandedId by remember(range) { mutableStateOf<String?>(null) }
    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle(title)
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = FolioDivider)
                }
                val expanded = expandedId == row.id
                DividendBarRowItem(
                    row = row,
                    total = total,
                    expanded = expanded,
                    onToggle = { expandedId = if (expanded) null else row.id },
                )
                if (expanded && row.flows.isNotEmpty()) {
                    DividendFlowsCard(row.flows)
                }
            }
        }
    }
}

@Composable
private fun DividendBarRowItem(
    row: DividendBarRow,
    total: BigDecimal,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val share = if (total.signum() > 0) {
        row.amountCny.divide(total, 6, java.math.RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    Column(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(row.color),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = FolioInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCashCny(row.amountCny),
                style = MaterialTheme.typography.titleSmall,
                color = FolioInk,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatPercent(share),
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起流水" else "展开流水",
                tint = FolioMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "${row.count} 笔",
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.padding(start = 16.dp),
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF1F1F1)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(row.color),
            )
        }
    }
}

/**
 * Drill-down card below an expanded dividend row: every dividend in that
 * slice, newest first, with its paying instrument, destination account and
 * CNY amount; immediately withdrawn ones are tagged.
 */
@Composable
private fun DividendFlowsCard(flows: List<DividendRecord>) {
    val sorted = flows.sortedByDescending { it.date }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
    ) {
        sorted.forEachIndexed { index, record ->
            if (index > 0) {
                HorizontalDivider(color = FolioDivider)
            }
            DividendFlowRowItem(record)
        }
    }
}

@Composable
private fun DividendFlowRowItem(record: DividendRecord) {
    val source = record.sourceInstrumentCode?.let { code -> "$code ${record.sourceInstrumentName}" }
        ?: record.sourceInstrumentName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Three lines so long instrument names get the full width: the paying
        // instrument, the destination account, then date and CNY amount.
        Text(
            text = source,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "入账 ${record.destinationInstitutionName} · ${record.destinationInstrumentName}",
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.date.toString() +
                    (if (record.withdrawn) " · 立即转出" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = FolioMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCashCny(record.amountCny),
                style = MaterialTheme.typography.labelLarge,
                color = FolioInk,
            )
        }
    }
}

/**
 * Drill-down card below an expanded month row: every external deposit or
 * withdrawal in that month, newest first, with its cash position, direction
 * and amount; amounts in CNY at the current rate, falling back to native
 * when no FX is available.
 */
@Composable
private fun ExternalFlowsCard(
    events: List<ExternalEvent>,
    data: PortfolioData,
    fxRates: Map<String, BigDecimal>,
) {
    val sorted = events.sortedByDescending { it.date }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
    ) {
        sorted.forEachIndexed { index, event ->
            if (index > 0) {
                HorizontalDivider(color = FolioDivider)
            }
            ExternalFlowRowItem(event, data, fxRates)
        }
    }
}

@Composable
private fun ExternalFlowRowItem(
    event: ExternalEvent,
    data: PortfolioData,
    fxRates: Map<String, BigDecimal>,
) {
    val isIn = event.direction == ExternalDirection.IN
    val amountCny = fxToCny(event.currency, fxRates)
        ?.let { event.amount.multiply(it).toCnyAmount() }
    val noteSuffix = event.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Three lines so long names get the full width: the cash position,
        // the direction and context, then date and amount.
        Text(
            text = shortPosition(data, event.position),
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildString {
                append(if (isIn) "入金" else "出金")
                append(" · ")
                append(
                    if (event.classification == ExternalClassification.CONSUMPTION) "消费"
                    else "本金",
                )
                event.counterpartyLabel?.let { append(" · 外部账户 ").append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.localDate().toString() + noteSuffix,
                style = MaterialTheme.typography.labelSmall,
                color = FolioMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = (if (isIn) "+" else "-") +
                    (amountCny?.let { formatCashCny(it) }
                        ?: formatCashNative(event.amount, event.currency)),
                style = MaterialTheme.typography.labelLarge,
                color = FolioInk,
            )
        }
    }
}

private fun shortDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

private fun LazyListScope.rebalanceContent(stats: RebalanceStats, range: DateRange) {
    if (stats.count == 0) {
        item { EmptyBlock("所选区间内没有调仓（不同标的之间的转账）。") }
        item { RebalanceFootnote(stats) }
        return
    }
    item {
        WhiteBlock {
            SummaryLine("调仓笔数", "${stats.count} 笔")
            HorizontalDivider(color = FolioDivider)
            SummaryLine("涉及天数", "${stats.dayCount} 天")
            HorizontalDivider(color = FolioDivider)
            SummaryLine(
                "平均间隔",
                stats.averageIntervalDays?.let { "约 $it 天一次" } ?: "--",
            )
            HorizontalDivider(color = FolioDivider)
            SummaryLine("近 30 天", "${stats.recent30DaysCount} 次")
            HorizontalDivider(color = FolioDivider)
            SummaryLine("调仓金额", formatCny(stats.amountCny))
            HorizontalDivider(color = FolioDivider)
            SummaryLine("手续费/损耗", formatCny(stats.feeLossCny))
            if (stats.incomplete) {
                Text(
                    text = "部分调仓缺少价格或汇率，金额可能不完整",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioCoral,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    item { RebalanceMonthlyChart(stats.months) }
    if (stats.categoryFlows.isNotEmpty()) {
        item {
            RebalanceFlowBlock(
                flows = stats.categoryFlows,
                range = range,
                records = stats.records,
            )
        }
    }
    if (stats.topPairs.isNotEmpty()) {
        item {
            RebalancePairsBlock(
                pairs = stats.topPairs,
                range = range,
                records = stats.records,
            )
        }
    }
    item { RebalanceFootnote(stats) }
}

@Composable
private fun RebalanceFootnote(stats: RebalanceStats) {
    val excluded = buildList {
        if (stats.accountMoveCount > 0) add("同一标的换机构 ${stats.accountMoveCount} 笔")
        if (stats.fxSwapCount > 0) add("现金换汇 ${stats.fxSwapCount} 笔")
        if (stats.cashCategoryCount > 0) add("现金类别内互转 ${stats.cashCategoryCount} 笔")
    }
    val text = buildString {
        append("口径：只统计不同标的之间的转账，同一天多笔计 1 个调仓日")
        if (excluded.isNotEmpty()) {
            append("；${excluded.joinToString("、")}未计入")
        }
        append("。")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = FolioMuted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun RebalanceMonthlyChart(months: List<RebalanceMonth>) {
    val ordered = months.asReversed()
    val maxCount = ordered.maxOfOrNull { it.count } ?: 0
    val hasAssetSwap = ordered.any { it.assetToAsset > 0 }
    val swapColor = Color(0xFFB2BEC3)
    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle("月度调仓")
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                val bottomPad = 1.dp.toPx()
                val chartHeight = size.height - bottomPad
                val step = if (ordered.isEmpty()) size.width else size.width / ordered.size
                val barWidth = (step * 0.55f).coerceAtLeast(2.dp.toPx())
                drawLine(
                    color = FolioDivider,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )
                ordered.forEachIndexed { index, month ->
                    if (month.count <= 0 || maxCount <= 0) return@forEachIndexed
                    val centerX = step * index + step / 2
                    val left = centerX - barWidth / 2
                    val segments = listOf(
                        (chartHeight * month.assetToAsset / maxCount) to swapColor,
                        (chartHeight * month.assetToCash / maxCount) to FolioBlue,
                        (chartHeight * month.cashToAsset / maxCount) to FolioGold,
                    ).filter { (height, _) -> height > 0f }
                    var bottom = chartHeight
                    segments.forEachIndexed { segmentIndex, (height, color) ->
                        val segmentTop = bottom - height
                        if (segmentIndex == segments.lastIndex) {
                            // Only the top of the bar is rounded; the junctions
                            // between colors and the baseline stay square.
                            val radius = 2.dp.toPx()
                                .coerceAtMost(minOf(barWidth / 2f, height))
                            val path = Path().apply {
                                moveTo(left, bottom)
                                lineTo(left, segmentTop + radius)
                                quadraticTo(left, segmentTop, left + radius, segmentTop)
                                lineTo(left + barWidth - radius, segmentTop)
                                quadraticTo(
                                    left + barWidth,
                                    segmentTop,
                                    left + barWidth,
                                    segmentTop + radius,
                                )
                                lineTo(left + barWidth, bottom)
                                close()
                            }
                            drawPath(path, color = color)
                        } else {
                            drawRect(
                                color = color,
                                topLeft = Offset(left, segmentTop),
                                size = Size(barWidth, height),
                            )
                        }
                        bottom -= height
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = ordered.firstOrNull()?.month?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
                Text(
                    text = ordered.lastOrNull()?.month?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "■ 现金→资产",
                    style = MaterialTheme.typography.labelSmall,
                    color = FolioGold,
                )
                Text(
                    text = "■ 资产→现金",
                    style = MaterialTheme.typography.labelSmall,
                    color = FolioBlue,
                )
                if (hasAssetSwap) {
                    Text(
                        text = "■ 资产→资产",
                        style = MaterialTheme.typography.labelSmall,
                        color = swapColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun RebalanceFlowBlock(
    flows: List<RebalanceCategoryFlow>,
    range: DateRange,
    records: List<RebalanceRecord>,
) {
    // Net flow per category: positive = net bought into the category,
    // negative = net sold out of it.
    val nets = flows
        .map { it to it.inCny.subtract(it.outCny) }
        .sortedByDescending { (_, net) -> net }
    val maxGain = nets.map { it.second }.maxOrNull()?.coerceAtLeast(BigDecimal.ZERO)
        ?: BigDecimal.ZERO
    val maxLoss = nets.map { it.second }.minOrNull()?.abs() ?: BigDecimal.ZERO
    val combined = maxGain.add(maxLoss)
    val zeroFraction = if (combined.signum() > 0) {
        maxLoss.divide(combined, 10, RoundingMode.HALF_UP).toFloat()
    } else {
        0.5f
    }
    // Which category row is expanded; reset whenever the range changes.
    var expandedId by remember(range) { mutableStateOf<String?>(null) }
    WhiteBlock {
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("调仓流向")
            }
            nets.forEachIndexed { index, (flow, net) ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = FolioDivider,
                    )
                }
                val expanded = expandedId == flow.categoryId
                RebalanceFlowRow(
                    flow = flow,
                    net = net,
                    maxGain = maxGain,
                    maxLoss = maxLoss,
                    zeroFraction = zeroFraction,
                    expanded = expanded,
                    onToggle = { expandedId = if (expanded) null else flow.categoryId },
                )
                if (expanded) {
                    RebalanceFlowsCard(
                        flows = records.filter {
                            it.fromCategoryId == flow.categoryId ||
                                it.toCategoryId == flow.categoryId
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RebalanceFlowRow(
    flow: RebalanceCategoryFlow,
    net: BigDecimal,
    maxGain: BigDecimal,
    maxLoss: BigDecimal,
    zeroFraction: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val color = pnlColor(net)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(flow.categoryId),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = flow.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${flow.outCount + flow.inCount} 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            ) {
                val barHeight = size.height
                val radius = CornerRadius(barHeight / 2f)
                drawRoundRect(
                    color = Color(0xFFF1F1F1),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, barHeight),
                    cornerRadius = radius,
                )
                val zeroX = size.width * zeroFraction
                if (net.signum() > 0 && maxGain.signum() > 0) {
                    val barWidth = (net.toFloat() / maxGain.toFloat())
                        .coerceIn(0f, 1f) * (size.width - zeroX)
                    if (barWidth > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(zeroX, 0f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = radius,
                        )
                    }
                } else if (net.signum() < 0 && maxLoss.signum() > 0) {
                    val barWidth = (net.abs().toFloat() / maxLoss.toFloat())
                        .coerceIn(0f, 1f) * zeroX
                    if (barWidth > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(zeroX - barWidth, 0f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = radius,
                        )
                    }
                }
                drawLine(
                    color = FolioMuted.copy(alpha = 0.45f),
                    start = Offset(zeroX, -1.dp.toPx()),
                    end = Offset(zeroX, barHeight + 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = signedCny(net),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "收起流水" else "展开流水",
            tint = FolioMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun signedCny(value: BigDecimal): String = when {
    value.signum() > 0 -> "+" + formatCny(value)
    value.signum() < 0 -> "-" + formatCny(value.abs())
    else -> formatCny(value)
}

@Composable
private fun RebalancePairsBlock(
    pairs: List<RebalancePair>,
    range: DateRange,
    records: List<RebalanceRecord>,
) {
    // Which pair row is expanded; reset whenever the range changes.
    var expandedId by remember(range) { mutableStateOf<String?>(null) }
    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle("最频繁的调仓方向")
            pairs.forEachIndexed { index, pair ->
                if (index > 0) {
                    HorizontalDivider(color = FolioDivider)
                }
                val id = "${pair.fromCategoryId}|${pair.toCategoryId}"
                val expanded = expandedId == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clickable(onClick = {
                            expandedId = if (expanded) null else id
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pair.fromName} → ${pair.toName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FolioInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${pair.count} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = FolioMuted,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatCny(pair.amountCny),
                        style = MaterialTheme.typography.bodySmall,
                        color = FolioInk,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector =
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起流水" else "展开流水",
                        tint = FolioMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (expanded) {
                    RebalanceFlowsCard(
                        records.filter {
                            it.fromCategoryId == pair.fromCategoryId &&
                                it.toCategoryId == pair.toCategoryId
                        },
                    )
                }
            }
        }
    }
}

/**
 * Drill-down card below an expanded 调仓流向 / 调仓方向 row: every
 * rebalancing trade in that slice, newest first, with both instruments and
 * the sold-leg CNY value.
 */
@Composable
private fun RebalanceFlowsCard(
    flows: List<RebalanceRecord>,
    modifier: Modifier = Modifier,
) {
    val sorted = flows.sortedByDescending { it.date }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
    ) {
        sorted.forEachIndexed { index, record ->
            if (index > 0) {
                HorizontalDivider(color = FolioDivider)
            }
            RebalanceFlowRowItem(record)
        }
    }
}

@Composable
private fun RebalanceFlowRowItem(record: RebalanceRecord) {
    val amount = record.outCny ?: record.inCny
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Three lines so both instruments get the full width: the sold leg,
        // the bought leg, then date and CNY amount.
        Text(
            text = "转出 ${record.fromInstrumentLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "转入 ${record.toInstrumentLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.date.toString() +
                    (if (record.feeLossCny.signum() != 0) {
                        " · 损耗 " + formatCny(record.feeLossCny)
                    } else {
                        ""
                    }),
                style = MaterialTheme.typography.labelSmall,
                color = FolioMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCny(amount),
                style = MaterialTheme.typography.labelLarge,
                color = FolioInk,
            )
        }
    }
}

private fun LazyListScope.distributionContent(
    title: String,
    rows: List<DistributionRowData>,
    total: BigDecimal,
    holdings: Map<String, List<InstitutionHolding>>? = null,
) {
    item {
        if (rows.isEmpty()) {
            EmptyBlock("暂无$title 数据。")
        } else {
            // Which row is expanded; only used when drill-down details exist.
            var expandedId by remember { mutableStateOf<String?>(null) }
            WhiteBlock {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = FolioDivider)
                    }
                    val detail = holdings?.get(row.id)?.takeIf { it.isNotEmpty() }
                    val expanded = expandedId == row.id
                    DistributionRowItem(
                        row = row,
                        total = total,
                        detail = detail,
                        expanded = expanded,
                        onToggle = {
                            expandedId = if (expanded) null else row.id
                        },
                    )
                    if (expanded && detail != null) {
                        InstitutionHoldingsCard(detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummary(
    curve: List<DailyPortfolioValue>,
    flows: List<DailyFlow>,
    categoryId: String?,
    categoryPnl: List<CategoryPnl>,
) {
    val start = curve.firstOrNull { it.totalCny != null }?.totalCny
    val end = curve.lastOrNull { it.totalCny != null }?.totalCny
    val change = if (start != null && end != null) end.subtract(start) else null
    val incomplete = categoryPnl.any { it.incomplete || it.pnlCny == null }
    val pnl = if (!incomplete) {
        categoryPnl.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.pnlCny ?: BigDecimal.ZERO) }
    } else {
        null
    }

    // Non-market flows inside the range: external money for the whole
    // portfolio, external plus cross-category movement for one category.
    var inflow = BigDecimal.ZERO
    var outflow = BigDecimal.ZERO
    flows.forEach { flow ->
        val amounts = if (categoryId == null) {
            flow.externalByCategory.values
        } else {
            // A day can have offsetting entries (a dividend arriving and being
            // withdrawn immediately); count both, not just their net.
            listOfNotNull(
                flow.externalByCategory[categoryId]?.takeIf { it.signum() != 0 },
                flow.internalByCategory[categoryId]?.takeIf { it.signum() != 0 },
            )
        }
        amounts.forEach { amount ->
            if (amount.signum() > 0) {
                inflow = inflow.add(amount)
            } else if (amount.signum() < 0) {
                outflow = outflow.add(amount.abs())
            }
        }
    }
    val netFlow = inflow.subtract(outflow)
    val marketPnl = change?.subtract(netFlow)
    val flowLabel = if (categoryId == null) "出入金" else "资金流"
    val flowSub = if (categoryId == null) {
        "入金 ${formatCashCny(inflow)} · 出金 ${formatCashCny(outflow)}"
    } else {
        "流入 ${formatCny(inflow)} · 流出 ${formatCny(outflow)}"
    }
    val marketColor = when {
        marketPnl == null || marketPnl.signum() == 0 -> FolioInk
        marketPnl.signum() > 0 -> FolioCoral
        else -> FolioGreen
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigPnlHeader(label = "区间盈亏", pnl = pnl, incomplete = incomplete)
        WhiteBlock {
            Spacer(modifier = Modifier.height(4.dp))
            SummaryLine("期初", formatCny(start))
            HorizontalDivider(color = FolioDivider)
            SummaryLine(
                label = flowLabel,
                value = if (categoryId == null) formatCashCny(netFlow) else formatCny(netFlow),
                sub = flowSub,
            )
            HorizontalDivider(color = FolioDivider)
            SummaryLine(
                label = "市场盈亏",
                value = formatCny(marketPnl),
                sub = "价格 + 分红 − 费用",
                color = marketColor,
            )
            HorizontalDivider(color = FolioDivider)
            SummaryLine("期末", formatCny(end), emphasize = true)
        }
    }
}

/**
 * The asset curve with its flow markers, plus a toggle between the actual
 * asset curve and the flow-adjusted ("market only") one.
 */
@Composable
private fun AssetChartSection(
    curve: List<DailyPortfolioValue>,
    adjustedCurve: List<DailyPortfolioValue>,
    flows: List<DailyFlow>,
    categoryId: String?,
) {
    var marketOnly by rememberSaveable { mutableStateOf(false) }
    var showMarkers by rememberSaveable { mutableStateOf(false) }
    val hasAdjusted = adjustedCurve.isNotEmpty()
    val display = if (marketOnly && hasAdjusted) adjustedCurve else curve
    val rangeReturn = if (marketOnly) adjustedReturn(adjustedCurve) else null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartModeChip(
                label = "含资金流",
                selected = !marketOnly,
                onClick = { marketOnly = false },
            )
            ChartModeChip(
                label = "剔除资金流",
                selected = marketOnly,
                onClick = { marketOnly = true },
            )
            ChartModeChip(
                label = "资金标记",
                selected = showMarkers,
                onClick = { showMarkers = !showMarkers },
            )
        }
        if (rangeReturn != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "区间市场收益 " + formatPercent(rangeReturn),
                    style = MaterialTheme.typography.labelMedium,
                    color = pnlColor(rangeReturn),
                )
            }
        }
        HistoryLineChart(display, flows, categoryId, showMarkers)
    }
}

@Composable
private fun ChartModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFFDEFD8) else Color(0xFFF3F4F3))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) FolioGold else FolioMuted,
        )
    }
}

/** Total market return of a flow-adjusted curve, as a fraction. */
private fun adjustedReturn(curve: List<DailyPortfolioValue>): BigDecimal? {
    val first = curve.firstOrNull { it.totalCny != null }?.totalCny ?: return null
    val last = curve.lastOrNull { it.totalCny != null }?.totalCny ?: return null
    if (first.signum() <= 0) return null
    return last.subtract(first).divide(first, 6, RoundingMode.HALF_UP)
}

@Composable
private fun HistoryLineChart(
    curve: List<DailyPortfolioValue>,
    flows: List<DailyFlow> = emptyList(),
    categoryId: String? = null,
    showMarkers: Boolean = false,
) {
    val values = curve.mapNotNull { it.totalCny }
    if (values.isEmpty()) {
        EmptyBlock("没有可用的历史价格，暂时无法绘制曲线。")
        return
    }
    val minValue = values.minOrNull() ?: BigDecimal.ZERO
    val maxValue = values.maxOrNull() ?: BigDecimal.ZERO
    val lineColor = FolioGold
    val gridColor = FolioDivider
    val minFloat = minValue.toFloat()
    val maxFloat = maxValue.toFloat()
    val flat = maxFloat - minFloat <= 0f
    val span = if (flat) 1f else maxFloat - minFloat
    val singlePoint = values.size == 1
    // Days with a non-market change: external money gets an arrow, movement
    // between categories gets a neutral diamond.
    // One marker per day with a non-market change. For a single category any
    // money arriving (from outside or from another category) is an inflow, so
    // no separate "reallocation" marker is needed; the total curve only counts
    // money crossing the portfolio boundary.
    val flowMarkers = remember(flows, categoryId, showMarkers) {
        if (!showMarkers) {
            return@remember emptyMap<LocalDate, BigDecimal>()
        }
        flows.mapNotNull { flow ->
            val net = if (categoryId == null) flow.netTotal else flow.netFor(categoryId)
            val anyChange = if (categoryId == null) {
                net.signum() != 0
            } else {
                (flow.externalByCategory[categoryId]?.signum() ?: 0) != 0 ||
                    (flow.internalByCategory[categoryId]?.signum() ?: 0) != 0
            }
            if (!anyChange) {
                null
            } else {
                // Same-day opposite entries cancel in the net; fall back to the
                // external leg so the day still shows a direction.
                val direction = when {
                    net.signum() != 0 -> net
                    categoryId != null ->
                        flow.externalByCategory[categoryId]
                            ?: flow.internalByCategory.getValue(categoryId)
                    else -> net
                }
                flow.date to direction
            }
        }.toMap()
    }
    val hasMarkers = flowMarkers.isNotEmpty()

    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                val padding = 6.dp.toPx()
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2
                val stepX = if (curve.size > 1) chartWidth / (curve.size - 1) else 0f
                val bottom = padding + chartHeight

                repeat(3) { index ->
                    val y = padding + chartHeight * (index / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(padding, y),
                        end = Offset(padding + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (singlePoint) {
                    val y = padding + chartHeight / 2f
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(padding, y),
                        end = Offset(padding + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val gradient = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
                    startY = padding,
                    endY = bottom,
                )
                val segment = mutableListOf<Offset>()

                fun flushSegment() {
                    if (segment.size >= 2) {
                        val line = Path()
                        line.moveTo(segment.first().x, segment.first().y)
                        segment.drop(1).forEach { point -> line.lineTo(point.x, point.y) }
                        val fill = Path()
                        fill.addPath(line)
                        fill.lineTo(segment.last().x, bottom)
                        fill.lineTo(segment.first().x, bottom)
                        fill.close()
                        drawPath(fill, brush = gradient)
                        drawPath(line, color = lineColor, style = Stroke(width = 2.dp.toPx()))
                    } else if (segment.size == 1) {
                        drawCircle(
                            color = lineColor,
                            radius = 2.dp.toPx(),
                            center = segment.first(),
                        )
                    }
                    segment.clear()
                }

                curve.forEachIndexed { index, point ->
                    val value = point.totalCny
                    if (value == null) {
                        flushSegment()
                        return@forEachIndexed
                    }
                    val x = if (singlePoint) {
                        padding + chartWidth / 2f
                    } else {
                        padding + stepX * index
                    }
                    val normalized = if (flat) {
                        0.5f
                    } else {
                        (value.toFloat() - minFloat) / span
                    }
                    val y = padding + chartHeight * (1f - normalized)
                    segment.add(Offset(x, y))
                }
                flushSegment()

                // Flow markers sit on the bottom edge so they never cover the
                // curve; an up arrow is money in, a down arrow money out.
                val markerBaseY = bottom - 2.dp.toPx()
                val arrowHeight = 7.dp.toPx()
                val arrowHalfWidth = 4.dp.toPx()
                curve.forEachIndexed { index, point ->
                    val marker = flowMarkers[point.date] ?: return@forEachIndexed
                    val x = if (singlePoint) {
                        padding + chartWidth / 2f
                    } else {
                        padding + stepX * index
                    }
                    if (marker.signum() > 0) {
                        val path = Path().apply {
                            moveTo(x, markerBaseY - arrowHeight)
                            lineTo(x + arrowHalfWidth, markerBaseY)
                            lineTo(x - arrowHalfWidth, markerBaseY)
                            close()
                        }
                        drawPath(path, color = FolioGreen)
                    } else {
                        val path = Path().apply {
                            moveTo(x, markerBaseY)
                            lineTo(x + arrowHalfWidth, markerBaseY - arrowHeight)
                            lineTo(x - arrowHalfWidth, markerBaseY - arrowHeight)
                            close()
                        }
                        drawPath(path, color = FolioCoral)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = curve.firstOrNull()?.date?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
                Text(
                    text = curve.lastOrNull()?.date?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("最低 ${formatCny(minValue)}", style = MaterialTheme.typography.bodySmall, color = FolioMuted)
                Text("最高 ${formatCny(maxValue)}", style = MaterialTheme.typography.bodySmall, color = FolioMuted)
            }
            if (hasMarkers) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = if (categoryId == null) "▲ 入金" else "▲ 流入",
                        style = MaterialTheme.typography.labelSmall,
                        color = FolioGreen,
                    )
                    Text(
                        text = if (categoryId == null) "▼ 出金" else "▼ 流出",
                        style = MaterialTheme.typography.labelSmall,
                        color = FolioCoral,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigPnlHeader(label: String, pnl: BigDecimal?, incomplete: Boolean) {
    val color = pnlColor(pnl)
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FolioMuted,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (pnl != null) formatCny(pnl) else "--",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        if (incomplete) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "数据不完整（缺少历史价格）",
                style = MaterialTheme.typography.bodySmall,
                color = FolioCoral,
            )
        }
    }
}

@Composable
private fun BigValueHeader(label: String, value: String, color: Color) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FolioMuted,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun CategoryPnlBlock(
    rows: List<CategoryPnl>,
    startValues: Map<String, BigDecimal?>,
    instruments: List<InstrumentPnl>,
    range: DateRange,
) {
    val sorted = rows.sortedWith(
        compareBy<CategoryPnl> { it.pnlCny == null }
            .thenByDescending { it.pnlCny ?: BigDecimal.ZERO },
    )
    val maxGain = sorted.mapNotNull { it.pnlCny }
        .maxOrNull()
        ?.coerceAtLeast(BigDecimal.ZERO)
        ?: BigDecimal.ZERO
    val maxLoss = sorted.mapNotNull { it.pnlCny }
        .minOrNull()
        ?.abs()
        ?: BigDecimal.ZERO
    val combined = maxGain.add(maxLoss)
    val zeroFraction = if (combined.signum() > 0) {
        maxLoss.divide(combined, 10, java.math.RoundingMode.HALF_UP).toFloat()
    } else {
        0.5f
    }
    // Which category row is expanded; reset whenever the range changes.
    var expandedId by remember(range) { mutableStateOf<String?>(null) }
    val instrumentsByCategory = remember(instruments) {
        instruments.groupBy { it.categoryId }
    }

    WhiteBlock {
        sorted.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = FolioDivider)
            }
            val expanded = expandedId == row.categoryId
            CategoryPnlRow(
                pnl = row,
                maxGain = maxGain,
                maxLoss = maxLoss,
                zeroFraction = zeroFraction,
                startValue = startValues[row.categoryId],
                expanded = expanded,
                onToggle = { expandedId = if (expanded) null else row.categoryId },
            )
            if (expanded) {
                InstrumentPnlCard(instrumentsByCategory[row.categoryId].orEmpty())
            }
        }
    }
}

@Composable
private fun CategoryPnlRow(
    pnl: CategoryPnl,
    maxGain: BigDecimal,
    maxLoss: BigDecimal,
    zeroFraction: Float,
    startValue: BigDecimal?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val value = pnl.pnlCny
    val color = pnlColor(value)
    // Range return: this category's P&L over its value at the start of the
    // range. Negative when the category lost money over the range.
    val share = if (value != null && startValue != null && startValue.signum() > 0) {
        value.divide(startValue, 6, java.math.RoundingMode.HALF_UP)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(pnl.categoryId),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pnl.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FolioInk,
                )
                if (share != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatPercent(share),
                        style = MaterialTheme.typography.bodySmall,
                        color = FolioMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            ) {
                val barHeight = size.height
                val radius = CornerRadius(barHeight / 2f)
                drawRoundRect(
                    color = Color(0xFFF1F1F1),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, barHeight),
                    cornerRadius = radius,
                )

                val zeroX = size.width * zeroFraction
                if (value != null) {
                    if (value.signum() > 0 && maxGain.signum() > 0) {
                        val barWidth = (value.toFloat() / maxGain.toFloat())
                            .coerceIn(0f, 1f) * (size.width - zeroX)
                        if (barWidth > 0f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(zeroX, 0f),
                                size = Size(barWidth, barHeight),
                                cornerRadius = radius,
                            )
                        }
                    } else if (value.signum() < 0 && maxLoss.signum() > 0) {
                        val barWidth = (value.abs().toFloat() / maxLoss.toFloat())
                            .coerceIn(0f, 1f) * zeroX
                        if (barWidth > 0f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(zeroX - barWidth, 0f),
                                size = Size(barWidth, barHeight),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
                drawLine(
                    color = FolioMuted.copy(alpha = 0.45f),
                    start = Offset(zeroX, -1.dp.toPx()),
                    end = Offset(zeroX, barHeight + 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (pnl.incomplete) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "数据不完整（缺少历史价格）",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioCoral,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatCny(value),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) {
                Icons.Outlined.ExpandLess
            } else {
                Icons.Outlined.ExpandMore
            },
            contentDescription = if (expanded) "收起标的明细" else "展开标的明细",
            tint = FolioMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun InstrumentPnlCard(rows: List<InstrumentPnl>) {
    // Cash instruments with exactly zero P&L carry no information in the
    // drill-down; hide them. Non-cash rows and incomplete (null) P&L are kept.
    val visible = rows.filterNot {
        it.isCash && it.pnlCny != null && it.pnlCny.signum() == 0
    }
    val sorted = visible.sortedWith(
        compareBy<InstrumentPnl> { it.pnlCny == null }
            .thenByDescending { it.pnlCny ?: BigDecimal.ZERO },
    )
    Column(
        modifier = Modifier
            .padding(start = 48.dp, end = 16.dp, top = 2.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
    ) {
        if (sorted.isEmpty()) {
            Text(
                text = "该类别暂无标的明细",
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        } else {
            sorted.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = FolioDivider)
                }
                InstrumentPnlRow(row)
            }
        }
    }
}

@Composable
private fun InstrumentPnlRow(item: InstrumentPnl) {
    val value = item.pnlCny
    val color = pnlColor(value)
    // Range return of this instrument: P&L over its value at the range start,
    // the same denominator convention as the category row above.
    val share = if (value != null && item.startValue != null &&
        item.startValue.signum() > 0
    ) {
        value.divide(item.startValue, 6, java.math.RoundingMode.HALF_UP)
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.code.isNotEmpty()) {
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = FolioMuted,
                )
                if (share != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = FolioMuted,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            if (share != null) {
                Text(
                    text = formatPercent(share),
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatCny(value),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        if (item.incomplete) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "数据不完整（缺少历史价格）",
                style = MaterialTheme.typography.labelSmall,
                color = FolioCoral,
            )
        }
    }
}

@Composable
private fun FlowRowItem(
    flow: MonthFlow,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val net = flow.inflow.subtract(flow.outflow)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${flow.month.year} 年 ${flow.month.monthValue} 月",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "投入 ${formatCashCny(flow.inflow)} · 取出 ${formatCashCny(flow.outflow)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
            Text(
                text = formatCashCny(net),
                style = MaterialTheme.typography.titleSmall,
                color = if (net.signum() < 0) FolioGreen else FolioInk,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起流水" else "展开流水",
                tint = FolioMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DistributionRowItem(
    row: DistributionRowData,
    total: BigDecimal,
    detail: List<InstitutionHolding>?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val share = if (total.signum() > 0) {
        row.value.divide(total, 6, java.math.RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .then(
                if (detail != null) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(row.color),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = FolioInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCny(row.value),
                style = MaterialTheme.typography.titleSmall,
                color = FolioInk,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatPercent(share),
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
            if (detail != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起标的明细" else "展开标的明细",
                    tint = FolioMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF1F1F1)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(row.color),
            )
        }
    }
}

/**
 * Drill-down card below an expanded 机构分布 row: each position held at this
 * institution with its quantity (in the instrument's own unit) and current
 * CNY value. Unlike the 类别盈亏 card, this shows holdings, not P&L, and
 * amounts stay neutral; cash rows carry no quantity, only the value.
 */
@Composable
private fun InstitutionHoldingsCard(holdings: List<InstitutionHolding>) {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
    ) {
        holdings.forEachIndexed { index, holding ->
            if (index > 0) {
                HorizontalDivider(color = FolioDivider)
            }
            InstitutionHoldingRow(holding)
        }
    }
}

@Composable
private fun InstitutionHoldingRow(holding: InstitutionHolding) {
    if (holding.isCash) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = holding.name,
                style = MaterialTheme.typography.bodyMedium,
                color = FolioInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatCashCny(holding.valueCny),
                style = MaterialTheme.typography.labelLarge,
                color = FolioInk,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = holding.name,
                style = MaterialTheme.typography.bodyMedium,
                color = FolioInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatQuantity(holding.quantity) + " " + holding.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatCny(holding.valueCny),
                    style = MaterialTheme.typography.labelLarge,
                    color = FolioInk,
                )
            }
        }
    }
}

@Composable
private fun CategoryTable(snapshot: PortfolioSnapshot, onlyCategoryId: String? = null) {
    val categories = remember(snapshot.categories, onlyCategoryId) {
        if (onlyCategoryId == null) {
            snapshot.categories
        } else {
            snapshot.categories.filter { it.category.id == onlyCategoryId }
        }
    }
    WhiteBlock {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle("当前类别金额")
            categories.forEach { category ->
                val color = runCatching {
                    Color(android.graphics.Color.parseColor(category.category.color))
                }.getOrElse { FolioGold }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FolioInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCny(category.valueCny),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FolioInk,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatPercent(category.share),
                        style = MaterialTheme.typography.bodySmall,
                        color = FolioMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhiteBlock(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
    ) {
        Column(
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = FolioInk,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasize: Boolean = false,
    sub: String? = null,
    color: Color = FolioInk,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasize) FolioInk else FolioMuted,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = FolioMuted,
                )
            }
        }
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = color,
        )
    }
}

@Composable
private fun LoadingBlock(message: String? = null) {
    WhiteBlock {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = FolioGold,
            )
            if (message != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = FolioMuted)
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    WhiteBlock {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = FolioCoral,
            )
            TextButton(onClick = onRetry) {
                Text("重试", color = FolioGold)
            }
        }
    }
}

@Composable
private fun EmptyBlock(text: String) {
    WhiteBlock {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FolioMuted,
        )
    }
}

private fun pnlColor(pnl: BigDecimal?): Color = when {
    pnl == null -> FolioMuted
    pnl.signum() > 0 -> FolioCoral
    pnl.signum() < 0 -> FolioGreen
    else -> FolioMuted
}

private fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    CategoryIds.DIVIDEND -> Icons.Outlined.Savings
    CategoryIds.GROWTH -> Icons.AutoMirrored.Outlined.TrendingUp
    CategoryIds.US_EQUITY -> Icons.Outlined.Public
    CategoryIds.US_BOND -> Icons.Outlined.AccountBalance
    CategoryIds.CN_BOND -> Icons.Outlined.Payments
    CategoryIds.GOLD -> Icons.Outlined.Diamond
    CategoryIds.CASH -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.AutoMirrored.Outlined.ShowChart
}

private fun institutionRows(
    snapshot: PortfolioSnapshot,
    data: PortfolioData,
): List<DistributionRowData> {
    val palette = listOf(
        FolioGold,
        FolioCoral,
        Color(0xFF0984E3),
        Color(0xFF6C5CE7),
        FolioGreen,
        Color(0xFFB2BEC3),
    )
    // Institutions without any current value are noise in the distribution.
    val withNames = snapshot.brokerValues
        .filter { (_, value) -> value.signum() != 0 }
        .map { (id, value) ->
            val institution = data.institutions.firstOrNull { it.id == id }
            val name = institution?.chartAlias ?: institution?.name ?: id
            Triple(id, name, value)
        }
        .sortedByDescending { it.third }
    return withNames.mapIndexed { index, (id, name, value) ->
        DistributionRowData(id, name, value, palette[index % palette.size])
    }
}

/**
 * Per-institution position detail for the 机构分布 drill-down: each held
 * instrument with its quantity (in the instrument's own unit) and current
 * CNY value. Zero-quantity positions are noise; cash positions carry no
 * quantity display, only their value.
 */
private fun holdingsByInstitution(
    data: PortfolioData,
    positions: Map<PositionKey, BigDecimal>,
    positionValues: Map<PositionKey, BigDecimal?>,
): Map<String, List<InstitutionHolding>> {
    return positions
        .filter { (_, quantity) -> quantity.signum() != 0 }
        .mapNotNull { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId) ?: return@mapNotNull null
            key.institutionId to InstitutionHolding(
                name = instrument.name,
                quantity = quantity,
                unit = instrument.unit,
                valueCny = positionValues[key],
                isCash = instrument.kind == InstrumentKind.CASH,
            )
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) ->
            pairs.map { it.second }
                .sortedWith(
                    compareBy<InstitutionHolding> { it.valueCny == null }
                        .thenByDescending { it.valueCny ?: BigDecimal.ZERO },
                )
        }
}

private fun currencyRows(snapshot: PortfolioSnapshot): List<DistributionRowData> {
    val colors = mapOf(
        "CNY" to FolioGold,
        "USD" to Color(0xFF0984E3),
        "HKD" to Color(0xFF6C5CE7),
        "PHYSICAL_GOLD" to Color(0xFFF9CA24),
    )
    // Currencies without any current value are noise in the distribution.
    return snapshot.currencyValues
        .filter { (_, value) -> value.signum() != 0 }
        .map { (currency, value) ->
            val name = when (currency) {
                "CNY" -> "人民币"
                "USD" -> "美元"
                "HKD" -> "港元"
                "PHYSICAL_GOLD" -> "实物黄金"
                else -> currency
            }
            DistributionRowData(currency, name, value, colors[currency] ?: FolioMuted)
        }
        .sortedByDescending { it.value }
}

private fun fxToCny(currency: String, fxRates: Map<String, BigDecimal>): BigDecimal? =
    when (currency.uppercase()) {
        "CNY", "CNH" -> BigDecimal.ONE
        else -> fxRates[currency.uppercase()]
    }

/**
 * Fingerprint of the price information in the snapshot (instrument prices and
 * FX rates). Used to detect whether a quote refresh actually changed a price,
 * so that a refresh without changes does not rebuild the history result.
 */
private fun quotesFingerprint(quotes: QuoteSnapshot): String = buildString {
    quotes.quotes.forEach { (instrumentId, quote) ->
        append(instrumentId).append('=').append(quote.price).append(';')
    }
    append('|')
    quotes.fxRates.forEach { (currency, rate) ->
        append(currency).append('=').append(rate).append(';')
    }
}

private fun monthlyFlows(
    data: PortfolioData,
    fxRates: Map<String, BigDecimal>,
    range: DateRange,
): List<MonthFlow> {
    val map = linkedMapOf<YearMonth, MutableList<ExternalEvent>>()
    data.events.filterIsInstance<ExternalEvent>().forEach { event ->
        val date = event.localDate()
        if (date.isBefore(range.from) || date.isAfter(range.to)) return@forEach
        val month = YearMonth.from(parseEventTime(event.date))
        map.getOrPut(month) { mutableListOf() }.add(event)
    }
    return map.entries
        .sortedBy { it.key }
        .map { (month, events) ->
            var inflow = BigDecimal.ZERO
            var outflow = BigDecimal.ZERO
            events.forEach { event ->
                val amountCny = fxToCny(event.currency, fxRates)
                    ?.let { event.amount.multiply(it).toCnyAmount() } ?: return@forEach
                if (event.direction == ExternalDirection.IN) {
                    inflow = inflow.add(amountCny)
                } else {
                    outflow = outflow.add(amountCny)
                }
            }
            MonthFlow(month, inflow, outflow, events)
        }
}
