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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayKnowledge
import com.oasismall.oasisai.ui.components.AgentNavIcon

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
    onGoDesign: () -> Unit,
    onGoScanShoot: () -> Unit,
    onImportFingerprints: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HomeHeroCard(ui.neural?.learnedNow ?: 0, ui.neural?.fingerprintsNow ?: 0) }

                item {
                    SectionTitle(Icons.Default.Home, "At home")
                    Text(
                        "PARAY lives here — memory, logs, and neural files stay in his folder even when Oasis is closed.",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item { NeuralCard(ui.neural, ui.barcodePatterns) }
                items(ui.folders.size) { i ->
                    val folder = ui.folders[i]
                    FolderCard(folder.name, folder.path.name, folder.fileCount)
                }

                item {
                    SectionTitle(Icons.Default.Store, "At the office — Oasis AI")
                    Text(
                        "PARAY goes to work inside Oasis: Design shelf labels, AGENT scan & teach, import PC fingerprints.",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    OfficeCard(
                        lastWorkplace = ui.office?.lastWorkplace.orEmpty(),
                        onDesign = {
                            viewModel.recordOfficeVisit("design")
                            onGoDesign()
                        },
                        onScanShoot = {
                            viewModel.recordOfficeVisit("scan_shoot")
                            onGoScanShoot()
                        },
                        onImport = {
                            viewModel.recordOfficeVisit("import_fingerprints")
                            onImportFingerprints()
                        },
                    )
                }

                item {
                    Button(
                        onClick = onBackToOasis,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ParayTeal),
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null)
                        Text("  Back to Oasis AI office", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ParayGlow)
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeHeroCard(learned: Int, fingerprints: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ParaySurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.AutoFixHigh, null, tint = ParayGlow, modifier = Modifier.padding(4.dp))
            Column {
                Text("Visual agent", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$learned products learned · $fingerprints fingerprints", color = ParayGlow.copy(alpha = 0.85f))
                Text("Built for Oasis · lives on his own", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NeuralCard(neural: com.oasismall.oasisai.domain.paray.ParayNeuralSnapshot?, barcodePatterns: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = ParaySurface.copy(alpha = 0.9f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatLine("Model", neural?.modelId?.ifBlank { "Growing…" } ?: "…")
            StatLine("Matcher", neural?.matcherMode ?: "…")
            StatLine("Learn events", "${neural?.learnEvents ?: 0}")
            StatLine("Barcode patterns", "$barcodePatterns")
            StatLine("GPU", if (neural?.gpuAvailable == true) "Ready" else "CPU")
        }
    }
}

@Composable
private fun FolderCard(name: String, pathLabel: String, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = ParaySurface.copy(alpha = 0.7f))) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = ParayTeal)
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.Medium)
                    Text("paray_home/$pathLabel", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("$count files", color = ParayGlow)
        }
    }
}

@Composable
private fun OfficeCard(
    lastWorkplace: String,
    onDesign: () -> Unit,
    onScanShoot: () -> Unit,
    onImport: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = ParayTeal.copy(alpha = 0.25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lastWorkplace.isNotBlank()) {
                Text("Last shift: $lastWorkplace", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onDesign, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Brush, null, tint = Color.White)
                Text("  Design — shelf labels", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = onScanShoot, modifier = Modifier.fillMaxWidth()) {
                AgentNavIcon(selected = true)
                Text("  AGENT — scan & teach PARAY", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoFixHigh, null, tint = Color.White)
                Text("  Import PC fingerprints", color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
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
