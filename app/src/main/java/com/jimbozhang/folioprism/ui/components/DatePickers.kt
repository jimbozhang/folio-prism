package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.history.RangePreset
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    val selectableDates = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                return (minDate == null || !date.isBefore(minDate)) &&
                    (maxDate == null || !date.isAfter(maxDate))
            }

            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) &&
                    (maxDate == null || year <= maxDate.year)
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    onDismiss()
                },
            ) {
                Text("确定", color = FolioGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = FolioMuted)
            }
        },
    ) {
        DatePicker(
            state = state,
            dateFormatter = remember { ChineseDatePickerFormatter() },
            headline = {
                val date = state.selectedDateMillis?.toUtcLocalDate()
                Text(
                    text = date?.let { "${it.year} 年 ${it.monthValue} 月 ${it.dayOfMonth} 日" }
                        ?: "选择日期",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            },
            showModeToggle = true,
        )
    }
}

/**
 * Material's Chinese date formatter omits "日" in some input-mode states. Keep
 * an explicit year/month/day rendering everywhere in the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class ChineseDatePickerFormatter : DatePickerFormatter {
    override fun formatMonthYear(utcTimeMillis: Long?, locale: Locale): String? {
        val date = utcTimeMillis?.toUtcLocalDate() ?: return null
        return "${date.year} 年 ${date.monthValue} 月"
    }

    override fun formatDate(
        utcTimeMillis: Long?,
        locale: Locale,
        forContentDescription: Boolean,
    ): String? {
        val date = utcTimeMillis?.toUtcLocalDate() ?: return null
        val base = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"
        if (!forContentDescription) return base
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
        return base + weekday
    }
}

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSelected: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择时间",
                    style = MaterialTheme.typography.titleLarge,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = FolioMuted)
                    }
                    TextButton(
                        onClick = {
                            onSelected(LocalTime.of(state.hour, state.minute))
                            onDismiss()
                        },
                    ) {
                        Text("确定", color = FolioGold)
                    }
                }
            }
        }
    }
}

/**
 * Arbitrary chart range picker: quick presets plus independent start/end date
 * pickers (the Material date picker also accepts typed input).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateRangePickerDialog(
    initial: DateRange,
    today: LocalDate,
    earliest: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
) {
    var from by remember { mutableStateOf(initial.from) }
    var to by remember { mutableStateOf(initial.to) }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    val activePreset = RangePreset.entries.firstOrNull { preset ->
        val range = DateRange.preset(preset, today, earliest)
        range.from == from && range.to == to
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择日期区间",
                    style = MaterialTheme.typography.titleLarge,
                    color = FolioInk,
                )
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RangePreset.entries.forEach { preset ->
                        val presetRange = DateRange.preset(preset, today, earliest)
                        FolioFilterChip(
                            selected = activePreset == preset,
                            onClick = {
                                from = presetRange.from
                                to = presetRange.to
                            },
                            label = preset.label,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                DateRangeRow(
                    label = "开始日期",
                    date = from,
                    onClick = { editingStart = true },
                )
                HorizontalDivider(color = FolioDivider)
                DateRangeRow(
                    label = "结束日期",
                    date = to,
                    onClick = { editingStart = false },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "共 ${DateRange(from, to).days} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = FolioMuted,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = FolioMuted)
                    }
                    Button(
                        onClick = {
                            val finalTo = if (to.isAfter(today)) today else to
                            val finalFrom = if (from.isAfter(finalTo)) finalTo else from
                            onConfirm(DateRange(finalFrom, finalTo))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FolioGold,
                            contentColor = Color(0xFF2B1D0E),
                        ),
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }

    val editingDate = editingStart
    if (editingDate != null) {
        SingleDatePickerDialog(
            initial = if (editingDate) from else to,
            minDate = today.minusYears(10),
            maxDate = today,
            onDismiss = { editingStart = null },
            onSelected = { picked ->
                if (editingDate) {
                    from = picked
                    if (to.isBefore(picked)) to = picked
                } else {
                    to = picked
                    if (from.isAfter(picked)) from = picked
                }
            },
        )
    }
}

@Composable
private fun DateRangeRow(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日",
            style = MaterialTheme.typography.bodyLarge,
            color = FolioInk,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = FolioMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
