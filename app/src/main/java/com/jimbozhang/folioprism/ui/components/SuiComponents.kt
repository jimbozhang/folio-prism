package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGoldSoft
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted

/**
 * Text-only tab row in the style of Sui's record editor: five visible tabs and
 * an optional overflow menu, so the row never needs horizontal scrolling.
 */
@Composable
fun SuiTabRow(
    tabs: List<Pair<String, String>>,
    overflow: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val overflowSelection = overflow.firstOrNull { it.first == selected }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (value, label) ->
            TabItem(
                label = label,
                selected = selected == value,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSelected(value) },
            )
        }
        if (overflow.isNotEmpty()) {
            Box {
                TabItem(
                    label = overflowSelection?.second ?: "",
                    selected = overflowSelection != null,
                    showChevron = true,
                    modifier = Modifier.widthIn(min = 40.dp),
                    enabled = enabled,
                    onClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    overflow.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (selected == value) FolioGold else FolioInk,
                                )
                            },
                            trailingIcon = {
                                if (selected == value) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = FolioGold)
                                }
                            },
                            onClick = {
                                onSelected(value)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) FolioGold else FolioMuted,
                    maxLines = 1,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "更多",
                    tint = if (selected) FolioGold else FolioMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (selected) FolioGold else Color.Transparent),
        )
    }
}

/** A white block of form rows, separated from the next block by a grey band. */
@Composable
fun FormBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
fun FormDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = FolioDivider,
    )
}

@Composable
fun FormRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioMuted,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = FolioMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun FormValue(
    text: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "未选择",
    color: Color = FolioInk,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    val blank = text.isNullOrBlank()
    Text(
        text = if (blank) placeholder else text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (blank) FolioMuted else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Inline dropdown used inside form rows; the menu lists the current value with a check. */
@Composable
fun InlineDropdown(
    selectedId: String?,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    placeholder: String = "未选择",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormValue(
                text = selectedLabel,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = FolioMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (id == selectedId) FolioGold else FolioInk,
                        )
                    },
                    trailingIcon = {
                        if (id == selectedId) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = FolioGold)
                        }
                    },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Compact pill dropdown used as a list filter in headers. */
@Composable
fun FilterDropdown(
    selectedId: String?,
    allId: String,
    options: List<Pair<String, String>>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selectedId }?.second ?: "全部类型"
    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = FolioGoldSoft,
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = null,
                    tint = FolioGold,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = FolioGold,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = FolioGold,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val items = listOf(allId to "全部类型") + options
            items.forEach { (id, itemLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel,
                            color = if (selectedId == id || (id == allId && selectedId == null)) {
                                FolioGold
                            } else {
                                FolioInk
                            },
                        )
                    },
                    trailingIcon = {
                        if (selectedId == id || (id == allId && selectedId == null)) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = FolioGold)
                        }
                    },
                    onClick = {
                        onSelected(if (id == allId) null else id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Large amount input matching Sui's editor: small label and unit above, big
 * number, and an underline that turns gold while focused.
 */
@Composable
fun AmountHero(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    placeholder: String = "0.00",
    allowNegative: Boolean = false,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val selectionColors = remember {
        TextSelectionColors(
            handleColor = FolioGold,
            backgroundColor = FolioGold.copy(alpha = 0.25f),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = FolioMuted,
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = FolioMuted,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(sanitizeDecimal(it, allowNegative)) },
                enabled = enabled,
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) FolioInk else FolioMuted,
                ),
                cursorBrush = SolidColor(FolioGold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = 36.sp,
                                    lineHeight = 42.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FolioDivider,
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            thickness = if (focused) 2.dp else 1.dp,
            color = if (focused) FolioGold else FolioDivider,
        )
        if (!secondary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = FolioMuted,
            )
        }
    }
}

/** Small right-aligned numeric field used for prices, FX rates and quantities. */
@Composable
fun InlineNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    allowNegative: Boolean = false,
) {
    val selectionColors = remember {
        TextSelectionColors(
            handleColor = FolioGold,
            backgroundColor = FolioGold.copy(alpha = 0.25f),
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(sanitizeDecimal(it, allowNegative)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = FolioInk,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(FolioGold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = FolioDivider,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** Inline text field used for notes and adjustment reasons. */
@Composable
fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 3,
) {
    val selectionColors = remember {
        TextSelectionColors(
            handleColor = FolioGold,
            backgroundColor = FolioGold.copy(alpha = 0.25f),
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = FolioInk),
            cursorBrush = SolidColor(FolioGold),
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = FolioDivider,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private fun sanitizeDecimal(input: String, allowNegative: Boolean): String {
    val builder = StringBuilder()
    var dotSeen = false
    input.forEachIndexed { index, char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
            char == '-' && allowNegative && index == 0 && builder.isEmpty() ->
                builder.append(char)
        }
    }
    return builder.toString()
}

@Composable
fun PositionValue(
    data: PortfolioData,
    institutionId: String?,
    instrumentId: String?,
) {
    val institutionName = data.institutions.firstOrNull { it.id == institutionId }?.name
    val instrument = instrumentId?.let { data.instrument(it) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = institutionName ?: "机构",
            style = MaterialTheme.typography.bodyLarge,
            color = if (institutionName == null) FolioMuted else FolioInk,
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

/** Text + switch row without the fixed label column, used for editor toggles. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FolioGold,
                checkedBorderColor = FolioGold,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE6E6E6),
                uncheckedBorderColor = Color(0xFFD6D6D6),
            ),
        )
    }
}

/** Left/right arrow button used by the chart range bar. */
@Composable
fun RangeArrowButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) FolioInk else FolioDivider
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
