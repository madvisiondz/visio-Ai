package com.oasismall.oasisai.ui.screens.camerabatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.util.PriceFormatter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBatchImportScreen(
    viewModel: CameraBatchImportViewModel,
    onBack: () -> Unit,
) {
    val rows by viewModel.pendingRows.collectAsStateWithLifecycle()
    val photoroomPath by viewModel.photoroomPath.collectAsStateWithLifecycle()
    val photoroomCount by viewModel.photoroomPngCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPhotoroomList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoRoom import") },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "All pending shots (To shoot, Articles, AGENT, Batch). Rescan after PhotoRoom saves PNGs.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Batch folder: ${viewModel.batchFolder}/",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text("$photoroomCount PNG(s) in PhotoRoom folder", style = MaterialTheme.typography.labelLarge)
            }
            message?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::refreshPhotoroomList,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Rescan PhotoRoom")
                    }
                    Button(
                        onClick = viewModel::importAllMatched,
                        enabled = !busy && rows.any { it.photoroomPng != null },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text("Import all matched")
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "No shots waiting. Use Camera batch to take photos first.",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(rows, key = { it.item.id }) { row ->
                PendingImportCard(
                    row = row,
                    busy = busy,
                    onImport = { viewModel.importOne(row.item.id) },
                    onRemove = { viewModel.removePending(row.item.id) },
                )
            }
        }
    }
}

@Composable
private fun PendingImportCard(
    row: PendingBatchRow,
    busy: Boolean,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    val found = row.photoroomPng != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbSource: Any? = row.photoroomPng?.previewSource()
                ?: File(row.item.shotPath).takeIf { it.exists() }
            if (thumbSource != null) {
                AsyncImage(
                    model = thumbSource,
                    contentDescription = row.item.designation,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.item.designation, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("Barcode: ${row.item.barcode}", style = MaterialTheme.typography.bodySmall)
                row.item.price?.let {
                    Text(PriceFormatter.format(it), color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    if (found) "PhotoRoom PNG found" else "Waiting for PhotoRoom PNG",
                    color = if (found) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onImport, enabled = found && !busy) {
                    Text("Import")
                }
                OutlinedButton(onClick = onRemove, enabled = !busy) {
                    Text("Remove")
                }
            }
        }
    }
}
