package com.jimbozhang.folioprism.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.Precision
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.eventsReferencingInstrument
import com.jimbozhang.folioprism.ui.components.FormDivider
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineDropdown
import com.jimbozhang.folioprism.ui.components.InlineNumberField
import com.jimbozhang.folioprism.ui.components.InlineTextField
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.util.UUID

@Composable
internal fun InstrumentManagementScreen(
    data: PortfolioData,
    onBack: () -> Unit,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Instrument?>(null) }
    val groups = remember(data.instruments, data.settings.categories) {
        val categories = data.settings.categories.sortedBy { it.order }
        val known = categories.mapNotNull { category ->
            val items = data.instruments
                .filter { it.categoryId == category.id }
                .sortedBy { it.code }
            if (items.isEmpty()) null else category to items
        }
        val knownIds = categories.map { it.id }.toSet()
        val uncategorized = data.instruments
            .filter { it.categoryId !in knownIds }
            .sortedBy { it.code }
        if (uncategorized.isEmpty()) known else known + (null to uncategorized)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ManagementTopBar(
            title = "标的",
            onBack = onBack,
            onAdd = {
                editing = null
                showEditor = true
            },
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (data.instruments.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    ManagementListCard {
                        Text(
                            text = "暂无标的",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FolioMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                groups.forEach { (category, instruments) ->
                    item {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            ManagementGroupHeader(
                                title = category?.name ?: "未分类",
                                count = instruments.size,
                                dotColor = category?.let { categoryColor(it.color) },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ManagementListCard {
                                instruments.forEachIndexed { index, instrument ->
                                    if (index > 0) {
                                        HorizontalDivider(color = FolioDivider)
                                    }
                                    ManagementRow(
                                        icon = categoryIcon(instrument.categoryId),
                                        accent = categoryColor(category?.color),
                                        title = "${instrument.code} ${instrument.name}",
                                        subtitle = instrumentSubtitle(instrument),
                                        onClick = {
                                            editing = instrument
                                            showEditor = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        InstrumentFormDialog(
            initial = editing,
            categories = data.settings.categories,
            onDismiss = {
                showEditor = false
                editing = null
            },
            onSave = { instrument ->
                onUpdateData { current ->
                    val exists = current.instruments.any { it.id == instrument.id }
                    current.copy(
                        instruments = if (exists) {
                            current.instruments.map {
                                if (it.id == instrument.id) instrument else it
                            }
                        } else {
                            current.instruments + instrument
                        },
                    )
                }
                null
            },
            onDelete = editing?.let { instrument ->
                {
                    if (data.eventsReferencingInstrument(instrument.id)) {
                        "存在引用该标的的事件，无法删除"
                    } else {
                        onUpdateData { current ->
                            current.copy(
                                instruments = current.instruments
                                    .filterNot { it.id == instrument.id },
                            )
                        }
                        null
                    }
                }
            },
        )
    }
}

@Composable
private fun InstrumentFormDialog(
    initial: Instrument?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Instrument) -> String?,
    onDelete: (() -> String?)?,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var code by remember(initial?.id) { mutableStateOf(initial?.code ?: "") }
    var categoryId by remember(initial?.id) {
        mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: "")
    }
    var currency by remember(initial?.id) {
        mutableStateOf(initial?.currency ?: "CNY")
    }
    var unit by remember(initial?.id) { mutableStateOf(initial?.unit ?: "份") }
    var kind by remember(initial?.id) {
        mutableStateOf(initial?.kind ?: InstrumentKind.QUOTED)
    }
    var quoteMode by remember(initial?.id) {
        mutableStateOf(initial?.quote?.mode ?: QuoteMode.AUTO)
    }
    var manualPrice by remember(initial?.id) {
        mutableStateOf(initial?.quote?.manualPrice?.toPlainString() ?: "")
    }
    var priceDecimals by remember(initial?.id) {
        mutableStateOf((initial?.precision?.priceDecimals ?: 2).toString())
    }
    var quantityDecimals by remember(initial?.id) {
        mutableStateOf((initial?.precision?.quantityDecimals ?: 2).toString())
    }
    var exposure by remember(initial?.id) {
        mutableStateOf(initial?.currencyExposure ?: "auto")
    }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }

    ManagementFormDialog(
        title = if (initial == null) "新增标的" else "编辑标的",
        editing = initial != null,
        deleteTitle = "删除该标的？",
        onDismiss = onDismiss,
        onSave = {
            val price = manualPrice.toBigDecimalOrNull()
            when {
                code.isBlank() || name.isBlank() || categoryId.isBlank() ->
                    "代码、名称、类别不能为空"
                quoteMode == QuoteMode.MANUAL && (price == null || price.signum() <= 0) ->
                    "手工价格必须大于 0"
                else -> onSave(
                    Instrument(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        code = code.trim(),
                        name = name.trim(),
                        currency = currency,
                        unit = unit.ifBlank { "份" },
                        kind = kind,
                        quote = QuoteConfig(
                            mode = quoteMode,
                            source = when (quoteMode) {
                                QuoteMode.AUTO -> "auto"
                                QuoteMode.MANUAL -> "manual"
                                QuoteMode.NONE -> "none"
                            },
                            manualPrice = if (quoteMode == QuoteMode.MANUAL) price else null,
                        ),
                        precision = Precision(
                            priceDecimals = priceDecimals.toIntOrNull()?.coerceIn(0, 8) ?: 2,
                            quantityDecimals = quantityDecimals.toIntOrNull()?.coerceIn(0, 8) ?: 2,
                        ),
                        currencyExposure = exposure,
                        notes = notes.trim(),
                    ),
                )
            }
        },
        onDelete = onDelete,
    ) {
        ManagementNameInput(
            label = "标的名称",
            value = name,
            onValueChange = { name = it },
            placeholder = "请输入标的名称",
        )
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.EditNote,
                iconTint = FolioGold,
                label = "代码",
            ) {
                InlineTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = "如 008163",
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Category,
                iconTint = FolioGreen,
                label = "类别",
            ) {
                InlineDropdown(
                    selectedId = categoryId,
                    options = categories.sortedBy { it.order }.map { it.id to it.name },
                    onSelected = { categoryId = it },
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.CurrencyExchange,
                iconTint = FolioBlue,
                label = "币种",
            ) {
                InlineDropdown(
                    selectedId = currency,
                    options = listOf(
                        "CNY" to "CNY",
                        "USD" to "USD",
                        "HKD" to "HKD",
                        "CNH" to "CNH",
                    ),
                    onSelected = { currency = it },
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioPurple,
                label = "单位",
            ) {
                InlineTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    placeholder = "份 / 股 / 克 / 元",
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.Category,
                iconTint = FolioGold,
                label = "标的类型",
            ) {
                InlineDropdown(
                    selectedId = kind.name,
                    options = listOf(
                        "QUOTED" to "有净值",
                        "CASH" to "现金",
                    ),
                    onSelected = { kind = InstrumentKind.valueOf(it) },
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                iconTint = FolioCoral,
                label = "行情方式",
            ) {
                InlineDropdown(
                    selectedId = quoteMode.name,
                    options = listOf(
                        "AUTO" to "自动",
                        "MANUAL" to "手工价格",
                        "NONE" to "无行情",
                    ),
                    onSelected = { quoteMode = QuoteMode.valueOf(it) },
                )
            }
            if (quoteMode == QuoteMode.MANUAL) {
                FormDivider()
                FormRow(
                    icon = Icons.Outlined.Tune,
                    iconTint = FolioGold,
                    label = "手工价格",
                ) {
                    InlineNumberField(
                        value = manualPrice,
                        onValueChange = { manualPrice = it },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioGold,
                label = "价格小数",
            ) {
                InlineNumberField(
                    value = priceDecimals,
                    onValueChange = { priceDecimals = it },
                    modifier = Modifier.width(80.dp),
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioGold,
                label = "数量小数",
            ) {
                InlineNumberField(
                    value = quantityDecimals,
                    onValueChange = { quantityDecimals = it },
                    modifier = Modifier.width(80.dp),
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Public,
                iconTint = FolioBlue,
                label = "币种暴露",
            ) {
                InlineDropdown(
                    selectedId = exposure,
                    options = listOf(
                        "auto" to "自动",
                        "CNY" to "CNY",
                        "USD" to "USD",
                        "HKD" to "HKD",
                        "PHYSICAL_GOLD" to "实物黄金",
                    ),
                    onSelected = { exposure = it },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.BookmarkBorder,
                iconTint = FolioPurple,
                label = "备注",
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                InlineTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "...",
                    singleLine = false,
                    maxLines = 4,
                )
            }
        }
    }
}

private fun instrumentSubtitle(instrument: Instrument): String =
    "${instrument.currency} · ${instrument.unit} · ${instrument.kind.label()} · ${instrument.quote.mode.label()}"

private fun InstrumentKind.label(): String = when (this) {
    InstrumentKind.CASH -> "现金"
    InstrumentKind.QUOTED -> "有净值"
}

private fun QuoteMode.label(): String = when (this) {
    QuoteMode.AUTO -> "自动"
    QuoteMode.MANUAL -> "手工"
    QuoteMode.NONE -> "无行情"
}
