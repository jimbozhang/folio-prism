package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.components.AmountHero
import com.jimbozhang.folioprism.ui.components.FormDivider
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineDropdown
import com.jimbozhang.folioprism.ui.components.InlineNumberField
import com.jimbozhang.folioprism.ui.components.InlineTextField
import com.jimbozhang.folioprism.ui.components.PositionValue
import com.jimbozhang.folioprism.ui.components.ToggleRow
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.components.formatNative
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.math.BigDecimal

@Composable
internal fun TransferSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val fromInstrument = form.fromInstrumentId?.let { data.instrument(it) }
    val toInstrument = form.toInstrumentId?.let { data.instrument(it) }
    val fromValue = legValueCny(form.fromQuantity, form.fromPrice, form.fromFx, fromInstrument)
    val toValue = legValueCny(form.toQuantity, form.toPrice, form.toFx, toInstrument)

    AmountHero(
        label = "转出",
        unit = heroQuantityLabel(fromInstrument),
        value = form.fromQuantity,
        onValueChange = { form.fromQuantity = it },
        secondary = when {
            fromInstrument == null -> "未选择账户"
            fromValue != null -> "≈ ${formatCny(fromValue)}"
            else -> null
        },
    )
    AmountHero(
        label = "转入",
        unit = heroQuantityLabel(toInstrument),
        value = form.toQuantity,
        onValueChange = { form.toQuantity = it },
        secondary = when {
            toInstrument == null -> "未选择账户"
            toValue != null -> "≈ ${formatCny(toValue)}"
            else -> null
        },
    )
    if (fromValue != null && toValue != null) {
        val fee = form.transferFee.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val feeSuffix = if (fee.signum() > 0) {
            " · 手续费 " + formatNative(fee, transferFeeCurrency(fromInstrument, toInstrument))
        } else {
            ""
        }
        Text(
            text = "转出价值 ${formatCny(fromValue)} · 转入价值 ${formatCny(toValue)}" +
                " · 损耗 ${formatCny(fromValue.subtract(toValue))}" + feeSuffix,
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
    }
    FormDivider()
    TransferAccountsRow(data, form, onPick)
    FormDivider()
    PriceFxSection(form, fromInstrument, toInstrument)
}

@Composable
private fun TransferAccountsRow(
    data: PortfolioData,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            tint = FolioGold,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "账户",
            style = MaterialTheme.typography.bodyMedium,
            color = FolioMuted,
            modifier = Modifier.width(56.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        TransferAccountColumn(
            data = data,
            institutionId = form.fromInstitutionId,
            instrumentId = form.fromInstrumentId,
            label = "转出",
            modifier = Modifier.weight(1f),
            onClick = { onPick(PickerTarget.TRANSFER_FROM) },
        )
        SwapButton { swapTransferSides(form) }
        TransferAccountColumn(
            data = data,
            institutionId = form.toInstitutionId,
            instrumentId = form.toInstrumentId,
            label = "转入",
            modifier = Modifier.weight(1f),
            onClick = { onPick(PickerTarget.TRANSFER_TO) },
        )
    }
}

