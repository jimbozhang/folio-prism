package com.jimbozhang.folioprism.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.ui.components.FormDivider
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineNumberField
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun CategoryManagementScreen(
    data: PortfolioData,
    onBack: () -> Unit,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    val categories = remember(data.settings.categories) {
        data.settings.categories.sortedBy { it.order }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ManagementTopBar(title = "类别与再平衡", onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    ManagementGroupHeader(
                        title = "再平衡目标",
                        count = categories.size,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ManagementListCard {
                        categories.forEachIndexed { index, category ->
                            if (index > 0) {
                                HorizontalDivider(color = FolioDivider)
                            }
                            ManagementRow(
                                icon = categoryIcon(category.id),
                                accent = categoryColor(category.color),
                                title = category.name,
                                subtitle = "目标 ${pct(category.targetPct)}" +
                                    " · 区间 ${pct(category.minPct)} – ${pct(category.maxPct)}",
                                onClick = { editing = category },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { category ->
        CategoryFormDialog(
            initial = category,
            onDismiss = { editing = null },
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
                null
            },
        )
    }
}

@Composable
private fun CategoryFormDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> String?,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var target by remember(initial.id) { mutableStateOf(initial.targetPct.toPlainString()) }
    var min by remember(initial.id) { mutableStateOf(initial.minPct.toPlainString()) }
    var max by remember(initial.id) { mutableStateOf(initial.maxPct.toPlainString()) }

    ManagementFormDialog(
        title = "编辑类别",
        editing = false,
        deleteTitle = "",
        onDismiss = onDismiss,
        onSave = {
            val targetValue = target.toBigDecimalOrNull()
            val minValue = min.toBigDecimalOrNull()
            val maxValue = max.toBigDecimalOrNull()
            when {
                name.isBlank() -> "请输入类别名称"
                targetValue == null || minValue == null || maxValue == null ->
                    "请输入有效的百分比"
                minValue > maxValue -> "最低不能大于最高"
                else -> onSave(
                    initial.copy(
                        name = name.trim(),
                        targetPct = targetValue,
                        minPct = minValue,
                        maxPct = maxValue,
                    ),
                )
            }
        },
        onDelete = null,
    ) {
        ManagementNameInput(
            label = "类别名称",
            value = name,
            onValueChange = { name = it },
            placeholder = "请输入类别名称",
        )
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioGold,
                label = "目标 %",
            ) {
                PercentRowValue(target) { target = it }
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioGold,
                label = "最低 %",
            ) {
                PercentRowValue(min) { min = it }
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.Tune,
                iconTint = FolioGold,
                label = "最高 %",
            ) {
                PercentRowValue(max) { max = it }
            }
        }
    }
}

@Composable
private fun PercentRowValue(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineNumberField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = "%",
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun pct(value: BigDecimal): String =
    value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"
