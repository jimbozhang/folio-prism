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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.calc.TransferPricing
import com.jimbozhang.folioprism.domain.calc.TransferSuggestion
import com.jimbozhang.folioprism.domain.calc.Valuation
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
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
import com.jimbozhang.folioprism.ui.components.formatQuantity
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
internal fun TransferSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    positions: Map<PositionKey, BigDecimal>,
    onPick: (PickerTarget) -> Unit,
) {
    val fromInstrument = form.fromInstrumentId?.let { data.instrument(it) }
    val toInstrument = form.toInstrumentId?.let { data.instrument(it) }
    val fromValue = nativeLegValue(
        form.fromQuantity,
        form.fromPrice,
        form.fromPriceInCny,
        fromInstrument,
        quotes,
    )
    val toValue = nativeLegValue(
        form.toQuantity,
        form.toPrice,
        form.toPriceInCny,
        toInstrument,
        quotes,
    )
    val lossCurrency = fromInstrument?.currency ?: "CNY"
    val autoLoss = autoLossNative(form, fromInstrument, toInstrument, fromValue, toValue, quotes)

    val fromKey = if (form.fromInstitutionId != null && form.fromInstrumentId != null) {
        PositionKey(form.fromInstitutionId!!, form.fromInstrumentId!!)
    } else {
        null
    }
    val fromHolding = if (fromInstrument != null) {
        fromKey?.let { positions[it] } ?: BigDecimal.ZERO
    } else {
        null
    }
    val fromHoldingValue = if (fromKey != null && fromHolding != null) {
        Valuation.valuePosition(
            data,
            fromKey,
            fromHolding,
            LivePriceProvider(quotes),
            LocalDate.now(),
        )
    } else {
        null
    }

    val fromQuantityValue = form.fromQuantity.toBigDecimalOrNull()
    val suggestedTo = TransferSuggestion.suggestToQuantity(
        data = data,
        quotes = quotes,
        fromInstrumentId = form.fromInstrumentId,
        fromQuantity = fromQuantityValue,
        fromPrice = nativePriceOrNull(
            form.fromPrice,
            form.fromPriceInCny,
            fromInstrument,
            quotes,
        ),
        toInstrumentId = form.toInstrumentId,
        toPrice = nativePriceOrNull(
            form.toPrice,
            form.toPriceInCny,
            toInstrument,
            quotes,
        ),
    )
    // Receive-side rebalancing: move enough value in to bring the destination
    // category to its target share, filling both sides of the transfer.
    val suggestedIn = TransferSuggestion.suggestRebalanceIn(
        data = data,
        positions = positions,
        provider = LivePriceProvider(quotes),
        date = LocalDate.now(),
        fromInstitutionId = form.fromInstitutionId,
        fromInstrumentId = form.fromInstrumentId,
        toInstitutionId = form.toInstitutionId,
        toInstrumentId = form.toInstrumentId,
    )

    // Seed the receive side while it is blank or still holds the last
    // suggestion. Once the user types another number, that number wins.
    //
    // The first pass only arms the baseline instead of recomputing: when
    // editing, toQuantity already holds the saved value, so keep it rather
    // than rewriting it with current quotes; arming lets later from-side
    // edits (holding slider, quantity, prices) drive it from there.
    var firstSuggestionPass by remember { mutableStateOf(true) }
    LaunchedEffect(
        form.fromInstitutionId,
        form.fromInstrumentId,
        form.fromQuantity,
        form.fromPrice,
        form.toInstitutionId,
        form.toInstrumentId,
        form.toPrice,
    ) {
        if (firstSuggestionPass) {
            firstSuggestionPass = false
            form.lastSuggestedToQuantity = form.toQuantity
            return@LaunchedEffect
        }
        val result = TransferSuggestion.applyQuantitySuggestion(
            current = form.toQuantity,
            lastSuggested = form.lastSuggestedToQuantity,
            suggested = suggestedTo,
        )
        if (result.value != form.toQuantity) form.toQuantity = result.value
        form.lastSuggestedToQuantity = result.lastSuggested
    }

    TransferAccountsRow(data, form, onPick)
    FormDivider()
    AmountHero(
        label = "转出",
        unit = heroQuantityLabel(fromInstrument),
        value = form.fromQuantity,
        onValueChange = { form.fromQuantity = it },
        secondary = if (fromInstrument != null && fromValue != null) {
            formatNative(fromValue, fromInstrument.currency)
        } else {
            null
        },
    )
    if (fromInstrument != null && fromHolding != null) {
        HoldingHint(fromInstrument, fromHolding, fromHoldingValue)
        if (fromHolding.signum() > 0) {
            HoldingSlider(
                holding = fromHolding,
                quantityText = form.fromQuantity,
                decimals = fromInstrument.precision.quantityDecimals,
                onAuto = {
                    val auto = TransferSuggestion.suggestRebalanceOutQuantity(
                        data = data,
                        positions = positions,
                        provider = LivePriceProvider(quotes),
                        date = LocalDate.now(),
                        fromInstitutionId = form.fromInstitutionId,
                        fromInstrumentId = form.fromInstrumentId,
                        toInstrumentId = form.toInstrumentId,
                    )
                    if (auto != null) form.fromQuantity = auto.toPlainString()
                },
                onQuantityChange = { form.fromQuantity = it },
            )
        }
        if (exceedsHolding(fromQuantityValue, fromHolding)) {
            Text(
                text = "超过当前持仓，保存后持仓将为负",
                style = MaterialTheme.typography.bodySmall,
                color = FolioCoral,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
    }

    AmountHero(
        label = "转入",
        unit = heroQuantityLabel(toInstrument),
        value = form.toQuantity,
        onValueChange = { form.toQuantity = it },
        secondary = if (toInstrument != null && toValue != null) {
            formatNative(toValue, toInstrument.currency)
        } else {
            null
        },
    )
    if (suggestedIn != null &&
        (suggestedIn.fromQuantity.toPlainString() != form.fromQuantity ||
            suggestedIn.toQuantity.toPlainString() != form.toQuantity)
    ) {
        TextButton(
            onClick = {
                form.fromQuantity = suggestedIn.fromQuantity.toPlainString()
                form.toQuantity = suggestedIn.toQuantity.toPlainString()
                // Treat the result as explicit user input so the value-based
                // suggestion does not replace it afterwards.
                form.lastSuggestedToQuantity = null
            },
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = "按目标占比：" + formatQuantity(suggestedIn.toQuantity) + " " +
                    heroQuantityLabel(toInstrument) +
                    if (suggestedIn.cappedBySource) "（受转出上限）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = FolioGold,
            )
        }
    }
    if (suggestedTo != null && suggestedTo.toPlainString() != form.toQuantity) {
        TextButton(
            onClick = {
                form.toQuantity = suggestedTo.toPlainString()
                form.lastSuggestedToQuantity = suggestedTo.toPlainString()
            },
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = "按价值换算：" + formatQuantity(suggestedTo) + " " +
                    heroQuantityLabel(toInstrument),
                style = MaterialTheme.typography.bodySmall,
                color = FolioGold,
            )
        }
    }

    if (fromValue != null && toValue != null && fromInstrument != null && toInstrument != null) {
        TransferSummary(
            fromValue = fromValue,
            toValue = toValue,
            fromInstrument = fromInstrument,
            toInstrument = toInstrument,
            feeLossText = form.feeLoss,
            feeFromCash = form.feeFromCash,
            lossCurrency = lossCurrency,
            autoLoss = autoLoss,
            quotes = quotes,
        )
    }
    PriceSection(data, quotes, form, fromInstrument, toInstrument, positions, autoLoss, onPick)
}

