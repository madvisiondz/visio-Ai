package com.oasismall.oasisai.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.data.model.PrintBatchStatus
import com.oasismall.oasisai.util.ExportShareHelper
import com.oasismall.oasisai.util.PriceFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintHistoryScreen(
    viewModel: PrintHistoryViewModel,
    onBatchClick: (Long) -> Unit,
) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Print History") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(batches, key = { it.id }) { batch ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onBatchClick(batch.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(batch.templateName, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(batch.createdAt)))
                        Text("${batch.itemCount} items — ${batch.status}${if (batch.isPromo) " — PROMO" else ""}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintBatchDetailScreen(
    batchId: Long,
    viewModel: PrintHistoryViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(batchId) { viewModel.loadBatch(batchId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch #$batchId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.batch?.let { batch ->
                Text(batch.templateName)
                Text("Status: ${batch.status}")
                batch.campaignName?.let { Text("Campaign: $it") }
                batch.exportPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        OutlinedButton(
                            onClick = { ExportShareHelper.openPdf(context, file) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open PDF") }
                        OutlinedButton(
                            onClick = { ExportShareHelper.sharePdf(context, file) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Share PDF") }
                    }
                }
                Button(onClick = { viewModel.markPrinted(batchId) }) { Text("Mark as printed") }
                if (batch.status != PrintBatchStatus.PLACED.name) {
                    OutlinedButton(onClick = { viewModel.markPlaced(batchId) }) {
                        Text("Mark as placed on shelf")
                    }
                }
            }
            detail.items.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.designationSnapshot)
                        Text(PriceFormatter.format(item.priceSnapshot))
                        Text(item.barcodeSnapshot)
                    }
                }
            }
        }
    }
}
