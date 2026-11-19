package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGoldSoft
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.math.BigDecimal

/**
 * Bottom picker for a position (institution x instrument), modelled after the
 * account panel in Sui's record editor. Institutions are a scrollable list on
 * the left, instruments on the right.
 */
@Composable
fun PositionPickerPanel(
    title: String,
    institutions: List<Institution>,
    instruments: List<Instrument>,
    categoryNames: Map<String, String>,
    selectedInstitutionId: String?,
    selectedInstrumentId: String?,
    quantities: Map<PositionKey, BigDecimal> = emptyMap(),
    values: Map<PositionKey, BigDecimal?> = emptyMap(),
    heldOnlyFilter: Boolean = false,
    eligibleInstrumentIds: Set<String>? = null,
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
    var showAll by remember(heldOnlyFilter, eligibleInstrumentIds) { mutableStateOf(false) }
    val instrumentListState = rememberLazyListState()
    LaunchedEffect(institutionId) {
        instrumentListState.scrollToItem(0)
    }

    // When an asset filter is active, institutions without any matching
    // holding are hidden from the left column.
    val visibleInstitutions = if (eligibleInstrumentIds != null && !showAll) {
        institutionsHoldingAny(institutions, eligibleInstrumentIds, quantities)
    } else {
        institutions
    }
    LaunchedEffect(visibleInstitutions.map { it.id }) {
        if (visibleInstitutions.none { it.id == institutionId }) {
            institutionId = visibleInstitutions.firstOrNull()?.id
        }
    }

    val visibleInstruments = when {
        eligibleInstrumentIds != null && !showAll ->
            instruments.filter { it.id in eligibleInstrumentIds }

        heldOnlyFilter && !showAll -> instruments.filter { instrument ->
            val quantity = quantities[PositionKey(institutionId ?: "", instrument.id)]
            quantity != null && quantity.signum() != 0
        }

        else -> instruments
    }
    val sortedInstruments = visibleInstruments.sortedWith(
        compareByDescending<Instrument> {
            values[PositionKey(institutionId ?: "", it.id)] ?: BigDecimal.valueOf(Long.MIN_VALUE)
        }.thenBy { it.code },
    )

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
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .width(132.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFF7F8F7)),
                ) {
                    items(visibleInstitutions, key = { it.id }) { institution ->
                        InstitutionRow(
                            name = institution.name,
                            selected = institution.id == institutionId,
                            onClick = { institutionId = institution.id },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(FolioDivider),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    if (heldOnlyFilter || eligibleInstrumentIds != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when {
                                    eligibleInstrumentIds != null && showAll -> "显示全部资产"
                                    eligibleInstrumentIds != null -> "仅显示可用资产"
                                    showAll -> "显示全部标的"
                                    else -> "仅显示持仓"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = FolioMuted,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(
                                    text = if (showAll) {
                                        if (eligibleInstrumentIds != null) {
                                            "仅显示可用"
                                        } else {
                                            "仅显示持仓"
                                        }
                                    } else {
                                        "显示全部"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FolioGold,
                                )
                            }
                        }
                        HorizontalDivider(color = FolioDivider)
                    }

                    if (visibleInstitutions.isEmpty()) {
                        PickerHint("没有包含可用资产的机构，可显示全部资产")
                    } else if (sortedInstruments.isEmpty()) {
                        PickerHint(
                            when {
                                eligibleInstrumentIds != null && !showAll ->
                                    "当前机构暂无可用资产"
                                heldOnlyFilter && !showAll ->
                                    "当前机构暂无持仓，可显示全部标的"
                                else -> "暂无标的"
                            },
                        )
                    } else {
                        LazyColumn(
                            state = instrumentListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(sortedInstruments, key = { it.id }) { instrument ->
                                val key = PositionKey(institutionId ?: "", instrument.id)
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
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                                append(
                                                    categoryNames[instrument.categoryId]
                                                        ?: instrument.categoryId,
                                                )
                                                append(" · ")
                                                append(instrument.currency)
                                                append(" · ")
                                                append(instrument.unit)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FolioMuted,
                                        )
                                        val quantity = quantities[key]
                                        if (quantity != null && quantity.signum() != 0) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "持有 " + formatQuantity(quantity) + " " +
                                                    instrument.unit +
                                                    (values[key]?.let { " · 约 " + formatCny(it) } ?: ""),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FolioMuted,
                                            )
                                        }
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
                                    modifier = Modifier.padding(start = 14.dp),
                                    color = FolioDivider,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstitutionRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) FolioGoldSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) FolioGold else Color.Transparent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) FolioInk else FolioMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun institutionsHoldingAny(
    institutions: List<Institution>,
    instrumentIds: Set<String>,
    quantities: Map<PositionKey, BigDecimal>,
): List<Institution> = institutions.filter { institution ->
    quantities.any { (key, quantity) ->
        key.institutionId == institution.id &&
            key.instrumentId in instrumentIds &&
            quantity.signum() != 0
    }
}

@Composable
private fun PickerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FolioMuted,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 24.dp),
    )
}