@Composable
private fun TransferAccountColumn(
    data: PortfolioData,
    institutionId: String?,
    instrumentId: String?,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val institution = data.institutions.firstOrNull { it.id == institutionId }
    val instrument = instrumentId?.let { data.instrument(it) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FolioGold,
        )
        Text(
            text = institution?.name ?: "机构",
            style = MaterialTheme.typography.bodyMedium,
            color = if (institution == null) FolioMuted else FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = instrument?.let { "${it.code} ${it.name}" } ?: "标的",
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5F5F5))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SwapHoriz,
            contentDescription = "交换转出转入",
            tint = FolioMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PriceFxSection(
    form: EventForm,
    fromInstrument: Instrument?,
    toInstrument: Instrument?,
) {
    Column {
        Text(
            text = "价格与汇率",
            style = MaterialTheme.typography.labelMedium,
            color = FolioMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
        )
        if (fromInstrument?.kind == InstrumentKind.QUOTED) {
            FormDivider()
            PriceRow("转出单价", form.fromPrice, { form.fromPrice = it }, fromInstrument.currency)
        }
        if (toInstrument?.kind == InstrumentKind.QUOTED) {
            FormDivider()
            PriceRow("转入单价", form.toPrice, { form.toPrice = it }, toInstrument.currency)
        }
        if (needsFx(fromInstrument)) {
            FormDivider()
            FxRow("转出汇率", fromInstrument!!.currency, form.fromFx) { form.fromFx = it }
        }
        if (needsFx(toInstrument)) {
            FormDivider()
            FxRow("转入汇率", toInstrument!!.currency, form.toFx) { form.toFx = it }
        }
        FormDivider()
        val feeCurrency = transferFeeCurrency(fromInstrument, toInstrument)
        FormRow(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            iconTint = FolioGreen,
            label = "手续费",
        ) {
            Text(
                text = feeCurrency,
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
                modifier = Modifier.weight(1f),
            )
            InlineNumberField(
                value = form.transferFee,
                onValueChange = { form.transferFee = it },
                modifier = Modifier.width(120.dp),
                placeholder = "0",
            )
        }
    }
}

@Composable
internal fun IncomeSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val destination = form.incomeInstrumentId?.let { data.instrument(it) }
    val category = destination?.currency ?: "CNY"
    val amount = form.incomeAmount.toBigDecimalOrNull()
    val fx = form.incomeFx.toBigDecimalOrNull()
    val converted = if (needsFx(destination) && amount != null && fx != null) {
        amount.multiply(fx)
    } else {
        null
    }

    AmountHero(
        label = if (form.incomeKind == "dividend") "分红金额" else "利息金额",
        unit = category,
        value = form.incomeAmount,
        onValueChange = { form.incomeAmount = it },
        secondary = when {
            destination == null -> "未选择账户"
            converted != null -> "≈ ${formatCny(converted)}"
            else -> null
        },
    )
    FormDivider()
    FormRow(
        icon = Icons.Outlined.Category,
        iconTint = FolioGreen,
        label = "类型",
    ) {
        InlineDropdown(
            selectedId = form.incomeKind,
            options = listOf("dividend" to "分红", "interest" to "利息"),
            onSelected = { form.incomeKind = it },
        )
    }
    FormDivider()
    ToggleRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        text = "记录来源持仓",
        checked = form.incomeHasSource,
        onCheckedChange = {
            form.incomeHasSource = it
            if (!it) {
                form.sourceInstitutionId = null
                form.sourceInstrumentId = null
            }
        },
    )
    if (form.incomeHasSource) {
        FormDivider()
        FormRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = FolioGold,
            label = "来源",
            onClick = { onPick(PickerTarget.INCOME_SOURCE) },
            showChevron = true,
        ) {
            PositionValue(data, form.sourceInstitutionId, form.sourceInstrumentId)
        }
    }
    FormDivider()
    FormRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        label = "入账",
        onClick = { onPick(PickerTarget.INCOME_DESTINATION) },
        showChevron = true,
    ) {
        PositionValue(data, form.incomeInstitutionId, form.incomeInstrumentId)
    }
    if (needsFx(destination)) {
        FormDivider()
        FxRow("汇率", destination!!.currency, form.incomeFx) { form.incomeFx = it }
    }
    FormDivider()
    ToggleRow(
        icon = Icons.Outlined.SwapHoriz,
        iconTint = FolioBlue,
        text = "立即转出到外部消费账户",
        checked = form.immediateWithdrawal,
        onCheckedChange = { form.immediateWithdrawal = it },
    )
    if (form.immediateWithdrawal) {
        FormDivider()
        FormRow(
            icon = Icons.Outlined.EditNote,
            iconTint = FolioPurple,
            label = "外部",
        ) {
            InlineTextField(
                value = form.externalLabel,
                onValueChange = { form.externalLabel = it },
                placeholder = "外部账户标签",
            )
        }
    }
}

