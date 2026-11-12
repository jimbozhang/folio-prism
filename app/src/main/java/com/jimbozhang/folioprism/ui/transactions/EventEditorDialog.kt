package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jimbozhang.folioprism.domain.calc.EventValues
import com.jimbozhang.folioprism.domain.calc.localDate
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
import com.jimbozhang.folioprism.ui.components.PositionPickerPanel
import com.jimbozhang.folioprism.ui.components.SingleDatePickerDialog
import com.jimbozhang.folioprism.ui.components.SuiTabRow
import com.jimbozhang.folioprism.ui.components.TimeOfDayPickerDialog
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val TRANSFER_LOSS_NOTE = "转账损耗"
private const val TRANSFER_FEE_NOTE = "转账手续费"

private val eventTypeOptions = listOf(
    "transfer" to "转账",
    "income" to "收益",
    "expense" to "支出",
    "external" to "外部",
    "opening" to "期初",
    "adjustment" to "调整",
)

internal enum class PickerTarget(val title: String) {
    TRANSFER_FROM("选择转出持仓"),
    TRANSFER_TO("选择转入持仓"),
    INCOME_SOURCE("选择来源持仓"),
    INCOME_DESTINATION("选择入账账户"),
    EXTERNAL_CASH("选择现金账户"),
    EXPENSE_CASH("选择支付账户"),
    OPENING_POSITION("选择期初持仓"),
    ADJUSTMENT_POSITION("选择调整持仓"),
}

internal class EventForm(
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
    var transferFee by mutableStateOf("0")

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
    var openingValue by mutableStateOf("")
    var openingFx by mutableStateOf("1")

    var adjustmentInstitutionId by mutableStateOf<String?>(null)
    var adjustmentInstrumentId by mutableStateOf<String?>(null)
    var adjustmentValue by mutableStateOf("")
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
                transferFee = data.events
                    .filterIsInstance<ExpenseEvent>()
                    .firstOrNull {
                        it.linkedTransferId == initial.id && it.note == TRANSFER_FEE_NOTE
                    }
                    ?.amount?.toPlainString() ?: "0"
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
                val cash = data.instrument(initial.position.instrumentId)?.kind == InstrumentKind.CASH
                openingValue = if (cash) {
                    initial.amount?.toPlainString() ?: ""
                } else {
                    initial.quantity?.toPlainString() ?: ""
                }
                openingFx = initial.fxToCny?.toPlainString() ?: "1"
            }

            is AdjustmentEvent -> {
                adjustmentInstitutionId = initial.position.institutionId
                adjustmentInstrumentId = initial.position.instrumentId
                val cash = data.instrument(initial.position.instrumentId)?.kind == InstrumentKind.CASH
                adjustmentValue = if (cash) {
                    initial.deltaAmount?.toPlainString() ?: ""
                } else {
                    initial.deltaQuantity?.toPlainString() ?: ""
                }
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
    onDelete: (() -> Unit)? = null,
    onSave: (List<Event>) -> Unit,
) {
    val form = remember(initial?.id) { EventForm(initial, data) }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val cashInstruments = remember(data.instruments) {
        data.instruments.filter { it.kind == InstrumentKind.CASH }
    }
    val categoryNames = remember(data.settings.categories) {
        data.settings.categories.associate { it.id to it.name }
    }

    val save: () -> Unit = {
        buildEvents(data, quotes, form, initial)
            .onSuccess(onSave)
            .onFailure { error = it.message ?: "保存失败" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .background(Color.White),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.White,
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        Column {
                            EditorTopBar(
                                editing = initial != null,
                                onDismiss = onDismiss,
                                onDelete = onDelete?.let { { showDeleteConfirm = true } },
                                onSave = save,
                            )
                            SuiTabRow(
                                tabs = eventTypeOptions.take(5),
                                overflow = eventTypeOptions.drop(5),
                                selected = form.type,
                                enabled = initial == null,
                                onSelected = {
                                    form.type = it
                                    error = null
                                },
                            )
                            HorizontalDivider(color = FolioDivider)
                        }
                    },
                    bottomBar = {
                        EditorSaveBar(error = error, onSave = save)
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (form.type) {
                            "transfer" -> TransferSection(data, quotes, form) { picker = it }
                            "income" -> IncomeSection(data, quotes, form) { picker = it }
                            "external" -> ExternalSection(data, quotes, form) { picker = it }
                            "expense" -> ExpenseSection(data, quotes, form) { picker = it }
                            "opening" -> OpeningSection(data, quotes, form) { picker = it }
                            "adjustment" -> AdjustmentSection(data, quotes, form) { picker = it }
                        }
                        CommonDetails(
                            form = form,
                            onPickDate = { showDatePicker = true },
                            onPickTime = { showTimePicker = true },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                val target = picker
                if (target != null) {
                    val config = pickerConfig(target, data, quotes, form, cashInstruments)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { picker = null },
                    )
                    PositionPickerPanel(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        title = config.title,
                        institutions = data.institutions,
                        instruments = config.instruments,
                        categoryNames = categoryNames,
                        selectedInstitutionId = config.institutionId,
                        selectedInstrumentId = config.instrumentId,
                        onSelect = config.onSelect,
                        onDismiss = { picker = null },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val current = formDateTime(form)
        SingleDatePickerDialog(
            initial = current.toLocalDate(),
            onDismiss = { showDatePicker = false },
            onSelected = { date -> setFormDateTime(form, LocalDateTime.of(date, current.toLocalTime())) },
        )
    }
    if (showTimePicker) {
        val current = formDateTime(form)
        TimeOfDayPickerDialog(
            initialHour = current.hour,
            initialMinute = current.minute,
            onDismiss = { showTimePicker = false },
            onSelected = { time -> setFormDateTime(form, LocalDateTime.of(current.toLocalDate(), time)) },
        )
    }
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔事件？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = FolioCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    editing: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
            Spacer(modifier = Modifier.weight(1f))
            onDelete?.let { delete ->
                IconButton(onClick = delete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = FolioMuted,
                    )
                }
            }
            TextButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = FolioGold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text("保存", color = FolioGold)
            }
        }
        Text(
            text = if (editing) "编辑事件" else "记一笔",
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = FolioInk,
        )
    }
}

