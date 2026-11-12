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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.StackedLineChart
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.HistoryUiState
import com.jimbozhang.folioprism.ui.components.DateRangePickerDialog
import com.jimbozhang.folioprism.ui.components.RangeArrowButton
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatPercent
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

private enum class ChartType(
    val label: String,
    val icon: ImageVector,
    val needsHistory: Boolean,
    val usesRange: Boolean,
) {
    ASSETS("总资产", Icons.AutoMirrored.Outlined.ShowChart, needsHistory = true, usesRange = true),
    CATEGORY_ASSETS("类别资产", Icons.Outlined.StackedLineChart, needsHistory = true, usesRange = true),
    CATEGORY_PNL("类别盈亏", Icons.Outlined.PieChart, needsHistory = true, usesRange = true),
    FLOWS("累计投入取出", Icons.Outlined.Savings, needsHistory = false, usesRange = true),
    EXPENSES("费用统计", Icons.AutoMirrored.Outlined.ReceiptLong, needsHistory = false, usesRange = true),
    INSTITUTIONS("机构分布", Icons.Outlined.AccountBalance, needsHistory = false, usesRange = false),
    CURRENCIES("币种分布", Icons.Outlined.CurrencyExchange, needsHistory = false, usesRange = false),
}

private data class MonthFlow(
    val month: YearMonth,
    val inflow: BigDecimal,
    val outflow: BigDecimal,
)

private data class MonthExpense(
    val month: YearMonth,
    val total: BigDecimal,
    val byCategory: Map<String, BigDecimal>,
)

private data class DistributionRowData(
    val name: String,
    val value: BigDecimal,
    val color: Color,
)

private val dateRangeSaver = listSaver<DateRange, Long>(
    save = { listOf(it.from.toEpochDay(), it.to.toEpochDay()) },
    restore = { DateRange(LocalDate.ofEpochDay(it[0]), LocalDate.ofEpochDay(it[1])) },
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
    val categoryNames = remember(data.settings.categories) {
        data.settings.categories.associate { it.id to it.name }
    }
    val categoriesById = remember(data.settings.categories) {
        data.settings.categories.associateBy { it.id }
    }
    val today = LocalDate.now()
    val earliestEvent = remember(data.events) {
        data.events.minOfOrNull { it.localDate() }
    }

    var chart by rememberSaveable { mutableStateOf(ChartType.ASSETS) }
    var range by rememberSaveable(stateSaver = dateRangeSaver) {
        mutableStateOf(DateRange.yearToDate(today))
    }
    var showRangePicker by remember { mutableStateOf(false) }
    val orderedCategories = remember(data.settings.categories) {
        data.settings.categories.sortedBy { it.order }
    }
    var categoryId by rememberSaveable {
        mutableStateOf(orderedCategories.firstOrNull()?.id ?: "")
    }
    val selectedCategory = orderedCategories.firstOrNull { it.id == categoryId }
        ?: orderedCategories.firstOrNull()

    val monthlyFlows = remember(data.events, range) { monthlyFlows(data, range) }
    val monthlyExpenses = remember(data.events, categoryNames, range) {
        monthlyExpenses(data, categoryNames, range)
    }
    val institutionRows = remember(snapshot.brokerValues, data.institutions) {
        institutionRows(snapshot, data)
    }
    val currencyRows = remember(snapshot.currencyValues) { currencyRows(snapshot) }

    LaunchedEffect(chart, range, data.events.size) {
        if (!chart.needsHistory) return@LaunchedEffect
        val ready = historyState as? HistoryUiState.Ready
        if (ready == null || ready.result.from != range.from || ready.result.to != range.to) {
            onLoadHistory(range.from, range.to)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ChartHeader(
            chart = chart,
            contentPadding = contentPadding,
            onSelected = { chart = it },
        )
        if (chart == ChartType.CATEGORY_ASSETS && selectedCategory != null) {
            CategorySelector(
                categories = orderedCategories,
                selected = selectedCategory,
                onSelected = { categoryId = it },
            )
        }

        LazyColumn(
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
                    onRetry = { onLoadHistory(range.from, range.to) },
                )

                ChartType.CATEGORY_ASSETS -> categoryAssetsContent(
                    historyState = historyState,
                    range = range,
                    snapshot = snapshot,
                    categoryId = selectedCategory?.id ?: "",
                    categoryName = selectedCategory?.name ?: "",
                    onRetry = { onLoadHistory(range.from, range.to) },
                )

                ChartType.CATEGORY_PNL -> categoryPnlContent(
                    historyState = historyState,
                    range = range,
                    onRetry = { onLoadHistory(range.from, range.to) },
                )

                ChartType.FLOWS -> flowsContent(monthlyFlows)
                ChartType.EXPENSES -> expensesContent(monthlyExpenses)

                ChartType.INSTITUTIONS -> distributionContent(
                    title = "机构分布",
                    rows = institutionRows,
                    total = snapshot.totalValueCny,
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
    selected: Category,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
                    .background(categoryColor(selected)),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = selected.name,
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
            categories.forEach { category ->
                val isSelected = category.id == selected.id
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
                enabled = range.to.isBefore(today),
                onClick = { onShift(range.shiftForward(today)) },
            )
        }
    }
}

private fun LazyListScope.assetsContent(
    historyState: HistoryUiState,
    range: DateRange,
    snapshot: PortfolioSnapshot,
    onRetry: () -> Unit,
) {
    val ready = (historyState as? HistoryUiState.Ready)
        ?.takeIf { it.result.from == range.from && it.result.to == range.to }

    when {
        ready != null -> {
            item { HistorySummary(ready.result.curve, ready.result.categoryPnl) }
            item { HistoryLineChart(ready.result.curve) }
        }

        historyState is HistoryUiState.Error -> item { ErrorBlock(historyState.message, onRetry) }
        else -> item { LoadingBlock() }
    }
    item { CategoryTable(snapshot) }
}

private fun LazyListScope.categoryAssetsContent(
    historyState: HistoryUiState,
    range: DateRange,
    snapshot: PortfolioSnapshot,
    categoryId: String,
    categoryName: String,
    onRetry: () -> Unit,
) {
    val ready = (historyState as? HistoryUiState.Ready)
        ?.takeIf { it.result.from == range.from && it.result.to == range.to }

    when {
        ready != null -> {
            val pnl = ready.result.categoryPnl.firstOrNull { it.categoryId == categoryId }
                ?: CategoryPnl(categoryId, categoryName, null, true)
            val curve = ready.result.curve.map { point ->
                DailyPortfolioValue(
                    date = point.date,
                    totalCny = point.categoryValues[categoryId],
                    categoryValues = emptyMap(),
                    incomplete = point.categoryValues[categoryId] == null && point.incomplete,
                )
            }
            item { HistorySummary(curve, listOf(pnl)) }
            item { HistoryLineChart(curve) }
        }

        historyState is HistoryUiState.Error -> item { ErrorBlock(historyState.message, onRetry) }
        else -> item { LoadingBlock() }
    }
    item { CategoryTable(snapshot, categoryId) }
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
            item { BigPnlHeader("类别盈亏合计", totalPnl, incomplete) }
            item { CategoryPnlBlock(pnl) }
        }

        historyState is HistoryUiState.Error -> item { ErrorBlock(historyState.message, onRetry) }
        else -> item { LoadingBlock() }
    }
}

