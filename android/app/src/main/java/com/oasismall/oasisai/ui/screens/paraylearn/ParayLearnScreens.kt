package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

private val tabs = listOf("Learn", "Memory", "Knowledge", "Statistics")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParayMainScreen(
    viewModel: ParayMainViewModel,
    onStartLearning: () -> Unit,
    onNavigateParayHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PARAY") },
                actions = {
                    OutlinedButton(onClick = onNavigateParayHome, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Home")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(label) },
                    )
                }
            }
            when (uiState.selectedTab) {
                0 -> ParayLearnTab(
                    stats = uiState.stats,
                    loading = uiState.loading,
                    error = uiState.error,
                    onRefresh = viewModel::refresh,
                    onStartLearning = onStartLearning,
                )
                else -> ParayPlaceholderTab(tabs[uiState.selectedTab])
            }
        }
    }
}

@Composable
private fun ParayLearnTab(
    stats: com.oasismall.oasisai.domain.paray.ParayLearnQueueStats?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onStartLearning: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Products ready for learning",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Teach PARAY what trusted catalog products look like in the real world. " +
                "Identity (barcode, designation, PNG) already exists — learning adds visual knowledge.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRefresh) { Text("Retry") }
        } else if (stats != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RequirementRow("CSV article exists", true)
                    RequirementRow("PNG exists", true)
                    RequirementRow("Barcode exists", true)
                    Spacer(Modifier.height(8.dp))
                    Text("Remaining: ${stats.readyCount} products", fontWeight = FontWeight.SemiBold)
                    Text("Learned: ${stats.learnedCount} products")
                    Text("Pending: ${stats.pendingCount} products")
                    Text(
                        "Partially learned: ${stats.partiallyLearnedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
                enabled = stats.pendingCount > 0,
            ) {
                Text("Start learning")
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh queue")
            }
        }
    }
}

@Composable
private fun RequirementRow(label: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Text(label)
    }
}

@Composable
private fun ParayPlaceholderTab(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title — coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParayLearnSessionScreen(
    viewModel: ParayLearnSessionViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.startNextProduct()
    }

    androidx.compose.runtime.LaunchedEffect(uiState.complete) {
        if (uiState.complete && uiState.product == null) {
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PARAY Learn") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        if (uiState.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            uiState.product?.let { product ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = File(product.pngPath),
                            contentDescription = product.designation,
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(product.designation, fontWeight = FontWeight.Bold)
                            Text("Barcode: ${product.barcode}", style = MaterialTheme.typography.bodySmall)
                            Text("PNG: Available", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            ViewProgressRow("Front", uiState.progressFront)
            ViewProgressRow("Left", uiState.progressLeft)
            ViewProgressRow("Right", uiState.progressRight)
            ViewProgressRow("Back", uiState.progressBack)

            Text(
                uiState.instruction,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    uiState.mismatch -> MaterialTheme.colorScheme.error
                    uiState.complete -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )

            if (uiState.phase == com.oasismall.oasisai.domain.paray.ParayLearnPhase.FRONT_CONFIRM) {
                Text(
                    "Match: ${(uiState.frontSimilarity * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                if (uiState.cameraActive) {
                    ParayLearnCameraPreview(
                        enabled = true,
                        onFrameFeatures = viewModel::onFrameFeatures,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (uiState.complete) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Learning complete", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.startNextProduct() }) {
                            Text("Next product")
                        }
                        OutlinedButton(onClick = onFinished, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Done")
                        }
                    }
                }
            }

            if (uiState.mismatch) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = viewModel::retryFront, modifier = Modifier.weight(1f)) {
                        Text("Retry front")
                    }
                    OutlinedButton(onClick = viewModel::skipProduct, modifier = Modifier.weight(1f)) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewProgressRow(label: String, done: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (done) "✓" else "□", fontWeight = FontWeight.Bold)
        Text(label)
    }
}
