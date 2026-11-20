package com.jimbozhang.folioprism.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.CategorySnapshot
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.calc.RangeBarWindow
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.goldPremium
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.HoldingRow
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatCnyCompact
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.components.formatPercent
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    contentPadding: PaddingValues,
) {
    val positions = remember(data) { EventReplay.replay(data.events) }
    val snapshot = remember(data, positions, quotes) {
        SnapshotCalculator.build(
            data = data,
            positions = positions,
            provider = LivePriceProvider(quotes),
            date = LocalDate.now(),
        )
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    val firstCategoryIndex = 2 + if (snapshot.hasMissingPrices) 1 else 0

    BackHandler(enabled = listState.canScrollBackward) {
        scope.launch { listState.animateScrollToItem(0) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(snapshot) }

        if (data.instruments.isEmpty()) {
            item { EmptyHomeCard() }
        } else {
            if (snapshot.hasMissingPrices) {
                item { MissingPricesBanner() }
            }
            item {
                PortfolioOverview(
                    categories = snapshot.categories,
                    onCategoryClick = { categoryId ->
                        expandedIds = expandedIds + categoryId
                        val categoryIndex = snapshot.categories
                            .indexOfFirst { it.category.id == categoryId }
                        if (categoryIndex >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(firstCategoryIndex + categoryIndex)
                            }
                        }
                    },
                )
            }
            items(snapshot.categories, key = { it.category.id }) { category ->
                CategoryCard(
                    category = category,
                    data = data,
                    quotes = quotes,
                    snapshot = snapshot,
                    positions = positions,
                    expanded = category.category.id in expandedIds,
                    onToggleExpanded = {
                        val id = category.category.id
                        expandedIds = if (id in expandedIds) {
                            expandedIds - id
                        } else {
                            expandedIds + id
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PortfolioOverview(
    categories: List<CategorySnapshot>,
    onCategoryClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        val scale = SnapshotCalculator.overviewScale(categories)
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "资产配置",
                style = MaterialTheme.typography.titleMedium,
                color = FolioInk,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            categories.forEachIndexed { index, category ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = FolioDivider,
                    )
                }
                OverviewRow(
                    category = category,
                    scale = scale,
                    onClick = { onCategoryClick(category.category.id) },
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(
    category: CategorySnapshot,
    scale: Float,
    onClick: () -> Unit,
) {
    val color = remember(category.category.color) { parseCategoryColor(category.category) }
    val statusColor = if (category.inRange) FolioGreen else FolioCoral
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(58.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = category.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolioInk,
                    maxLines = 1,
                )
            }
            Text(
                text = if (category.incomplete) {
                    "数据不完整"
                } else {
                    "目标 ${formatPercent(category.targetShare)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (category.incomplete) FolioCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OverviewBar(
            category = category,
            color = color,
            scale = scale,
            modifier = Modifier
                .weight(1f)
                .height(14.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.width(96.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = formatCny(category.valueCny),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FolioInk,
                maxLines = 1,
            )
            Text(
                text = formatPercent(category.share),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OverviewBar(
    category: CategorySnapshot,
    color: Color,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val share = category.visualShare.toFloat()
    val target = category.visualTarget.toFloat()
    val min = category.visualMin.toFloat()
    val max = category.visualMax.toFloat()

    Canvas(modifier = modifier) {
        val trackHeight = 8.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = Color(0xFFF1F1F1),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = radius,
        )
        val minX = (min / scale).coerceIn(0f, 1f) * size.width
        val maxX = (max / scale).coerceIn(0f, 1f) * size.width
        if (maxX > minX) {
            drawRoundRect(
                color = FolioGreen.copy(alpha = 0.14f),
                topLeft = Offset(minX, top),
                size = Size(maxX - minX, trackHeight),
                cornerRadius = radius,
            )
        }
        val fillWidth = (share / scale).coerceIn(0f, 1f) * size.width
        if (fillWidth > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size(fillWidth, trackHeight),
                cornerRadius = radius,
            )
        }
        if (maxX > minX) {
            listOf(minX, maxX).forEach { x ->
                drawLine(
                    color = Color(0xFF9AA3A6),
                    start = Offset(x, top - 1.5.dp.toPx()),
                    end = Offset(x, top + trackHeight + 1.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        val targetX = (target / scale).coerceIn(0f, 1f) * size.width
        drawLine(
            color = FolioInk.copy(alpha = 0.5f),
            start = Offset(targetX, top - 2.dp.toPx()),
            end = Offset(targetX, top + trackHeight + 2.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun SummaryCard(snapshot: PortfolioSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = "总资产",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatCny(snapshot.totalValueCny),
                style = MaterialTheme.typography.headlineLarge,
                color = FolioInk,
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FolioDivider)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric(
                    label = "红利",
                    value = formatCny(snapshot.dividendValueCny),
                    secondary = formatPercent(snapshot.dividendShare),
                )
                Metric(
                    label = "组合",
                    value = formatCny(snapshot.portfolioValueCny),
                    secondary = formatPercent(snapshot.portfolioShare),
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, secondary: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = FolioInk,
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryCard(
    category: CategorySnapshot,
    data: PortfolioData,
    quotes: QuoteSnapshot,
    snapshot: PortfolioSnapshot,
    positions: Map<PositionKey, BigDecimal>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val categoryColor = remember(category.category.color) { parseCategoryColor(category.category) }
    val statusColor = if (category.inRange) FolioGreen else FolioCoral
    val holdings = remember(category.category.id, positions, data.instruments, snapshot) {
        positions.entries
            .filter { (key, quantity) ->
                quantity.signum() != 0 &&
                    data.instrument(key.instrumentId)?.categoryId == category.category.id
            }
            .sortedByDescending { (key, _) -> snapshot.positionValues[key] ?: BigDecimal.ZERO }
    }
    val usdSubtotal = remember(holdings, quotes) {
        var native = BigDecimal.ZERO
        holdings.forEach { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId)
            if (instrument?.currency?.uppercase() == "USD") {
                val price = quotes.quotes[instrument.id]?.price ?: instrument.quote.manualPrice
                if (price != null) {
                    native = native.add(quantity.multiply(price))
                }
            }
        }
        native.takeIf { it.signum() != 0 }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(categoryColor),
                    )
                    Text(
                        text = category.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = FolioInk,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatPercent(category.share),
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = formatCny(category.valueCny),
                        style = MaterialTheme.typography.titleLarge,
                        color = FolioInk,
                    )
                    Text(
                        text = "目标 ${formatCny(category.targetValueCny)} · ${formatPercent(category.targetShare)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "区间 ${formatPercent(SnapshotCalculator.fraction(category.category.minPct))}" +
                        " – ${formatPercent(SnapshotCalculator.fraction(category.category.maxPct))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            CategoryRangeBar(
                category = category,
                expanded = expanded,
                goldSource = data.settings.gold.defaultSource,
                usdCny = quotes.fxRates["USD"],
            )
            usdSubtotal?.let { native ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "美元资产 ${formatNative(native, "USD")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (category.incomplete) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "部分价格缺失，估值不完整",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioCoral,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = FolioDivider)
                if (holdings.isEmpty()) {
                    Text(
                        text = "该类别暂无持仓",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    holdings.forEach { (key, quantity) ->
                        HoldingRow(
                            data = data,
                            quotes = quotes,
                            snapshot = snapshot,
                            key = key,
                            quantity = quantity,
                        )
                        HorizontalDivider(color = FolioDivider)
                    }
                }
            }
        }
    }
}

/**
 * One rule threshold annotated on the range bar: where it sits on the bar,
 * what it is as a percent, the dynamic CNY amount at that percent, and how
 * far the current value is from it.
 */
private data class ThresholdAnnotation(
    val name: String,
    val position: Float,
    val percent: BigDecimal,
    val amount: BigDecimal?,
    val diff: BigDecimal?,
    /**
     * true: a positive diff keeps the category in range (lower bound);
     * false: a negative diff keeps it in range (upper bound);
     * null: neutral reference (target).
     */
    val goodWhenPositive: Boolean?,
    /** Gold only: the base unit price at this threshold, in the active gold source's unit. */
    val unitPriceLabel: String? = null,
)

@Composable
private fun CategoryRangeBar(
    category: CategorySnapshot,
    expanded: Boolean,
    goldSource: String,
    usdCny: BigDecimal?,
) {
    val minShare = SnapshotCalculator.fraction(category.category.minPct)
    val maxShare = SnapshotCalculator.fraction(category.category.maxPct)
    val window = remember(category) {
        RangeBarWindow.of(
            share = category.share.toFloat(),
            minShare = minShare.toFloat(),
            maxShare = maxShare.toFloat(),
        )
    }
    val markerPosition = window.position(category.share.toFloat())
    val statusColor = if (category.inRange) FolioGreen else FolioCoral
    // Threshold unit prices follow the configured gold source: AU9999 quotes
    // CNY per gram, XAU quotes USD per troy ounce.
    val goldInCnyPerGram = goldSource.equals("AU9999", ignoreCase = true) ||
        goldSource.equals("sge", ignoreCase = true)
    val minPriceLabel = goldPriceLabel(
        cnyPerGram = category.goldMinPriceCnyPerGram,
        inCnyPerGram = goldInCnyPerGram,
        usdCny = usdCny,
    )
    val maxPriceLabel = goldPriceLabel(
        cnyPerGram = category.goldMaxPriceCnyPerGram,
        inCnyPerGram = goldInCnyPerGram,
        usdCny = usdCny,
    )
    val thresholds = remember(category, window, minPriceLabel, maxPriceLabel) {
        listOf(
            ThresholdAnnotation(
                name = "最低",
                position = window.position(minShare.toFloat()),
                percent = minShare,
                amount = category.minValueCny,
                diff = category.gapToMinCny,
                goodWhenPositive = true,
                unitPriceLabel = minPriceLabel,
            ),
            ThresholdAnnotation(
                name = "标准",
                position = window.position(category.targetShare.toFloat()),
                percent = category.targetShare,
                amount = category.targetValueCny,
                diff = category.gapToTargetCny,
                goodWhenPositive = null,
            ),
            ThresholdAnnotation(
                name = "最高",
                position = window.position(maxShare.toFloat()),
                percent = maxShare,
                amount = category.maxValueCny,
                diff = category.gapToMaxCny,
                goodWhenPositive = false,
                unitPriceLabel = maxPriceLabel,
            ),
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        MarkerCaption(
            percent = category.share,
            amount = category.valueCny,
            position = markerPosition,
            color = statusColor,
        )
        RangeTrack(
            window = window,
            minShare = minShare.toFloat(),
            maxShare = maxShare.toFloat(),
            targetShare = category.targetShare.toFloat(),
            markerPosition = markerPosition,
            markerColor = statusColor,
        )
        ThresholdAxis(
            window = window,
            thresholds = thresholds,
            expanded = expanded,
        )
    }
}

/** Current value callout pinned above the marker circle. */
@Composable
private fun MarkerCaption(
    percent: BigDecimal,
    amount: BigDecimal?,
    position: Float,
    color: Color,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val percentText = formatPercent(percent)
    val amountText = formatCnyCompact(amount)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(30.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val edgePx = with(density) { 2.dp.toPx() }
        val captionWidth = maxOf(
            measurer.measure(percentText, style).size.width,
            measurer.measure(amountText, style).size.width,
        ).toFloat()
        val center = clampLabelCenter(position * widthPx, captionWidth, widthPx, edgePx)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = with(density) { (center - widthPx / 2f).toDp() }),
        ) {
            Text(
                text = percentText,
                style = style,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = amountText,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The bar itself. The rule band is tinted green, everything outside it is
 * tinted coral, so out-of-range areas read as a danger zone before any label.
 */
@Composable
private fun RangeTrack(
    window: RangeBarWindow,
    minShare: Float,
    maxShare: Float,
    targetShare: Float,
    markerPosition: Float,
    markerColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        val trackHeight = 8.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = Color(0xFFF1F1F1),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = radius,
        )
        val minX = window.position(minShare) * size.width
        val maxX = window.position(maxShare) * size.width
        if (minX > 0f) {
            drawRoundRect(
                color = FolioCoral.copy(alpha = 0.10f),
                topLeft = Offset(0f, top),
                size = Size(minX, trackHeight),
                cornerRadius = radius,
            )
        }
        if (maxX > minX) {
            drawRoundRect(
                color = FolioGreen.copy(alpha = 0.16f),
                topLeft = Offset(minX, top),
                size = Size(maxX - minX, trackHeight),
                cornerRadius = radius,
            )
        }
        if (maxX < size.width) {
            drawRoundRect(
                color = FolioCoral.copy(alpha = 0.10f),
                topLeft = Offset(maxX, top),
                size = Size(size.width - maxX, trackHeight),
                cornerRadius = radius,
            )
        }
        listOf(minX, maxX).forEach { x ->
            drawLine(
                color = Color(0xFFB3B9BB),
                start = Offset(x, top - 2.dp.toPx()),
                end = Offset(x, top + trackHeight + 2.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val targetX = window.position(targetShare) * size.width
        drawLine(
            color = FolioInk.copy(alpha = 0.45f),
            start = Offset(targetX, top - 3.dp.toPx()),
            end = Offset(targetX, top + trackHeight + 3.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
        val markerCenter = Offset(markerPosition * size.width, size.height / 2f)
        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = markerCenter)
        drawCircle(color = markerColor, radius = 4.dp.toPx(), center = markerCenter)
    }
}

/**
 * Threshold annotations below the bar, each centred under its own tick:
 * percent and dynamic amount always, plus the threshold name and the
 * distance to the current value when the card is expanded.
 */
@Composable
private fun ThresholdAxis(
    window: RangeBarWindow,
    thresholds: List<ThresholdAnnotation>,
    expanded: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val percentStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val amountStyle = MaterialTheme.typography.labelSmall
    val unitPriceStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val diffStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val endStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val leftEnd = "${(window.min * 100f).roundToInt()}%"
    val rightEnd = "${(window.max * 100f).roundToInt()}%"
    // Gold adds a threshold unit-price line under the dynamic amount.
    val hasUnitPrices = thresholds.any { it.unitPriceLabel != null }
    val axisHeight = (if (expanded) 58.dp else 34.dp) + if (hasUnitPrices) 15.dp else 0.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(axisHeight)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { 6.dp.toPx() }
        val edgePx = with(density) { 2.dp.toPx() }

        val widths = thresholds.map { threshold ->
            val texts = buildList {
                if (expanded) add(threshold.name to nameStyle)
                add(formatPercent(threshold.percent) to percentStyle)
                add(formatCnyCompact(threshold.amount) to amountStyle)
                threshold.unitPriceLabel?.let { add(it to unitPriceStyle) }
                if (expanded) add(diffText(threshold.diff) to diffStyle)
            }
            texts.maxOf { (text, style) -> measurer.measure(text, style).size.width }.toFloat()
        }
        val leftEndWidth = measurer.measure(leftEnd, endStyle).size.width.toFloat()
        val rightEndWidth = measurer.measure(rightEnd, endStyle).size.width.toFloat()
        // The window end labels share the percent line, so only the percent
        // text needs to clear them; column-to-column separation still uses
        // the widest line of each column.
        val percentWidths = thresholds.map {
            measurer.measure(formatPercent(it.percent), percentStyle).size.width.toFloat()
        }
        val centers = resolveColumnCenters(
            desired = thresholds.map { it.position * widthPx },
            widths = widths,
            edgeWidths = percentWidths,
            leftBound = edgePx + leftEndWidth + gapPx,
            rightBound = widthPx - edgePx - rightEndWidth - gapPx,
            gap = gapPx,
        )

        // Window ends sit on the percent line so they read as axis bounds,
        // not as threshold names.
        val endTop = if (expanded) 12.dp else 0.dp
        Text(
            text = leftEnd,
            style = endStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(top = endTop),
        )
        Text(
            text = rightEnd,
            style = endStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = endTop),
        )
        thresholds.forEachIndexed { index, threshold ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = with(density) { (centers[index] - widthPx / 2f).toDp() }),
            ) {
                if (expanded) {
                    Text(
                        text = threshold.name,
                        style = nameStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatPercent(threshold.percent),
                    style = percentStyle,
                    color = FolioInk,
                )
                Text(
                    text = formatCnyCompact(threshold.amount),
                    style = amountStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                threshold.unitPriceLabel?.let { label ->
                    Text(
                        text = label,
                        style = unitPriceStyle,
                        color = FolioGold,
                    )
                }
                if (expanded) {
                    Text(
                        text = diffText(threshold.diff),
                        style = diffStyle,
                        color = diffColor(threshold),
                    )
                }
            }
        }
    }
}

@Composable
private fun diffColor(threshold: ThresholdAnnotation): Color {
    val diff = threshold.diff ?: return MaterialTheme.colorScheme.onSurfaceVariant
    val goodWhenPositive = threshold.goodWhenPositive
        ?: return FolioInk.copy(alpha = 0.75f)
    val good = if (diff.signum() == 0) {
        true
    } else if (goodWhenPositive) {
        diff.signum() > 0
    } else {
        diff.signum() < 0
    }
    return if (good) FolioGreen else FolioCoral
}

private fun diffText(diff: BigDecimal?): String = when {
    diff == null -> "--"
    diff.signum() == 0 -> "持平"
    diff.signum() > 0 -> "+" + formatCnyCompact(diff)
    else -> formatCnyCompact(diff)
}

private val TROY_OUNCE_GRAMS = BigDecimal("31.1035")

/**
 * Formats a gold threshold price in the active source's unit: CNY per gram
 * for AU9999, USD per troy ounce for XAU (base price, before premiums).
 */
private fun goldPriceLabel(
    cnyPerGram: BigDecimal?,
    inCnyPerGram: Boolean,
    usdCny: BigDecimal?,
): String? {
    if (cnyPerGram == null) return null
    if (inCnyPerGram) return formatCny(cnyPerGram) + "/克"
    val rate = usdCny ?: return null
    if (rate.signum() <= 0) return null
    val usdPerOunce = cnyPerGram
        .multiply(TROY_OUNCE_GRAMS)
        .divide(rate, 10, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP)
    return formatNative(usdPerOunce, "USD") + "/盎司"
}

/** Keeps a label centred on [center] but fully inside the bar. */
private fun clampLabelCenter(center: Float, width: Float, total: Float, edge: Float): Float {
    val half = width / 2f
    val minCenter = half + edge
    val maxCenter = total - half - edge
    if (minCenter >= maxCenter) return total / 2f
    return center.coerceIn(minCenter, maxCenter)
}

/**
 * Column centres follow their tick positions, but are nudged apart so
 * neighbouring annotations never overlap and never leave the bar.
 */
private fun resolveColumnCenters(
    desired: List<Float>,
    widths: List<Float>,
    edgeWidths: List<Float>,
    leftBound: Float,
    rightBound: Float,
    gap: Float,
): List<Float> {
    if (desired.isEmpty()) return emptyList()
    val centers = desired.toFloatArray()

    fun separateForward() {
        centers[0] = maxOf(centers[0], leftBound + edgeWidths.first() / 2f)
        for (index in 1 until centers.size) {
            val minimum = centers[index - 1] + widths[index - 1] / 2f + gap + widths[index] / 2f
            if (centers[index] < minimum) centers[index] = minimum
        }
    }

    fun separateBackward() {
        centers[centers.lastIndex] = minOf(
            centers[centers.lastIndex],
            rightBound - edgeWidths.last() / 2f,
        )
        for (index in centers.lastIndex - 1 downTo 0) {
            val maximum = centers[index + 1] - widths[index + 1] / 2f - gap - widths[index] / 2f
            if (centers[index] > maximum) centers[index] = maximum
        }
    }

    separateForward()
    separateBackward()
    separateForward()
    return centers.toList()
}

@Composable
private fun EmptyHomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun MissingPricesBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, FolioCoral.copy(alpha = 0.25f)),
    ) {
        Text(
            text = "部分标的价格缺失",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun parseCategoryColor(category: Category): Color =
    runCatching { Color(android.graphics.Color.parseColor(category.color)) }
        .getOrElse { FolioGold }
