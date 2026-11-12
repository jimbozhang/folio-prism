package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.calc.eventComparator
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.FolioFilterChip
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted

@Composable
fun TransactionsScreen(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
    contentPadding: PaddingValues,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val categoryNames = remember(data.settings.categories) {
        data.settings.categories.associate { it.id to it.name }
    }
    val sorted = remember(data.events) {
        data.events.sortedWith(eventComparator().reversed())
    }
    val filtered = remember(sorted, typeFilter) {
        if (typeFilter == null) sorted else sorted.filter { it.filterKey() == typeFilter }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "交易",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FolioFilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = "全部",
                    )
                    listOf(
                        "transfer" to "转账",
                        "income" to "收益",
                        "external" to "外部",
                        "expense" to "支出",
                        "opening" to "期初",
                        "adjustment" to "调整",
                    ).forEach { (key, label) ->
                        FolioFilterChip(
                            selected = typeFilter == key,
                            onClick = { typeFilter = key },
                            label = label,
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = if (sorted.isEmpty()) {
                                "暂无事件。点击右下角「+」录入第一笔。"
                            } else {
                                "当前筛选下没有事件。"
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        data = data,
                        categoryNames = categoryNames,
                        onEdit = {
                            editingEvent = event
                            showEditor = true
                        },
                        onDelete = {
                            val remove = linkedIdsToRemove(data, event)
                            onUpdateData { current ->
                                current.copy(events = current.events.filterNot { it.id in remove })
                            }
                        },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingEvent = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = FolioGold,
            contentColor = Color(0xFF2B1D0E),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("记一笔") },
        )
    }

    if (showEditor) {
        EventEditorDialog(
            data = data,
            quotes = quotes,
            initial = editingEvent,
            onDismiss = { showEditor = false },
            onSave = { newEvents ->
                val old = editingEvent
                onUpdateData { current ->
                    var events = current.events
                    if (old != null) {
                        val remove = linkedIdsToRemove(current, old)
                        events = events.filterNot { it.id in remove }
                    }
                    current.copy(events = events + newEvents)
                }
                showEditor = false
            },
        )
    }
}

@Composable
private fun EventRow(
    event: Event,
    data: PortfolioData,
    categoryNames: Map<String, String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = eventAccent(event)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.jimbozhang.folioprism.ui.theme.FolioDivider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = eventIcon(event),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = eventTypeLabel(event),
                        style = MaterialTheme.typography.titleSmall,
                        color = FolioInk,
                    )
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "编辑",
                                tint = FolioMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = FolioMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    text = event.date.take(16).replace('T', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = eventSummary(data, categoryNames, event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolioInk,
                )
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun eventIcon(event: Event): ImageVector = when (event) {
    is TransferEvent -> Icons.Filled.SwapHoriz
    is IncomeEvent -> Icons.Filled.Payments
    is ExternalEvent -> Icons.Filled.AccountBalanceWallet
    is ExpenseEvent -> Icons.AutoMirrored.Filled.ReceiptLong
    is OpeningEvent -> Icons.Filled.Flag
    is AdjustmentEvent -> Icons.Filled.Tune
}

private fun eventAccent(event: Event): Color = when (event) {
    is TransferEvent -> FolioGold
    is IncomeEvent -> FolioCoral
    is ExternalEvent -> Color(0xFF0984E3)
    is ExpenseEvent -> FolioGreen
    is OpeningEvent -> Color(0xFF6C5CE7)
    is AdjustmentEvent -> FolioMuted
}

private fun Event.filterKey(): String = when (this) {
    is TransferEvent -> "transfer"
    is IncomeEvent -> "income"
    is ExternalEvent -> "external"
    is ExpenseEvent -> "expense"
    is OpeningEvent -> "opening"
    is AdjustmentEvent -> "adjustment"
}

private fun eventTypeLabel(event: Event): String = when (event) {
    is TransferEvent -> "转账"
    is IncomeEvent -> if (event.kind == IncomeKind.DIVIDEND) "现金分红" else "利息"
    is ExternalEvent -> if (event.direction == ExternalDirection.IN) "外部入金" else "外部出金"
    is ExpenseEvent -> "支出"
    is OpeningEvent -> "期初"
    is AdjustmentEvent -> "调整"
}

private fun eventSummary(
    data: PortfolioData,
    categoryNames: Map<String, String>,
    event: Event,
): String = when (event) {
    is TransferEvent -> {
        val from = positionLabel(data, event.from)
        val to = positionLabel(data, event.to)
        "${from} → ${to} · 转出 ${formatCny(event.fromValueCny)} / 转入 ${formatCny(event.toValueCny)}"
    }

    is IncomeEvent -> {
        val source = event.source?.let { positionLabel(data, it) } ?: "外部"
        val kind = if (event.kind == IncomeKind.DIVIDEND) "分红" else "利息"
        "$kind · $source → ${positionLabel(data, event.destination)} · " +
            formatNative(event.amount, event.currency) +
            (if (event.immediateExternalWithdrawal) "（已转出）" else "")
    }

    is ExternalEvent -> {
        val direction = if (event.direction == ExternalDirection.IN) "入金" else "出金"
        "$direction · ${positionLabel(data, event.position)} · " +
            formatNative(event.amount, event.currency)
    }

    is ExpenseEvent -> {
        val position = event.position?.let { positionLabel(data, it) } ?: "非现金损耗"
        val category = categoryNames[event.categoryId] ?: event.categoryId
        "支出 · $position · $category · " + formatNative(event.amount, event.currency)
    }

    is OpeningEvent -> {
        val instrument = data.instrument(event.position.instrumentId)
        val unit = instrument?.unit ?: "份"
        val value = event.quantity?.let { formatQuantity(it) + " $unit" }
            ?: event.amount?.let { formatNative(it, instrument?.currency ?: "CNY") }
            ?: "--"
        "期初 · ${positionLabel(data, event.position)} · $value"
    }

    is AdjustmentEvent -> {
        val instrument = data.instrument(event.position.instrumentId)
        val unit = instrument?.unit ?: "份"
        val delta = event.deltaQuantity?.let { "份额 " + formatQuantity(it) + " $unit" }
            ?: event.deltaAmount?.let { "金额 " + formatNative(it, instrument?.currency ?: "CNY") }
            ?: "--"
        "调整 · ${positionLabel(data, event.position)} · $delta · ${event.reason}"
    }
}

private fun positionLabel(data: PortfolioData, ref: PositionRef): String {
    val institution = data.institutions.firstOrNull { it.id == ref.institutionId }?.name
        ?: ref.institutionId
    val instrument = data.instrument(ref.instrumentId)
    val instrumentLabel = instrument?.let { "${it.code} ${it.name}" } ?: ref.instrumentId
    return "$institution · $instrumentLabel"
}

private fun linkedIdsToRemove(data: PortfolioData, event: Event): Set<String> {
    val ids = mutableSetOf(event.id)
    when (event) {
        is TransferEvent -> data.events
            .filterIsInstance<ExpenseEvent>()
            .filter { it.linkedTransferId == event.id }
            .forEach { ids += it.id }

        is IncomeEvent -> event.linkedEventId?.let { ids += it }
        else -> Unit
    }
    return ids
}