@Composable
private fun HoldingHint(
    instrument: Instrument,
    holding: BigDecimal,
    valueCny: BigDecimal?,
) {
    val text = if (instrument.kind == InstrumentKind.CASH) {
        "现金余额 " + formatNative(holding, instrument.currency)
    } else {
        "当前持仓 " + formatQuantity(holding) + " " + instrument.unit
    }
    val showCny = valueCny != null &&
        !(instrument.kind == InstrumentKind.CASH &&
            instrument.currency.uppercase() in setOf("CNY", "CNH"))
    val cnySuffix = if (showCny) " · 约 " + formatCny(valueCny) else ""
    Text(
        text = text + cnySuffix,
        style = MaterialTheme.typography.bodySmall,
        color = FolioMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun HoldingSlider(
    holding: BigDecimal,
    quantityText: String,
    decimals: Int,
    onAuto: () -> Unit,
    onQuantityChange: (String) -> Unit,
) {
    val holdingFloat = holding.toFloat()
    if (holdingFloat <= 0f) return
    val current = quantityText.toBigDecimalOrNull()?.toFloat() ?: 0f
    val fraction = (current / holdingFloat).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = fraction,
            onValueChange = { value ->
                val quantity = holding
                    .multiply(BigDecimal.valueOf(value.toDouble()))
                    .setScale(decimals, RoundingMode.DOWN)
                onQuantityChange(quantity.toPlainString())
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = FolioGold,
                activeTrackColor = FolioGold,
                inactiveTrackColor = FolioDivider,
            ),
        )
        TextButton(onClick = { onAuto() }) {
            Text(
                text = "自动",
                style = MaterialTheme.typography.labelMedium,
                color = FolioGold,
            )
        }
    }
}

