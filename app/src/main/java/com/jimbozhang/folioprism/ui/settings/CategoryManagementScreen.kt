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
import androidx.compose.material3.Surface
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
import com.jimbozhang.folioprism.domain.model.CategoryNames
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.SATELLITE_RULE
import com.jimbozhang.folioprism.ui.components.FormDivider
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineNumberField
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
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
    val strategyCategories = categories.filterNot { it.specialRule == SATELLITE_RULE }
    val satelliteCategories = categories.filter { it.specialRule == SATELLITE_RULE }

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
                        count = strategyCategories.size,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ManagementListCard {
                        strategyCategories.forEachIndexed { index, category ->
                            if (index > 0) {
                                HorizontalDivider(color = FolioDivider)
                            }
                            ManagementRow(
                                icon = categoryIcon(category.id),
                                accent = categoryColor(category.color),
                                title = CategoryNames.builtIn(category),
                                subtitle = "目标 ${pct(category.targetPct)}" +
                                    " · 区间 ${pct(category.minPct)} – ${pct(category.maxPct)}",
                                onClick = { editing = category },
                            )
                        }
                    }
                    if (satelliteCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ManagementGroupHeader(
                            title = "卫星资产",
                            count = satelliteCategories.size,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ManagementListCard {
                            satelliteCategories.forEachIndexed { index, category ->
                                if (index > 0) {
                                    HorizontalDivider(color = FolioDivider)
                                }
                                ManagementRow(
                                    icon = categoryIcon(category.id),
                                    accent = categoryColor(category.color),
                                    title = CategoryNames.builtIn(category),
                                    subtitle = "计入总资产 · 不参与再平衡 · 名称内置",
                                    onClick = null,
                                )
                            }
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
                targetValue == null || minValue == null || maxValue == null ->
                    "请输入有效的百分比"
                minValue > maxValue -> "最低不能大于最高"
                else -> onSave(
                    initial.copy(
                        targetPct = targetValue,
                        minPct = minValue,
                        maxPct = maxValue,
                    ),
                )
            }
        },
        onDelete = null,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "类别名称",
                    style = MaterialTheme.typography.labelMedium,
                    color = FolioMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CategoryNames.builtIn(initial),
                    style = MaterialTheme.typography.headlineSmall,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "名称内置，不可修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
            }
        }
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
