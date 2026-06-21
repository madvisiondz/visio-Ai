package com.oasismall.oasisai.ui.screens.visiopro

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.oasismall.oasisai.ui.components.CatalogChangeBadge
import com.oasismall.oasisai.ui.components.catalogChangeGlow
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

    LaunchedEffect(filteredPresets) {
        viewModel.setNavigationPresetIds(filteredPresets.map { it.preset.id })
    }

    val navigationPosition = remember(filteredPresets, ui.selectedPresetId) {
        val ids = filteredPresets.map { it.preset.id }
        val index = ids.indexOf(ui.selectedPresetId)
        if (index >= 0) index + 1 to ids.size else null
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

            val checkedCount = ui.checkedPresetIds.size
            val visibleIds = filteredPresets.map { it.preset.id }
            val allVisibleChecked = visibleIds.isNotEmpty() && visibleIds.all { it in ui.checkedPresetIds }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allVisibleChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            viewModel.selectAllVisible(visibleIds)
                        } else {
                            viewModel.clearChecked()
                        }
                    },
                )
                Text(
                    if (checkedCount > 0) "$checkedCount sélectionné(s)" else "Sélectionner",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.exportCheckedList() },
                    enabled = checkedCount > 0 && !ui.isExporting,
                ) {
                    if (ui.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        if (ui.isExporting) "Export…" else "Exporter sélection",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "Exporte chaque carte rendue en PNG (pas de page A4) — réseaux sociaux ou impression selon l'onglet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
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
                        val showInlinePrice =
                            row.preset.theme.showPrice &&
                                category != VisioProCategory.FISH
                        val openEditor = { viewModel.selectPreset(row.preset.id) }
                        val isChecked = row.preset.id in ui.checkedPresetIds
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .catalogChangeGlow(row.priceChangeGlow),
                            leadingContent = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.togglePresetChecked(row.preset.id) },
                                )
                            },
                            headlineContent = {
                                Text(
                                    row.preset.article.labelFr,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(onClick = openEditor),
                                )
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        buildString {
                                            if (!showInlinePrice) {
                                                val priceLabel =
                                                    row.price?.let { "${PriceFormatter.formatNumber(it)} DA" } ?: "—"
                                                append(priceLabel)
                                                row.preset.article.barcodeSuffix?.let { append(" · code $it") }
                                            } else {
                                                row.preset.article.barcodeSuffix?.let { append("code $it") }
                                            }
                                            if (row.lastExportAt != null) append(" · exporté")
                                        },
                                        modifier = Modifier.clickable(onClick = openEditor),
                                    )
                                    if (row.userOverrodePrice && row.csvBaselinePrice != null) {
                                        Text(
                                            "Prix saisi · CSV ${PriceFormatter.formatNumber(row.csvBaselinePrice)} DA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                    if (row.priceChangeGlow && row.previousCatalogPrice != null && row.csvCatalogPrice != null) {
                                        Text(
                                            "Prix CSV : ${PriceFormatter.formatNumber(row.previousCatalogPrice)} → ${PriceFormatter.formatNumber(row.csvCatalogPrice)} DA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (row.priceChangeGlow) {
                                        CatalogChangeBadge(active = true)
                                    }
                                }
                            },
                            trailingContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (showInlinePrice) {
                                        VisioProNumericPriceField(
                                            value = row.editablePriceText,
                                            onValueChange = { viewModel.updateInlinePrice(row.preset.id, it) },
                                            onCommit = { viewModel.commitInlinePrice(row.preset.id) },
                                            minWidthDp = 72,
                                            modifier = Modifier.widthIn(min = 72.dp, max = 108.dp),
                                        )
                                    }
                                    if (row.lastExportAt != null) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
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
                articleIndex = navigationPosition?.first,
                articleCount = navigationPosition?.second,
                onDesignationChange = viewModel::updateDesignationInput,
                onDesignationFontRatioChange = viewModel::updateDesignationFontRatio,
                onApplyDesignation = viewModel::commitManualDesignation,
                onResetDesignation = viewModel::reloadDesignationFromPreset,
                onNavigateNext = { viewModel.navigateArticle(forward = true) },
                onNavigatePrevious = { viewModel.navigateArticle(forward = false) },
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
    articleIndex: Int?,
    articleCount: Int?,
    onDesignationChange: (String) -> Unit,
    onDesignationFontRatioChange: (Float) -> Unit,
    onApplyDesignation: () -> Unit,
    onResetDesignation: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
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
    val labelFr = selectedRow?.preset?.article?.labelFr.orEmpty()
    val isSocial = ui.channel == VisioProChannel.SOCIAL
    val scrollState = rememberScrollState()
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(labelFr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (articleIndex != null && articleCount != null) {
                    Text(
                        "Article $articleIndex / $articleCount · glissez la carte ← →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerInput(ui.selectedPresetId) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                totalDragX <= -swipeThresholdPx -> onNavigateNext()
                                totalDragX >= swipeThresholdPx -> onNavigatePrevious()
                            }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ui.previewBitmap?.let { bitmap ->
                val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = labelFr,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentScale = ContentScale.Fit,
                )
            } ?: CircularProgressIndicator()
        }

        Text("Désignation sur la carte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(
            "Saisissez en arabe pour l'impression et les réseaux sociaux (varie par article).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rtlStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                OutlinedTextField(
                    value = ui.designationInput,
                    onValueChange = onDesignationChange,
                    label = { Text("Désignation (AR)") },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 3,
                    textStyle = rtlStyle,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onApplyDesignation() }),
                )
            }
            Button(onClick = onApplyDesignation) {
                Text("OK")
            }
            IconButton(onClick = onResetDesignation) {
                Icon(Icons.Default.Refresh, contentDescription = "Réinitialiser la désignation")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Taille désignation", style = MaterialTheme.typography.bodySmall)
            Text(
                "${(ui.designationFontRatio * 100).toInt()} %",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = ui.designationFontRatio,
            onValueChange = onDesignationFontRatioChange,
            valueRange = 0.02f..0.12f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Ajustez par article (chaque carte a sa propre taille). Glissez la carte ← → pour passer à l'article suivant.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedRow?.preset?.theme?.showPrice != false && category != VisioProCategory.FISH) {
            Text("Prix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VisioProNumericPriceField(
                    value = ui.priceInput,
                    onValueChange = onPriceChange,
                    label = "Montant (DA)",
                    modifier = Modifier.weight(1f),
                    onCommit = onApplyPrice,
                )
                Button(onClick = onApplyPrice) {
                    Text("OK")
                }
                IconButton(onClick = onReloadCsv) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recharger depuis CSV")
                }
            }
            Text(
                priceSourceLabel(ui.priceSource, ui.channel, selectedRow?.csvBaselinePrice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedRow?.previousCatalogPrice != null && selectedRow.csvCatalogPrice != null &&
                selectedRow.priceChangeGlow
            ) {
                Text(
                    "Changement CSV : ${PriceFormatter.formatNumber(selectedRow.previousCatalogPrice)} → ${PriceFormatter.formatNumber(selectedRow.csvCatalogPrice)} DA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
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
                Text(if (ui.isExporting) "Export…" else "Exporter cette carte")
            }
        }
    }
}

private fun priceSourceLabel(
    source: VisioProPriceSource,
    channel: VisioProChannel,
    csvBaseline: Double? = null,
): String = when (source) {
    VisioProPriceSource.CSV -> "Prix catalogue CSV (modifiable ci-dessus)"
    VisioProPriceSource.MANUAL -> {
        val csvHint = csvBaseline?.let { " — CSV : ${PriceFormatter.formatNumber(it)} DA" }.orEmpty()
        if (channel == VisioProChannel.PRINT) {
            "Prix modifié par vous (impression)$csvHint"
        } else {
            "Prix modifié par vous$csvHint"
        }
    }
    VisioProPriceSource.SOCIAL_MEMORY -> "Prix mémorisé"
    VisioProPriceSource.NONE -> "Aucun prix CSV — saisissez manuellement"
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH)
    return fmt.format(Date(millis))
}