@Composable
private fun EditorSaveBar(error: String?, onSave: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 50.dp),
        ) {
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioCoral,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FolioGold,
                    contentColor = Color(0xFF2B1D0E),
                ),
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private class PickerConfig(
    val title: String,
    val instruments: List<Instrument>,
    val institutionId: String?,
    val instrumentId: String?,
    val onSelect: (String, String) -> Unit,
)

private fun pickerConfig(
    target: PickerTarget,
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    cashInstruments: List<Instrument>,
): PickerConfig {
    fun prefillFx(instrumentId: String, setter: (String) -> Unit) {
        val instrument = data.instrument(instrumentId) ?: return
        setter(defaultFx(quotes, instrument.currency))
    }

    return when (target) {
        PickerTarget.TRANSFER_FROM -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.fromInstitutionId,
            instrumentId = form.fromInstrumentId,
        ) { institutionId, instrumentId ->
            form.fromInstitutionId = institutionId
            form.fromInstrumentId = instrumentId
            val instrument = data.instrument(instrumentId)
            if (form.fromPrice.isBlank() && instrument?.kind == InstrumentKind.QUOTED) {
                form.fromPrice = quotePrice(quotes, instrument)?.toPlainString() ?: ""
            }
            if (instrument != null && (form.fromFx.isBlank() || form.fromFx == "1")) {
                form.fromFx = defaultFx(quotes, instrument.currency)
            }
        }

        PickerTarget.TRANSFER_TO -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.toInstitutionId,
            instrumentId = form.toInstrumentId,
        ) { institutionId, instrumentId ->
            form.toInstitutionId = institutionId
            form.toInstrumentId = instrumentId
            val instrument = data.instrument(instrumentId)
            if (form.toPrice.isBlank() && instrument?.kind == InstrumentKind.QUOTED) {
                form.toPrice = quotePrice(quotes, instrument)?.toPlainString() ?: ""
            }
            if (instrument != null && (form.toFx.isBlank() || form.toFx == "1")) {
                form.toFx = defaultFx(quotes, instrument.currency)
            }
        }

        PickerTarget.INCOME_SOURCE -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.sourceInstitutionId,
            instrumentId = form.sourceInstrumentId,
        ) { institutionId, instrumentId ->
            form.sourceInstitutionId = institutionId
            form.sourceInstrumentId = instrumentId
        }

        PickerTarget.INCOME_DESTINATION -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.incomeInstitutionId,
            instrumentId = form.incomeInstrumentId,
        ) { institutionId, instrumentId ->
            form.incomeInstitutionId = institutionId
            form.incomeInstrumentId = instrumentId
            if (form.incomeFx.isBlank() || form.incomeFx == "1") {
                prefillFx(instrumentId) { form.incomeFx = it }
            }
        }

        PickerTarget.EXTERNAL_CASH -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.externalInstitutionId,
            instrumentId = form.externalInstrumentId,
        ) { institutionId, instrumentId ->
            form.externalInstitutionId = institutionId
            form.externalInstrumentId = instrumentId
            if (form.externalFx.isBlank() || form.externalFx == "1") {
                prefillFx(instrumentId) { form.externalFx = it }
            }
        }

        PickerTarget.EXPENSE_CASH -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.expenseInstitutionId,
            instrumentId = form.expenseInstrumentId,
        ) { institutionId, instrumentId ->
            form.expenseInstitutionId = institutionId
            form.expenseInstrumentId = instrumentId
            if (form.expenseFx.isBlank() || form.expenseFx == "1") {
                prefillFx(instrumentId) { form.expenseFx = it }
            }
        }

        PickerTarget.OPENING_POSITION -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.openingInstitutionId,
            instrumentId = form.openingInstrumentId,
        ) { institutionId, instrumentId ->
            form.openingInstitutionId = institutionId
            form.openingInstrumentId = instrumentId
            if (form.openingFx.isBlank() || form.openingFx == "1") {
                prefillFx(instrumentId) { form.openingFx = it }
            }
        }

        PickerTarget.ADJUSTMENT_POSITION -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.adjustmentInstitutionId,
            instrumentId = form.adjustmentInstrumentId,
        ) { institutionId, instrumentId ->
            form.adjustmentInstitutionId = institutionId
            form.adjustmentInstrumentId = instrumentId
            if (form.adjustmentFx.isBlank() || form.adjustmentFx == "1") {
                prefillFx(instrumentId) { form.adjustmentFx = it }
            }
        }
    }
}

