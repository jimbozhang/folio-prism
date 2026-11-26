package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toMoneyAmount
import com.jimbozhang.folioprism.ui.components.formatCashNative
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal

@Composable
internal fun EventDetailDialog(
    data: PortfolioData,
    event: Event,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = FolioInk,
                        )
                    }
                    Text(
                        text = "交易详情",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = FolioInk,
                    )
                    TextButton(onClick = onEdit) {
                        Text("编辑", color = FolioGold)
                    }
                }
                HorizontalDivider(color = FolioDivider)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DetailHeader(data = data, event = event)
                    DetailBody(data = data, event = event)
                    if (event.note.isNotBlank()) {
                        NoteBlock(note = event.note)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(data: PortfolioData, event: Event) {
    val accent = eventAccent(event)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = eventIcon(event),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventTypeLabel(event),
                style = MaterialTheme.typography.titleMedium,
                color = FolioInk,
            )
            Text(
                text = event.date.take(16).replace('T', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
        }
        Text(
            text = eventAmount(data, event),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
    }
}

@Composable
private fun DetailBody(data: PortfolioData, event: Event) {
    val categoryNames = data.settings.categories.associate { it.id to it.name }
    val rows = when (event) {
        is TransferEvent -> transferRows(data, event)
        is IncomeEvent -> incomeRows(data, event)
        is ExternalEvent -> externalRows(data, event)
        is ExpenseEvent -> expenseRows(data, event, categoryNames)
        is OpeningEvent -> openingRows(data, event)
        is AdjustmentEvent -> adjustmentRows(data, event)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFAFAFA),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(color = FolioDivider)
                }
                DetailLine(label = label, value = value)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NoteBlock(note: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFAFAFA),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "备注",
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = FolioInk,
            )
        }
    }
}

private fun transferRows(data: PortfolioData, event: TransferEvent): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "转出" to positionLabel(data, event.from)
    rows += "转出数量" to quantityLabel(data, event.from, event.fromQuantity)
    event.fromPrice?.let {
        rows += "转出单价" to formatNative(it, currencyOf(data, event.from))
    }
    rows += "转入" to positionLabel(data, event.to)
    rows += "转入数量" to quantityLabel(data, event.to, event.toQuantity)
    event.toPrice?.let {
        rows += "转入单价" to formatNative(it, currencyOf(data, event.to))
    }
    if (event.lossAmount.signum() != 0) {
        rows += "损耗" to signedNative(event.lossAmount.toMoneyAmount(), event.lossCurrency)
    }
    val feeAmount = event.feeAmount
    if (feeAmount != null && feeAmount.signum() != 0) {
        val position = event.feePosition?.let { positionLabel(data, it) }
        val fee = signedNative(
            feeAmount.toMoneyAmount(),
            event.feeCurrency ?: currencyOf(data, event.from),
        )
        rows += "手续费" to (position?.let { "$it · $fee" } ?: fee)
    }
    return rows
}

private fun incomeRows(data: PortfolioData, event: IncomeEvent): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "类型" to if (event.kind == IncomeKind.DIVIDEND) "现金分红" else "利息"
    rows += "来源" to (event.source?.let { positionLabel(data, it) } ?: "外部")
    rows += "入账" to positionLabel(data, event.destination)
    rows += "金额" to formatCashNative(event.amount, event.currency)
    rows += "立即转出" to if (event.immediateExternalWithdrawal) {
        event.externalAccountLabel?.let { "是（$it）" } ?: "是"
    } else {
        "否"
    }
    return rows
}

private fun externalRows(data: PortfolioData, event: ExternalEvent): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "方向" to if (event.direction == ExternalDirection.IN) "入金" else "出金"
    rows += "分类" to if (event.classification == ExternalClassification.CONSUMPTION) "消费" else "本金"
    rows += "账户" to positionLabel(data, event.position)
    rows += "金额" to formatCashNative(event.amount, event.currency)
    event.counterpartyLabel?.let { rows += "外部账户" to it }
    return rows
}

private fun expenseRows(
    data: PortfolioData,
    event: ExpenseEvent,
    categoryNames: Map<String, String>,
): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "账户" to (event.position?.let { positionLabel(data, it) } ?: "非现金损耗")
    rows += "类别" to (categoryNames[event.categoryId] ?: event.categoryId)
    rows += "金额" to signedCashNative(event.amount, event.currency)
    event.linkedTransferId?.let { rows += "关联转账" to it }
    return rows
}

private fun openingRows(data: PortfolioData, event: OpeningEvent): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "持仓" to positionLabel(data, event.position)
    event.quantity?.let {
        rows += "数量" to quantityLabel(data, event.position, it)
    }
    event.amount?.let {
        val formatted = if (isCashOf(data, event.position)) {
            formatCashNative(it, currencyOf(data, event.position))
        } else {
            formatNative(it, currencyOf(data, event.position))
        }
        rows += "金额" to formatted
    }
    event.price?.let {
        rows += "单价" to formatNative(it, currencyOf(data, event.position))
    }
    return rows
}

private fun adjustmentRows(data: PortfolioData, event: AdjustmentEvent): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "持仓" to positionLabel(data, event.position)
    event.deltaQuantity?.let {
        val formatted = if (isCashOf(data, event.position)) {
            signedCashQuantity(it, unitOf(data, event.position))
        } else {
            signedQuantity(it, unitOf(data, event.position))
        }
        rows += "数量增减" to formatted
    }
    event.deltaAmount?.let {
        val formatted = if (isCashOf(data, event.position)) {
            signedCashNative(it, currencyOf(data, event.position))
        } else {
            signedNative(it, currencyOf(data, event.position))
        }
        rows += "金额增减" to formatted
    }
    event.price?.let {
        rows += "单价" to formatNative(it, currencyOf(data, event.position))
    }
    rows += "原因" to event.reason
    return rows
}

private fun positionLabel(data: PortfolioData, ref: PositionRef): String {
    val institution = data.institutions.firstOrNull { it.id == ref.institutionId }?.name
        ?: ref.institutionId
    val instrument = data.instrument(ref.instrumentId)
    return if (instrument == null) {
        "$institution · ${ref.instrumentId}"
    } else {
        "$institution · ${instrument.code} ${instrument.name}"
    }
}

private fun quantityLabel(data: PortfolioData, ref: PositionRef, quantity: BigDecimal): String {
    val instrument = data.instrument(ref.instrumentId) ?: return formatQuantity(quantity)
    return if (instrument.kind == InstrumentKind.CASH) {
        formatCashNative(quantity, instrument.currency)
    } else {
        formatQuantity(quantity) + " " + instrument.unit
    }
}

private fun isCashOf(data: PortfolioData, ref: PositionRef): Boolean =
    data.instrument(ref.instrumentId)?.kind == InstrumentKind.CASH

private fun currencyOf(data: PortfolioData, ref: PositionRef): String =
    data.instrument(ref.instrumentId)?.currency ?: "CNY"

private fun unitOf(data: PortfolioData, ref: PositionRef): String =
    data.instrument(ref.instrumentId)?.unit ?: "份"