private fun exceedsHolding(quantity: BigDecimal?, holding: BigDecimal): Boolean =
    quantity != null && quantity.signum() > 0 && quantity > holding

@Composable
private fun TransferSummary(
    fromValue: BigDecimal,
    toValue: BigDecimal,
    fromInstrument: Instrument,
    toInstrument: Instrument,
    feeLossText: String,
    feeFromCash: Boolean,
    lossCurrency: String,
    autoLoss: BigDecimal?,
    quotes: QuoteSnapshot,
) {
    val crossCurrency = !sameCurrency(fromInstrument, toInstrument)
    val embedded = autoLoss ?: BigDecimal.ZERO
    val feeLoss = feeLossText.toBigDecimalOrNull() ?: embedded
    val cashFee = if (feeFromCash) feeLoss.subtract(embedded) else null
    val cashSuffix = if (cashFee != null) {
        " · 现金账户扣 " + formatNative(cashFee, lossCurrency)
    } else {
        ""
    }
    val ratio = if (feeLoss.signum() > 0 && fromValue.signum() > 0) {
        feeLoss.multiply(BigDecimal(100)).divide(fromValue, 1, RoundingMode.HALF_UP)
    } else {
        null
    }
    Text(
        text = "转出价值 " + formatNative(fromValue, fromInstrument.currency) +
            " · 转入价值 " + formatNative(toValue, toInstrument.currency) +
            " · 手续费/损耗 " + formatNative(feeLoss, lossCurrency) + cashSuffix +
            (ratio?.let { " · 损耗占比 " + it.toPlainString() + "%" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = FolioMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
    if (crossCurrency && toValue.signum() > 0) {
        val fromCny = cnyValue(fromValue, fromInstrument.currency, quotes)
        val toCny = cnyValue(toValue, toInstrument.currency, quotes)
        val parts = mutableListOf<String>()
        if (fromCny != null && toCny != null) {
            parts += "折合 " + formatCny(fromCny) + " → " + formatCny(toCny)
        }
        parts += "1 " + toInstrument.currency + " ≈ " +
            fromValue.divide(toValue, 4, RoundingMode.HALF_UP).toPlainString() + " " +
            fromInstrument.currency
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

private fun cnyValue(
    value: BigDecimal,
    currency: String,
    quotes: QuoteSnapshot,
): BigDecimal? = when (currency.uppercase()) {
    "CNY", "CNH" -> value
    else -> quotes.fxRates[currency.uppercase()]?.let { value.multiply(it).toCnyAmount() }
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
private fun PriceSection(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    form: EventForm,
    fromInstrument: Instrument?,
    toInstrument: Instrument?,
    positions: Map<PositionKey, BigDecimal>,
    autoLoss: BigDecimal?,
    onPick: (PickerTarget) -> Unit,
) {
    val lossCurrency = fromInstrument?.currency ?: "CNY"
    val crossCurrency = fromInstrument != null && toInstrument != null &&
        !sameCurrency(fromInstrument, toInstrument)
    Column {
        if (fromInstrument?.kind == InstrumentKind.QUOTED) {
            FormDivider()
            PriceRow(
                label = "转出单价",
                value = form.fromPrice,
                onValueChange = { form.fromPrice = it },
                currency = fromInstrument.currency,
                inCny = form.fromPriceInCny,
                nativePreview = priceNativePreview(
                    form.fromPrice,
                    form.fromPriceInCny,
                    fromInstrument,
                    quotes,
                ),
                onToggleCny = { toCny ->
                    togglePriceCurrency(
                        fromLeg = true,
                        toCny = toCny,
                        form = form,
                        instrument = fromInstrument,
                        quotes = quotes,
                    )
                },
            )
        }
        if (toInstrument?.kind == InstrumentKind.QUOTED) {
            FormDivider()
            PriceRow(
                label = "转入单价",
                value = form.toPrice,
                onValueChange = { form.toPrice = it },
                currency = toInstrument.currency,
                inCny = form.toPriceInCny,
                nativePreview = priceNativePreview(
                    form.toPrice,
                    form.toPriceInCny,
                    toInstrument,
                    quotes,
                ),
                onToggleCny = { toCny ->
                    togglePriceCurrency(
                        fromLeg = false,
                        toCny = toCny,
                        form = form,
                        instrument = toInstrument,
                        quotes = quotes,
                    )
                },
            )
        }
        FormDivider()
        FormRow(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            iconTint = FolioGreen,
            label = "手续费/损耗",
            labelWidth = 88.dp,
        ) {
            Text(
                text = when {
                    form.feeLoss.isNotBlank() -> lossCurrency
                    autoLoss != null ->
                        lossCurrency + " · 自动 " + formatNative(autoLoss, lossCurrency)
                    crossCurrency -> lossCurrency + " · 跨币种不自动计算"
                    else -> lossCurrency
                },
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
                modifier = Modifier.weight(1f),
            )
            InlineNumberField(
                value = form.feeLoss,
                onValueChange = { form.feeLoss = it },
                modifier = Modifier.width(120.dp),
                placeholder = "自动",
                allowNegative = true,
            )
        }
        FormDivider()
        ToggleRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = FolioGold,
            text = "手续费出自其他账户",
            checked = form.feeFromCash,
            onCheckedChange = { checked ->
                form.feeFromCash = checked
                if (!checked) {
                    form.feeInstitutionId = null
                    form.feeInstrumentId = null
                } else if (form.feeInstrumentId == null) {
                    defaultFeePosition(data, form, positions, lossCurrency)
                        ?.let { (institutionId, instrumentId) ->
                            form.feeInstitutionId = institutionId
                            form.feeInstrumentId = instrumentId
                        }
                }
            },
        )
        if (form.feeFromCash) {
            FormDivider()
            FormRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                iconTint = FolioGold,
                label = "手续费账户",
            ) {
                TransferAccountColumn(
                    data = data,
                    institutionId = form.feeInstitutionId,
                    instrumentId = form.feeInstrumentId,
                    label = "现金账户",
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(PickerTarget.FEE_CASH) },
                )
            }
        }
    }
}

private fun defaultFeePosition(
    data: PortfolioData,
    form: EventForm,
    positions: Map<PositionKey, BigDecimal>,
    lossCurrency: String,
): Pair<String, String>? {
    val held = positions.keys.firstOrNull { key ->
        key.institutionId == form.fromInstitutionId &&
            data.instrument(key.instrumentId)?.let {
                it.kind == InstrumentKind.CASH &&
                    it.currency.equals(lossCurrency, ignoreCase = true)
            } == true
    }
    if (held != null) return held.institutionId to held.instrumentId
    val instrument = data.instruments.firstOrNull {
        it.kind == InstrumentKind.CASH &&
            it.currency.equals(lossCurrency, ignoreCase = true)
    } ?: data.instruments.firstOrNull { it.kind == InstrumentKind.CASH }
        ?: return null
    val institutionId = form.fromInstitutionId
        ?: data.institutions.firstOrNull()?.id
        ?: return null
    return institutionId to instrument.id
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
    FormDivider()
    AmountHero(
        label = if (form.incomeKind == "dividend") "分红金额" else "利息金额",
        unit = category,
        value = form.incomeAmount,
        onValueChange = { form.incomeAmount = it },
    )
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
    val isIn = form.externalDirection == "in"

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
    FormDivider()
    AmountHero(
        label = if (isIn) "入金金额" else "出金金额",
        unit = currency,
        value = form.externalAmount,
        onValueChange = { form.externalAmount = it },
    )
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
    }
    FormDivider()
    AmountHero(
        label = "损耗金额",
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

    FormRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        iconTint = FolioGold,
        label = "持仓",
        onClick = { onPick(PickerTarget.OPENING_POSITION) },
        showChevron = true,
    ) {
        PositionValue(data, form.openingInstitutionId, form.openingInstrumentId)
    }
    FormDivider()
    AmountHero(
        label = if (instrument != null && !cash) "期初份额" else "期初金额",
        unit = unit,
        value = form.openingValue,
        onValueChange = { form.openingValue = it },
    )
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
    AmountHero(
        label = if (instrument != null && !cash) "份额增减" else "金额增减",
        unit = unit,
        value = form.adjustmentValue,
        onValueChange = { form.adjustmentValue = it },
        secondary = if (instrument != null) "可输入负数" else null,
        allowNegative = true,
    )
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
    inCny: Boolean,
    nativePreview: BigDecimal?,
    onToggleCny: (Boolean) -> Unit,
) {
    Column {
        FormRow(
            icon = Icons.Outlined.Tune,
            iconTint = FolioGold,
            label = label,
        ) {
            if (TransferPricing.isCny(currency)) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                    modifier = Modifier.weight(1f),
                )
            } else {
                InlineDropdown(
                    selectedId = if (inCny) "CNY" else currency,
                    options = listOf(currency to currency, "CNY" to "CNY"),
                    onSelected = { id -> onToggleCny(id == "CNY") },
                    modifier = Modifier.weight(1f),
                )
            }
            InlineNumberField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(120.dp),
            )
        }
        if (inCny) {
            Text(
                text = if (nativePreview != null) {
                    "折合 " + formatNative(nativePreview, currency)
                } else {
                    "缺少 $currency 汇率，无法换算"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (nativePreview != null) FolioMuted else FolioCoral,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp),
            )
        }
    }
}

