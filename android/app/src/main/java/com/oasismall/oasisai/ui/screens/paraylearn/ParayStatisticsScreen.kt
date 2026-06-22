package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ParayStatisticsScreen(viewModel: ParayStatisticsViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    when {
        ui.loading -> {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
        ui.error != null -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            }
        }
        ui.data != null -> {
            StatisticsContent(data = ui.data!!, onRefresh = viewModel::refresh)
        }
    }
}

@Composable
private fun StatisticsContent(data: ParayStatisticsUiData, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Cached KPIs from learn_index, knowledge_summary, workflow_summary, recognition_summary",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionTitle("Learning")
            LearningProgressCards(data.learning)
        }
        item {
            CoverageBar(data.learning.coveragePercent)
        }

        item {
            SectionTitle("Knowledge")
            KnowledgeProgressCards(data.knowledge)
        }

        item {
            SectionTitle("Workflow")
            WorkflowKpiCard(data.workflow)
        }

        item {
            SectionTitle("Recognition intelligence")
            RecognitionKpiCard(data.recognition)
        }

        item {
            SectionTitle("Learning trend")
        }
        if (data.learningTrend.isEmpty()) {
            item {
                Text(
                    "No learning activity in cache yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(data.learningTrend, key = { "${it.barcode}-${it.learnedAt}" }) { entry ->
                LearningTrendCard(entry)
            }
        }

        item {
            Text(
                "Knowledge updated: ${formatStatsDate(data.knowledgeUpdatedAt)} · " +
                    "Workflow updated: ${formatStatsDate(data.workflowUpdatedAt)} · " +
                    "Recognition: ${formatStatsDate(data.recognitionGeneratedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun LearningProgressCards(kpis: ParayLearningKpis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiProgressCard("Ready", kpis.readyCount, Modifier.weight(1f))
            KpiProgressCard("Learned", kpis.learnedCount, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiProgressCard("Partial", kpis.partiallyLearnedCount, Modifier.weight(1f))
            KpiProgressCard("Pending", kpis.pendingCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KnowledgeProgressCards(kpis: ParayKnowledgeKpis) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpiProgressCard("Brands", kpis.knownBrands, Modifier.weight(1f))
        KpiProgressCard("Categories", kpis.knownCategories, Modifier.weight(1f))
        KpiProgressCard("Families", kpis.knownFamilies, Modifier.weight(1f))
    }
}

@Composable
private fun KpiProgressCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CoverageBar(coveragePercent: Float) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Learning coverage", fontWeight = FontWeight.SemiBold)
                Text("${coveragePercent.roundToInt()}%")
            }
            LinearProgressIndicator(
                progress = { (coveragePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "From knowledge_summary.json (cached)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecognitionKpiCard(kpis: ParayRecognitionKpis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WorkflowRow("Recognition failures", kpis.failures.toString())
            WorkflowRow("Unknown products", kpis.unknownProducts.toString())
            WorkflowRow("Packaging drifts", kpis.packagingDrifts.toString())
            WorkflowRow("Manual corrections", kpis.manualCorrections.toString())
            WorkflowRow("Most problematic", kpis.mostProblematicLabel ?: "—")
            Text(
                "From recognition_summary.json (cached)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkflowKpiCard(kpis: ParayWorkflowKpis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WorkflowRow("Most used screen", kpis.mostUsedScreen ?: "—")
            WorkflowRow("Most used feature", kpis.mostUsedFeature ?: "—")
            WorkflowRow("AGENT usage", kpis.agentUsageCount.toString())
            WorkflowRow("Design exports", kpis.designExportsCount.toString())
        }
    }
}

@Composable
private fun WorkflowRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LearningTrendCard(entry: ParayLearningTrendEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.designation, fontWeight = FontWeight.SemiBold)
            Text("Barcode: ${entry.barcode}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${trendStatusLabel(entry.status)} · ${formatStatsDate(entry.learnedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun trendStatusLabel(status: ParayLearnStatus): String = when (status) {
    ParayLearnStatus.LEARNED -> "Learned"
    ParayLearnStatus.PARTIALLY_LEARNED -> "Partial progress"
    ParayLearnStatus.NOT_LEARNED -> "Updated"
}

private fun formatStatsDate(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