private fun LazyListScope.flowsContent(monthly: List<MonthFlow>) {
    val inflow = monthly.fold(BigDecimal.ZERO) { acc, flow -> acc.add(flow.inflow) }
    val outflow = monthly.fold(BigDecimal.ZERO) { acc, flow -> acc.add(flow.outflow) }
    val net = inflow.subtract(outflow)

    item {
        BigValueHeader(
            label = "区间净投入",
            value = formatCny(net),
            color = if (net.signum() < 0) FolioGreen else FolioInk,
        )
    }
    item {
        if (monthly.isEmpty()) {
            EmptyBlock("所选区间内没有外部本金流入/流出事件。")
        } else {
            WhiteBlock {
                SummaryLine("累计投入", formatCny(inflow))
                HorizontalDivider(color = FolioDivider)
                SummaryLine("累计取出", formatCny(outflow))
                HorizontalDivider(color = FolioDivider)
                SummaryLine("净投入", formatCny(net), emphasize = true)
                monthly.asReversed().forEach { flow ->
                    HorizontalDivider(color = FolioDivider)
                    FlowRowItem(flow)
                }
            }
        }
    }
}

private fun LazyListScope.expensesContent(monthly: List<MonthExpense>) {
    val total = monthly.fold(BigDecimal.ZERO) { acc, expense -> acc.add(expense.total) }

    item {
        BigValueHeader(
            label = "区间支出合计",
            value = formatCny(total),
            color = FolioInk,
        )
    }
    item {
        if (monthly.isEmpty()) {
            EmptyBlock("所选区间内没有支出事件。")
        } else {
            WhiteBlock {
                monthly.asReversed().forEachIndexed { index, expense ->
                    if (index > 0) {
                        HorizontalDivider(color = FolioDivider)
                    }
                    ExpenseRowItem(expense)
                }
            }
        }
    }
}

private fun LazyListScope.distributionContent(
    title: String,
    rows: List<DistributionRowData>,
    total: BigDecimal,
) {
    item {
        BigValueHeader(
            label = "当前总资产",
            value = formatCny(total),
            color = FolioInk,
        )
    }
    item {
        if (rows.isEmpty()) {
            EmptyBlock("暂无$title 数据。")
        } else {
            WhiteBlock {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = FolioDivider)
                    }
                    DistributionRowItem(row, total)
                }
            }
        }
    }
}

@Composable
private fun HistorySummary(
    curve: List<DailyPortfolioValue>,
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigPnlHeader(label = "区间盈亏（扣资金流）", pnl = pnl, incomplete = incomplete)
        WhiteBlock {
            Spacer(modifier = Modifier.height(4.dp))
            SummaryLine("期初", formatCny(start))
            HorizontalDivider(color = FolioDivider)
            SummaryLine("期末", formatCny(end))
            HorizontalDivider(color = FolioDivider)
            SummaryLine("曲线变化（含资金流）", formatCny(change), emphasize = true)
        }
    }
}

