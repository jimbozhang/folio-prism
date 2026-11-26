package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.EventValues
import com.jimbozhang.folioprism.domain.calc.TransferPricing
import com.jimbozhang.folioprism.domain.calc.TransferSuggestion
import com.jimbozhang.folioprism.domain.calc.Valuation
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
import com.jimbozhang.folioprism.domain.money.toMoneyAmount
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.LocalFieldVisibilityRequester
import com.jimbozhang.folioprism.ui.components.PositionPickerPanel
import com.jimbozhang.folioprism.ui.components.SingleDatePickerDialog
import com.jimbozhang.folioprism.ui.components.SuiTabRow
import com.jimbozhang.folioprism.ui.components.TimeOfDayPickerDialog
import com.jimbozhang.folioprism.ui.components.rememberWindowBottomOverflowPx
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
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
    "expense" to "损耗",
    "external" to "外部",
    "opening" to "期初",
    "adjustment" to "调整",
)

internal enum class PickerTarget(val title: String) {
    TRANSFER_FROM("选择转出持仓"),
    TRANSFER_TO("选择转入持仓"),
    FEE_CASH("选择手续费账户"),
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
    /** True when [fromPrice] is typed in CNY instead of the instrument currency. */
    var fromPriceInCny by mutableStateOf(false)

    var toInstitutionId by mutableStateOf<String?>(null)
    var toInstrumentId by mutableStateOf<String?>(null)
    var toQuantity by mutableStateOf("")
    var toPrice by mutableStateOf("")
    /** True when [toPrice] is typed in CNY instead of the instrument currency. */
    var toPriceInCny by mutableStateOf(false)
    /** Last value this form suggested for [toQuantity]; user input wins. */
    var lastSuggestedToQuantity by mutableStateOf<String?>(null)
    /** Last derived value written to [fromPrice]; user input wins. */
    var lastSuggestedFromPrice by mutableStateOf<String?>(null)
    /** Last derived value written to [toPrice]; user input wins. */
    var lastSuggestedToPrice by mutableStateOf<String?>(null)
    var feeLoss by mutableStateOf("")
    var feeFromCash by mutableStateOf(false)
    var feeInstitutionId by mutableStateOf<String?>(null)
    var feeInstrumentId by mutableStateOf<String?>(null)

    var incomeKind by mutableStateOf("dividend")
    var incomeHasSource by mutableStateOf(true)
    var sourceInstitutionId by mutableStateOf<String?>(null)
    var sourceInstrumentId by mutableStateOf<String?>(null)
    var incomeInstitutionId by mutableStateOf<String?>(null)
    var incomeInstrumentId by mutableStateOf<String?>(null)
    var incomeAmount by mutableStateOf("")
    var immediateWithdrawal by mutableStateOf(false)
    var externalLabel by mutableStateOf("")

    var externalDirection by mutableStateOf("out")
    var externalClassification by mutableStateOf("capital")
    var externalInstitutionId by mutableStateOf<String?>(null)
    var externalInstrumentId by mutableStateOf<String?>(null)
    var externalAmount by mutableStateOf("")

    var expenseNoPosition by mutableStateOf(false)
    var expenseInstitutionId by mutableStateOf<String?>(null)
    var expenseInstrumentId by mutableStateOf<String?>(null)
    var expenseAmount by mutableStateOf("")
    var expenseCategoryId by mutableStateOf(
        data.settings.categories.firstOrNull()?.id ?: CategoryIds.CASH,
    )

    var openingInstitutionId by mutableStateOf<String?>(null)
    var openingInstrumentId by mutableStateOf<String?>(null)
    var openingValue by mutableStateOf("")

    var adjustmentInstitutionId by mutableStateOf<String?>(null)
    var adjustmentInstrumentId by mutableStateOf<String?>(null)
    var adjustmentValue by mutableStateOf("")
    var adjustmentReason by mutableStateOf("")