/** Converts the price fields of one leg between native currency and CNY. */
private fun togglePriceCurrency(
    fromLeg: Boolean,
    toCny: Boolean,
    form: EventForm,
    instrument: Instrument,
    quotes: QuoteSnapshot,
) {
    val convert: (String?) -> String? = { text ->
        text?.toBigDecimalOrNull()?.let { value ->
            if (toCny) {
                TransferPricing.enteredPrice(value, true, instrument, quotes.fxRates)
                    ?.toPlainString()
            } else {
                TransferPricing.nativePrice(value, true, instrument, quotes.fxRates)
                    ?.toPlainString()
            }
        }
    }
    if (fromLeg) {
        form.fromPrice = convert(form.fromPrice) ?: ""
        form.lastSuggestedFromPrice = convert(form.lastSuggestedFromPrice)
        form.fromPriceInCny = toCny
    } else {
        form.toPrice = convert(form.toPrice) ?: ""
        form.lastSuggestedToPrice = convert(form.lastSuggestedToPrice)
        form.toPriceInCny = toCny
    }
}

private fun priceNativePreview(
    priceText: String,
    inCny: Boolean,
    instrument: Instrument,
    quotes: QuoteSnapshot,
): BigDecimal? =
    if (!inCny) {
        null
    } else {
        priceText.toBigDecimalOrNull()?.let {
            TransferPricing.nativePrice(it, true, instrument, quotes.fxRates)
        }
    }

