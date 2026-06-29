package com.oasismall.oasisai.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oasismall.oasisai.BuildConfig
import com.oasismall.oasisai.domain.visio.PhotoroomStorage
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateImportHistory: () -> Unit = {},
    onNavigateImageManager: () -> Unit,
    onNavigateScanner: () -> Unit,
    onNavigateBackgroundRemoval: () -> Unit = {},
    onNavigatePhoneSync: () -> Unit = {},
    onNavigateHistory: () -> Unit = {},
    onNavigateReport: () -> Unit = {},
    onNavigateVisioProSettings: () -> Unit = {},
    onNavigateImportantRayons: () -> Unit = {},
) {
    val context = LocalContext.current
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoroomFolderLabel by viewModel.photoroomFolderLabel.collectAsStateWithLifecycle()
    val photoroomCustomFolder by viewModel.photoroomCustomFolder.collectAsStateWithLifecycle()
    val backupEncryptionEnabled by viewModel.backupEncryptionEnabled.collectAsStateWithLifecycle()
    var backupPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshPhotoroomFolder(context)
    }
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
    val photoroomFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.setPhotoroomFolder(context, uri)
        }
    }
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importGestiumCsv(context, uri)
    }
    val backupImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFullBackup(context, uri)
    }
    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportFullBackup(context, uri)
    }
    val visioProExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportVisioProBundle(context, uri)
    }
    val visioProImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importVisioProBundle(context, uri)
    }
    val visioProMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importVisioProMedia(context, uri)
    }

    fun defaultBackupZipName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "VisioAi_backup_$stamp.zip"
    }

    fun defaultVisioProZipName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "VisioPRO_export_$stamp.zip"
    }

    var showPurgeConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val busy = uiState.isLoadingImages || uiState.isReindexing || uiState.isImportingCsv ||
        uiState.isExportingPngs || uiState.isPurgingCatalog || uiState.isExportingBackup ||
        uiState.isImportingBackup || uiState.isExportingVisioPro || uiState.isImportingVisioPro ||
        uiState.isImportingVisioProMedia || uiState.isRestoringSubBarcodeFlavors

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text("Settings")
                    Text(
                        "Visio Ai ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }) { padding ->
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
                        if (overview.importantRayonsFiltered) {
                            Text(
                                "Stats scoped to ${overview.importantRayonsCount} important rayon(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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

            item { SectionTitle("Catalogue") }
            item {
                SettingsRow(
                    title = "Rayons importants",
                    subtitle = "Filtrer rapport CSV, statistiques et filtres Articles",
                    icon = Icons.Default.Assessment,
                    onClick = onNavigateImportantRayons,
                )
            }

            item { SectionTitle("VisioPRO") }
            item {
                SettingsRow(
                    title = "Install VisioPRO images",
                    subtitle = "One-time pack (~100 MB) — VisioPRO-media.zip from your PC",
                    icon = Icons.Default.Image,
                    enabled = !busy,
                    onClick = {
                        visioProMediaPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Listes VisioPRO",
                    subtitle = "Articles par carte · Fruits / Légumes depuis « Fruits et Légumes », ordre d'affichage",
                    icon = Icons.Default.Storefront,
                    onClick = onNavigateVisioProSettings,
                )
            }

            item { SectionTitle("Data uploads") }
            item {
                SettingsRow(
                    title = "Import Gestium CSV",
                    subtitle = "Articles database from ERP export (designation + barcode)",
                    icon = Icons.Default.CloudUpload,
                    enabled = !busy,
                    onClick = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                )
            }
            item {
                SettingsRow(
                    title = "Import history",
                    subtitle = "Past CSV imports and per-article change log",
                    icon = Icons.Default.History,
                    onClick = onNavigateImportHistory,
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
                    title = "PhotoRoom folder",
                    subtitle = "Current: $photoroomFolderLabel — used by Camera batch import (default ${PhotoroomStorage.DEFAULT_DISPLAY_PATH})",
                    icon = Icons.Default.Image,
                    enabled = !busy,
                    onClick = { photoroomFolderPicker.launch(null) },
                )
            }
            if (photoroomCustomFolder) {
                item {
                    SettingsRow(
                        title = "Reset PhotoRoom folder",
                        subtitle = "Go back to scanning ${PhotoroomStorage.DEFAULT_DISPLAY_PATH} on device storage",
                        icon = Icons.Default.Refresh,
                        enabled = !busy,
                        onClick = { viewModel.clearPhotoroomFolder(context) },
                    )
                }
            }
            item {
                SettingsRow(
                    title = "Export PNG database",
                    subtitle = "All gallery PNGs incl. sub-barcode variants → Download/VisioAi/Product_images[date]/",
                    icon = Icons.Default.FileUpload,
                    enabled = !busy,
                    onClick = { viewModel.exportProductImages(context) },
                    trailing = {
                        if (uiState.isExportingPngs) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Re-index product images",
                    subtitle = "Match PNGs by barcode (file Details) then designation; write missing tags",
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.reindexProductImages(context) },
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

            item { SectionTitle("Device transfer") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ListItem(
                            headlineContent = { Text("Encrypt backup (AES-256)") },
                            supportingContent = {
                                Text("Optional password for full ZIP export and import on another device")
                            },
                            trailingContent = {
                                Switch(
                                    checked = backupEncryptionEnabled,
                                    onCheckedChange = viewModel::setBackupEncryptionEnabled,
                                    enabled = !busy,
                                )
                            },
                        )
                        if (backupEncryptionEnabled) {
                            OutlinedTextField(
                                value = backupPassword,
                                onValueChange = {
                                    backupPassword = it
                                    viewModel.setBackupPassword(it)
                                },
                                label = { Text("Backup password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true,
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
            item {
                SettingsRow(
                    title = "Export full backup (ZIP)",
                    subtitle = if (backupEncryptionEnabled) {
                        "Encrypted export — catalog, carts, PNGs, VisioPRO, Design"
                    } else {
                        "Choose where to save — catalog, carts, PNGs, VisioPRO, Design (not kept in app cache)"
                    },
                    icon = Icons.Default.CloudUpload,
                    enabled = !busy,
                    onClick = { backupExportPicker.launch(defaultBackupZipName()) },
                    trailing = {
                        if (uiState.isExportingBackup) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Import full backup (ZIP)",
                    subtitle = "Restore everything from a VisioAi_backup_*.zip (replaces current data)",
                    icon = Icons.Default.FileDownload,
                    enabled = !busy,
                    onClick = { showImportConfirm = true },
                    trailing = {
                        if (uiState.isImportingBackup) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Export VisioPRO presets",
                    subtitle = "Choose where to save ZIP — sections, photos, catalog PNGs, designs, prices",
                    icon = Icons.Default.Storefront,
                    enabled = !busy,
                    onClick = { visioProExportPicker.launch(defaultVisioProZipName()) },
                    trailing = {
                        if (uiState.isExportingVisioPro) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Import VisioPRO presets",
                    subtitle = "Restore from VisioPRO_export_*.zip (same format as export — merges sections & photos)",
                    icon = Icons.Default.FileDownload,
                    enabled = !busy,
                    onClick = {
                        visioProImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    trailing = {
                        if (uiState.isImportingVisioPro) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Sync sub-PNGs",
                    subtitle = "Scan flavor PNGs (metadata) → link sub-barcodes for search & scanner; run after CSV if you added flavor PNGs",
                    icon = Icons.Default.Link,
                    enabled = !busy,
                    onClick = { viewModel.syncSubPngs(context) },
                    trailing = {
                        if (uiState.isRestoringSubBarcodeFlavors) CircularProgressIndicator()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Purge Gestium catalog",
                    subtitle = "Remove articles & carts — saves sub-barcode flavor map, keeps PNG files on device",
                    icon = Icons.Default.DeleteForever,
                    enabled = !busy,
                    onClick = { showPurgeConfirm = true },
                    trailing = {
                        if (uiState.isPurgingCatalog) CircularProgressIndicator()
                    },
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
                        "Runs in background — you can lock the screen. Check the notification for progress.",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (showPurgeConfirm) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirm = false },
                title = { Text("Purge Gestium catalog?") },
                text = {
                    Text(
                        "This removes all imported articles, CSV import history, carts, and VisioPRO list links. " +
                            "Sub-barcode flavor links are saved to a local registry first. PNG files stay on the device — " +
                            "re-import your CSV and flavors re-link automatically.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPurgeConfirm = false
                        viewModel.purgeGestiumCatalog(context)
                    }) { Text("Purge") }
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") }
                },
            )
        }

        if (showImportConfirm) {
            AlertDialog(
                onDismissRequest = { showImportConfirm = false },
                title = { Text("Import full backup?") },
                text = {
                    Text(
                        "This replaces catalog, images, VisioPRO settings, carts, and history on this phone " +
                            "with the selected VisioAi_backup ZIP.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showImportConfirm = false
                        backupImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }) { Text("Choose ZIP") }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
                },
            )
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
