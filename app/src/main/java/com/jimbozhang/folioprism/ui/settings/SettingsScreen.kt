package com.jimbozhang.folioprism.ui.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.GoldPremium
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.InstitutionType
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.Precision
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.eventsReferencingInstitution
import com.jimbozhang.folioprism.domain.model.eventsReferencingInstrument
import com.jimbozhang.folioprism.platform.storage.StoragePaths
import com.jimbozhang.folioprism.ui.components.FolioFilterChip
import com.jimbozhang.folioprism.ui.components.formatPercent
import java.math.BigDecimal
import java.util.UUID

@Composable
fun SettingsScreen(
    data: PortfolioData,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var showInstitutionEditor by remember { mutableStateOf(false) }
    var editingInstitution by remember { mutableStateOf<Institution?>(null) }
    var showInstrumentEditor by remember { mutableStateOf(false) }
    var editingInstrument by remember { mutableStateOf<Instrument?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingPremiumInstitution by remember { mutableStateOf<Institution?>(null) }
    var showGoldSourceEditor by remember { mutableStateOf(false) }

    val categoryNames = remember(data.settings.categories) {
        data.settings.categories.associate { it.id to it.name }
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
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            SettingsCard("数据") {
                Text(
                    text = StoragePaths.dataDir().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "机构 ${data.institutions.size} · 标的 ${data.instruments.size} · 事件 ${data.events.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "备份保留 ${data.settings.backup.retainCount} 份；可直接用文件管理器复制 JSON。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionHeader(title = "机构", addLabel = "新增") {
                editingInstitution = null
                showInstitutionEditor = true
            }
        }
        items(data.institutions, key = { it.id }) { institution ->
            InstitutionRow(
                institution = institution,
                onEdit = {
                    editingInstitution = institution
                    showInstitutionEditor = true
                },
                onDelete = {
                    if (data.eventsReferencingInstitution(institution.id)) {
                        Toast.makeText(context, "存在引用该机构的事件，无法删除", Toast.LENGTH_SHORT).show()
                    } else {
                        onUpdateData { current ->
                            current.copy(institutions = current.institutions.filterNot { it.id == institution.id })
                        }
                    }
                },
            )
        }

        item {
            SectionHeader(title = "标的", addLabel = "新增") {
                editingInstrument = null
                showInstrumentEditor = true
            }
        }
        items(data.instruments.sortedBy { categoryNames[it.categoryId] ?: it.categoryId }, key = { it.id }) { instrument ->
            InstrumentRow(
                instrument = instrument,
                categoryName = categoryNames[instrument.categoryId] ?: instrument.categoryId,
                onEdit = {
                    editingInstrument = instrument
                    showInstrumentEditor = true
                },
                onDelete = {
                    if (data.eventsReferencingInstrument(instrument.id)) {
                        Toast.makeText(context, "存在引用该标的的事件，无法删除", Toast.LENGTH_SHORT).show()
                    } else {
                        onUpdateData { current ->
                            current.copy(instruments = current.instruments.filterNot { it.id == instrument.id })
                        }
                    }
                },
            )
        }

        item {
            SectionHeader(title = "类别与再平衡")
        }
        items(data.settings.categories.sortedBy { it.order }, key = { it.id }) { category ->
            CategoryRow(
                category = category,
                onEdit = { editingCategory = category },
            )
        }

        item {
            SectionHeader(title = "黄金")
        }
        item {
            SettingsCard("金价源") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = data.settings.gold.defaultSource,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showGoldSourceEditor = true }) {
                        Text("修改")
                    }
                }
            }
        }
        items(data.institutions, key = { "gold-${it.id}" }) { institution ->
            val premium = data.settings.gold.premiums
                .firstOrNull { it.institutionId == institution.id }
                ?.premiumCnyPerGram ?: BigDecimal.ZERO
            SettingsCard(institution.name) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "加价 ¥${premium.toPlainString()}/克",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { editingPremiumInstitution = institution }) {
                        Text("修改")
                    }
                }
            }
        }

        item {
            SettingsCard("手工编辑") {
                Text(
                    text = "可在 App 关闭时直接编辑 folio-prism.json；启动时会校验格式，错误会进入调试页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showInstitutionEditor) {
        InstitutionEditorDialog(
            initial = editingInstitution,
            onDismiss = { showInstitutionEditor = false },
            onSave = { institution ->
                onUpdateData { current ->
                    val exists = current.institutions.any { it.id == institution.id }
                    current.copy(
                        institutions = if (exists) {
                            current.institutions.map { if (it.id == institution.id) institution else it }
                        } else {
                            current.institutions + institution
                        },
                    )
                }
                showInstitutionEditor = false
            },
        )
    }

    if (showInstrumentEditor) {
        InstrumentEditorDialog(
            initial = editingInstrument,
            categories = data.settings.categories,
            onDismiss = { showInstrumentEditor = false },
            onSave = { instrument ->
                onUpdateData { current ->
                    val exists = current.instruments.any { it.id == instrument.id }
                    current.copy(
                        instruments = if (exists) {
                            current.instruments.map { if (it.id == instrument.id) instrument else it }
                        } else {
                            current.instruments + instrument
                        },
                    )
                }
                showInstrumentEditor = false
            },
        )
    }

    editingCategory?.let { category ->
        CategoryEditorDialog(
            initial = category,
            onDismiss = { editingCategory = null },
            onSave = { updated ->
                onUpdateData { current ->
                    current.copy(
                        settings = current.settings.copy(
                            categories = current.settings.categories.map {
                                if (it.id == updated.id) updated else it
                            },
                        ),
                    )
                }
                editingCategory = null
            },
        )
    }

    editingPremiumInstitution?.let { institution ->
        GoldPremiumDialog(
            institution = institution,
            initial = data.settings.gold.premiums
                .firstOrNull { it.institutionId == institution.id }
                ?.premiumCnyPerGram ?: BigDecimal.ZERO,
            onDismiss = { editingPremiumInstitution = null },
            onSave = { premium ->
                onUpdateData { current ->
                    val premiums = current.settings.gold.premiums
                        .filterNot { it.institutionId == institution.id } +
                        GoldPremium(institution.id, premium)
                    current.copy(
                        settings = current.settings.copy(
                            gold = current.settings.gold.copy(premiums = premiums),
                        ),
                    )
                }
                editingPremiumInstitution = null
            },
        )
    }

    if (showGoldSourceEditor) {
        GoldSourceDialog(
            initial = data.settings.gold.defaultSource,
            onDismiss = { showGoldSourceEditor = false },
            onSave = { source ->
                onUpdateData { current ->
                    current.copy(
                        settings = current.settings.copy(
                            gold = current.settings.gold.copy(defaultSource = source),
                        ),
                    )
                }
                showGoldSourceEditor = false
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    addLabel: String? = null,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (onAdd != null && addLabel != null) {
            TextButton(onClick = onAdd) {
                Text(addLabel)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun InstitutionRow(
    institution: Institution,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsCard(institution.name) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = institution.type.label() +
                    (institution.chartAlias?.let { " · 图表别名 $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun InstrumentRow(
    instrument: Instrument,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsCard("${instrument.code} ${instrument.name}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$categoryName · ${instrument.currency} · ${instrument.kind.label()} · ${instrument.quote.mode.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
) {
    SettingsCard(category.name + if (category.specialRule == "dividend") "（红利口径）" else "") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "目标 ${formatPercent(fraction(category.targetPct))}" +
                    " · 区间 ${formatPercent(fraction(category.minPct))}" +
                    " – ${formatPercent(fraction(category.maxPct))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
        }
    }
}

@Composable
private fun InstitutionEditorDialog(
    initial: Institution?,
    onDismiss: () -> Unit,
    onSave: (Institution) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: InstitutionType.BANK) }
    var chartAlias by remember(initial?.id) { mutableStateOf(initial?.chartAlias ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增机构" else "编辑机构") },
        text = {
            DialogColumn {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipRow(
                    options = listOf(
                        InstitutionType.BANK to "银行",
                        InstitutionType.BROKER to "券商",
                        InstitutionType.OTHER to "其他",
                    ),
                    selected = type,
                    onSelected = { type = it },
                )
                OutlinedTextField(
                    value = chartAlias,
                    onValueChange = { chartAlias = it },
                    label = { Text("图表显示别名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "请输入名称"
                } else {
                    onSave(
                        Institution(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            chartAlias = chartAlias.takeIf { it.isNotBlank() },
                            notes = notes,
                        ),
                    )
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InstrumentEditorDialog(
    initial: Instrument?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Instrument) -> Unit,
) {
    var categoryId by remember(initial?.id) {
        mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: "")
    }
    var code by remember(initial?.id) { mutableStateOf(initial?.code ?: "") }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var currency by remember(initial?.id) { mutableStateOf(initial?.currency ?: "CNY") }
    var unit by remember(initial?.id) { mutableStateOf(initial?.unit ?: "份") }
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: InstrumentKind.QUOTED) }
    var quoteMode by remember(initial?.id) { mutableStateOf(initial?.quote?.mode ?: QuoteMode.AUTO) }
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
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增标的" else "编辑标的") },
        text = {
            DialogColumn {
                Text("类别", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = categories.sortedBy { it.order }.map { it.id to it.name },
                    selected = categoryId,
                    onSelected = { categoryId = it },
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("代码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("币种", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = listOf("CNY" to "CNY", "USD" to "USD", "HKD" to "HKD", "CNH" to "CNH"),
                    selected = currency,
                    onSelected = { currency = it },
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("单位（份/股/克/元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("标的类型", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = listOf(
                        InstrumentKind.QUOTED to "有净值",
                        InstrumentKind.CASH to "现金",
                    ),
                    selected = kind,
                    onSelected = { kind = it },
                )
                Text("行情方式", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = listOf(
                        QuoteMode.AUTO to "自动",
                        QuoteMode.MANUAL to "手工价格",
                        QuoteMode.NONE to "无行情",
                    ),
                    selected = quoteMode,
                    onSelected = { quoteMode = it },
                )
                if (quoteMode == QuoteMode.MANUAL) {
                    OutlinedTextField(
                        value = manualPrice,
                        onValueChange = { manualPrice = it; error = null },
                        label = { Text("手工价格") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceDecimals,
                        onValueChange = { priceDecimals = it },
                        label = { Text("价格小数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = quantityDecimals,
                        onValueChange = { quantityDecimals = it },
                        label = { Text("数量小数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("币种暴露（分布图）", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = listOf(
                        "auto" to "自动",
                        "CNY" to "CNY",
                        "USD" to "USD",
                        "HKD" to "HKD",
                        "PHYSICAL_GOLD" to "实物黄金",
                    ),
                    selected = exposure,
                    onSelected = { exposure = it },
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = manualPrice.toBigDecimalOrNull()
                if (code.isBlank() || name.isBlank() || categoryId.isBlank()) {
                    error = "代码、名称、类别不能为空"
                } else if (quoteMode == QuoteMode.MANUAL && (price == null || price.signum() <= 0)) {
                    error = "手工价格必须大于 0"
                } else {
                    onSave(
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
                            notes = initial?.notes ?: "",
                        ),
                    )
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CategoryEditorDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var target by remember(initial.id) { mutableStateOf(initial.targetPct.toPlainString()) }
    var min by remember(initial.id) { mutableStateOf(initial.minPct.toPlainString()) }
    var max by remember(initial.id) { mutableStateOf(initial.maxPct.toPlainString()) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑类别：${initial.name}") },
        text = {
            DialogColumn {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it; error = null },
                    label = { Text("目标 %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it; error = null },
                        label = { Text("最低 %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = { max = it; error = null },
                        label = { Text("最高 %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val targetValue = target.toBigDecimalOrNull()
                val minValue = min.toBigDecimalOrNull()
                val maxValue = max.toBigDecimalOrNull()
                if (name.isBlank()) {
                    error = "名称不能为空"
                } else if (targetValue == null || minValue == null || maxValue == null) {
                    error = "请输入有效的百分比"
                } else if (minValue > maxValue) {
                    error = "最低不能大于最高"
                } else {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            targetPct = targetValue,
                            minPct = minValue,
                            maxPct = maxValue,
                        ),
                    )
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun GoldSourceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var source by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金价源") },
        text = {
            Column {
                ChipRow(
                    options = listOf("XAU" to "国际金价 XAU", "AU9999" to "上海金 AU9999"),
                    selected = source,
                    onSelected = { source = it },
                )
                Text(
                    text = "只使用选中的源；失败时按启动门禁处理，不自动切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(source) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun GoldPremiumDialog(
    institution: Institution,
    initial: BigDecimal,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
) {
    var value by remember(institution.id) { mutableStateOf(initial.toPlainString()) }
    var error by remember(institution.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${institution.name} 黄金加价") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("人民币/克加价") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "可以为负数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = value.toBigDecimalOrNull()
                if (parsed == null) error = "请输入数字" else onSave(parsed)
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DialogColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            FolioFilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = label,
            )
        }
    }
}

private fun fraction(percent: BigDecimal): BigDecimal =
    percent.divide(BigDecimal(100), 6, java.math.RoundingMode.HALF_UP)

private fun InstitutionType.label(): String = when (this) {
    InstitutionType.BANK -> "银行"
    InstitutionType.BROKER -> "券商"
    InstitutionType.OTHER -> "其他"
}

private fun InstrumentKind.label(): String = when (this) {
    InstrumentKind.CASH -> "现金"
    InstrumentKind.QUOTED -> "有净值"
}

private fun QuoteMode.label(): String = when (this) {
    QuoteMode.AUTO -> "自动"
    QuoteMode.MANUAL -> "手工"
    QuoteMode.NONE -> "无行情"
}
