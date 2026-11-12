package com.jimbozhang.folioprism.ui.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.calc.localDate
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.HistoryUiState
import com.jimbozhang.folioprism.ui.components.FolioFilterChip
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatPercent
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

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

private val rangeOptions = listOf(
    "1M" to "近 1 月",
    "3M" to "近 3 月",
    "YTD" to "今年至今",
    "1Y" to "近 1 年",
    "ALL" to "全部",
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
    val tabs = listOf("总资产", "类别盈亏", "累计投入取出", "费用统计", "机构分布", "币种分布")
    var tab by remember { mutableStateOf(0) }
    var rangeKey by remember { mutableStateOf("YTD") }

    val today = LocalDate.now()
    val earliestEvent = remember(data.events) {
        data.events.minOfOrNull { it.localDate() } ?: today
    }
    val rangeFrom = when (rangeKey) {
        "1M" -> today.minusMonths(1)
        "3M" -> today.minusMonths(3)
        "YTD" -> LocalDate.of(today.year, 1, 1)
        "1Y" -> today.minusYears(1)
        "ALL" -> earliestEvent
        else -> LocalDate.of(today.year, 1, 1)
    }

    LaunchedEffect(tab, rangeKey, data.events.size) {
        if (tab == 0 || tab == 1) {
            val ready = historyState as? HistoryUiState.Ready
            if (ready == null || ready.result.from != rangeFrom || ready.result.to != today) {
                onLoadHistory(rangeFrom, today)
            }
        }
    }

    val monthlyFlows = remember(data.events) { monthlyFlows(data) }
    val monthlyExpenses = remember(data.events, categoryNames) {
        monthlyExpenses(data, categoryNames)
    }
    val institutionRows = remember(snapshot.brokerValues, data.institutions) {
        snapshot.brokerValues.map { (id, value) ->
            val name = data.institutions.firstOrNull { it.id == id }?.chartAlias
                ?: data.institutions.firstOrNull { it.id == id }?.name
                ?: id
            name to value
        }.sortedByDescending { it.second }
    }
    val currencyRows = remember(snapshot.currencyValues) {
        snapshot.currencyValues.map { (currency, value) ->
            when (currency) {
                "CNY" -> "人民币"
                "USD" -> "美元"
                "HKD" -> "港元"
                "PHYSICAL_GOLD" -> "实物黄金"
                else -> currency
            } to value
        }.sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("图表", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    FolioFilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = label,
                    )
                }
            }
        }
        if (tab == 0 || tab == 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rangeOptions.forEach { (key, label) ->
                        FolioFilterChip(
                            selected = rangeKey == key,
                            onClick = { rangeKey = key },
                            label = label,
                        )
                    }
                }
            }
        }

        when (tab) {
            0 -> {
                item {
                    HistorySummaryCard(
                        historyState = historyState,
                        onRetry = { onLoadHistory(rangeFrom, today) },
                    )
                }
                (historyState as? HistoryUiState.Ready)?.let { ready ->
                    item { HistoryLineChart(ready.result.curve) }
                    if (ready.result.logs.isNotEmpty()) {
                        item { LogsCard(ready.result.logs) }
                    }
                }
                item { CategoryTable(snapshot) }
            }

            1 -> {
                when (val current = historyState) {
                    is HistoryUiState.Ready -> {
                        val incomplete = current.result.categoryPnl.any { it.incomplete || it.pnlCny == null }
                        val totalPnl = if (incomplete) {
                            null
                        } else {
                            current.result.categoryPnl.fold(BigDecimal.ZERO) { acc, item ->
                                acc.add(item.pnlCny ?: BigDecimal.ZERO)
                            }
                        }
                        val maxAbs = current.result.categoryPnl
                            .mapNotNull { it.pnlCny?.abs() }
                            .maxOrNull() ?: BigDecimal.ZERO
                        item {
                            BigPnlHeader(
                                label = "类别盈亏合计",
                                pnl = totalPnl,
                                incomplete = incomplete,
                            )
                        }
                        items(current.result.categoryPnl) { pnl ->
                            PnlRow(pnl, maxAbs)
                        }
                        if (current.result.logs.isNotEmpty()) {
                            item { LogsCard(current.result.logs) }
                        }
                    }

                    is HistoryUiState.Error -> item {
                        ErrorCard(current.message) { onLoadHistory(rangeFrom, today) }
                    }

                    else -> item { LoadingCard() }
                }
            }

            2 -> {
                item {
                    SummaryCard(
                        title = "累计投入 / 取出",
                        rows = listOf(
                            "累计投入" to formatCny(monthlyFlows.fold(BigDecimal.ZERO) { acc, f -> acc.add(f.inflow) }),
                            "累计取出" to formatCny(monthlyFlows.fold(BigDecimal.ZERO) { acc, f -> acc.add(f.outflow) }),
                            "净投入" to formatCny(
                                monthlyFlows.fold(BigDecimal.ZERO) { acc, f ->
                                    acc.add(f.inflow).subtract(f.outflow)
                                },
                            ),
                        ),
                    )
                }
                if (monthlyFlows.isEmpty()) {
                    item { EmptyCard("暂无外部本金流入/流出事件。") }
                } else {
                    items(monthlyFlows) { flow ->
                        FlowRow(flow)
                    }
                }
            }

            3 -> {
                item {
                    SummaryCard(
                        title = "费用统计",
                        rows = listOf(
                            "支出合计" to formatCny(
                                monthlyExpenses.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.total) },
                            ),
                        ),
                    )
                }
                if (monthlyExpenses.isEmpty()) {
                    item { EmptyCard("暂无支出事件。") }
                } else {
                    items(monthlyExpenses) { expense ->
                        ExpenseRow(expense)
                    }
                }
            }

            4 -> {
                if (institutionRows.isEmpty()) {
                    item { EmptyCard("暂无机构持仓。") }
                } else {
                    item { DistributionHeader("机构分布") }
                    items(institutionRows) { (name, value) ->
                        DistributionRow(name, value, snapshot.totalValueCny)
                    }
                }
            }

            5 -> {
                if (currencyRows.isEmpty()) {
                    item { EmptyCard("暂无币种暴露。") }
                } else {
                    item { DistributionHeader("币种分布") }
                    items(currencyRows) { (name, value) ->
                        DistributionRow(name, value, snapshot.totalValueCny)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(
    historyState: HistoryUiState,
    onRetry: () -> Unit,
) {
    when (historyState) {
        is HistoryUiState.Ready -> {
            val ready = historyState.result
            val start = ready.curve.firstOrNull { it.totalCny != null }?.totalCny
            val end = ready.curve.lastOrNull { it.totalCny != null }?.totalCny
            val change = if (start != null && end != null) end.subtract(start) else null
            val pnlIncomplete = ready.categoryPnl.any { it.incomplete || it.pnlCny == null }
            val pnl = if (!pnlIncomplete) {
                ready.categoryPnl.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(item.pnlCny ?: BigDecimal.ZERO)
                }
            } else {
                null
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigPnlHeader(
                    label = "区间盈亏（扣资金流）",
                    pnl = pnl,
                    incomplete = pnlIncomplete,
                )
                SummaryCard(
                    title = "总资产曲线",
                    rows = listOf(
                        "期初（${ready.from}）" to formatCny(start),
                        "期末（${ready.to}）" to formatCny(end),
                        "曲线变化（含资金流）" to formatCny(change),
                    ),
                )
            }
        }

        is HistoryUiState.Error -> ErrorCard(historyState.message, onRetry)
        else -> LoadingCard()
    }
}

@Composable
private fun HistoryLineChart(curve: List<DailyPortfolioValue>) {
    val values = curve.mapNotNull { it.totalCny }
    if (values.isEmpty()) {
        EmptyCard("没有可用的历史价格，暂时无法绘制曲线。")
        return
    }
    val minValue = values.minOrNull() ?: BigDecimal.ZERO
    val maxValue = values.maxOrNull() ?: BigDecimal.ZERO
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val minFloat = minValue.toFloat()
    val maxFloat = maxValue.toFloat()
    val span = (maxFloat - minFloat).takeIf { it > 0f } ?: 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                val padding = 8.dp.toPx()
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2
                val stepX = if (curve.size > 1) chartWidth / (curve.size - 1) else 0f

                repeat(3) { index ->
                    val y = padding + chartHeight * (index / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(padding, y),
                        end = Offset(padding + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                var path = Path()
                var started = false
                curve.forEachIndexed { index, point ->
                    val value = point.totalCny ?: run {
                        if (started) {
                            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
                            path = Path()
                            started = false
                        }
                        return@forEachIndexed
                    }
                    val x = padding + stepX * index
                    val normalized = (value.toFloat() - minFloat) / span
                    val y = padding + chartHeight * (1f - normalized)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                    drawCircle(
                        color = lineColor,
                        radius = 1.5.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
                if (started) {
                    drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = curve.firstOrNull()?.date?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = curve.lastOrNull()?.date?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("最低 ${formatCny(minValue)}", style = MaterialTheme.typography.bodySmall)
                Text("最高 ${formatCny(maxValue)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BigPnlHeader(label: String, pnl: BigDecimal?, incomplete: Boolean) {
    val color = when {
        pnl == null -> FolioMuted
        pnl.signum() > 0 -> FolioCoral
        pnl.signum() < 0 -> FolioGreen
        else -> FolioMuted
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (pnl != null) formatCny(pnl) else "--",
            style = MaterialTheme.typography.headlineLarge,
            color = color,
        )
        if (incomplete) {
            Text(
                text = "数据不完整（缺少历史价格）",
                style = MaterialTheme.typography.bodySmall,
                color = FolioCoral,
            )
        }
    }
}

@Composable
private fun PnlRow(pnl: CategoryPnl, maxAbs: BigDecimal) {
    val value = pnl.pnlCny
    val color = when {
        value == null -> FolioMuted
        value.signum() > 0 -> FolioCoral
        value.signum() < 0 -> FolioGreen
        else -> FolioMuted
    }
    val fraction = if (value != null && maxAbs.signum() > 0) {
        value.abs().toFloat() / maxAbs.toFloat()
    } else {
        0f
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(pnl.categoryName, style = MaterialTheme.typography.bodyMedium)
                    if (pnl.incomplete) {
                        Text(
                            "数据不完整（缺少历史价格）",
                            style = MaterialTheme.typography.bodySmall,
                            color = FolioCoral,
                        )
                    }
                }
                Text(
                    text = formatCny(value),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
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
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(6.dp))
            Text("正在获取历史行情…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun LogsCard(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("历史行情日志", style = MaterialTheme.typography.titleSmall)
            logs.takeLast(10).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SummaryCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FlowRow(flow: MonthFlow) {
    val net = flow.inflow.subtract(flow.outflow)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(flow.month.toString(), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("投入 ${formatCny(flow.inflow)}", style = MaterialTheme.typography.bodySmall)
                Text("取出 ${formatCny(flow.outflow)}", style = MaterialTheme.typography.bodySmall)
                Text("净 ${formatCny(net)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: MonthExpense) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(expense.month.toString(), style = MaterialTheme.typography.titleSmall)
                Text(formatCny(expense.total), style = MaterialTheme.typography.titleSmall)
            }
            expense.byCategory.forEach { (category, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(category, style = MaterialTheme.typography.bodySmall)
                    Text(formatCny(value), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DistributionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DistributionRow(name: String, value: BigDecimal, total: BigDecimal) {
    val share = if (total.signum() > 0) value.divide(total, 6, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatCny(value)} · ${formatPercent(share)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { share.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CategoryTable(snapshot: PortfolioSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("当前类别金额", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            snapshot.categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = category.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatCny(category.valueCny) + " · " + formatPercent(category.share),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun monthlyFlows(data: PortfolioData): List<MonthFlow> {
    val map = linkedMapOf<YearMonth, MutableList<ExternalEvent>>()
    data.events.filterIsInstance<ExternalEvent>().forEach { event ->
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
): List<MonthExpense> {
    val map = linkedMapOf<YearMonth, MutableMap<String, BigDecimal>>()
    data.events.filterIsInstance<ExpenseEvent>().forEach { event ->
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
