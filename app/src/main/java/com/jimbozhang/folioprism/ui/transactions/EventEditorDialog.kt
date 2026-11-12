package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jimbozhang.folioprism.domain.calc.EventValues
import com.jimbozhang.folioprism.domain.calc.parseEventTime
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.OptionChips
import com.jimbozhang.folioprism.ui.components.PositionPicker
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private class EventForm(
    initial: Event?,
    data: PortfolioData,
) {
    var type by mutableStateOf(initial.typeKey())
    var date by mutableStateOf(
        initial?.let { prettyDate(it.date) } ?: prettyDate(OffsetDateTime.now()),
    )
    var note by mutableStateOf(initial?.note ?: "")

    var fromInstitutionId by mutableStateOf<String?>(null)
    var fromInstrumentId by mutableStateOf<String?>(null)
    var fromQuantity by mutableStateOf("")
    var fromPrice by mutableStateOf("")
    var fromFx by mutableStateOf("1")

    var toInstitutionId by mutableStateOf<String?>(null)
    var toInstrumentId by mutableStateOf<String?>(null)
    var toQuantity by mutableStateOf("")
    var toPrice by mutableStateOf("")
    var toFx by mutableStateOf("1")

    var incomeKind by mutableStateOf("dividend")
    var incomeHasSource by mutableStateOf(true)
    var sourceInstitutionId by mutableStateOf<String?>(null)
    var sourceInstrumentId by mutableStateOf<String?>(null)
    var incomeInstitutionId by mutableStateOf<String?>(null)
    var incomeInstrumentId by mutableStateOf<String?>(null)
    var incomeAmount by mutableStateOf("")
    var incomeFx by mutableStateOf("1")
    var immediateWithdrawal by mutableStateOf(false)
    var externalLabel by mutableStateOf("")

    var externalDirection by mutableStateOf("out")
    var externalClassification by mutableStateOf("capital")
    var externalInstitutionId by mutableStateOf<String?>(null)
    var externalInstrumentId by mutableStateOf<String?>(null)
    var externalAmount by mutableStateOf("")
    var externalFx by mutableStateOf("1")

    var expenseNoPosition by mutableStateOf(false)
    var expenseInstitutionId by mutableStateOf<String?>(null)
    var expenseInstrumentId by mutableStateOf<String?>(null)
    var expenseAmount by mutableStateOf("")
    var expenseFx by mutableStateOf("1")
    var expenseCategoryId by mutableStateOf(
        data.settings.categories.firstOrNull()?.id ?: CategoryIds.CASH,
    )

    var openingInstitutionId by mutableStateOf<String?>(null)
    var openingInstrumentId by mutableStateOf<String?>(null)
    var openingQuantity by mutableStateOf("")
    var openingAmount by mutableStateOf("")
    var openingFx by mutableStateOf("1")

    var adjustmentInstitutionId by mutableStateOf<String?>(null)
    var adjustmentInstrumentId by mutableStateOf<String?>(null)
    var adjustmentQuantity by mutableStateOf("")
    var adjustmentAmount by mutableStateOf("")
    var adjustmentFx by mutableStateOf("1")
    var adjustmentReason by mutableStateOf("")

    init {
        when (initial) {
            is TransferEvent -> {
                fromInstitutionId = initial.from.institutionId
                fromInstrumentId = initial.from.instrumentId
                fromQuantity = initial.fromQuantity.toPlainString()
                fromPrice = initial.fromPrice?.toPlainString() ?: ""
                fromFx = initial.fromFxToCny.toPlainString()
                toInstitutionId = initial.to.institutionId
                toInstrumentId = initial.to.instrumentId
                toQuantity = initial.toQuantity.toPlainString()
                toPrice = initial.toPrice?.toPlainString() ?: ""
                toFx = initial.toFxToCny.toPlainString()
            }

            is IncomeEvent -> {
                incomeKind = if (initial.kind == IncomeKind.DIVIDEND) "dividend" else "interest"
                incomeHasSource = initial.source != null
                sourceInstitutionId = initial.source?.institutionId
                sourceInstrumentId = initial.source?.instrumentId
                incomeInstitutionId = initial.destination.institutionId
                incomeInstrumentId = initial.destination.instrumentId
                incomeAmount = initial.amount.toPlainString()
                incomeFx = initial.fxToCny.toPlainString()
                immediateWithdrawal = initial.immediateExternalWithdrawal
                externalLabel = initial.externalAccountLabel ?: ""
            }

            is ExternalEvent -> {
                externalDirection = if (initial.direction == ExternalDirection.IN) "in" else "out"
                externalClassification =
                    if (initial.classification == ExternalClassification.CONSUMPTION) "consumption" else "capital"
                externalInstitutionId = initial.position.institutionId
                externalInstrumentId = initial.position.instrumentId
                externalAmount = initial.amount.toPlainString()
                externalFx = initial.fxToCny.toPlainString()
            }

            is ExpenseEvent -> {
                expenseNoPosition = initial.position == null
                expenseInstitutionId = initial.position?.institutionId
                expenseInstrumentId = initial.position?.instrumentId
                expenseAmount = initial.amount.toPlainString()
                expenseFx = initial.fxToCny.toPlainString()
                expenseCategoryId = initial.categoryId
            }

            is OpeningEvent -> {
                openingInstitutionId = initial.position.institutionId
                openingInstrumentId = initial.position.instrumentId
                openingQuantity = initial.quantity?.toPlainString() ?: ""
                openingAmount = initial.amount?.toPlainString() ?: ""
                openingFx = initial.fxToCny?.toPlainString() ?: "1"
            }

            is AdjustmentEvent -> {
                adjustmentInstitutionId = initial.position.institutionId
                adjustmentInstrumentId = initial.position.instrumentId
                adjustmentQuantity = initial.deltaQuantity?.toPlainString() ?: ""
                adjustmentAmount = initial.deltaAmount?.toPlainString() ?: ""
                adjustmentFx = initial.fxToCny?.toPlainString() ?: "1"
                adjustmentReason = initial.reason
            }

            null -> Unit
        }
    }
}