@Composable
private fun HistoryLineChart(curve: List<DailyPortfolioValue>) {
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
private fun CategoryPnlBlock(rows: List<CategoryPnl>) {
    val maxAbs = rows.mapNotNull { it.pnlCny?.abs() }.maxOrNull() ?: BigDecimal.ZERO
    val totalAbs = rows.mapNotNull { it.pnlCny?.abs() }
        .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }

    WhiteBlock {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = FolioDivider)
            }
            CategoryPnlRow(row, maxAbs, totalAbs)
        }
    }
}

@Composable
private fun CategoryPnlRow(
    pnl: CategoryPnl,
    maxAbs: BigDecimal,
    totalAbs: BigDecimal,
) {
    val value = pnl.pnlCny
    val color = pnlColor(value)
    val fraction = if (value != null && maxAbs.signum() > 0) {
        value.abs().toFloat() / maxAbs.toFloat()
    } else {
        0f
    }
    val share = if (value != null && totalAbs.signum() > 0) {
        value.abs().divide(totalAbs, 6, java.math.RoundingMode.HALF_UP)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFF1F1F1)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color),
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
    }
}

@Composable
private fun FlowRowItem(flow: MonthFlow) {
    val net = flow.inflow.subtract(flow.outflow)
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
                text = "投入 ${formatCny(flow.inflow)} · 取出 ${formatCny(flow.outflow)}",
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
        }
        Text(
            text = formatCny(net),
            style = MaterialTheme.typography.titleSmall,
            color = if (net.signum() < 0) FolioGreen else FolioInk,
        )
    }
}

@Composable
private fun ExpenseRowItem(expense: MonthExpense) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${expense.month.year} 年 ${expense.month.monthValue} 月",
                style = MaterialTheme.typography.bodyLarge,
                color = FolioInk,
            )
            if (expense.byCategory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = expense.byCategory.entries.joinToString(" · ") {
                        "${it.key} ${formatCny(it.value)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
        }
        Text(
            text = formatCny(expense.total),
            style = MaterialTheme.typography.titleSmall,
            color = FolioInk,
        )
    }
}

@Composable
private fun DistributionRowItem(row: DistributionRowData, total: BigDecimal) {
    val share = if (total.signum() > 0) {
        row.value.divide(total, 6, java.math.RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
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
        Column(content = content)
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
private fun SummaryLine(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasize) FolioInk else FolioMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = FolioInk,
        )
    }
}

@Composable
private fun LoadingBlock() {
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
            Spacer(modifier = Modifier.width(10.dp))
            Text("正在获取历史行情…", style = MaterialTheme.typography.bodyMedium, color = FolioMuted)
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
    return snapshot.brokerValues
        .map { (id, value) ->
            val institution = data.institutions.firstOrNull { it.id == id }
            val name = institution?.chartAlias ?: institution?.name ?: id
            name to value
        }
        .sortedByDescending { it.second }
        .mapIndexed { index, (name, value) ->
            DistributionRowData(name, value, palette[index % palette.size])
        }
}

private fun currencyRows(snapshot: PortfolioSnapshot): List<DistributionRowData> {
    val colors = mapOf(
        "CNY" to FolioGold,
        "USD" to Color(0xFF0984E3),
        "HKD" to Color(0xFF6C5CE7),
        "PHYSICAL_GOLD" to Color(0xFFF9CA24),
    )
    return snapshot.currencyValues
        .map { (currency, value) ->
            val name = when (currency) {
                "CNY" -> "人民币"
                "USD" -> "美元"
                "HKD" -> "港元"
                "PHYSICAL_GOLD" -> "实物黄金"
                else -> currency
            }
            DistributionRowData(name, value, colors[currency] ?: FolioMuted)
        }
        .sortedByDescending { it.value }
}

private fun monthlyFlows(data: PortfolioData, range: DateRange): List<MonthFlow> {
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
                if (event.direction == ExternalDirection.IN) {
                    inflow = inflow.add(event.amountCny)
                } else {
                    outflow = outflow.add(event.amountCny)
                }
            }
            MonthFlow(month, inflow, outflow)
        }
}

private fun monthlyExpenses(
    data: PortfolioData,
    categoryNames: Map<String, String>,
    range: DateRange,
): List<MonthExpense> {
    val map = linkedMapOf<YearMonth, MutableMap<String, BigDecimal>>()
    data.events.filterIsInstance<ExpenseEvent>().forEach { event ->
        val date = event.localDate()
        if (date.isBefore(range.from) || date.isAfter(range.to)) return@forEach
        val month = YearMonth.from(parseEventTime(event.date))
        val categories = map.getOrPut(month) { linkedMapOf() }
        val name = categoryNames[event.categoryId] ?: event.categoryId
        categories[name] = (categories[name] ?: BigDecimal.ZERO).add(event.amountCny)
    }
    return map.entries
        .sortedBy { it.key }
        .map { (month, categories) ->
            MonthExpense(
                month = month,
                total = categories.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) },
                byCategory = categories,
            )
        }
}