    init {
        when (initial) {
            is TransferEvent -> {
                fromInstitutionId = initial.from.institutionId
                fromInstrumentId = initial.from.instrumentId
                fromQuantity = initial.fromQuantity.toPlainString()
                fromPrice = initial.fromPrice?.toPlainString() ?: ""
                toInstitutionId = initial.to.institutionId
                toInstrumentId = initial.to.instrumentId
                toQuantity = initial.toQuantity.toPlainString()
                toPrice = initial.toPrice?.toPlainString() ?: ""
                feeLoss = initial.lossAmount.toPlainString()
                val feePosition = initial.feePosition
                if (feePosition != null) {
                    feeFromCash = true
                    feeInstitutionId = feePosition.institutionId
                    feeInstrumentId = feePosition.instrumentId
                }
            }

            is IncomeEvent -> {
                incomeKind = if (initial.kind == IncomeKind.DIVIDEND) "dividend" else "interest"
                incomeHasSource = initial.source != null
                sourceInstitutionId = initial.source?.institutionId
                sourceInstrumentId = initial.source?.instrumentId
                incomeInstitutionId = initial.destination.institutionId
                incomeInstrumentId = initial.destination.instrumentId
                incomeAmount = initial.amount.toPlainString()
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
            }

            is ExpenseEvent -> {
                expenseNoPosition = initial.position == null
                expenseInstitutionId = initial.position?.institutionId
                expenseInstrumentId = initial.position?.instrumentId
                expenseAmount = initial.amount.toPlainString()
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
    onRequestQuote: (Instrument) -> Unit = {},
    onSave: (List<Event>) -> Unit,
) {
    val form = remember(initial?.id) { EventForm(initial, data) }
    // Quotes arrive (or update) asynchronously; keep the price fields in sync
    // with the latest quote while the user has not typed their own value.
    LaunchedEffect(quotes) {
        val from = form.fromInstrumentId?.let(data::instrument)
        if (from?.kind == InstrumentKind.QUOTED) {
            val applied = TransferSuggestion.applyPriceSuggestion(
                current = form.fromPrice,
                lastSuggested = form.lastSuggestedFromPrice,
                instrumentChanged = false,
                suggested = suggestedPriceText(quotes, from, form.fromPriceInCny),
            )
            form.fromPrice = applied.value
            form.lastSuggestedFromPrice = applied.lastSuggested
        }
        val to = form.toInstrumentId?.let(data::instrument)
        if (to?.kind == InstrumentKind.QUOTED) {
            val applied = TransferSuggestion.applyPriceSuggestion(
                current = form.toPrice,
                lastSuggested = form.lastSuggestedToPrice,
                instrumentChanged = false,
                suggested = suggestedPriceText(quotes, to, form.toPriceInCny),
            )
            form.toPrice = applied.value
            form.lastSuggestedToPrice = applied.lastSuggested
        }
    }
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

    // Holdings excluding the event being edited, so an edit never counts its
    // own out-leg as available again.
    val editablePositions = remember(data.events, initial?.id) {
        val excluded = initial?.let { linkedIdsToRemove(data, it) } ?: emptySet()
        EventReplay.replay(data.events.filterNot { it.id in excluded })
    }
    val positionValues = remember(editablePositions, quotes) {
        editablePositions.mapValues { (key, quantity) ->
            Valuation.valuePosition(
                data,
                key,
                quantity,
                LivePriceProvider(quotes),
                LocalDate.now(),
            )
        }
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
        // This view lives in the dialog window (LocalView outside the Dialog
        // belongs to the main window and cannot reach it). Do not let the
        // window manager pan the window around the keyboard: a pan pushes the
        // top bar off screen, and the content below already scrolls the
        // focused field into view itself.
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
                ?: return@LaunchedEffect
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        // The window may extend past the visible area (see
        // rememberWindowBottomOverflowPx), and the visible frame no longer
        // excludes the keyboard once the window manager is told not to pan or
        // resize around it, so the bottom padding is the measured window
        // overflow plus the IME inset; safeDrawing only contributes its top
        // and horizontal parts.
        val windowOverflowPx = rememberWindowBottomOverflowPx()
        val density = LocalDensity.current
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        val bottomOverflowPx = windowOverflowPx + imeBottomPx
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(bottom = with(density) { bottomOverflowPx.toDp() })
                    .background(Color.White),
            ) {
                val formScroll = rememberScrollState()
                // Bottom edge of the scrollable form viewport in window
                // coordinates; a focused field asks to be scrolled above it
                // when the keyboard covers part of the window.
                var viewportBottomPx by remember { mutableStateOf(0f) }
                val scope = rememberCoroutineScope()
                val fieldMarginPx = with(density) { 16.dp.toPx() }
                val ensureFieldVisible = remember(scope, fieldMarginPx) {
                    { coordinates: LayoutCoordinates ->
                        val fieldBottom =
                            coordinates.positionInWindow().y + coordinates.size.height
                        val overflow = fieldBottom + fieldMarginPx - viewportBottomPx
                        if (overflow > 0f) {
                            scope.launch {
                                val target = (formScroll.value + overflow)
                                    .coerceAtMost(formScroll.maxValue.toFloat())
                                formScroll.scrollTo(target.toInt())
                            }
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalFieldVisibilityRequester provides ensureFieldVisible,
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
                            if (!error.isNullOrBlank()) {
                                Text(
                                    text = error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FolioCoral,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                )
                            }
                            HorizontalDivider(color = FolioDivider)
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .onGloballyPositioned {
                                viewportBottomPx =
                                    it.positionInWindow().y + it.size.height
                            }
                            .verticalScroll(formScroll),
                    ) {
                        when (form.type) {
                            "transfer" -> TransferSection(
                                data = data,
                                quotes = quotes,
                                form = form,
                                positions = editablePositions,
                            ) { picker = it }
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
                }

                val target = picker
                if (target != null) {
                    val config = pickerConfig(
                        target = target,
                        data = data,
                        quotes = quotes,
                        form = form,
                        cashInstruments = cashInstruments,
                        initial = initial,
                        onRequestQuote = onRequestQuote,
                    )
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
                        quantities = editablePositions,
                        values = positionValues,
                        heldOnlyFilter = config.heldFilter,
                        eligibleInstrumentIds = config.eligibleInstrumentIds,
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

private class PickerConfig(
    val title: String,
    val instruments: List<Instrument>,
    val institutionId: String?,
    val instrumentId: String?,
    val heldFilter: Boolean = false,
    val eligibleInstrumentIds: Set<String>? = null,
    val onSelect: (String, String) -> Unit,
)

private fun pickerConfig(
    target: PickerTarget,
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    cashInstruments: List<Instrument>,
    initial: Event?,
    onRequestQuote: (Instrument) -> Unit,
): PickerConfig {
    return when (target) {
        PickerTarget.TRANSFER_FROM -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.fromInstitutionId,
            instrumentId = form.fromInstrumentId,
            heldFilter = true,
        ) { institutionId, instrumentId ->
            val instrument = data.instrument(instrumentId)
            val instrumentChanged = instrumentId != form.fromInstrumentId
            form.fromInstitutionId = institutionId
            form.fromInstrumentId = instrumentId
            if (instrumentChanged) form.fromPriceInCny = false
            when {
                instrument?.kind != InstrumentKind.QUOTED -> {
                    form.fromPrice = ""
                    form.fromPriceInCny = false
                    form.lastSuggestedFromPrice = null
                }

                else -> {
                    val suggested = suggestedPriceText(quotes, instrument, form.fromPriceInCny)
                    // A different instrument always takes its own quote; for
                    // the same instrument, the derived price only replaces a
                    // blank field or one that still holds our previous
                    // suggestion, so a manually typed price survives (e.g.
                    // when only the institution changes).
                    val applied = TransferSuggestion.applyPriceSuggestion(
                        current = form.fromPrice,
                        lastSuggested = form.lastSuggestedFromPrice,
                        instrumentChanged = instrumentChanged,
                        suggested = suggested,
                    )
                    form.fromPrice = applied.value
                    form.lastSuggestedFromPrice = applied.lastSuggested
                    if (suggested == null) {
                        // Just added and never quoted: fetch its price on demand.
                        onRequestQuote(instrument)
                    }
                }
            }
        }

        PickerTarget.TRANSFER_TO -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.toInstitutionId,
            instrumentId = form.toInstrumentId,
        ) { institutionId, instrumentId ->
            val instrument = data.instrument(instrumentId)
            val instrumentChanged = instrumentId != form.toInstrumentId
            form.toInstitutionId = institutionId
            form.toInstrumentId = instrumentId
            if (instrumentChanged) form.toPriceInCny = false
            when {
                instrument?.kind != InstrumentKind.QUOTED -> {
                    form.toPrice = ""
                    form.toPriceInCny = false
                    form.lastSuggestedToPrice = null
                }

                else -> {
                    val suggested = suggestedPriceText(quotes, instrument, form.toPriceInCny)
                    // See TRANSFER_FROM: only a different instrument (or a
                    // blank field, or our own previous suggestion) takes the
                    // derived price; user input wins.
                    val applied = TransferSuggestion.applyPriceSuggestion(
                        current = form.toPrice,
                        lastSuggested = form.lastSuggestedToPrice,
                        instrumentChanged = instrumentChanged,
                        suggested = suggested,
                    )
                    form.toPrice = applied.value
                    form.lastSuggestedToPrice = applied.lastSuggested
                    if (suggested == null) {
                        onRequestQuote(instrument)
                    }
                }
            }
        }

        PickerTarget.FEE_CASH -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.feeInstitutionId,
            instrumentId = form.feeInstrumentId,
        ) { institutionId, instrumentId ->
            form.feeInstitutionId = institutionId
            form.feeInstrumentId = instrumentId
        }

        PickerTarget.INCOME_SOURCE -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.sourceInstitutionId,
            instrumentId = form.sourceInstrumentId,
            eligibleInstrumentIds = if (initial == null && form.incomeKind == "dividend") {
                dividendSourceInstrumentIds(data)
            } else {
                null
            },
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
        }

        PickerTarget.EXTERNAL_CASH -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.externalInstitutionId,
            instrumentId = form.externalInstrumentId,
        ) { institutionId, instrumentId ->
            form.externalInstitutionId = institutionId
            form.externalInstrumentId = instrumentId
        }

        PickerTarget.EXPENSE_CASH -> PickerConfig(
            title = target.title,
            instruments = cashInstruments,
            institutionId = form.expenseInstitutionId,
            instrumentId = form.expenseInstrumentId,
        ) { institutionId, instrumentId ->
            form.expenseInstitutionId = institutionId
            form.expenseInstrumentId = instrumentId
        }

        PickerTarget.OPENING_POSITION -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.openingInstitutionId,
            instrumentId = form.openingInstrumentId,
        ) { institutionId, instrumentId ->
            form.openingInstitutionId = institutionId
            form.openingInstrumentId = instrumentId
        }

        PickerTarget.ADJUSTMENT_POSITION -> PickerConfig(
            title = target.title,
            instruments = data.instruments,
            institutionId = form.adjustmentInstitutionId,
            instrumentId = form.adjustmentInstrumentId,
        ) { institutionId, instrumentId ->
            form.adjustmentInstitutionId = institutionId
            form.adjustmentInstrumentId = instrumentId
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
            val fromEnteredPrice = if (fromInstrument.kind == InstrumentKind.QUOTED) {
                positive(form.fromPrice, "${fromInstrument.code} 单价")
            } else {
                null
            }
            val toEnteredPrice = if (toInstrument.kind == InstrumentKind.QUOTED) {
                positive(form.toPrice, "${toInstrument.code} 单价")
            } else {
                null
            }
            val fromPrice = fromEnteredPrice?.let {
                TransferPricing.nativePrice(it, form.fromPriceInCny, fromInstrument, quotes.fxRates)
                    ?: error("缺少 ${fromInstrument.currency} 当前汇率，请刷新行情")
            }
            val toPrice = toEnteredPrice?.let {
                TransferPricing.nativePrice(it, form.toPriceInCny, toInstrument, quotes.fxRates)
                    ?: error("缺少 ${toInstrument.currency} 当前汇率，请刷新行情")
            }
            val values = EventValues.transferValues(
                fromQuantity = fromQuantity,
                toQuantity = toQuantity,
                fromPrice = fromPrice,
                toPrice = toPrice,
            )
            val lossCurrency = fromInstrument.currency
            val sameCurrency =
                fromInstrument.currency.equals(toInstrument.currency, ignoreCase = true) ||
                    (fromInstrument.currency.uppercase() in setOf("CNY", "CNH") &&
                        toInstrument.currency.uppercase() in setOf("CNY", "CNH"))
            val autoLoss = if (sameCurrency) {
                values.fromNative.subtract(values.toNative).toMoneyAmount()
            } else {
                val fromCny = TransferPricing.exactCnyValue(
                    instrument = fromInstrument,
                    quantity = fromQuantity,
                    nativePrice = fromPrice,
                    enteredPrice = fromEnteredPrice,
                    inCny = form.fromPriceInCny,
                )
                val toCny = TransferPricing.exactCnyValue(
                    instrument = toInstrument,
                    quantity = toQuantity,
                    nativePrice = toPrice,
                    enteredPrice = toEnteredPrice,
                    inCny = form.toPriceInCny,
                )
                if (fromCny != null && toCny != null) {
                    TransferPricing.lossInCurrency(
                        lossCny = fromCny.subtract(toCny),
                        lossCurrency = lossCurrency,
                        fxRates = quotes.fxRates,
                    ) ?: BigDecimal.ZERO
                } else {
                    BigDecimal.ZERO
                }
            }
            val totalLoss = if (form.feeLoss.isBlank()) {
                autoLoss
            } else {
                form.feeLoss.toBigDecimalOrNull() ?: error("请输入有效的 手续费/损耗")
            }
            val feeCashInstrument = if (form.feeFromCash) {
                val feeInstrumentId = form.feeInstrumentId ?: error("请选择手续费账户")
                requireInstrument(data, feeInstrumentId, "手续费账户")
            } else {
                null
            }
            if (feeCashInstrument != null && feeCashInstrument.kind != InstrumentKind.CASH) {
                error("手续费账户必须是现金标的")
            }
            val feeCashInstitutionId = if (feeCashInstrument != null) {
                form.feeInstitutionId ?: error("请选择手续费账户机构")
            } else {
                null
            }
            // The part not already embedded in the from/to values is paid from
            // the separate cash account. Convert at the current FX rate.
            val extraLoss = feeCashInstrument?.let { totalLoss.subtract(autoLoss) }
            val feeAmount = extraLoss?.let {
                val lossFx = currentFx(lossCurrency, quotes)
                val feeFx = currentFx(feeCashInstrument!!.currency, quotes)
                it.multiply(lossFx).divide(feeFx, 8, RoundingMode.HALF_UP).toMoneyAmount()
            }
            val feePosition =
                if (feeCashInstrument != null && feeCashInstitutionId != null &&
                    feeAmount != null && feeAmount.signum() != 0
                ) {
                    PositionRef(feeCashInstitutionId, feeCashInstrument.id)
                } else {
                    null
                }
            val transfer = TransferEvent(
                id = if (initial is TransferEvent) initial.id else UUID.randomUUID().toString(),
                date = form.date,
                note = form.note,
                from = PositionRef(fromInstitutionId, fromInstrument.id),
                to = PositionRef(toInstitutionId, toInstrument.id),
                fromQuantity = fromQuantity,
                toQuantity = toQuantity,
                fromPrice = fromPrice,
                toPrice = toPrice,
                lossAmount = if (feeCashInstrument != null) autoLoss else totalLoss,
                lossCurrency = lossCurrency,
                feePosition = feePosition,
                feeAmount = feeAmount?.takeIf { it.signum() != 0 },
                feeCurrency = feeCashInstrument?.currency,
            )
            listOf(transfer)
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
            val incomeId = if (initial is IncomeEvent) initial.id else UUID.randomUUID().toString()
            val externalId = if (form.immediateWithdrawal) UUID.randomUUID().toString() else null
            val income = IncomeEvent(
                id = incomeId,
                date = form.date,
                note = form.note,
                kind = if (form.incomeKind == "dividend") IncomeKind.DIVIDEND else IncomeKind.INTEREST,
                source = source,
                destination = PositionRef(destinationInstitutionId, destinationInstrument.id),
                amount = amount,
                currency = destinationInstrument.currency,
                immediateExternalWithdrawal = form.immediateWithdrawal,
                externalAccountLabel = form.externalLabel.takeIf { it.isNotBlank() },
                linkedEventId = externalId,
            )
            val external = externalId?.let {
                ExternalEvent(
                    id = it,
                    date = form.date,
                    note = "收益转出",
                    direction = ExternalDirection.OUT,
                    classification = ExternalClassification.CONSUMPTION,
                    position = PositionRef(destinationInstitutionId, destinationInstrument.id),
                    amount = amount,
                    currency = destinationInstrument.currency,
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
                    note = form.note,
                    direction = direction,
                    classification = classification,
                    position = PositionRef(institutionId, instrument.id),
                    amount = amount,
                    currency = instrument.currency,
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
                if (instrument.kind != InstrumentKind.CASH) error("现金损耗必须使用现金标的")
                PositionRef(institutionId, instrument.id)
            }
            val currency = if (form.expenseNoPosition) {
                "CNY"
            } else {
                data.instrument(position!!.instrumentId)!!.currency
            }
            listOf(
                ExpenseEvent(
                    id = if (initial is ExpenseEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    note = form.note,
                    position = position,
                    amount = amount,
                    currency = currency,
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
            val cash = instrument.kind == InstrumentKind.CASH
            val value = nonZero(form.openingValue, if (cash) "期初金额" else "期初份额")
            val quantity = if (cash) null else value
            val amount = if (cash) value else null
            listOf(
                OpeningEvent(
                    id = if (initial is OpeningEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    note = form.note,
                    position = PositionRef(institutionId, instrument.id),
                    quantity = quantity,
                    amount = amount,
                    price = if (cash) null else quotePrice(quotes, instrument),
                ),
            )
        }

        "adjustment" -> {
            val institutionId = form.adjustmentInstitutionId ?: error("请选择机构")
            val instrument = requireInstrument(data, form.adjustmentInstrumentId, "标的")
            if (form.adjustmentReason.isBlank()) error("请填写调整原因")
            val cash = instrument.kind == InstrumentKind.CASH
            val value = nonZero(form.adjustmentValue, if (cash) "金额增减" else "份额增减")
            val deltaQuantity = if (cash) null else value
            val deltaAmount = if (cash) value else null
            listOf(
                AdjustmentEvent(
                    id = if (initial is AdjustmentEvent) initial.id else UUID.randomUUID().toString(),
                    date = form.date,
                    note = form.note,
                    position = PositionRef(institutionId, instrument.id),
                    deltaQuantity = deltaQuantity,
                    deltaAmount = deltaAmount,
                    price = if (cash) null else quotePrice(quotes, instrument),
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

/**
 * A live quote rendered in the price field's entry currency: native by
 * default, or CNY when the user switched that leg's price to CNY.
 */
private fun suggestedPriceText(
    quotes: QuoteSnapshot,
    instrument: Instrument,
    inCny: Boolean,
): String? {
    val native = TransferSuggestion.suggestPrice(quotes, instrument)?.toBigDecimalOrNull()
        ?: return null
    return TransferPricing.enteredPrice(native, inCny, instrument, quotes.fxRates)
        ?.toPlainString()
}

internal fun currentFx(currency: String, quotes: QuoteSnapshot): BigDecimal =
    when (currency.uppercase()) {
        "CNY", "CNH" -> BigDecimal.ONE
        else -> quotes.fxRates[currency.uppercase()]
            ?: error("缺少 $currency 当前汇率，请刷新行情")
    }

private fun quotePrice(quotes: QuoteSnapshot, instrument: Instrument?): BigDecimal? =
    instrument?.let { quotes.quotes[it.id]?.price ?: it.quote.manualPrice }

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

internal fun heroQuantityLabel(instrument: Instrument?): String = when {
    instrument == null -> ""
    instrument.kind == InstrumentKind.CASH -> instrument.currency
    else -> instrument.unit
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

/**
 * Dividend sources are limited to dividend-category assets and USD-denominated
 * US equity/bond assets, matching the assets that pay distributions.
 */
internal fun dividendSourceInstrumentIds(data: PortfolioData): Set<String> {
    val usEquityBond = setOf(CategoryIds.US_EQUITY, CategoryIds.US_BOND)
    return data.instruments
        .filter { instrument ->
            val category = data.settings.categories.firstOrNull { it.id == instrument.categoryId }
            category?.specialRule == "dividend" ||
                (instrument.categoryId in usEquityBond &&
                    instrument.currency.equals("USD", ignoreCase = true))
        }
        .map { it.id }
        .toSet()
}

internal fun swapTransferSides(form: EventForm) {
    val fromInst = form.fromInstrumentId
    val fromInstId = form.fromInstitutionId
    val fromQty = form.fromQuantity
    val fromPx = form.fromPrice
    val fromInCny = form.fromPriceInCny
    val fromLastPx = form.lastSuggestedFromPrice

    form.fromInstrumentId = form.toInstrumentId
    form.fromInstitutionId = form.toInstitutionId
    form.fromQuantity = form.toQuantity
    form.fromPrice = form.toPrice
    form.fromPriceInCny = form.toPriceInCny
    form.lastSuggestedFromPrice = form.lastSuggestedToPrice

    form.toInstrumentId = fromInst
    form.toInstitutionId = fromInstId
    form.toQuantity = fromQty
    form.toPrice = fromPx
    form.toPriceInCny = fromInCny
    form.lastSuggestedToPrice = fromLastPx
    form.lastSuggestedToQuantity = null
}