@Composable
fun EventEditorDialog(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    initial: Event?,
    onDismiss: () -> Unit,
    onSave: (List<Event>) -> Unit,
) {
    val form = remember(initial?.id) { EventForm(initial, data) }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    val cashInstruments = remember(data.instruments) {
        data.instruments.filter { it.kind == InstrumentKind.CASH }
    }

    val save: () -> Unit = {
        buildEvents(data, quotes, form, initial)
            .onSuccess(onSave)
            .onFailure { error = it.message ?: "保存失败" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                        text = if (initial == null) "记一笔" else "编辑事件",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = FolioInk,
                    )
                    TextButton(onClick = save) {
                        Text("保存", color = FolioGold)
                    }
                }
                HorizontalDivider(color = FolioDivider)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OptionChips(
                        options = listOf(
                            "transfer" to "转账",
                            "income" to "收益",
                            "external" to "外部出入金",
                            "expense" to "支出",
                            "opening" to "期初",
                            "adjustment" to "调整",
                        ),
                        selected = form.type,
                        onSelected = { form.type = it },
                        enabled = initial == null,
                    )
                    OutlinedTextField(
                        value = form.date,
                        onValueChange = { form.date = it; error = null },
                        label = { Text("日期（yyyy-MM-dd HH:mm）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (form.type) {
                        "transfer" -> TransferSection(data, quotes, form)
                        "income" -> IncomeSection(data, quotes, form, cashInstruments)
                        "external" -> ExternalSection(data, quote = quotes, form = form, cashInstruments = cashInstruments)
                        "expense" -> ExpenseSection(data, quotes, form, cashInstruments)
                        "opening" -> OpeningSection(data, quotes, form)
                        "adjustment" -> AdjustmentSection(data, quotes, form)
                    }

                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { form.note = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferSection(data: PortfolioData, quotes: QuoteSnapshot, form: EventForm) {
    PositionPicker(
        label = "转出",
        institutions = data.institutions,
        instruments = data.instruments,
        institutionId = form.fromInstitutionId,
        instrumentId = form.fromInstrumentId,
        onInstitutionSelected = { form.fromInstitutionId = it },
        onInstrumentSelected = {
            form.fromInstrumentId = it
            if (form.fromPrice.isBlank()) {
                form.fromPrice = quotePrice(quotes, data.instrument(it))?.toPlainString() ?: ""
            }
            if (form.fromFx.isBlank() || form.fromFx == "1") {
                form.fromFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    PositionPicker(
        label = "转入",
        institutions = data.institutions,
        instruments = data.instruments,
        institutionId = form.toInstitutionId,
        instrumentId = form.toInstrumentId,
        onInstitutionSelected = { form.toInstitutionId = it },
        onInstrumentSelected = {
            form.toInstrumentId = it
            if (form.toPrice.isBlank()) {
                form.toPrice = quotePrice(quotes, data.instrument(it))?.toPlainString() ?: ""
            }
            if (form.toFx.isBlank() || form.toFx == "1") {
                form.toFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    val fromInstrument = form.fromInstrumentId?.let { data.instrument(it) }
    val toInstrument = form.toInstrumentId?.let { data.instrument(it) }
    OutlinedTextField(
        value = form.fromQuantity,
        onValueChange = { form.fromQuantity = it; },
        label = {
            Text(
                if (fromInstrument?.kind == InstrumentKind.CASH) "转出金额（原币）"
                else "转出份额",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.toQuantity,
        onValueChange = { form.toQuantity = it },
        label = {
            Text(
                if (toInstrument?.kind == InstrumentKind.CASH) "转入金额（原币）"
                else "转入份额",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (fromInstrument?.kind == InstrumentKind.QUOTED) {
        OutlinedTextField(
            value = form.fromPrice,
            onValueChange = { form.fromPrice = it },
            label = { Text("转出单价（${fromInstrument.currency}）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (toInstrument?.kind == InstrumentKind.QUOTED) {
        OutlinedTextField(
            value = form.toPrice,
            onValueChange = { form.toPrice = it },
            label = { Text("转入单价（${toInstrument.currency}）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.fromFx,
            onValueChange = { form.fromFx = it },
            label = { Text("转出汇率→CNY") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.toFx,
            onValueChange = { form.toFx = it },
            label = { Text("转入汇率→CNY") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    transferPreview(data, form)?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "转出与转入价值不相等时，会自动生成一笔损耗支出。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IncomeSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    cashInstruments: List<Instrument>,
) {
    OptionChips(
        options = listOf("dividend" to "分红", "interest" to "利息"),
        selected = form.incomeKind,
        onSelected = { form.incomeKind = it },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("有来源持仓", style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = form.incomeHasSource,
            onCheckedChange = {
                form.incomeHasSource = it
                if (!it) {
                    form.sourceInstitutionId = null
                    form.sourceInstrumentId = null
                }
            },
        )
    }
    if (form.incomeHasSource) {
        PositionPicker(
            label = "来源持仓",
            institutions = data.institutions,
            instruments = data.instruments,
            institutionId = form.sourceInstitutionId,
            instrumentId = form.sourceInstrumentId,
            onInstitutionSelected = { form.sourceInstitutionId = it },
            onInstrumentSelected = { form.sourceInstrumentId = it },
        )
    }
    PositionPicker(
        label = "入账现金账户",
        institutions = data.institutions,
        instruments = cashInstruments,
        institutionId = form.incomeInstitutionId,
        instrumentId = form.incomeInstrumentId,
        onInstitutionSelected = { form.incomeInstitutionId = it },
        onInstrumentSelected = {
            form.incomeInstrumentId = it
            if (form.incomeFx.isBlank() || form.incomeFx == "1") {
                form.incomeFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    OutlinedTextField(
        value = form.incomeAmount,
        onValueChange = { form.incomeAmount = it },
        label = { Text("金额（入账币种）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.incomeFx,
        onValueChange = { form.incomeFx = it },
        label = { Text("汇率→CNY") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("立即转出到外部消费账户", style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = form.immediateWithdrawal,
            onCheckedChange = { form.immediateWithdrawal = it },
        )
    }
    if (form.immediateWithdrawal) {
        OutlinedTextField(
            value = form.externalLabel,
            onValueChange = { form.externalLabel = it },
            label = { Text("外部账户标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExternalSection(
    data: PortfolioData,
    quote: QuoteSnapshot,
    form: EventForm,
    cashInstruments: List<Instrument>,
) {
    OptionChips(
        options = listOf("in" to "入金", "out" to "出金"),
        selected = form.externalDirection,
        onSelected = { form.externalDirection = it },
    )
    if (form.externalDirection == "out") {
        OptionChips(
            options = listOf("capital" to "本金取出", "consumption" to "消费/领出"),
            selected = form.externalClassification,
            onSelected = { form.externalClassification = it },
        )
    }
    PositionPicker(
        label = "现金账户",
        institutions = data.institutions,
        instruments = cashInstruments,
        institutionId = form.externalInstitutionId,
        instrumentId = form.externalInstrumentId,
        onInstitutionSelected = { form.externalInstitutionId = it },
        onInstrumentSelected = {
            form.externalInstrumentId = it
            if (form.externalFx.isBlank() || form.externalFx == "1") {
                form.externalFx = defaultFx(quote, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    OutlinedTextField(
        value = form.externalAmount,
        onValueChange = { form.externalAmount = it },
        label = { Text("金额（原币）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.externalFx,
        onValueChange = { form.externalFx = it },
        label = { Text("汇率→CNY") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExpenseSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    cashInstruments: List<Instrument>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("非现金损耗（无现金账户）", style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = form.expenseNoPosition,
            onCheckedChange = {
                form.expenseNoPosition = it
                if (it) {
                    form.expenseInstitutionId = null
                    form.expenseInstrumentId = null
                }
            },
        )
    }
    if (!form.expenseNoPosition) {
        PositionPicker(
            label = "支付现金账户",
            institutions = data.institutions,
            instruments = cashInstruments,
            institutionId = form.expenseInstitutionId,
            instrumentId = form.expenseInstrumentId,
            onInstitutionSelected = { form.expenseInstitutionId = it },
            onInstrumentSelected = {
                form.expenseInstrumentId = it
                if (form.expenseFx.isBlank() || form.expenseFx == "1") {
                    form.expenseFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
                }
            },
        )
    }
    OutlinedTextField(
        value = form.expenseAmount,
        onValueChange = { form.expenseAmount = it },
        label = { Text("金额（可为负；非现金损耗按 CNY）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!form.expenseNoPosition) {
        OutlinedTextField(
            value = form.expenseFx,
            onValueChange = { form.expenseFx = it },
            label = { Text("汇率→CNY") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text("盈亏归属类别", style = MaterialTheme.typography.labelMedium)
    OptionChips(
        options = data.settings.categories.sortedBy { it.order }.map { it.id to it.name },
        selected = form.expenseCategoryId,
        onSelected = { form.expenseCategoryId = it },
    )
}

@Composable
private fun OpeningSection(data: PortfolioData, quotes: QuoteSnapshot, form: EventForm) {
    PositionPicker(
        label = "期初持仓",
        institutions = data.institutions,
        instruments = data.instruments,
        institutionId = form.openingInstitutionId,
        instrumentId = form.openingInstrumentId,
        onInstitutionSelected = { form.openingInstitutionId = it },
        onInstrumentSelected = {
            form.openingInstrumentId = it
            if (form.openingFx.isBlank() || form.openingFx == "1") {
                form.openingFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    val instrument = form.openingInstrumentId?.let { data.instrument(it) }
    if (instrument?.kind == InstrumentKind.CASH) {
        OutlinedTextField(
            value = form.openingAmount,
            onValueChange = { form.openingAmount = it },
            label = { Text("期初金额（原币）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = form.openingQuantity,
            onValueChange = { form.openingQuantity = it },
            label = { Text("期初份额") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = form.openingFx,
        onValueChange = { form.openingFx = it },
        label = { Text("汇率→CNY") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AdjustmentSection(data: PortfolioData, quotes: QuoteSnapshot, form: EventForm) {
    PositionPicker(
        label = "调整持仓",
        institutions = data.institutions,
        instruments = data.instruments,
        institutionId = form.adjustmentInstitutionId,
        instrumentId = form.adjustmentInstrumentId,
        onInstitutionSelected = { form.adjustmentInstitutionId = it },
        onInstrumentSelected = {
            form.adjustmentInstrumentId = it
            if (form.adjustmentFx.isBlank() || form.adjustmentFx == "1") {
                form.adjustmentFx = defaultFx(quotes, data.instrument(it)?.currency ?: "CNY")
            }
        },
    )
    val instrument = form.adjustmentInstrumentId?.let { data.instrument(it) }
    if (instrument?.kind == InstrumentKind.CASH) {
        OutlinedTextField(
            value = form.adjustmentAmount,
            onValueChange = { form.adjustmentAmount = it },
            label = { Text("金额增减（可为负）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = form.adjustmentQuantity,
            onValueChange = { form.adjustmentQuantity = it },
            label = { Text("份额增减（可为负）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = form.adjustmentFx,
        onValueChange = { form.adjustmentFx = it },
        label = { Text("汇率→CNY") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.adjustmentReason,
        onValueChange = { form.adjustmentReason = it },
        label = { Text("原因（必填）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildEvents(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    initial: Event?,
): Result<List<Event>> = runCatching {
    runCatching { parseEventTime(form.date) }.getOrElse { error("日期格式无效") }
    val now = OffsetDateTime.now().toString()
    val createdAt = initial?.createdAt?.takeIf { it.isNotBlank() } ?: now

    when (form.type) {
        "transfer" -> {
            val fromInstitutionId = form.fromInstitutionId ?: error("请选择转出机构")
            val toInstitutionId = form.toInstitutionId ?: error("请选择转入机构")
            val fromInstrument = requireInstrument(data, form.fromInstrumentId, "转出标的")
            val toInstrument = requireInstrument(data, form.toInstrumentId, "转入标的")
            if (fromInstitutionId == toInstitutionId && fromInstrument.id == toInstrument.id) {
                error("转出与转入不能是同一持仓")
            }
            val fromQuantity = positive(form.fromQuantity, "转出数量")
            val toQuantity = positive(form.toQuantity, "转入数量")
            val fromPrice = priceFor(fromInstrument, form.fromPrice)
            val toPrice = priceFor(toInstrument, form.toPrice)
            val fromFx = fxFor(form.fromFx, fromInstrument.currency, quotes)
            val toFx = fxFor(form.toFx, toInstrument.currency, quotes)
            val values = EventValues.transferValues(
                fromQuantity = fromQuantity,
                toQuantity = toQuantity,
                fromPrice = fromPrice,
                toPrice = toPrice,
                fromFxToCny = fromFx,
                toFxToCny = toFx,
            )
            val transferId = if (initial is TransferEvent) initial.id else UUID.randomUUID().toString()
            val transfer = TransferEvent(
                id = transferId,
                date = form.date,
                createdAt = createdAt,
                note = form.note,
                from = PositionRef(fromInstitutionId, fromInstrument.id),
                to = PositionRef(toInstitutionId, toInstrument.id),
                fromQuantity = fromQuantity,
                toQuantity = toQuantity,
                fromPrice = fromPrice,
                toPrice = toPrice,
                fromFxToCny = fromFx,
                toFxToCny = toFx,
                fromValueCny = values.fromValueCny,
                toValueCny = values.toValueCny,
                lossValueCny = values.lossValueCny,
            )
            val lossExpense = if (values.lossValueCny.signum() != 0) {
                ExpenseEvent(
                    id = UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = now,
                    note = "转账损耗",
                    position = null,
                    amount = values.lossValueCny,
                    currency = "CNY",
                    fxToCny = BigDecimal.ONE,
                    amountCny = values.lossValueCny,
                    categoryId = lossCategory(data, fromInstrument, toInstrument),
                    linkedTransferId = transferId,
                )
            } else {
                null
            }
            listOfNotNull(transfer, lossExpense)
        }

        "income" -> {
            val destinationInstitutionId = form.incomeInstitutionId
                ?: error("请选择入账机构")
            val destinationInstrument = requireInstrument(data, form.incomeInstrumentId, "入账账户")
            if (destinationInstrument.kind != InstrumentKind.CASH) {
                error("入账账户必须是现金标的")
            }
            val source = if (form.incomeHasSource) {
                val sourceInstitutionId = form.sourceInstitutionId ?: error("请选择来源机构")
                val sourceInstrument = requireInstrument(data, form.sourceInstrumentId, "来源标的")
                PositionRef(sourceInstitutionId, sourceInstrument.id)
            } else {
                null
            }
            if (form.incomeKind == "dividend" && source == null) {
                error("分红必须选择来源持仓")
            }
            val amount = positive(form.incomeAmount, "金额")
            val fx = fxFor(form.incomeFx, destinationInstrument.currency, quotes)
            val amountCny = amount.multiply(fx)
            val incomeId = if (initial is IncomeEvent) initial.id else UUID.randomUUID().toString()
            val externalId = if (form.immediateWithdrawal) UUID.randomUUID().toString() else null
            val income = IncomeEvent(
                id = incomeId,
                date = form.date,
                createdAt = createdAt,
                note = form.note,
                kind = if (form.incomeKind == "dividend") IncomeKind.DIVIDEND else IncomeKind.INTEREST,
                source = source,
                destination = PositionRef(destinationInstitutionId, destinationInstrument.id),
                amount = amount,
                currency = destinationInstrument.currency,
                fxToCny = fx,
                amountCny = amountCny,
                immediateExternalWithdrawal = form.immediateWithdrawal,
                externalAccountLabel = form.externalLabel.takeIf { it.isNotBlank() },
                linkedEventId = externalId,
            )
            val external = externalId?.let {
                ExternalEvent(
                    id = it,
                    date = form.date,
                    createdAt = now,
                    note = "收益转出",
                    direction = ExternalDirection.OUT,
                    classification = ExternalClassification.CONSUMPTION,
                    position = PositionRef(destinationInstitutionId, destinationInstrument.id),
                    amount = amount,
                    currency = destinationInstrument.currency,
                    fxToCny = fx,
                    amountCny = amountCny,
                    counterpartyLabel = form.externalLabel.takeIf { it.isNotBlank() },
                )
            }
            listOfNotNull(income, external)
        }

        "external" -> {
            val institutionId = form.externalInstitutionId ?: error("请选择现金账户机构")
            val instrument = requireInstrument(data, form.externalInstrumentId, "现金账户")
            if (instrument.kind != InstrumentKind.CASH) error("外部出入金必须使用现金标的")
            val amount = positive(form.externalAmount, "金额")
            val fx = fxFor(form.externalFx, instrument.currency, quotes)
            val direction = if (form.externalDirection == "in") ExternalDirection.IN else ExternalDirection.OUT
            val classification = if (direction == ExternalDirection.IN) {
                ExternalClassification.CAPITAL
            } else if (form.externalClassification == "consumption") {
                ExternalClassification.CONSUMPTION
            } else {
                ExternalClassification.CAPITAL
            }
            listOf(
                ExternalEvent(
                    id = if (initial is ExternalEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = createdAt,
                    note = form.note,
                    direction = direction,
                    classification = classification,
                    position = PositionRef(institutionId, instrument.id),
                    amount = amount,
                    currency = instrument.currency,
                    fxToCny = fx,
                    amountCny = amount.multiply(fx),
                    counterpartyLabel = null,
                ),
            )
        }

        "expense" -> {
            val amount = nonZero(form.expenseAmount, "金额")
            val position = if (form.expenseNoPosition) {
                null
            } else {
                val institutionId = form.expenseInstitutionId ?: error("请选择现金账户机构")
                val instrument = requireInstrument(data, form.expenseInstrumentId, "现金账户")
                if (instrument.kind != InstrumentKind.CASH) error("现金支出必须使用现金标的")
                PositionRef(institutionId, instrument.id)
            }
            val currency = if (form.expenseNoPosition) {
                "CNY"
            } else {
                data.instrument(position!!.instrumentId)!!.currency
            }
            val fx = if (form.expenseNoPosition) BigDecimal.ONE else fxFor(form.expenseFx, currency, quotes)
            listOf(
                ExpenseEvent(
                    id = if (initial is ExpenseEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = createdAt,
                    note = form.note,
                    position = position,
                    amount = amount,
                    currency = currency,
                    fxToCny = fx,
                    amountCny = amount.multiply(fx),
                    categoryId = form.expenseCategoryId,
                    linkedTransferId = (initial as? ExpenseEvent)?.linkedTransferId,
                ),
            )
        }

        "opening" -> {
            val institutionId = form.openingInstitutionId ?: error("请选择机构")
            val instrument = requireInstrument(data, form.openingInstrumentId, "标的")
            val fx = fxFor(form.openingFx, instrument.currency, quotes)
            val quantity: BigDecimal?
            val amount: BigDecimal?
            val valueCny: BigDecimal?
            if (instrument.kind == InstrumentKind.CASH) {
                amount = nonZero(form.openingAmount, "期初金额")
                quantity = null
                valueCny = amount.multiply(fx)
            } else {
                quantity = nonZero(form.openingQuantity, "期初份额")
                amount = null
                val price = quotePrice(quotes, instrument)
                valueCny = price?.let { quantity.multiply(it).multiply(fx) }
            }
            listOf(
                OpeningEvent(
                    id = if (initial is OpeningEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = createdAt,
                    note = form.note,
                    position = PositionRef(institutionId, instrument.id),
                    quantity = quantity,
                    amount = amount,
                    price = if (instrument.kind == InstrumentKind.QUOTED) quotePrice(quotes, instrument) else null,
                    fxToCny = fx,
                    valueCny = valueCny,
                ),
            )
        }

        "adjustment" -> {
            val institutionId = form.adjustmentInstitutionId ?: error("请选择机构")
            val instrument = requireInstrument(data, form.adjustmentInstrumentId, "标的")
            if (form.adjustmentReason.isBlank()) error("请填写调整原因")
            val fx = fxFor(form.adjustmentFx, instrument.currency, quotes)
            val deltaQuantity: BigDecimal?
            val deltaAmount: BigDecimal?
            val valueCny: BigDecimal?
            if (instrument.kind == InstrumentKind.CASH) {
                deltaAmount = nonZero(form.adjustmentAmount, "金额增减")
                deltaQuantity = null
                valueCny = deltaAmount.multiply(fx)
            } else {
                deltaQuantity = nonZero(form.adjustmentQuantity, "份额增减")
                deltaAmount = null
                val price = quotePrice(quotes, instrument)
                valueCny = price?.let { deltaQuantity.multiply(it).multiply(fx) }
            }
            listOf(
                AdjustmentEvent(
                    id = if (initial is AdjustmentEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = createdAt,
                    note = form.note,
                    position = PositionRef(institutionId, instrument.id),
                    deltaQuantity = deltaQuantity,
                    deltaAmount = deltaAmount,
                    price = if (instrument.kind == InstrumentKind.QUOTED) quotePrice(quotes, instrument) else null,
                    fxToCny = fx,
                    valueCny = valueCny,
                    reason = form.adjustmentReason,
                ),
            )
        }

        else -> error("未知事件类型")
    }
}

private fun transferPreview(data: PortfolioData, form: EventForm): String? {
    val fromInstrument = form.fromInstrumentId?.let { data.instrument(it) } ?: return null
    val toInstrument = form.toInstrumentId?.let { data.instrument(it) } ?: return null
    val fromQuantity = form.fromQuantity.toBigDecimalOrNull() ?: return null
    val toQuantity = form.toQuantity.toBigDecimalOrNull() ?: return null
    val fromPrice = if (fromInstrument.kind == InstrumentKind.QUOTED) {
        form.fromPrice.toBigDecimalOrNull() ?: return null
    } else {
        null
    }
    val toPrice = if (toInstrument.kind == InstrumentKind.QUOTED) {
        form.toPrice.toBigDecimalOrNull() ?: return null
    } else {
        null
    }
    val fromFx = form.fromFx.toBigDecimalOrNull() ?: return null
    val toFx = form.toFx.toBigDecimalOrNull() ?: return null
    val values = EventValues.transferValues(
        fromQuantity, toQuantity, fromPrice, toPrice, fromFx, toFx,
    )
    return "转出价值 ¥${values.fromValueCny.setScale(2, java.math.RoundingMode.HALF_UP)}" +
        " · 转入价值 ¥${values.toValueCny.setScale(2, java.math.RoundingMode.HALF_UP)}" +
        " · 损耗 ¥${values.lossValueCny.setScale(2, java.math.RoundingMode.HALF_UP)}"
}

private fun requireInstrument(data: PortfolioData, id: String?, role: String): Instrument =
    data.instrument(id ?: error("请选择$role")) ?: error("$role 不存在")

private fun positive(text: String, label: String): BigDecimal =
    text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: error("请输入有效的$label")

private fun nonZero(text: String, label: String): BigDecimal =
    text.toBigDecimalOrNull()?.takeIf { it.signum() != 0 } ?: error("请输入有效的$label")

private fun priceFor(instrument: Instrument, text: String): BigDecimal? =
    if (instrument.kind == InstrumentKind.QUOTED) {
        positive(text, "${instrument.code} 单价")
    } else {
        null
    }

private fun fxFor(text: String, currency: String, quotes: QuoteSnapshot): BigDecimal {
    if (currency.uppercase() in setOf("CNY", "CNH")) return BigDecimal.ONE
    return text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        ?: quotes.fxRates[currency.uppercase()]
        ?: error("请输入 $currency → CNY 汇率")
}

private fun defaultFx(quotes: QuoteSnapshot, currency: String): String =
    if (currency.uppercase() in setOf("CNY", "CNH")) {
        "1"
    } else {
        quotes.fxRates[currency.uppercase()]?.toPlainString() ?: ""
    }

private fun quotePrice(quotes: QuoteSnapshot, instrument: Instrument?): BigDecimal? =
    instrument?.let { quotes.quotes[it.id]?.price ?: it.quote.manualPrice }

private fun lossCategory(
    data: PortfolioData,
    from: Instrument,
    to: Instrument,
): String = when {
    from.kind == InstrumentKind.CASH && to.kind != InstrumentKind.CASH -> to.categoryId
    from.kind != InstrumentKind.CASH && to.kind == InstrumentKind.CASH -> from.categoryId
    from.kind != InstrumentKind.CASH -> from.categoryId
    else -> CategoryIds.CASH
}

private val dateDisplayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun prettyDate(value: OffsetDateTime): String = value.format(dateDisplayFormat)

private fun prettyDate(iso: String): String =
    runCatching { prettyDate(parseEventTime(iso)) }.getOrElse { iso }

private fun Event?.typeKey(): String = when (this) {
    is TransferEvent -> "transfer"
    is IncomeEvent -> "income"
    is ExternalEvent -> "external"
    is ExpenseEvent -> "expense"
    is OpeningEvent -> "opening"
    is AdjustmentEvent -> "adjustment"
    null -> "transfer"
}