internal fun buildEvents(
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
            val fee = nonNegative(form.transferFee, "手续费")
            val feeCurrency = transferFeeCurrency(fromInstrument, toInstrument)
            val feeFx = when {
                fromInstrument.kind == InstrumentKind.CASH -> fromFx
                toInstrument.kind == InstrumentKind.CASH -> toFx
                else -> BigDecimal.ONE
            }
            val feePosition = when {
                fromInstrument.kind == InstrumentKind.CASH ->
                    PositionRef(fromInstitutionId, fromInstrument.id)
                toInstrument.kind == InstrumentKind.CASH ->
                    PositionRef(toInstitutionId, toInstrument.id)
                else -> null
            }
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
                    note = TRANSFER_LOSS_NOTE,
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
            val feeExpense = if (fee.signum() > 0) {
                ExpenseEvent(
                    id = UUID.randomUUID().toString(),
                    date = form.date,
                    createdAt = now,
                    note = TRANSFER_FEE_NOTE,
                    position = feePosition,
                    amount = fee,
                    currency = feeCurrency,
                    fxToCny = feeFx,
                    amountCny = fee.multiply(feeFx),
                    categoryId = lossCategory(data, fromInstrument, toInstrument),
                    linkedTransferId = transferId,
                )
            } else {
                null
            }
            listOfNotNull(transfer, lossExpense, feeExpense)
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
            val existingOpening = data.events
                .filterIsInstance<OpeningEvent>()
                .firstOrNull {
                    it.position == PositionRef(institutionId, instrument.id) &&
                        it.id != (initial as? OpeningEvent)?.id
                }
            if (existingOpening != null) {
                error("该持仓已于 ${existingOpening.localDate()} 有一笔期初交易")
            }
            val fx = fxFor(form.openingFx, instrument.currency, quotes)
            val cash = instrument.kind == InstrumentKind.CASH
            val value = nonZero(form.openingValue, if (cash) "期初金额" else "期初份额")
            val quantity = if (cash) null else value
            val amount = if (cash) value else null
            val valueCny = if (cash) {
                value.multiply(fx)
            } else {
                quotePrice(quotes, instrument)?.let { value.multiply(it).multiply(fx) }
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
                    price = if (cash) null else quotePrice(quotes, instrument),
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
            val cash = instrument.kind == InstrumentKind.CASH
            val value = nonZero(form.adjustmentValue, if (cash) "金额增减" else "份额增减")
            val deltaQuantity = if (cash) null else value
            val deltaAmount = if (cash) value else null
            val valueCny = if (cash) {
                value.multiply(fx)
            } else {
                quotePrice(quotes, instrument)?.let { value.multiply(it).multiply(fx) }
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
                    price = if (cash) null else quotePrice(quotes, instrument),
                    fxToCny = fx,
                    valueCny = valueCny,
                    reason = form.adjustmentReason,
                ),
            )
        }

        else -> error("未知事件类型")
    }
}

