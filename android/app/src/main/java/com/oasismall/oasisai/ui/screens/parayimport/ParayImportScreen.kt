package com.oasismall.oasisai.ui.screens.parayimport

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayImportStatus
import com.oasismall.oasisai.domain.paray.ParayKnowledge
import com.oasismall.oasisai.domain.paray.ParayNeuralSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParayImportScreen(
    viewModel: ParayImportViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startImport(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PARAY neural load") },
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
            item { StatusCard(ui.status, ui.progress, ui.error, ui.runningInBackground) }

            if (ui.status == ParayImportStatus.Complete && ui.result != null) {
                item { ResultCard(ui.result!!, ui.growthDelta) }
            }

            item { NeuralCard(ui.neural) }
            item { GrowthCard(ui.neural, ui.growthDelta, ui.status) }
            item { SignalsCard() }
            item { FutureCard() }
        }
    }
}

@Composable
private fun StatusCard(
    status: ParayImportStatus,
    progress: com.oasismall.oasisai.domain.paray.ParayImportProgress,
    error: String?,
    runningInBackground: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Import status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (status == ParayImportStatus.Running) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                when (status) {
                    ParayImportStatus.Idle -> progress.phase
                    ParayImportStatus.Running -> progress.phase
                    ParayImportStatus.Complete -> "All fingerprints loaded into PARAY memory."
                    ParayImportStatus.Failed -> error ?: "Import failed"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (status == ParayImportStatus.Running) {
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${progress.processed} / ${progress.total} · ${progress.imported} linked · " +
                        "${progress.skippedNoArticle} no article · ${progress.skippedInvalid} invalid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                progress.currentDesignation?.let { des ->
                    Text(
                        "Latest: $des",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
            }

            if (runningInBackground) {
                Text(
                    "Runs in background — safe to turn screen off or leave this screen. " +
                        "Check the notification for progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: com.oasismall.oasisai.domain.paray.ParayImportResult,
    growthDelta: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Load complete", fontWeight = FontWeight.SemiBold)
            Text("${result.imported} articles seeded · ${result.embeddingCount} neural fingerprints")
            if (growthDelta > 0) Text("PARAY memory grew by +$growthDelta products")
            if (result.skippedNoArticle > 0) {
                Text("${result.skippedNoArticle} skipped — import Gestium CSV first, then reload.")
            }
        }
    }
}

@Composable
private fun NeuralCard(neural: ParayNeuralSnapshot) {
    SectionCard(title = "Neural profile", icon = Icons.Default.Psychology) {
        StatRow("Agent", "${neural.agentName} v${neural.agentVersion}")
        StatRow("Embedding model", neural.modelId.ifBlank { "Not loaded yet" })
        StatRow("Vector size", "${neural.embeddingDim}-dim")
        if (neural.modelSource.isNotBlank()) StatRow("PC source", neural.modelSource)
        if (neural.modelGeneratedAt.isNotBlank()) StatRow("Built on PC", neural.modelGeneratedAt)
        StatRow("Matcher", neural.matcherMode)
        StatRow("GPU", if (neural.gpuAvailable) "Available (${neural.glesVersion ?: "GLES"})" else "CPU fallback")
        if (neural.lowRamDevice) StatRow("Device", "Low RAM — lite batches recommended")
        StatRow("Camera ID", if (neural.cameraReady) "Embeddings ready (TFLite scan next)" else "Waiting for fingerprints")
    }
}

@Composable
private fun GrowthCard(neural: ParayNeuralSnapshot, growthDelta: Int, status: ParayImportStatus) {
    SectionCard(title = "Memory & growth", icon = Icons.Default.TrendingUp) {
        StatRow("Products learned", "${neural.learnedNow} (was ${neural.learnedBefore})")
        StatRow("Neural fingerprints", "${neural.fingerprintsNow} (was ${neural.fingerprintsBefore})")
        StatRow("Design learn events", neural.learnEvents.toString())
        if (status == ParayImportStatus.Complete && growthDelta > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .padding(10.dp),
            ) {
                Text(
                    "+$growthDelta new visual memories from PC catalog",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SignalsCard() {
    SectionCard(title = "Learning signals", icon = Icons.Default.Memory) {
        ParayKnowledge.learningSignals.forEach { (key, desc) ->
            StatRow(key.replace('_', ' '), desc)
        }
    }
}

@Composable
private fun FutureCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Next growth", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ParayKnowledge.futureCapabilities.forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
    }
}