private fun nativePriceOrNull(
    priceText: String,
    inCny: Boolean,
    instrument: Instrument?,
    quotes: QuoteSnapshot,
): BigDecimal? {
    if (instrument == null) return null
    val entered = priceText.toBigDecimalOrNull() ?: return null
    return TransferPricing.nativePrice(entered, inCny, instrument, quotes.fxRates)
}

private fun nativeLegValue(
    quantityText: String,
    priceText: String,
    inCny: Boolean,
    instrument: Instrument?,
    quotes: QuoteSnapshot,
): BigDecimal? {
    if (instrument == null) return null
    val quantity = quantityText.toBigDecimalOrNull() ?: return null
    return if (instrument.kind == InstrumentKind.CASH) {
        quantity
    } else {
        nativePriceOrNull(priceText, inCny, instrument, quotes)?.let { quantity.multiply(it) }
    }
}

private fun sameCurrency(from: Instrument, to: Instrument): Boolean =
    from.currency.equals(to.currency, ignoreCase = true) ||
        (from.currency.uppercase() in setOf("CNY", "CNH") &&
            to.currency.uppercase() in setOf("CNY", "CNH"))

/**
 * Auto loss in the from-leg currency. Same-currency transfers use native
 * values; cross-currency transfers only when both legs have an exact CNY value
 * (e.g. CNY cash plus a foreign price typed in CNY).
 */
