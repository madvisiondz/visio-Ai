package com.oasismall.oasisai.ui.screens.visiopro

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProChannel
import com.oasismall.oasisai.domain.visiopro.VisioProPresetCatalog
import com.oasismall.oasisai.domain.visiopro.VisioProPriceSource
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.createVisioProCaptureUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisioProCategoryScreen(
    category: VisioProCategory,
    viewModel: VisioProViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val channels = VisioProPresetCatalog.channelsFor(category)
    val channelIndex = channels.indexOf(ui.channel).coerceAtLeast(0)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            viewModel.onPhotoCaptured(context, uri)
        }
        pendingCaptureUri = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            createVisioProCaptureUri(context)?.let { (uri, _) ->
                pendingCaptureUri = uri
                takePictureLauncher.launch(uri)
            }
        }
    }

    fun launchPhotoCapture() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                createVisioProCaptureUri(context)?.let { (uri, _) ->
                    pendingCaptureUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoCaptured(context, uri)
        }
    }

    LaunchedEffect(category) { viewModel.openCategory(category) }

    LaunchedEffect(ui.exportMessage) {
        ui.exportMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    val filteredPresets = remember(ui.presets, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            ui.presets
        } else {
            ui.presets.filter { row ->
                row.preset.article.labelFr.lowercase().contains(q) ||
                    row.preset.article.labelAr?.lowercase()?.contains(q) == true ||
                    row.preset.article.barcodeSuffix?.contains(q) == true
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${category.emoji} ${category.labelFr}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${filteredPresets.size} article(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (channels.size > 1) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    channels.forEachIndexed { index, channel ->
                        SegmentedButton(
                            selected = channelIndex == index,
                            onClick = {
                                viewModel.clearArticleSelection()
                                viewModel.setChannel(channel)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, channels.size),
                        ) {
                            Text(
                                when (channel) {
                                    VisioProChannel.SOCIAL -> "Réseaux sociaux"
                                    VisioProChannel.PRINT -> "Impression"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("Rechercher un article") },
                placeholder = { Text("Désignation ou code…") },
                singleLine = true,
            )

            if (ui.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filteredPresets, key = { it.preset.id }) { row ->
                        val priceLabel = row.price?.let { "${PriceFormatter.formatNumber(it)} DA" } ?: "—"
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPreset(row.preset.id) },
                            headlineContent = {
                                Text(
                                    row.preset.article.labelFr,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(priceLabel)
                                        row.preset.article.barcodeSuffix?.let { append(" · code $it") }
                                        if (row.lastExportAt != null) append(" · exporté")
                                    },
                                )
                            },
                            trailingContent = {
                                if (row.lastExportAt != null) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (ui.editorOpen && ui.selectedPresetId != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::clearArticleSelection,
            sheetState = sheetState,
        ) {
            VisioProArticleEditorSheet(
                ui = ui,
                category = category,
                onPriceChange = viewModel::updatePriceInput,
                onApplyPrice = viewModel::commitManualPrice,
                onReloadCsv = viewModel::reloadFromCsv,
                onAddToShare = viewModel::addCurrentToShare,
                onAddToPrintQueue = viewModel::addCurrentToPrintQueue,
                onExport = viewModel::exportCurrent,
                onShoot = ::launchPhotoCapture,
                onLoadImage = { pickImageLauncher.launch(arrayOf("image/*")) },
            )
        }
    }
}

@Composable
private fun VisioProArticleEditorSheet(
    ui: VisioProUiState,
    category: VisioProCategory,
    onPriceChange: (String) -> Unit,
    onApplyPrice: () -> Unit,
    onReloadCsv: () -> Unit,
    onAddToShare: () -> Unit,
    onAddToPrintQueue: () -> Unit,
    onExport: () -> Unit,
    onShoot: () -> Unit,
    onLoadImage: () -> Unit,
) {
    val selectedRow = ui.presets.firstOrNull { it.preset.id == ui.selectedPresetId }
    val label = selectedRow?.preset?.article?.labelFr.orEmpty()
    val isSocial = ui.channel == VisioProChannel.SOCIAL

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            ui.previewBitmap?.let { bitmap ->
                val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentScale = ContentScale.Fit,
                )
            } ?: CircularProgressIndicator()
        }

        if (selectedRow?.preset?.theme?.showPrice != false && category != VisioProCategory.FISH) {
            Text("Prix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = ui.priceInput,
                    onValueChange = onPriceChange,
                    label = { Text("Montant (DA)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = onApplyPrice) {
                    Text("OK")
                }
                IconButton(onClick = onReloadCsv) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recharger depuis CSV")
                }
            }
            Text(
                priceSourceLabel(ui.priceSource),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSocial && ui.usesDailyPhoto) {
            Text("Photo (réseaux sociaux)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                if (ui.hasArticlePhoto) {
                    "Photo enregistrée${ui.photoTakenAt?.let { " · ${formatDate(it)}" } ?: ""}"
                } else {
                    "Photographiez ou chargez une image pour la carte Facebook."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onShoot, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(if (ui.hasArticlePhoto) "Reprendre" else "Photographier")
                }
                OutlinedButton(onClick = onLoadImage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Charger")
                }
            }
        } else if (!isSocial) {
            Text("Image produit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                if (ui.hasArticlePhoto) {
                    "Image personnalisée${ui.photoTakenAt?.let { " · ${formatDate(it)}" } ?: ""} · remplace le catalogue"
                } else {
                    "Chargez une photo pour remplacer l'image catalogue sur l'étiquette."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onLoadImage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(if (ui.hasArticlePhoto) "Changer l'image" else "Charger l'image")
                }
                if (ui.hasArticlePhoto) {
                    OutlinedButton(onClick = onShoot, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Photographier")
                    }
                }
            }
        }

        HorizontalDivider()

        Button(
            onClick = onAddToShare,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Ajouter à To share")
        }

        if (ui.isFvPrintQuad) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAddToPrintQueue, modifier = Modifier.weight(1f)) {
                    Text("File A4 (${ui.printQueueSize})")
                }
                Button(
                    onClick = onExport,
                    enabled = !ui.isExporting && ui.printQueueSize > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (ui.isExporting) "…" else "Exporter A4",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "Modifiez le prix (OK) pour ajouter à la file A4 · jusqu'à 4 étiquettes par page.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (isSocial) {
            Button(
                onClick = onExport,
                enabled = !ui.isExporting && ui.previewBitmap != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ui.isExporting) "Export…" else "Exporter vers la galerie",
                )
            }
        } else {
            OutlinedButton(
                onClick = onExport,
                enabled = !ui.isExporting && ui.previewBitmap != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.isExporting) "Export…" else "Exporter étiquette magasin")
            }
        }
    }
}

private fun priceSourceLabel(source: VisioProPriceSource): String = when (source) {
    VisioProPriceSource.CSV -> "Prix catalogue CSV"
    VisioProPriceSource.MANUAL -> "Prix saisi manuellement"
    VisioProPriceSource.SOCIAL_MEMORY -> "Prix mémorisé"
    VisioProPriceSource.NONE -> "Aucun prix CSV — saisissez manuellement"
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH)
    return fmt.format(Date(millis))
}
