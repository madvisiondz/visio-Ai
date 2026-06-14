package com.oasismall.oasisai.ui.screens.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.data.model.ImportChangeType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onImportDetail: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imports by viewModel.imports.collectAsStateWithLifecycle()

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.previewFromUri(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Center") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Import GestiumERP CSV/TSV. Daily diff runs automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.error?.let { error ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                when {
                    uiState.progress != null -> {
                        val progress = uiState.progress!!
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${progress.label} — ${progress.normalizedPercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    uiState.isLoading -> CircularProgressIndicator()
                }
            }
            item {
                Button(
                    onClick = { picker.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pick CSV file") }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.importSample(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Load sample data (demo)")
                }
            }

            uiState.preview?.let { preview ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Import preview", style = MaterialTheme.typography.titleSmall)
                            Text("File: ${preview.fileName}")
                            Text("${preview.parseResult.rows.size} valid rows (delimiter '${preview.parseResult.delimiter}')")
                            if (preview.parseResult.skippedRows > 0) {
                                Text("${preview.parseResult.skippedRows} rows skipped")
                            }
                            Text("Sample rows:", style = MaterialTheme.typography.labelMedium)
                            preview.sampleRows.take(8).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (preview.sampleRows.size > 8) {
                                Text(
                                    "… and ${preview.sampleRows.size - 8} more in file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = viewModel::confirmImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        Text("Confirm import")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::cancelPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        Text("Cancel")
                    }
                }
            }

            uiState.lastResult?.summary?.let { s ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Last import", style = MaterialTheme.typography.titleSmall)
                            Text("New: ${s.newCount} | Price changed: ${s.priceChangedCount}")
                            Text("Renamed: ${s.renamedCount} | Removed: ${s.removedCount}")
                            if (s.missingImagesCount > 0) {
                                Text("${s.missingImagesCount} articles missing images")
                            }
                            Button(onClick = { onImportDetail(s.importId) }) { Text("View changes") }
                        }
                    }
                }
            }

            item {
                Text("Import history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(imports, key = { it.id }) { imp ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onImportDetail(imp.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(imp.fileName, fontWeight = FontWeight.SemiBold)
                        Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(imp.importedAt)))
                        Text("${imp.rowCount} rows — +${imp.newCount} new, ${imp.priceChangedCount} price changes")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDetailScreen(
    importId: Long,
    viewModel: ImportViewModel,
    onBack: () -> Unit,
) {
    val changes by viewModel.observeChanges(importId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showUnchanged by remember { mutableStateOf(false) }

    val visibleChanges = if (showUnchanged) {
        changes
    } else {
        changes.filter { it.changeType != ImportChangeType.UNCHANGED.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import #$importId") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = showUnchanged,
                    onClick = { showUnchanged = !showUnchanged },
                    label = { Text(if (showUnchanged) "Hide unchanged" else "Show unchanged") },
                )
                Text("${visibleChanges.size} changes shown", style = MaterialTheme.typography.bodySmall)
            }
            items(visibleChanges, key = { it.id }) { change ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(change.designation, fontWeight = FontWeight.SemiBold)
                        Text("${change.changeType} — ${change.barcode}")
                        change.oldValue?.let { Text("Old: $it") }
                        change.newValue?.let { Text("New: $it") }
                    }
                }
            }
        }
    }
}
