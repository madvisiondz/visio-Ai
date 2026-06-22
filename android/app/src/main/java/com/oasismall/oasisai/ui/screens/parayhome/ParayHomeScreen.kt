package com.oasismall.oasisai.ui.screens.parayhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayFusionHistoryEntry
import com.oasismall.oasisai.domain.paray.ParayFusionPreview
import com.oasismall.oasisai.domain.paray.ParayKnowledge
import com.oasismall.oasisai.ui.components.AgentNavIcon
import com.oasismall.oasisai.ui.components.paray.ParayPresenceLed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val ParayViolet = Color(0xFF5B2D8E)
private val ParayTeal = Color(0xFF1A7A7A)
private val ParayGlow = Color(0xFF9D6FD4)
private val ParayDark = Color(0xFF120A1C)
private val ParaySurface = Color(0xFF1E1229)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParayHomeScreen(
    viewModel: ParayHomeViewModel,
    onBackToOasis: () -> Unit,
    onOpenLearn: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenDesign: () -> Unit,
    onImportFingerprints: () -> Unit,
    onOpenSettings: () -> Unit,
    onExportKnowledge: () -> Unit,
    onImportKnowledge: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val fusion by viewModel.fusion.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ParayDark, ParayViolet.copy(alpha = 0.35f), ParayDark),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("PARAY", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                ui.manifest?.motto ?: ParayKnowledge.motto,
                                style = MaterialTheme.typography.labelSmall,
                                color = ParayGlow.copy(alpha = 0.9f),
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToOasis) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Oasis", tint = Color.White)
                        }
                    },
                    actions = {
                        ParayPresenceLed()
                        IconButton(onClick = viewModel::reload) {
                            Icon(Icons.Default.Refresh, "Refresh dashboard", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            if (ui.loading && ui.manifest == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ParayGlow)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { DashboardHero() }
                    item { SectionHeader(Icons.Default.Visibility, "Observer") }
                    item { ObserverSummaryCard(ui.observer) }
                    item { SectionHeader(Icons.Default.Psychology, "Knowledge") }
                    item { KnowledgeSummaryCard(ui.knowledge) }
                    item { SectionHeader(Icons.Default.AutoFixHigh, "Workflow") }
                    item { WorkflowSummaryCard(ui.workflow) }
                    item { SectionHeader(Icons.Default.Visibility, "Recognition curiosity") }
                    item { RecognitionSummaryCard(ui.recognition) }
                    item { SectionHeader(Icons.Default.Hub, "Knowledge Fusion") }
                    item {
                        KnowledgeFusionCard(
                            fusion = fusion,
                            onExport = onExportKnowledge,
                            onImport = onImportKnowledge,
                            onToggleHistory = viewModel::toggleFusionHistory,
                        )
                    }
                    item { SectionHeader(Icons.Default.Settings, "Quick actions") }
                    item {
                        QuickActionsCard(
                            onOpenLearn = {
                                viewModel.recordOfficeVisit("paray_learn")
                                onOpenLearn()
                            },
                            onOpenAgent = {
                                viewModel.recordOfficeVisit("agent")
                                onOpenAgent()
                            },
                            onOpenDesign = {
                                viewModel.recordOfficeVisit("design")
                                onOpenDesign()
                            },
                            onImportFingerprints = {
                                viewModel.recordOfficeVisit("import_fingerprints")
                                onImportFingerprints()
                            },
                            onOpenSettings = {
                                viewModel.recordOfficeVisit("settings")
                                onOpenSettings()
                            },
                        )
                    }
                }
            }
        }
    }

    fusion.preview?.let { preview ->
        FusionPreviewDialog(
            preview = preview,
            merging = fusion.merging,
            onMerge = viewModel::confirmMerge,
            onCancel = viewModel::cancelMerge,
        )
    }

    if (fusion.lastMessage != null && fusion.preview == null) {
        AlertDialog(
            onDismissRequest = viewModel::clearFusionMessage,
            title = { Text("Knowledge Fusion") },
            text = { Text(fusion.lastMessage ?: "") },
            confirmButton = {
                Button(onClick = viewModel::clearFusionMessage) { Text("OK") }
            },
        )
    }
}

