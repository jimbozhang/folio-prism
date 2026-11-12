package com.jimbozhang.folioprism.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.CategorySnapshot
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.calc.Valuation
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.goldPremium
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.components.formatPercent
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import java.math.BigDecimal
import java.time.LocalDate

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

    LazyColumn(
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
            items(snapshot.categories, key = { it.category.id }) { category ->
                CategoryCard(
                    category = category,
                    data = data,
                    quotes = quotes,
                    snapshot = snapshot,
                    positions = positions,
                )
            }
        }
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
) {
    val categoryColor = remember(category.category.color) { parseCategoryColor(category.category) }
    val statusColor = if (category.inRange) FolioGreen else FolioCoral
    var expanded by remember(category.category.id) { mutableStateOf(false) }
    val holdings = remember(category.category.id, positions, data.instruments) {
        positions.entries
            .filter { (key, quantity) ->
                quantity.signum() != 0 &&
                    data.instrument(key.instrumentId)?.categoryId == category.category.id
            }
            .sortedByDescending { (key, _) -> snapshot.positionValues[key] ?: BigDecimal.ZERO }
    }
    val usdSubtotal = remember(holdings, quotes, snapshot) {
        var native = BigDecimal.ZERO
        var cny = BigDecimal.ZERO
        holdings.forEach { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId)
            if (instrument?.currency?.uppercase() == "USD") {
                val price = quotes.quotes[instrument.id]?.price ?: instrument.quote.manualPrice
                if (price != null) {
                    native = native.add(quantity.multiply(price))
                    cny = cny.add(snapshot.positionValues[key] ?: BigDecimal.ZERO)
                }
            }
        }
        if (native.signum() == 0) null else native to cny
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FolioDivider),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
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
            CategoryRangeBar(category = category, color = categoryColor)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (category.inRange) "区间内" else "偏离区间",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
                usdSubtotal?.let { (native, cny) ->
                    Text(
                        text = "美元资产 ${formatNative(native, "USD")}（≈${formatCny(cny)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

@Composable
private fun CategoryRangeBar(category: CategorySnapshot, color: Color) {
    val share = category.share.toFloat()
    val target = category.targetShare.toFloat()
    val min = SnapshotCalculator.fraction(category.category.minPct).toFloat()
    val max = SnapshotCalculator.fraction(category.category.maxPct).toFloat()
    val scale = (maxOf(share, target, min, max, 0.05f) * 1.12f).coerceAtLeast(0.01f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val trackHeight = 6.dp.toPx()
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
                color = FolioGreen.copy(alpha = 0.12f),
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
        val targetX = (target / scale).coerceIn(0f, 1f) * size.width
        drawLine(
            color = FolioInk.copy(alpha = 0.45f),
            start = Offset(targetX, top - 2.dp.toPx()),
            end = Offset(targetX, top + trackHeight + 2.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun HoldingRow(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    snapshot: PortfolioSnapshot,
    key: PositionKey,
    quantity: BigDecimal,
) {
    val instrument = data.instrument(key.instrumentId) ?: return
    val institutionName = data.institutions.firstOrNull { it.id == key.institutionId }?.name
        ?: key.institutionId
    val quotePrice = quotes.quotes[instrument.id]?.price ?: instrument.quote.manualPrice
    val price = if (instrument.kind == InstrumentKind.QUOTED && quotePrice != null) {
        if (Valuation.isGold(instrument)) quotePrice.add(data.goldPremium(key.institutionId))
        else quotePrice
    } else {
        null
    }
    val nativeValue = when {
        instrument.kind == InstrumentKind.CASH -> quantity
        price != null -> quantity.multiply(price)
        else -> null
    }
    val cnyValue = snapshot.positionValues[key]
    val share = if (snapshot.totalValueCny.signum() > 0 && cnyValue != null) {
        SnapshotCalculator.ratio(cnyValue, snapshot.totalValueCny)
    } else {
        null
    }
    val valueText = when {
        nativeValue == null -> "--"
        instrument.currency.uppercase() in setOf("USD", "HKD") ->
            formatNative(nativeValue, instrument.currency) + " / " + formatCny(cnyValue)
        else -> formatNative(nativeValue, instrument.currency)
    }

    Column(modifier = Modifier.padding(vertical = 9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${instrument.code} ${instrument.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolioInk,
                )
                Text(
                    text = "$institutionName · ${formatQuantity(quantity)} ${instrument.unit}" +
                        (price?.let { " × " + formatNative(it, instrument.currency) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolioInk,
                )
                Text(
                    text = formatPercent(share),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "在设置中添加机构与标的，或导入调试数据后再查看持仓。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text = "部分标的价格暂不可用；接入实时行情后将显示完整估值。",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun parseCategoryColor(category: Category): Color =
    runCatching { Color(android.graphics.Color.parseColor(category.color)) }
        .getOrElse { FolioGold }
