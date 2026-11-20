package com.jimbozhang.folioprism.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGoldSoft
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import kotlinx.coroutines.launch

/**
 * Bottom-sheet filter for the transaction list: one roomy section per
 * dimension, so adding dimensions never squeezes the header. Changes apply
 * live; the footer button reports the result count and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionFilterSheet(
    data: PortfolioData,
    filter: TransactionFilter,
    resultCount: Int,
    onFilterChange: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // A tall filter must stop below the status bar so its rounded top and drag
    // handle stay visible; keep the list inside a scroll area below that.
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    // 72dp covers the sheet's drag handle plus breathing room under the bar.
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp -
        with(density) { statusBarTop.toDp() } - 72.dp
    val institutions = remember(data.institutions) {
        data.institutions.map { it.id to it.name }
    }
    val instruments = remember(data.instruments, data.settings.categories) {
        val categoryOrder = data.settings.categories.associate { it.id to it.order }
        data.instruments
            .sortedWith(
                compareBy(
                    { categoryOrder[it.categoryId] ?: Int.MAX_VALUE },
                    { it.code },
                ),
            )
            .map { it.id to "${it.code} ${it.name}" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = FolioDivider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onFilterChange(TransactionFilter()) },
                    enabled = !filter.isEmpty,
                ) {
                    Text(
                        text = "重置",
                        color = if (filter.isEmpty) FolioMuted else FolioGold,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                FilterChoiceSection(
                    title = "类型",
                    options = eventTypeFilterOptions,
                    selectedId = filter.type,
                    onSelected = { onFilterChange(filter.copy(type = it)) },
                )
                FilterChoiceSection(
                    title = "机构",
                    options = institutions,
                    selectedId = filter.institutionId,
                    onSelected = { onFilterChange(filter.copy(institutionId = it)) },
                )
                FilterChoiceSection(
                    title = "标的",
                    options = instruments,
                    selectedId = filter.instrumentId,
                    onSelected = { onFilterChange(filter.copy(instrumentId = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            HorizontalDivider(color = FolioDivider)
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FolioGold,
                    contentColor = Color(0xFF2B1D0E),
                ),
            ) {
                Text(
                    text = "查看 $resultCount 笔",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChoiceSection(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = FolioMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChoiceChip(
                label = "全部",
                selected = selectedId == null,
                onClick = { onSelected(null) },
            )
            options.forEach { (id, label) ->
                FilterChoiceChip(
                    label = label,
                    selected = selectedId == id,
                    onClick = { onSelected(id) },
                )
            }
        }
    }
}

@Composable
private fun FilterChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) FolioGoldSoft else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) FolioGold else FolioInk,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
