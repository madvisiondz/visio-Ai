package com.oasismall.oasisai.ui.screens.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.ui.components.ImportantRayonsFilterNote
import com.oasismall.oasisai.ui.components.ImportChangeCard
import com.oasismall.oasisai.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit,
    onArticleClick: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val printItems = remember { mutableStateMapOf<Long, List<PrintBatchItemEntity>>() }

    LaunchedEffect(state.designPrints) {
        state.designPrints.forEach { row ->
            if (!printItems.containsKey(row.batch.id)) {
                printItems[row.batch.id] = viewModel.loadPrintItems(row.batch.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "CSV diffs from Gestium imports and shelf labels generated from Design.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ImportantRayonsFilterNote(
                    filtered = state.importantRayonsFiltered,
                    count = state.importantRayonsCount,
                )
            }

            item { ReportSectionHeader("Latest CSV import") }
            item {
                ImportSummaryCard(
                    title = "Current file",
                    subtitle = state.latestImportSummary,
                    date = state.latestImport?.importedAt,
                )
            }
            item {
                ImportSummaryCard(
                    title = "Previous file",
                    subtitle = state.previousImportSummary,
                    date = state.previousImport?.importedAt,
                    muted = true,
                )
            }

            item { ReportSectionHeader("CSV changes (${state.csvChanges.size})") }
            if (state.csvChanges.isEmpty()) {
                item {
                    Text(
                        "No CSV changes recorded yet — import a new Gestium file to compare.",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                val orderedTypes = state.csvChangesByType.keys.sortedBy(::changeTypeOrder)
                orderedTypes.forEach { type ->
                    val rows = state.csvChangesByType[type].orEmpty()
                    item { ChangeTypeChip(changeTypeLabel(type), rows.size) }
                    items(rows, key = { "csv_${it.change.id}" }) { row ->
                        ImportChangeCard(
                            row = row.uiRow,
                            metaLine = "${row.importFileName} · ${formatDate(row.importDate)}",
                            onArticleClick = onArticleClick,
                            onAddToShare = viewModel::addToShareCart,
                            onAddToShoot = viewModel::addToPhotoshootCart,
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            item { ReportSectionHeader("Printed from Design (${state.designPrints.size})") }
            if (state.designPrints.isEmpty()) {
                item {
                    Text(
                        "No Design shelf exports yet — open Design, pick Shelf labels, and generate A4 JPEG.",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.designPrints, key = { "print_${it.batch.id}" }) { row ->
                    DesignPrintCard(
                        row = row,
                        items = printItems[row.batch.id].orEmpty(),
                        onArticleClick = onArticleClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportSectionHeader(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ImportSummaryCard(
    title: String,
    subtitle: String,
    date: Long?,
    muted: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (muted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            date?.let {
                Text(formatDate(it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChangeTypeChip(label: String, count: Int) {
    Text(
        "$label ($count)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun DesignPrintCard(
    row: ReportDesignPrintRow,
    items: List<PrintBatchItemEntity>,
    onArticleClick: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.batch.templateName, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatDate(row.batch.createdAt)} · ${row.batch.itemCount} labels · ${row.batch.status}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (items.isNotEmpty()) {
                items.forEach { item ->
                    Text(
                        "• ${item.designationSnapshot} — ${PriceFormatter.format(item.priceSnapshot)} DA",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .then(
                                if (item.articleId != null) {
                                    Modifier.clickable { onArticleClick(item.articleId!!) }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            } else {
                Text("Loading articles…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
