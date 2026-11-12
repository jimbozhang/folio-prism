package com.jimbozhang.folioprism.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.platform.storage.StoragePaths
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGreen

private enum class SettingsPage {
    MAIN,
    INSTITUTIONS,
    INSTRUMENTS,
    CATEGORIES,
    GOLD,
}

@Composable
fun SettingsScreen(
    data: PortfolioData,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
    contentPadding: PaddingValues,
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = SettingsPage.MAIN
    }
    if (page == SettingsPage.INSTITUTIONS) {
        InstitutionManagementScreen(
            data = data,
            onBack = { page = SettingsPage.MAIN },
            onUpdateData = onUpdateData,
        )
        return
    }
    if (page == SettingsPage.INSTRUMENTS) {
        InstrumentManagementScreen(
            data = data,
            onBack = { page = SettingsPage.MAIN },
            onUpdateData = onUpdateData,
        )
        return
    }
    if (page == SettingsPage.CATEGORIES) {
        CategoryManagementScreen(
            data = data,
            onBack = { page = SettingsPage.MAIN },
            onUpdateData = onUpdateData,
        )
        return
    }
    if (page == SettingsPage.GOLD) {
        GoldManagementScreen(
            data = data,
            onBack = { page = SettingsPage.MAIN },
            onUpdateData = onUpdateData,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            SettingsCard("数据") {
                Text(
                    text = StoragePaths.dataDir().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "机构 ${data.institutions.size} · 标的 ${data.instruments.size} · 事件 ${data.events.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "备份保留 ${data.settings.backup.retainCount} 份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard("管理") {
                ManagementLinkRow(
                    icon = Icons.Outlined.AccountBalance,
                    accent = FolioGold,
                    title = "机构",
                    trailing = "${data.institutions.size} 个",
                    onClick = { page = SettingsPage.INSTITUTIONS },
                )
                HorizontalDivider(color = FolioDivider)
                ManagementLinkRow(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    accent = FolioBlue,
                    title = "标的",
                    trailing = "${data.instruments.size} 个",
                    onClick = { page = SettingsPage.INSTRUMENTS },
                )
                HorizontalDivider(color = FolioDivider)
                ManagementLinkRow(
                    icon = Icons.Outlined.PieChart,
                    accent = FolioGreen,
                    title = "类别与再平衡",
                    trailing = "${data.settings.categories.size} 个",
                    onClick = { page = SettingsPage.CATEGORIES },
                )
                HorizontalDivider(color = FolioDivider)
                ManagementLinkRow(
                    icon = Icons.Outlined.Diamond,
                    accent = FolioGold,
                    title = "黄金价格修正",
                    trailing = data.settings.gold.defaultSource,
                    onClick = { page = SettingsPage.GOLD },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}