private fun autoLossNative(
    form: EventForm,
    fromInstrument: Instrument?,
    toInstrument: Instrument?,
    fromValue: BigDecimal?,
    toValue: BigDecimal?,
    quotes: QuoteSnapshot,
): BigDecimal? {
    if (fromInstrument == null || toInstrument == null) return null
    if (sameCurrency(fromInstrument, toInstrument)) {
        return if (fromValue != null && toValue != null) fromValue.subtract(toValue) else null
    }
    val fromQuantity = form.fromQuantity.toBigDecimalOrNull() ?: return null
    val toQuantity = form.toQuantity.toBigDecimalOrNull() ?: return null
    val fromCny = TransferPricing.exactCnyValue(
        instrument = fromInstrument,
        quantity = fromQuantity,
        nativePrice = nativePriceOrNull(
            form.fromPrice,
            form.fromPriceInCny,
            fromInstrument,
            quotes,
        ),
        enteredPrice = form.fromPrice.toBigDecimalOrNull(),
        inCny = form.fromPriceInCny,
    ) ?: return null
    val toCny = TransferPricing.exactCnyValue(
        instrument = toInstrument,
        quantity = toQuantity,
        nativePrice = nativePriceOrNull(form.toPrice, form.toPriceInCny, toInstrument, quotes),
        enteredPrice = form.toPrice.toBigDecimalOrNull(),
        inCny = form.toPriceInCny,
    ) ?: return null
    return TransferPricing.lossInCurrency(
        lossCny = fromCny.subtract(toCny),
        lossCurrency = fromInstrument.currency,
        fxRates = quotes.fxRates,
    )
}