@Composable
internal fun ExternalSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val instrument = form.externalInstrumentId?.let { data.instrument(it) }
    val currency = instrument?.currency ?: "CNY"
    val amount = form.externalAmount.toBigDecimalOrNull()
    val fx = form.externalFx.toBigDecimalOrNull()
    val converted = if (needsFx(instrument) && amount != null && fx != null) {
        amount.multiply(fx)
    } else {
        null
    }
    val isIn = form.externalDirection == "in"

    AmountHero(
        label = if (isIn) "入金金额" else "出金金额",
        unit = currency,
        value = form.externalAmount,
        onValueChange = { form.externalAmount = it },
        secondary = when {
            instrument == null -> "未选择账户"
            converted != null -> "≈ ${formatCny(converted)}"
            else -> null
        },
    )
    FormDivider()
    FormRow(
        icon = Icons.Outlined.SwapHoriz,
        iconTint = FolioGold,
        label = "方向",
    ) {
        InlineDropdown(
            selectedId = form.externalDirection,
            options = listOf("in" to "入金", "out" to "出金"),
            onSelected = { form.externalDirection = it },
        )
    }
    if (!isIn) {
        FormDivider()
        FormRow(
            icon = Icons.Outlined.Category,
            iconTint = FolioGreen,
            label = "分类",
        ) {
            InlineDropdown(
                selectedId = form.externalClassification,
                options = listOf("capital" to "本金取出", "consumption" to "消费/领出"),
                onSelected = { form.externalClassification = it },
            )
        }
    }
    FormDivider()
    FormRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        label = "账户",
        onClick = { onPick(PickerTarget.EXTERNAL_CASH) },
        showChevron = true,
    ) {
        PositionValue(data, form.externalInstitutionId, form.externalInstrumentId)
    }
    if (needsFx(instrument)) {
        FormDivider()
        FxRow("汇率", instrument!!.currency, form.externalFx) { form.externalFx = it }
    }
}

@Composable
internal fun ExpenseSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val cash = form.expenseInstrumentId?.let { data.instrument(it) }
    val currency = if (form.expenseNoPosition) "CNY" else cash?.currency ?: "CNY"

    AmountHero(
        label = "支出金额",
        unit = currency,
        value = form.expenseAmount,
        onValueChange = { form.expenseAmount = it },
        secondary = "可为负",
        allowNegative = true,
    )
    FormDivider()
    FormRow(
        icon = Icons.Outlined.Category,
        iconTint = FolioGreen,
        label = "类别",
    ) {
        InlineDropdown(
            selectedId = form.expenseCategoryId,
            options = data.settings.categories.sortedBy { it.order }.map { it.id to it.name },
            onSelected = { form.expenseCategoryId = it },
        )
    }
    FormDivider()
    ToggleRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        text = "非现金损耗（无现金账户）",
        checked = form.expenseNoPosition,
        onCheckedChange = {
            form.expenseNoPosition = it
            if (it) {
                form.expenseInstitutionId = null
                form.expenseInstrumentId = null
            }
        },
    )
    if (!form.expenseNoPosition) {
        FormDivider()
        FormRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = FolioGold,
            label = "账户",
            onClick = { onPick(PickerTarget.EXPENSE_CASH) },
            showChevron = true,
        ) {
            PositionValue(data, form.expenseInstitutionId, form.expenseInstrumentId)
        }
        if (needsFx(cash)) {
            FormDivider()
            FxRow("汇率", cash!!.currency, form.expenseFx) { form.expenseFx = it }
        }
    }
}

