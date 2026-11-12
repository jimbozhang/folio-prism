package com.jimbozhang.folioprism.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.model.GoldPremium
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.InstitutionType
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineDropdown
import com.jimbozhang.folioprism.ui.components.formatCny
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.math.BigDecimal

@Composable
internal fun GoldManagementScreen(
    data: PortfolioData,
    onBack: () -> Unit,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
) {
    var showSourceEditor by remember { mutableStateOf(false) }
    var editingPremiumInstitution by remember { mutableStateOf<Institution?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ManagementTopBar(title = "黄金价格修正", onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    ManagementListCard {
                        ManagementRow(
                            icon = Icons.Outlined.Diamond,
                            accent = FolioGold,
                            title = "金价源",
                            subtitle = sourceLabel(data.settings.gold.defaultSource),
                            onClick = { showSourceEditor = true },
                        )
                    }
                }
            }
            item {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    ManagementGroupHeader(
                        title = "机构加价",
                        count = data.institutions.size,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ManagementListCard {
                        if (data.institutions.isEmpty()) {
                            Text(
                                text = "暂无机构",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FolioMuted,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            data.institutions.forEachIndexed { index, institution ->
                                if (index > 0) {
                                    HorizontalDivider(color = FolioDivider)
                                }
                                ManagementRow(
                                    icon = institution.type.icon(),
                                    accent = institution.type.accent(),
                                    title = institution.name,
                                    subtitle = "加价 ${formatCny(premiumOf(data, institution.id))}/克",
                                    onClick = { editingPremiumInstitution = institution },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourceEditor) {
        GoldSourceFormDialog(
            initial = data.settings.gold.defaultSource,
            onDismiss = { showSourceEditor = false },
            onSave = { source ->
                onUpdateData { current ->
                    current.copy(
                        settings = current.settings.copy(
                            gold = current.settings.gold.copy(defaultSource = source),
                        ),
                    )
                }
                null
            },
        )
    }

    editingPremiumInstitution?.let { institution ->
        GoldPremiumFormDialog(
            institution = institution,
            initial = premiumOf(data, institution.id),
            onDismiss = { editingPremiumInstitution = null },
            onSave = { premium ->
                onUpdateData { current ->
                    val premiums = current.settings.gold.premiums
                        .filterNot { it.institutionId == institution.id } +
                        GoldPremium(institution.id, premium)
                    current.copy(
                        settings = current.settings.copy(
                            gold = current.settings.gold.copy(premiums = premiums),
                        ),
                    )
                }
                null
            },
        )
    }
}

@Composable
private fun GoldSourceFormDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> String?,
) {
    var source by remember(initial) { mutableStateOf(initial) }

    ManagementFormDialog(
        title = "金价源",
        editing = false,
        deleteTitle = "",
        onDismiss = onDismiss,
        onSave = { onSave(source) },
        onDelete = null,
    ) {
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.Diamond,
                iconTint = FolioGold,
                label = "金价源",
            ) {
                InlineDropdown(
                    selectedId = source,
                    options = listOf(
                        "XAU" to "国际金价 XAU",
                        "AU9999" to "上海金 AU9999",
                    ),
                    onSelected = { source = it },
                )
            }
        }
    }
}

@Composable
private fun GoldPremiumFormDialog(
    institution: Institution,
    initial: BigDecimal,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> String?,
) {
    var premium by remember(institution.id) { mutableStateOf(initial.toPlainString()) }

    ManagementFormDialog(
        title = institution.name,
        editing = false,
        deleteTitle = "",
        onDismiss = onDismiss,
        onSave = {
            val parsed = premium.toBigDecimalOrNull()
            if (parsed == null) "请输入数字" else onSave(parsed)
        },
        onDelete = null,
    ) {
        ManagementAmountInput(
            label = "黄金加价",
            unit = "CNY/克",
            value = premium,
            onValueChange = { premium = it },
            allowNegative = true,
            secondary = "可为负",
        )
    }
}

private fun premiumOf(data: PortfolioData, institutionId: String): BigDecimal =
    data.settings.gold.premiums
        .firstOrNull { it.institutionId == institutionId }
        ?.premiumCnyPerGram
        ?: BigDecimal.ZERO

private fun sourceLabel(source: String): String = when (source.uppercase()) {
    "AU9999" -> "上海金 AU9999"
    else -> "国际金价 XAU"
}

private fun InstitutionType.icon(): ImageVector = when (this) {
    InstitutionType.BANK -> Icons.Outlined.AccountBalance
    InstitutionType.BROKER -> Icons.AutoMirrored.Outlined.ShowChart
    InstitutionType.OTHER -> Icons.Outlined.AccountBalanceWallet
}

private fun InstitutionType.accent(): Color = when (this) {
    InstitutionType.BANK -> FolioGold
    InstitutionType.BROKER -> FolioCoral
    InstitutionType.OTHER -> FolioPurple
}
