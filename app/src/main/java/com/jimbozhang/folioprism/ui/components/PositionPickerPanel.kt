package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted

/**
 * Bottom picker for a position (institution x instrument), modelled after the
 * account panel in Sui's record editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionPickerPanel(
    title: String,
    institutions: List<Institution>,
    instruments: List<Instrument>,
    categoryNames: Map<String, String>,
    selectedInstitutionId: String?,
    selectedInstrumentId: String?,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var institutionId by remember(selectedInstitutionId, institutions) {
        mutableStateOf(
            selectedInstitutionId
                ?: institutions.firstOrNull()?.id,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = Color.White,
        shadowElevation = 24.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FolioDivider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FolioInk,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = FolioMuted,
                    )
                }
            }

            if (institutions.isEmpty()) {
                PickerHint("暂无机构")
                return@Column
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                institutions.forEach { institution ->
                    FolioFilterChip(
                        selected = institutionId == institution.id,
                        onClick = { institutionId = institution.id },
                        label = institution.name,
                    )
                }
            }

            HorizontalDivider(color = FolioDivider)

            if (instruments.isEmpty()) {
                PickerHint("暂无标的")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(instruments, key = { it.id }) { instrument ->
                        val selected = institutionId == selectedInstitutionId &&
                            instrument.id == selectedInstrumentId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = institutionId != null) {
                                    val institution = institutionId ?: return@clickable
                                    onSelect(institution, instrument.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${instrument.code} ${instrument.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = FolioInk,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        append(categoryNames[instrument.categoryId] ?: instrument.categoryId)
                                        append(" · ")
                                        append(instrument.currency)
                                        append(" · ")
                                        append(instrument.unit)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FolioMuted,
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = FolioGold,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = FolioDivider,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FolioMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}
