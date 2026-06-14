package com.oasismall.oasisai.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oasismall.oasisai.OasisApp
import com.oasismall.oasisai.domain.ReadyPngModel
import com.oasismall.oasisai.domain.paray.ParayImportStatus
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateImport: () -> Unit,
    onNavigateImageManager: () -> Unit,
    onNavigateScanner: () -> Unit,
    onNavigateGalleryLink: () -> Unit,
    onNavigateBackgroundRemoval: () -> Unit = {},
    onNavigatePhoneSync: () -> Unit = {},
    onNavigateHistory: () -> Unit = {},
    onNavigateReport: () -> Unit = {},
    onNavigateParayImport: () -> Unit = {},
    onNavigateParayHome: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as OasisApp
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val parayImport by app.parayImportManager.state.collectAsStateWithLifecycle()
    val readyImagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.loadReadyPngImages(context, uris)
    }
    val readyFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.loadReadyPngFolder(context, uri)
        }
    }
    val parayFingerprintPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            app.parayImportManager.enqueue(uri)
            onNavigateParayImport()
        }
    }

    val busy = uiState.isLoadingImages || uiState.isReindexing || uiState.isLoadingSample

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.message?.let { msg ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            color = if (uiState.messageIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            uiState.progress?.let { progress ->
                item {
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
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Database overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Articles in app: ${overview.totalArticles}")
                        Text("With Oasis gallery image: ${overview.withGalleryImage}")
                        Text("Missing image: ${overview.missingImages}")
                        Text("PNG files on device: ${overview.pngFilesOnDevice}")
                        Text("Oasis IMAGE ASSETS model PNGs: ${overview.oasisModelPngs}")
                        Text(
                            "Model tags: Barcode, Codeart, Designation, PriceNow, Rayon (PC script 2026-05-31)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("Data uploads") }
            item {
                SettingsRow(
                    title = "Import Gestium CSV",
                    subtitle = "Articles database from ERP export (designation + barcode)",
                    icon = Icons.Default.CloudUpload,
                    onClick = onNavigateImport,
                )
            }
            item {
                SettingsRow(
                    title = "Load sample data",
                    subtitle = "15 demo articles for testing without a real CSV",
                    icon = Icons.Default.Storage,
                    onClick = { viewModel.loadSampleData(context) },
                    trailing = {
                        if (uiState.isLoadingSample) CircularProgressIndicator()
                    },
                )
            }

            item { SectionTitle("PARAY") }
            item {
                SettingsRow(
                    title = "PARAY home",
                    subtitle = "PARAY's own space — memory folder, neural stats, links to Oasis work",
                    icon = Icons.Default.AutoFixHigh,
                    enabled = !busy,
                    onClick = onNavigateParayHome,
                )
            }
            item {
                SettingsRow(
                    title = "Import PARAY fingerprints",
                    subtitle = "Opens neural load screen — runs in background even if screen is off",
                    icon = Icons.Default.AutoFixHigh,
                    enabled = !busy,
                    onClick = {
                        if (parayImport.status == ParayImportStatus.Running ||
                            parayImport.status == ParayImportStatus.Complete
                        ) {
                            onNavigateParayImport()
                        } else {
                            parayFingerprintPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
                    },
                )
            }

            item { SectionTitle("Image gallery") }
            item {
                SettingsRow(
                    title = "Load IMAGE ASSETS folder",
                    subtitle = "All PNGs in folder (auto-batch 500 until complete; existing files skipped). Import CSV first.",
                    icon = Icons.Default.FileUpload,
                    enabled = !busy,
                    onClick = { readyFolderPicker.launch(null) },
                )
            }
            item {
                SettingsRow(
                    title = "Load ready PNG files",
                    subtitle = "Pick up to 500 PNGs at a time; Oasis model tags preserved",
                    icon = Icons.Default.Image,
                    enabled = !busy,
                    onClick = { readyImagesPicker.launch(arrayOf("image/png")) },
                )
            }
            item {
                SettingsRow(
                    title = "Re-index product images",
                    subtitle = "Match PNGs by barcode (file Details) then designation; write missing tags",
                    icon = Icons.Default.Refresh,
                    onClick = viewModel::reindexProductImages,
                    trailing = {
                        if (uiState.isReindexing) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Missing images",
                    subtitle = "Browse articles that still need a photo",
                    icon = Icons.Default.Image,
                    onClick = onNavigateImageManager,
                )
            }
            item {
                SettingsRow(
                    title = "Link images from gallery",
                    subtitle = "Select ready PNGs, scan barcode, then name/link them",
                    icon = Icons.Default.Image,
                    onClick = onNavigateGalleryLink,
                )
            }

            item { SectionTitle("Multi-phone sync") }
            item {
                SettingsRow(
                    title = "Phone sync (hotspot)",
                    subtitle = "Master receives shelf PNGs from other phones over Wi‑Fi — no internet",
                    icon = Icons.Default.Sync,
                    onClick = onNavigatePhoneSync,
                )
            }

            item { SectionTitle("History & tools") }
            item {
                SettingsRow(
                    title = "Report",
                    subtitle = "CSV changes (old vs new) and Design shelf prints",
                    icon = Icons.Default.Assessment,
                    onClick = onNavigateReport,
                )
            }
            item {
                SettingsRow(
                    title = "Work history",
                    subtitle = "Searches, scans, cart actions, and sync events",
                    icon = Icons.Default.History,
                    onClick = onNavigateHistory,
                )
            }
            item { SectionTitle("Tools") }
            item {
                SettingsRow(
                    title = "Remove background (offline)",
                    subtitle = "U2NetP on-device — import photo, cut out product, save transparent PNG",
                    icon = Icons.Default.AutoFixHigh,
                    onClick = onNavigateBackgroundRemoval,
                )
            }
            item {
                SettingsRow(
                    title = "Barcode scanner",
                    subtitle = "Look up article by barcode on shelf",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onNavigateScanner,
                )
            }
        }

        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    uiState.progress?.let { progress ->
                        Text(
                            progress.label,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${progress.normalizedPercent}%",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } ?: Text(
                        "Working…",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Keep Oasis AI open — large image loads take several minutes",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier,
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = trailing ?: {
                if (enabled) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            },
        )
    }
}