private fun requireInstrument(data: PortfolioData, id: String?, role: String): Instrument =
    data.instrument(id ?: error("请选择$role")) ?: error("$role 不存在")

private fun positive(text: String, label: String): BigDecimal =
    text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: error("请输入有效的$label")

private fun nonZero(text: String, label: String): BigDecimal =
    text.toBigDecimalOrNull()?.takeIf { it.signum() != 0 } ?: error("请输入有效的$label")

private fun nonNegative(text: String, label: String): BigDecimal =
    if (text.isBlank()) {
        BigDecimal.ZERO
    } else {
        text.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
            ?: error("请输入有效的$label")
    }

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

private fun formDateTime(form: EventForm): LocalDateTime =
    runCatching { parseEventTime(form.date).toLocalDateTime() }
        .getOrElse { LocalDateTime.now() }

private fun setFormDateTime(form: EventForm, value: LocalDateTime) {
    form.date = value.format(dateDisplayFormat)
}

private fun Event?.typeKey(): String = when (this) {
    is TransferEvent -> "transfer"
    is IncomeEvent -> "income"
    is ExternalEvent -> "external"
    is ExpenseEvent -> "expense"
    is OpeningEvent -> "opening"
    is AdjustmentEvent -> "adjustment"
    null -> "transfer"
}

internal fun transferFeeCurrency(from: Instrument?, to: Instrument?): String = when {
    from?.kind == InstrumentKind.CASH -> from.currency
    to?.kind == InstrumentKind.CASH -> to.currency
    else -> "CNY"
}

internal fun heroQuantityLabel(instrument: Instrument?): String = when {
    instrument == null -> "金额"
    instrument.kind == InstrumentKind.CASH -> instrument.currency
    else -> instrument.unit
}

internal fun legValueCny(
    quantityText: String,
    priceText: String,
    fxText: String,
    instrument: Instrument?,
): BigDecimal? {
    if (instrument == null) return null
    val quantity = quantityText.toBigDecimalOrNull() ?: return null
    val fx = fxText.toBigDecimalOrNull() ?: return null
    return if (instrument.kind == InstrumentKind.CASH) {
        quantity.multiply(fx)
    } else {
        val price = priceText.toBigDecimalOrNull() ?: return null
        quantity.multiply(price).multiply(fx)
    }
}

internal fun cnyEstimate(
    quotes: QuoteSnapshot,
    instrument: Instrument?,
    quantityText: String,
    fxText: String,
): BigDecimal? {
    if (instrument == null) return null
    val quantity = quantityText.toBigDecimalOrNull() ?: return null
    val fx = fxText.toBigDecimalOrNull() ?: return null
    val price = if (instrument.kind == InstrumentKind.QUOTED) {
        quotePrice(quotes, instrument) ?: return null
    } else {
        BigDecimal.ONE
    }
    return quantity.multiply(price).multiply(fx)
}

internal fun dateTimeLabels(form: EventForm): Pair<String, String> {
    val value = formDateTime(form)
    val date = value.toLocalDate()
    val today = LocalDate.now()
    val dateLabel = when {
        date == today -> "今天 ${date.monthValue}月${date.dayOfMonth}日"
        date == today.minusDays(1) -> "昨天 ${date.monthValue}月${date.dayOfMonth}日"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    val timeLabel = value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return dateLabel to timeLabel
}

internal fun setDate(form: EventForm, date: LocalDate) {
    setFormDateTime(form, LocalDateTime.of(date, formDateTime(form).toLocalTime()))
}

internal fun setTime(form: EventForm, time: LocalTime) {
    setFormDateTime(form, LocalDateTime.of(formDateTime(form).toLocalDate(), time))
}

internal fun swapTransferSides(form: EventForm) {
    val fromInst = form.fromInstrumentId
    val fromInstId = form.fromInstitutionId
    val fromQty = form.fromQuantity
    val fromPx = form.fromPrice
    val fromRate = form.fromFx

    form.fromInstrumentId = form.toInstrumentId
    form.fromInstitutionId = form.toInstitutionId
    form.fromQuantity = form.toQuantity
    form.fromPrice = form.toPrice
    form.fromFx = form.toFx

    form.toInstrumentId = fromInst
    form.toInstitutionId = fromInstId
    form.toQuantity = fromQty
    form.toPrice = fromPx
    form.toFx = fromRate
}