@Composable
private fun DashboardHero() {
    Card(colors = CardDefaults.cardColors(containerColor = ParaySurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Intelligence dashboard",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Cached summaries from observer, knowledge, workflow, and recognition — instant at any catalog size.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ParayGlow)
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ObserverSummaryCard(observer: com.oasismall.oasisai.domain.paray.ParayObserverSummary) {
    DashboardCard {
        StatLine("Last observation", formatTimestamp(observer.lastObservationAt))
        StatLine(
            "Catalog changes",
            if (observer.catalogChangesDetected) "Detected" else "None recent",
        )
        if (observer.lastChangeSummary.isNotBlank()) {
            Text(
                observer.lastChangeSummary,
                color = ParayGlow.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        StatLine("New products", observer.newProducts.toString())
        StatLine("Price changes", observer.priceChanges.toString())
        StatLine("Renamed", observer.renamedProducts.toString())
        StatLine("Removed", observer.removedProducts.toString())
    }
}

@Composable
private fun KnowledgeSummaryCard(knowledge: com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary) {
    DashboardCard {
        val known = knowledge.knownArticleCount.takeIf { it > 0 } ?: knowledge.totalArticles
        StatLine("Known articles", known.toString())
        StatLine("Brands discovered", knowledge.totalBrands.toString())
        StatLine("Categories discovered", knowledge.totalCategories.toString())
        StatLine("Families discovered", knowledge.totalFamilies.toString())
        StatLine("Learning coverage", "${knowledge.learnCoveragePercent.roundToInt()}%")
        StatLine("PNG coverage", "${knowledge.pngCoveragePercent.roundToInt()}%")
    }
}

@Composable
private fun RecognitionSummaryCard(recognition: com.oasismall.oasisai.domain.paray.ParayRecognitionSummary) {
    DashboardCard {
        StatLine("Failures observed", recognition.totalFailures.toString())
        StatLine("Low confidence matches", recognition.totalLowConfidence.toString())
        StatLine("Unknown products", recognition.totalUnknownBarcodes.toString())
        StatLine("Packaging drifts", recognition.totalPackagingDrifts.toString())
        val blind = recognition.topBlindSpot
        StatLine(
            "Top blind spot",
            blind?.let { "${it.designation ?: it.barcode} (${it.count})" } ?: "—",
        )
    }
}

@Composable
private fun WorkflowSummaryCard(workflow: com.oasismall.oasisai.domain.paray.ParayWorkflowSummary) {
    DashboardCard {
        val topScreens = workflow.topScreens.take(3)
        if (topScreens.isEmpty()) {
            StatLine("Most used screens", "—")
        } else {
            Text("Most used screens", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            topScreens.forEach { entry ->
                StatLine(
                    entry.screen,
                    "${entry.visits} visits · ${formatDuration(entry.averageDurationMs)} avg",
                )
            }
        }
        val topFeatures = workflow.topFeatures.filter { it.count > 0 }.take(3)
        if (topFeatures.isEmpty()) {
            StatLine("Most used features", "—")
        } else {
            Text("Most used features", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            topFeatures.forEach { usage ->
                StatLine(usage.feature.label, usage.count.toString())
            }
        }
        StatLine("Avg daily activity", formatDailyActivity(workflow.averageDailyActivity))
        StatLine("Design exports", workflow.designExportsCount.toString())
        StatLine("AGENT usage", workflow.agentUsageCount.toString())
    }
}

@Composable
private fun KnowledgeFusionCard(
    fusion: com.oasismall.oasisai.ui.screens.parayhome.ParayFusionUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    val busy = fusion.exporting || fusion.importing || fusion.merging
    DashboardCard {
        Text(
            "Share intelligence between PARAY instances — merge only, never overwrite.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
        if (fusion.progressMessage != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    color = ParayGlow,
                    strokeWidth = 2.dp,
                )
                Text(
                    fusion.progressMessage ?: "",
                    color = ParayGlow,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        OutlinedButton(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, null, tint = Color.White)
            Text("  Export Knowledge", color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Upload, null, tint = Color.White)
            Text("  Import Knowledge", color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(
            onClick = onToggleHistory,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.History, null, tint = Color.White)
            Text("  Fusion History", color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
        if (fusion.showHistory) {
            if (fusion.history.isEmpty()) {
                Text("No fusions yet.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            } else {
                fusion.history.take(8).forEach { entry ->
                    FusionHistoryLine(entry)
                }
            }
        }
    }
}

@Composable
private fun FusionHistoryLine(entry: ParayFusionHistoryEntry) {
    val added = entry.newArticles + entry.newLearnRecords + entry.newBrands +
        entry.newWorkflowPatterns + entry.newRecognitionPatterns
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            formatTimestamp(entry.fusedAt),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "+$added records · ${entry.conflictsResolved} conflicts resolved · PKP v${entry.packageVersion}",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FusionPreviewDialog(
    preview: ParayFusionPreview,
    merging: Boolean,
    onMerge: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!merging) onCancel() },
        title = { Text("Knowledge Package Summary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PARAY ${preview.manifest.parayVersion} · ${preview.manifest.knowledgeCount} records in package")
                StatLine("Articles", "+${preview.newArticles} new")
                StatLine("Brands", "+${preview.newBrands} new")
                StatLine("Learn records", "+${preview.newLearnRecords}")
                StatLine("Recognition patterns", "+${preview.newRecognitionPatterns}")
                StatLine("Workflow patterns", "+${preview.newWorkflowPatterns}")
                StatLine("Conflicts", preview.conflicts.toString())
                if (merging) {
                    CircularProgressIndicator(color = ParayGlow, strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onMerge, enabled = !merging) {
                Text(if (merging) "Merging…" else "Merge")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, enabled = !merging) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun QuickActionsCard(
    onOpenLearn: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenDesign: () -> Unit,
    onImportFingerprints: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = ParayTeal.copy(alpha = 0.22f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenLearn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Psychology, null, tint = Color.White)
                Text("  Open Learn", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = onOpenAgent, modifier = Modifier.fillMaxWidth()) {
                AgentNavIcon(selected = true)
                Text("  Open AGENT", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onOpenDesign, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Brush, null, tint = Color.White)
                Text("  Open Design", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = onImportFingerprints, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoFixHigh, null, tint = Color.White)
                Text("  Import fingerprints", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, tint = Color.White)
                Text("  Settings", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = onOpenLearn,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ParayTeal),
            ) {
                Text("Start learning")
            }
        }
    }
}

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ParaySurface.copy(alpha = 0.92f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes >= 1 -> "${minutes}m"
        else -> "${TimeUnit.MILLISECONDS.toSeconds(ms)}s"
    }
}

private fun formatDailyActivity(value: Float): String =
    if (value <= 0f) "—" else "${(value * 10f).roundToInt() / 10f} events/day"