@Composable
internal fun OpeningSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val instrument = form.openingInstrumentId?.let { data.instrument(it) }
    val cash = instrument?.kind == InstrumentKind.CASH
    val unit = instrument?.let { if (cash) it.currency else it.unit } ?: ""
    val converted = cnyEstimate(quotes, instrument, form.openingValue, form.openingFx)

    AmountHero(
        label = if (instrument != null && !cash) "期初份额" else "期初金额",
        unit = unit,
        value = form.openingValue,
        onValueChange = { form.openingValue = it },
        secondary = when {
            instrument == null -> "未选择持仓"
            converted != null -> "≈ ${formatCny(converted)}"
            else -> null
        },
    )
    FormDivider()
    FormRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        label = "持仓",
        onClick = { onPick(PickerTarget.OPENING_POSITION) },
        showChevron = true,
    ) {
        PositionValue(data, form.openingInstitutionId, form.openingInstrumentId)
    }
    if (needsFx(instrument)) {
        FormDivider()
        FxRow("汇率", instrument!!.currency, form.openingFx) { form.openingFx = it }
    }
}

@Composable
internal fun AdjustmentSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    onPick: (PickerTarget) -> Unit,
) {
    val instrument = form.adjustmentInstrumentId?.let { data.instrument(it) }
    val cash = instrument?.kind == InstrumentKind.CASH
    val unit = instrument?.let { if (cash) it.currency else it.unit } ?: ""
    val converted = cnyEstimate(quotes, instrument, form.adjustmentValue, form.adjustmentFx)

    AmountHero(
        label = if (instrument != null && !cash) "份额增减" else "金额增减",
        unit = unit,
        value = form.adjustmentValue,
        onValueChange = { form.adjustmentValue = it },
        secondary = when {
            instrument == null -> "未选择持仓"
            converted != null -> "≈ ${formatCny(converted)}"
            else -> "可输入负数"
        },
        allowNegative = true,
    )
    FormDivider()
    FormRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        label = "持仓",
        onClick = { onPick(PickerTarget.ADJUSTMENT_POSITION) },
        showChevron = true,
    ) {
        PositionValue(data, form.adjustmentInstitutionId, form.adjustmentInstrumentId)
    }
    FormDivider()
    FormRow(
        icon = Icons.Outlined.EditNote,
        iconTint = FolioPurple,
        label = "原因",
    ) {
        InlineTextField(
            value = form.adjustmentReason,
            onValueChange = { form.adjustmentReason = it },
            placeholder = "必填",
        )
    }
    if (needsFx(instrument)) {
        FormDivider()
        FxRow("汇率", instrument!!.currency, form.adjustmentFx) { form.adjustmentFx = it }
    }
}

@Composable
internal fun CommonDetails(
    form: EventForm,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val (dateLabel, timeLabel) = dateTimeLabels(form)
    FormDivider()
    FormRow(
        icon = Icons.Outlined.Schedule,
        iconTint = FolioCoral,
        label = "时间",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = FolioInk,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPickDate),
            )
            Surface(
                shape = CircleShape,
                color = Color(0xFFF5F5F5),
                modifier = Modifier.clickable(onClick = onPickTime),
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolioInk,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
    FormDivider()
    FormRow(
        icon = Icons.Outlined.BookmarkBorder,
        iconTint = FolioPurple,
        label = "备注",
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        InlineTextField(
            value = form.note,
            onValueChange = { form.note = it },
            placeholder = "...",
            singleLine = false,
            maxLines = 3,
        )
    }
}

@Composable
private fun PriceRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
) {
    FormRow(
        icon = Icons.Outlined.Tune,
        iconTint = FolioGold,
        label = label,
    ) {
        Text(
            text = currency,
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.weight(1f),
        )
        InlineNumberField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun FxRow(
    label: String,
    currency: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    FormRow(
        icon = Icons.Outlined.CurrencyExchange,
        iconTint = FolioBlue,
        label = label,
    ) {
        Text(
            text = "${currency.uppercase()} → CNY",
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.weight(1f),
        )
        InlineNumberField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(110.dp),
        )
    }
}

private fun needsFx(instrument: Instrument?): Boolean =
    instrument != null && instrument.currency.uppercase() !in setOf("CNY", "CNH")
