package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.Instrument

@Composable
fun PositionPicker(
    label: String,
    institutions: List<Institution>,
    instruments: List<Instrument>,
    institutionId: String?,
    instrumentId: String?,
    onInstitutionSelected: (String) -> Unit,
    onInstrumentSelected: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownSelector(
                text = institutions.firstOrNull { it.id == institutionId }?.name ?: "选择机构",
                options = institutions.map { it.id to it.name },
                onSelected = onInstitutionSelected,
                modifier = Modifier.weight(1f),
            )
            DropdownSelector(
                text = instruments.firstOrNull { it.id == instrumentId }
                    ?.let { "${it.code} ${it.name}" } ?: "选择标的",
                options = instruments.map { it.id to "${it.code} ${it.name}" },
                onSelected = onInstrumentSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun DropdownSelector(
    text: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("无可用选项") },
                    onClick = { expanded = false },
                )
            } else {
                options.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
