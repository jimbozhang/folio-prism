package com.jimbozhang.folioprism.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioInk
import com.jimbozhang.folioprism.ui.theme.FolioMuted

@Composable
internal fun ManagementTopBar(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FolioInk,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = FolioInk,
                modifier = Modifier.weight(1f),
            )
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新增",
                        tint = FolioGold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ManagementGroupHeader(
    title: String,
    count: Int? = null,
    dotColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = FolioMuted,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = "$count 个",
                style = MaterialTheme.typography.labelSmall,
                color = FolioMuted,
            )
        }
    }
}

@Composable
internal fun ManagementListCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun ManagementRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = FolioInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = FolioMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ManagementLinkRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    trailing: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = FolioInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall,
            color = FolioMuted,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = FolioMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ManagementNameInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = FolioMuted,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                    color = FolioInk,
                ),
                cursorBrush = SolidColor(FolioGold),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = 28.sp,
                                    lineHeight = 34.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FolioMuted.copy(alpha = 0.45f),
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = FolioDivider)
        }
    }
}

@Composable
internal fun ManagementAmountInput(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
    allowNegative: Boolean = false,
    secondary: String? = null,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
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
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(sanitizeDecimalInput(it, allowNegative)) },
                textStyle = TextStyle(
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Medium,
                    color = FolioInk,
                ),
                cursorBrush = SolidColor(FolioGold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "0.00",
                                style = TextStyle(
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FolioMuted.copy(alpha = 0.45f),
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = FolioDivider)
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
}

@Composable
internal fun ManagementFormSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun ManagementFormDialog(
    title: String,
    editing: Boolean,
    deleteTitle: String,
    onDismiss: () -> Unit,
    onSave: () -> String?,
    onDelete: (() -> String?)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val save: () -> Unit = {
        val message = onSave()
        if (message == null) onDismiss() else error = message
    }
    val delete = onDelete

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = FolioBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .background(FolioBackground),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = FolioBackground,
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        Surface(color = Color.White) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回",
                                        tint = FolioInk,
                                    )
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FolioInk,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (editing && delete != null) {
                                    IconButton(onClick = { showDeleteConfirm = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "删除",
                                            tint = FolioMuted,
                                        )
                                    }
                                }
                                TextButton(onClick = save) {
                                    Text("保存", color = FolioGold)
                                }
                            }
                        }
                    },
                    bottomBar = {
                        Surface(color = Color.White, shadowElevation = 8.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 10.dp,
                                        bottom = 50.dp,
                                    ),
                            ) {
                                error?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FolioCoral,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Button(
                                    onClick = save,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FolioGold,
                                        contentColor = Color(0xFF2B1D0E),
                                    ),
                                ) {
                                    Text("保存", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        content()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && delete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(deleteTitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val message = delete()
                        if (message == null) onDismiss() else error = message
                    },
                ) {
                    Text("删除", color = FolioCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

internal fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    CategoryIds.DIVIDEND -> Icons.Outlined.Savings
    CategoryIds.GROWTH -> Icons.AutoMirrored.Outlined.TrendingUp
    CategoryIds.US_EQUITY -> Icons.Outlined.Public
    CategoryIds.US_BOND -> Icons.Outlined.AccountBalance
    CategoryIds.CN_BOND -> Icons.Outlined.Payments
    CategoryIds.GOLD -> Icons.Outlined.Diamond
    CategoryIds.CASH -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.AutoMirrored.Outlined.ShowChart
}

internal fun categoryColor(hex: String?): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: FolioGold

private fun sanitizeDecimalInput(input: String, allowNegative: Boolean): String {
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
