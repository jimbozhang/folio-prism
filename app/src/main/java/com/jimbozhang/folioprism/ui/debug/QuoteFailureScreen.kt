package com.jimbozhang.folioprism.ui.debug

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteFailure
import java.math.BigDecimal

@Composable
fun QuoteFailureScreen(
    data: PortfolioData,
    failures: List<QuoteFailure>,
    logs: List<String>,
    onRefresh: () -> Unit,
    onSaveLog: ((Result<String>) -> Unit) -> Unit,
    onSetManualPrice: (String, BigDecimal) -> Unit,
) {
    val context = LocalContext.current
    val institutionNames = remember(data) {
        data.institutions.associate { it.id to it.name }
    }
    var manualTarget by remember { mutableStateOf<QuoteFailure?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            text = "行情获取失败",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "任一必需价格获取失败时不会进入主界面。请检查网络后刷新，" +
                "或为失败标的设置手工价格；汇率失败暂时只能重试。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(failures) { failure ->
                FailureCard(
                    failure = failure,
                    institutionNames = institutionNames,
                    onManualPrice = { manualTarget = failure },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("日志", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = logs.takeLast(200).joinToString("\n").ifBlank { "（无日志）" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                Text("刷新")
            }
            OutlinedButton(
                onClick = {
                    onSaveLog { result ->
                        result.onSuccess {
                            Toast.makeText(context, "日志已保存：$it", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("保存日志")
            }
        }
    }

    manualTarget?.let { target ->
        ManualPriceDialog(
            failure = target,
            onDismiss = { manualTarget = null },
            onConfirm = { price ->
                target.instrumentId?.let { onSetManualPrice(it, price) }
                manualTarget = null
            },
        )
    }
}

@Composable
private fun FailureCard(
    failure: QuoteFailure,
    institutionNames: Map<String, String>,
    onManualPrice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = failure.instrumentCode ?: failure.instrumentName ?: "汇率",
                style = MaterialTheme.typography.titleSmall,
            )
            failure.instrumentName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failure.institutionIds.isNotEmpty()) {
                Text(
                    text = "机构：" + failure.institutionIds.joinToString("、") {
                        institutionNames[it] ?: it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "类型：${failure.kind.name} · 来源：${failure.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = failure.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (failure.instrumentId != null) {
                TextButton(onClick = onManualPrice) {
                    Text("设置手工价格并重试")
                }
            }
        }
    }
}

@Composable
private fun ManualPriceDialog(
    failure: QuoteFailure,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置手工价格") },
        text = {
            Column {
                Text(
                    text = "${failure.instrumentCode ?: ""} ${failure.instrumentName ?: ""}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text("价格") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    singleLine = true,
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = text.toBigDecimalOrNull()
                    if (price == null || price.signum() <= 0) {
                        error = "请输入大于 0 的数字"
                    } else {
                        onConfirm(price)
                    }
                },
            ) {
                Text("保存并重试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
