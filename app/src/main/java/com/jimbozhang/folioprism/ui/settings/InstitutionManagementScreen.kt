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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EditNote
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
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.InstitutionType
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.eventsReferencingInstitution
import com.jimbozhang.folioprism.ui.components.FormDivider
import com.jimbozhang.folioprism.ui.components.FormRow
import com.jimbozhang.folioprism.ui.components.InlineDropdown
import com.jimbozhang.folioprism.ui.components.InlineTextField
import com.jimbozhang.folioprism.ui.theme.FolioBackground
import com.jimbozhang.folioprism.ui.theme.FolioBlue
import com.jimbozhang.folioprism.ui.theme.FolioCoral
import com.jimbozhang.folioprism.ui.theme.FolioDivider
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import com.jimbozhang.folioprism.ui.theme.FolioPurple
import java.util.UUID

@Composable
internal fun InstitutionManagementScreen(
    data: PortfolioData,
    onBack: () -> Unit,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Institution?>(null) }
    val groups = remember(data.institutions) {
        InstitutionType.entries
            .map { type -> type to data.institutions.filter { it.type == type } }
            .filter { it.second.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FolioBackground),
    ) {
        ManagementTopBar(
            title = "机构",
            onBack = onBack,
            onAdd = {
                editing = null
                showEditor = true
            },
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (data.institutions.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    ManagementListCard {
                        Text(
                            text = "暂无机构",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FolioMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                groups.forEach { (type, institutions) ->
                    item {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            ManagementGroupHeader(
                                title = type.label(),
                                count = institutions.size,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ManagementListCard {
                                institutions.forEachIndexed { index, institution ->
                                    if (index > 0) {
                                        HorizontalDivider(color = FolioDivider)
                                    }
                                    ManagementRow(
                                        icon = type.icon(),
                                        accent = type.accent(),
                                        title = institution.name,
                                        subtitle = institutionSubtitle(institution),
                                        onClick = {
                                            editing = institution
                                            showEditor = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        InstitutionFormDialog(
            initial = editing,
            onDismiss = {
                showEditor = false
                editing = null
            },
            onSave = { institution ->
                onUpdateData { current ->
                    val exists = current.institutions.any { it.id == institution.id }
                    current.copy(
                        institutions = if (exists) {
                            current.institutions.map {
                                if (it.id == institution.id) institution else it
                            }
                        } else {
                            current.institutions + institution
                        },
                    )
                }
                null
            },
            onDelete = editing?.let { institution ->
                {
                    if (data.eventsReferencingInstitution(institution.id)) {
                        "存在引用该机构的事件，无法删除"
                    } else {
                        onUpdateData { current ->
                            current.copy(
                                institutions = current.institutions
                                    .filterNot { it.id == institution.id },
                                settings = current.settings.copy(
                                    gold = current.settings.gold.copy(
                                        premiums = current.settings.gold.premiums
                                            .filterNot { it.institutionId == institution.id },
                                    ),
                                ),
                            )
                        }
                        null
                    }
                }
            },
        )
    }
}

@Composable
private fun InstitutionFormDialog(
    initial: Institution?,
    onDismiss: () -> Unit,
    onSave: (Institution) -> String?,
    onDelete: (() -> String?)?,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var type by remember(initial?.id) {
        mutableStateOf(initial?.type ?: InstitutionType.BANK)
    }
    var chartAlias by remember(initial?.id) {
        mutableStateOf(initial?.chartAlias ?: "")
    }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }

    ManagementFormDialog(
        title = if (initial == null) "新增机构" else "编辑机构",
        editing = initial != null,
        deleteTitle = "删除该机构？",
        onDismiss = onDismiss,
        onSave = {
            if (name.isBlank()) {
                "请输入机构名称"
            } else {
                onSave(
                    Institution(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        type = type,
                        chartAlias = chartAlias.takeIf { it.isNotBlank() },
                        notes = notes.trim(),
                    ),
                )
            }
        },
        onDelete = onDelete,
    ) {
        ManagementNameInput(
            label = "机构名称",
            value = name,
            onValueChange = { name = it },
            placeholder = "请输入机构名称",
        )
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.Category,
                iconTint = FolioGold,
                label = "类型",
            ) {
                InlineDropdown(
                    selectedId = type.name,
                    options = listOf(
                        "BANK" to "银行",
                        "BROKER" to "券商",
                        "OTHER" to "其他",
                    ),
                    onSelected = { type = InstitutionType.valueOf(it) },
                )
            }
            FormDivider()
            FormRow(
                icon = Icons.Outlined.EditNote,
                iconTint = FolioBlue,
                label = "别名",
            ) {
                InlineTextField(
                    value = chartAlias,
                    onValueChange = { chartAlias = it },
                    placeholder = "图表显示别名（可选）",
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ManagementFormSection {
            FormRow(
                icon = Icons.Outlined.BookmarkBorder,
                iconTint = FolioPurple,
                label = "备注",
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                InlineTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "...",
                    singleLine = false,
                    maxLines = 4,
                )
            }
        }
    }
}

private fun institutionSubtitle(institution: Institution): String {
    val parts = mutableListOf(institution.type.label())
    institution.chartAlias?.takeIf { it.isNotBlank() }?.let { parts += "图表 $it" }
    if (institution.notes.isNotBlank()) parts += institution.notes
    return parts.joinToString(" · ")
}

private fun InstitutionType.label(): String = when (this) {
    InstitutionType.BANK -> "银行"
    InstitutionType.BROKER -> "券商"
    InstitutionType.OTHER -> "其他"
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
