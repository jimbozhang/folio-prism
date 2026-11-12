package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.calc.eventComparator
import com.jimbozhang.folioprism.domain.calc.localDate
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
import com.jimbozhang.folioprism.ui.components.FilterDropdown
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGoldSoft
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val eventTypeOptions = listOf(
    "transfer" to "转账",
    "income" to "收益",
    "external" to "外部",
    "expense" to "支出",
    "opening" to "期初",
    "adjustment" to "调整",
)

private data class EventDay(
    val date: LocalDate,
    val events: List<Event>,
)

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
    val days = remember(filtered) {
        filtered.groupBy { it.localDate() }
            .map { (date, events) -> EventDay(date, events) }
    }
    val openNewEvent: () -> Unit = {
        editingEvent = null
        showEditor = true
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "交易",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                FilterDropdown(
                    selectedId = typeFilter,
                    allId = "all",
                    options = eventTypeOptions,
                    onSelected = { typeFilter = it },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = openNewEvent),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(FolioGoldSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "记一笔",
                            tint = FolioGold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
                                "暂无事件。"
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
                items(days, key = { it.date.toString() }) { day ->
                    DayGroup(
                        day = day,
                        data = data,
                        categoryNames = categoryNames,
                        onEdit = { event ->
                            editingEvent = event
                            showEditor = true
                        },
                    )
                }
            }
        }

    if (showEditor) {
        EventEditorDialog(
            data = data,
            quotes = quotes,
            initial = editingEvent,
            onDismiss = { showEditor = false },
            onDelete = editingEvent?.let { event ->
                {
                    val remove = linkedIdsToRemove(data, event)
                    onUpdateData { current ->
                        current.copy(events = current.events.filterNot { it.id in remove })
                    }
                    showEditor = false
                    editingEvent = null
                }
            },
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
private fun DayGroup(
    day: EventDay,
    data: PortfolioData,
    categoryNames: Map<String, String>,
    onEdit: (Event) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dayLabel(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = FolioMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${day.events.size} 笔",
                style = MaterialTheme.typography.labelSmall,
                color = FolioMuted,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                day.events.forEachIndexed { index, event ->
                    if (index > 0) {
                        HorizontalDivider(color = FolioDivider)
                    }
                    EventRow(
                        event = event,
                        data = data,
                        categoryNames = categoryNames,
                        onClick = { onEdit(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: Event,
    data: PortfolioData,
    categoryNames: Map<String, String>,
    onClick: () -> Unit,
) {
    val accent = eventAccent(event)
    val secondary = eventSecondaryAmount(data, event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = eventIcon(event),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventTypeLabel(event),
                style = MaterialTheme.typography.bodyMedium,
                color = FolioInk,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = eventSubtitle(data, categoryNames, event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.note.isNotBlank()) {
                Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = eventAmount(data, event),
                style = MaterialTheme.typography.bodyMedium,
                color = FolioInk,
                maxLines = 1,
            )
            secondary?.let { (text, color) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = FolioMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun eventIcon(event: Event): ImageVector = when (event) {
    is TransferEvent -> Icons.Outlined.SwapHoriz
    is IncomeEvent -> Icons.Outlined.Payments
    is ExternalEvent -> Icons.Outlined.AccountBalanceWallet
    is ExpenseEvent -> Icons.AutoMirrored.Outlined.ReceiptLong
    is OpeningEvent -> Icons.Outlined.Flag
    is AdjustmentEvent -> Icons.Outlined.Tune
}

private fun eventAccent(event: Event): Color = when (event) {
    is TransferEvent -> FolioGold
    is IncomeEvent -> FolioCoral
    is ExternalEvent -> FolioBlue
    is ExpenseEvent -> FolioGreen
    is OpeningEvent -> FolioPurple
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
    is IncomeEvent -> if (event.kind == IncomeKind.DIVIDEND) "分红" else "利息"
    is ExternalEvent -> if (event.direction == ExternalDirection.IN) "外部入金" else "外部出金"
    is ExpenseEvent -> "支出"
    is OpeningEvent -> "期初"
    is AdjustmentEvent -> "调整"
}

private fun eventSubtitle(
    data: PortfolioData,
    categoryNames: Map<String, String>,
    event: Event,
): String = when (event) {
    is TransferEvent -> "${shortPosition(data, event.from)} → ${shortPosition(data, event.to)}"

    is IncomeEvent -> {
        val source = event.source?.let { shortPosition(data, it) } ?: "外部"
        val path = "$source → ${shortPosition(data, event.destination)}"
        if (event.immediateExternalWithdrawal) "$path · 已转出" else path
    }

    is ExternalEvent -> shortPosition(data, event.position)

    is ExpenseEvent -> {
        val position = event.position?.let { shortPosition(data, it) } ?: "非现金损耗"
        val category = categoryNames[event.categoryId] ?: event.categoryId
        "$position · $category"
    }

    is OpeningEvent -> shortPosition(data, event.position)

    is AdjustmentEvent -> "${shortPosition(data, event.position)} · ${event.reason}"
}

private fun eventAmount(data: PortfolioData, event: Event): String = when (event) {
    is TransferEvent -> formatCny(event.toValueCny)

    is IncomeEvent -> "+" + formatNative(event.amount, event.currency)

    is ExternalEvent -> {
        val sign = if (event.direction == ExternalDirection.IN) "+" else "-"
        sign + formatNative(event.amount.abs(), event.currency)
    }

    is ExpenseEvent -> {
        val sign = if (event.amount.signum() >= 0) "-" else "+"
        sign + formatNative(event.amount.abs(), event.currency)
    }

    is OpeningEvent -> {
        val instrument = data.instrument(event.position.instrumentId)
        val unit = instrument?.unit ?: "份"
        event.quantity?.let { formatQuantity(it) + " " + unit }
            ?: event.amount?.let { formatNative(it, instrument?.currency ?: "CNY") }
            ?: "--"
    }

    is AdjustmentEvent -> {
        val instrument = data.instrument(event.position.instrumentId)
        val unit = instrument?.unit ?: "份"
        when {
            event.deltaQuantity != null ->
                signedQuantity(event.deltaQuantity, unit)
            event.deltaAmount != null ->
                signedNative(event.deltaAmount, instrument?.currency ?: "CNY")
            else -> "--"
        }
    }
}

private fun eventSecondaryAmount(
    data: PortfolioData,
    event: Event,
): Pair<String, Color>? = when (event) {
    is TransferEvent -> {
        if (event.lossValueCny.signum() != 0) {
            val color = if (event.lossValueCny.signum() > 0) FolioGreen else FolioCoral
            "损耗 ${signedCny(event.lossValueCny)}" to color
        } else {
            null
        }
    }

    is IncomeEvent ->
        if (event.currency.uppercase() in setOf("CNY", "CNH")) {
            null
        } else {
            "≈ ${formatCny(event.amountCny)}" to FolioMuted
        }

    is ExternalEvent ->
        if (event.currency.uppercase() in setOf("CNY", "CNH")) {
            null
        } else {
            "≈ ${formatCny(event.amountCny)}" to FolioMuted
        }

    is ExpenseEvent ->
        if (event.currency.uppercase() in setOf("CNY", "CNH")) {
            null
        } else {
            "≈ ${formatCny(event.amountCny)}" to FolioMuted
        }

    else -> null
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val base = when {
        date == today -> "今天 · ${date.monthValue}月${date.dayOfMonth}日"
        date == today.minusDays(1) -> "昨天 · ${date.monthValue}月${date.dayOfMonth}日"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    return "$base · $weekday"
}

private fun shortPosition(data: PortfolioData, ref: PositionRef): String {
    val institution = data.institutions.firstOrNull { it.id == ref.institutionId }?.name
        ?: ref.institutionId
    val instrument = data.instrument(ref.instrumentId)
    val code = instrument?.code ?: ref.instrumentId
    return "$institution $code"
}

private fun signedCny(value: BigDecimal): String =
    if (value.signum() < 0) "-" + formatCny(value.abs()) else formatCny(value)

private fun signedNative(value: BigDecimal, currency: String): String =
    (if (value.signum() < 0) "-" else "+") + formatNative(value.abs(), currency)

private fun signedQuantity(value: BigDecimal, unit: String): String =
    (if (value.signum() < 0) "-" else "+") + formatQuantity(value.abs()) + " " + unit

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
