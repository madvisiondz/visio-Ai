package com.oasismall.oasisai.ui.screens.visiopro.designer

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProChannel
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignLayerKind
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument
import com.oasismall.oasisai.domain.visiopro.designer.VisioProNormRect
import com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey
import com.oasismall.oasisai.domain.visiopro.designer.isSpatial
import com.oasismall.oasisai.util.PriceFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DESIGNER_TIPS_KEY = "studio_preset_tips_dismissed"

private fun Context.designerTipsDismissed(): Boolean =
    getSharedPreferences("visio_pro_designer", Context.MODE_PRIVATE)
        .getBoolean(DESIGNER_TIPS_KEY, false)

private fun Context.dismissDesignerTips() {
    getSharedPreferences("visio_pro_designer", Context.MODE_PRIVATE)
        .edit()
        .putBoolean(DESIGNER_TIPS_KEY, true)
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisioProDesignerHubScreen(
    viewModel: VisioProDesignerHubViewModel,
    onBack: () -> Unit,
    onOpenPreset: (VisioProPresetDesignKey) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var channelFilter by rememberSaveable { mutableStateOf<VisioProChannel?>(null) }
    val filteredCards = remember(ui.cards, channelFilter) {
        channelFilter?.let { ch -> ui.cards.filter { it.key.channel == ch } } ?: ui.cards
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Studio preset", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (ui.customizedCount > 0) {
                                "${ui.customizedCount} preset(s) personnalisé(s)"
                            } else {
                                "Designer · canvas par famille"
                            },
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
        if (ui.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Atelier de preset", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Touchez un preset · glissez les calques · pincez pour zoomer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = channelFilter == null,
                            onClick = { channelFilter = null },
                            label = { Text("Tous") },
                        )
                        FilterChip(
                            selected = channelFilter == VisioProChannel.SOCIAL,
                            onClick = { channelFilter = VisioProChannel.SOCIAL },
                            label = { Text("Réseaux") },
                        )
                        FilterChip(
                            selected = channelFilter == VisioProChannel.PRINT,
                            onClick = { channelFilter = VisioProChannel.PRINT },
                            label = { Text("Impression") },
                        )
                    }
                }
                VisioProCategory.entries.forEach { category ->
                    val cards = filteredCards.filter { it.key.category == category }
                    if (cards.isEmpty()) return@forEach
                    item {
                        Text(
                            "${category.emoji} ${category.labelFr}",
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    cards.forEach { card ->
                        item {
                            PresetHubCardItem(
                                card = card,
                                onClick = { onOpenPreset(card.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetHubCardItem(
    card: PresetHubCard,
    onClick: () -> Unit,
) {
    val channelLabel = when (card.key.channel) {
        VisioProChannel.SOCIAL -> "Réseaux"
        VisioProChannel.PRINT -> "Impression"
    }
    val channelColor = when (card.key.channel) {
        VisioProChannel.SOCIAL -> MaterialTheme.colorScheme.tertiary
        VisioProChannel.PRINT -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(card.key.labelFr, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(channelLabel, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                        border = null,
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = channelColor.copy(alpha = 0.15f),
                        ),
                    )
                    if (card.isCustomized) {
                        Text(
                            "Personnalisé",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                card.modifiedAt?.let { ts ->
                    Text(
                        "Modifié · ${formatHubDate(ts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisioProDesignerCanvasScreen(
    viewModel: VisioProDesignerViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var panelTab by rememberSaveable { mutableStateOf(0) }
    var showTips by rememberSaveable { mutableStateOf(!context.designerTipsDismissed()) }
    val layers = viewModel.availableLayers()
    val spatialLayers = viewModel.spatialLayers()

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun tryBack() {
        if (ui.isDirty) showExitDialog = true else onBack()
    }

    BackHandler(onBack = ::tryBack)

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Modifications non enregistrées") },
            text = { Text("Appliquer le preset avant de quitter, ou abandonner vos changements.") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    viewModel.save()
                    onBack()
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) { Text("Quitter sans enregistrer") }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Réinitialiser le preset ?") },
            text = { Text("Retour au design d'usine pour ${ui.presetKey.labelFr}. Cette action est irréversible.") },
            confirmButton = {
                Button(onClick = {
                    showResetDialog = false
                    viewModel.reset()
                }) { Text("Réinitialiser") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Annuler") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.presetKey.labelFr, fontWeight = FontWeight.SemiBold)
                            if (ui.isDirty) {
                                Text(
                                    " · ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "non enregistré",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            when {
                                ui.hasCustomSave && !ui.isDirty -> "Design personnalisé actif"
                                ui.isDirty -> "Modifications en cours"
                                else -> "Design par défaut"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::tryBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = ui.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = ui.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rétablir")
                    }
                    IconButton(onClick = { viewModel.setZoom(ui.zoom - 0.12f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom -")
                    }
                    IconButton(onClick = { viewModel.fitZoom() }) {
                        Icon(Icons.Default.FitScreen, contentDescription = "Ajuster")
                    }
                    IconButton(onClick = { viewModel.setZoom(ui.zoom + 0.12f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom +")
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = !ui.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = "Enregistrer")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Réinitialiser")
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !ui.isSaving,
                        modifier = Modifier.weight(1.2f),
                    ) {
                        if (ui.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Appliquer le preset")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showTips) {
                DesignerTipsBanner(
                    onDismiss = {
                        showTips = false
                        context.dismissDesignerTips()
                    },
                )
            }
            DesignerCanvasStage(
                document = ui.document,
                preview = ui.previewBitmap,
                isLoading = ui.isPreviewLoading,
                selectedLayer = ui.selectedLayer,
                spatialLayers = spatialLayers,
                showLayerFrames = ui.showLayerFrames,
                showSnapGuideH = ui.showSnapGuideH,
                showSnapGuideV = ui.showSnapGuideV,
                snapHint = ui.snapHint,
                zoom = ui.zoom,
                onSelectLayer = viewModel::selectLayer,
                onMoveLayer = viewModel::moveLayer,
                onResizeLayer = viewModel::resizeLayer,
                onGestureStart = viewModel::onGestureStart,
                onGestureEnd = viewModel::onGestureEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f),
            )

            TabRow(selectedTabIndex = panelTab) {
                Tab(
                    selected = panelTab == 0,
                    onClick = { panelTab = 0 },
                    text = { Text("Calques") },
                    icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = panelTab == 1,
                    onClick = { panelTab = 1 },
                    text = { Text("Réglages") },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }

            Column(
                Modifier
                    .weight(0.48f)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (panelTab) {
                    0 -> LayerToolsPanel(
                        layers = layers,
                        selectedLayer = ui.selectedLayer,
                        showLayerFrames = ui.showLayerFrames,
                        snapGuidesEnabled = ui.snapGuidesEnabled,
                        onSelectLayer = viewModel::selectLayer,
                        onToggleFrames = viewModel::toggleLayerFrames,
                        onToggleSnap = viewModel::toggleSnapGuides,
                        onAlignCenterH = viewModel::alignSelectedCenterHorizontal,
                        onAlignCenterV = viewModel::alignSelectedCenterVertical,
                        onResetLayer = viewModel::resetSelectedLayerLayout,
                        onNudge = { dx, dy -> viewModel.nudgeLayer(ui.selectedLayer, dx, dy) },
                    )
                    1 -> DesignerInspector(
                        document = ui.document,
                        presetKey = ui.presetKey,
                        selectedLayer = ui.selectedLayer,
                        onBackgroundTop = viewModel::updateBackgroundTop,
                        onBackgroundBottom = viewModel::updateBackgroundBottom,
                        onHeaderBand = viewModel::updateHeaderBand,
                        onAccent = viewModel::updateAccent,
                        onDesignationColor = viewModel::updateDesignationColor,
                        onPriceColor = viewModel::updatePriceColor,
                        onCodeColor = viewModel::updateCodeColor,
                        onCategoryTag = viewModel::updateCategoryTag,
                        onHeaderHeight = viewModel::updateHeaderBandHeight,
                        onDesignationFont = viewModel::updateDesignationFontRatio,
                        onPriceFont = viewModel::updatePriceFontRatio,
                        onCodeFont = viewModel::updateCodeFontRatio,
                        onSampleDesignation = viewModel::updateSampleDesignation,
                        onSampleDesignationAr = viewModel::updateSampleDesignationAr,
                        onSamplePrice = viewModel::updateSamplePrice,
                        onSampleCode = viewModel::updateSampleCode,
                        onNudge = { dx, dy -> viewModel.nudgeLayer(ui.selectedLayer, dx, dy) },
                        onFineNudge = { dx, dy -> viewModel.nudgeLayer(ui.selectedLayer, dx * 0.5f, dy * 0.5f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignerTipsBanner(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Guide rapide", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. Onglet Calques → choisir Photo, Prix…\n" +
                        "2. Glisser pour déplacer · coins pour redimensionner\n" +
                        "3. Onglet Réglages → couleurs et tailles\n" +
                        "4. Appliquer le preset pour toute la section",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Fermer")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerToolsPanel(
    layers: List<VisioProDesignLayerKind>,
    selectedLayer: VisioProDesignLayerKind,
    showLayerFrames: Boolean,
    snapGuidesEnabled: Boolean,
    onSelectLayer: (VisioProDesignLayerKind) -> Unit,
    onToggleFrames: () -> Unit,
    onToggleSnap: () -> Unit,
    onAlignCenterH: () -> Unit,
    onAlignCenterV: () -> Unit,
    onResetLayer: () -> Unit,
    onNudge: (Float, Float) -> Unit,
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            layers.forEach { layer ->
                FilterChip(
                    selected = selectedLayer == layer,
                    onClick = { onSelectLayer(layer) },
                    label = { Text("${layerIcon(layer)} ${layer.labelFr}") },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cadres calques", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = showLayerFrames, onCheckedChange = { onToggleFrames() })
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Aimant centre", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = snapGuidesEnabled, onCheckedChange = { onToggleSnap() })
        }
        if (selectedLayer.isSpatial()) {
            Text("Alignement · ${selectedLayer.labelFr}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAlignCenterH, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VerticalAlignCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Centre H", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onAlignCenterV, modifier = Modifier.weight(1f)) {
                    Text("Centre V", maxLines = 1)
                }
                OutlinedButton(onClick = onResetLayer, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            PositionControls(onNudge, { dx, dy -> onNudge(dx * 0.5f, dy * 0.5f) })
        } else {
            Text(
                "Sélectionnez Photo, Désignation, Prix ou Code pour déplacer et aligner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DesignerInspector(
    document: VisioProDesignerDocument,
    presetKey: VisioProPresetDesignKey,
    selectedLayer: VisioProDesignLayerKind,
    onBackgroundTop: (Int) -> Unit,
    onBackgroundBottom: (Int) -> Unit,
    onHeaderBand: (Int) -> Unit,
    onAccent: (Int) -> Unit,
    onDesignationColor: (Int) -> Unit,
    onPriceColor: (Int) -> Unit,
    onCodeColor: (Int) -> Unit,
    onCategoryTag: (String) -> Unit,
    onHeaderHeight: (Float) -> Unit,
    onDesignationFont: (Float) -> Unit,
    onPriceFont: (Float) -> Unit,
    onCodeFont: (Float) -> Unit,
    onSampleDesignation: (String) -> Unit,
    onSampleDesignationAr: (String) -> Unit,
    onSamplePrice: (String) -> Unit,
    onSampleCode: (String) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onFineNudge: (Float, Float) -> Unit,
) {
    Card(
        Modifier
            .padding(12.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Inspecteur · ${selectedLayer.labelFr}", fontWeight = FontWeight.SemiBold)

            if (selectedLayer == VisioProDesignLayerKind.DESIGNATION ||
                selectedLayer == VisioProDesignLayerKind.PRICE ||
                selectedLayer == VisioProDesignLayerKind.CODE
            ) {
                SampleContentSection(
                    document = document,
                    presetKey = presetKey,
                    selectedLayer = selectedLayer,
                    onSampleDesignation = onSampleDesignation,
                    onSampleDesignationAr = onSampleDesignationAr,
                    onSamplePrice = onSamplePrice,
                    onSampleCode = onSampleCode,
                )
                HorizontalDivider()
            }

            when (selectedLayer) {
                VisioProDesignLayerKind.BACKGROUND -> {
                    ColorRow("Fond haut", document.backgroundTop, onBackgroundTop)
                    ColorRow("Fond bas", document.backgroundBottom, onBackgroundBottom)
                }
                VisioProDesignLayerKind.HEADER -> {
                    ColorRow("Bandeau", document.headerBand, onHeaderBand)
                    ColorRow("Accent", document.accent, onAccent)
                    OutlinedTextField(
                        value = document.categoryTag,
                        onValueChange = onCategoryTag,
                        label = { Text("Texte bandeau") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    RatioSlider(
                        label = "Hauteur bandeau",
                        value = document.headerBandHeight,
                        valueRange = 0.06f..0.22f,
                        format = { "${(it * 100).toInt()} %" },
                        onValueChange = onHeaderHeight,
                    )
                }
                VisioProDesignLayerKind.PHOTO -> PositionControls(onNudge, onFineNudge)
                VisioProDesignLayerKind.DESIGNATION -> {
                    ColorRow("Couleur texte", document.designationColor, onDesignationColor)
                    RatioSlider(
                        label = "Taille police",
                        value = document.designationFontRatio,
                        valueRange = 0.02f..0.12f,
                        format = { "${(it * 100).toInt()} %" },
                        onValueChange = onDesignationFont,
                    )
                    PositionControls(onNudge, onFineNudge)
                }
                VisioProDesignLayerKind.PRICE -> {
                    ColorRow("Couleur prix", document.priceColor, onPriceColor)
                    RatioSlider(
                        label = "Taille police",
                        value = document.priceFontRatio,
                        valueRange = 0.04f..0.18f,
                        format = { "${(it * 100).toInt()} %" },
                        onValueChange = onPriceFont,
                    )
                    PositionControls(onNudge, onFineNudge)
                }
                VisioProDesignLayerKind.CODE -> {
                    ColorRow("Couleur code", document.codeColor, onCodeColor)
                    RatioSlider(
                        label = "Taille police",
                        value = document.codeFontRatio,
                        valueRange = 0.015f..0.08f,
                        format = { "${(it * 100).toInt()} %" },
                        onValueChange = onCodeFont,
                    )
                    PositionControls(onNudge, onFineNudge)
                }
            }
        }
    }
}

@Composable
private fun SampleContentSection(
    document: VisioProDesignerDocument,
    presetKey: VisioProPresetDesignKey,
    selectedLayer: VisioProDesignLayerKind,
    onSampleDesignation: (String) -> Unit,
    onSampleDesignationAr: (String) -> Unit,
    onSamplePrice: (String) -> Unit,
    onSampleCode: (String) -> Unit,
) {
    Text("Aperçu échantillon", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    Text(
        "Texte utilisé uniquement dans le studio — les vrais articles gardent leur désignation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (selectedLayer) {
        VisioProDesignLayerKind.DESIGNATION -> {
            OutlinedTextField(
                value = document.sampleDesignation,
                onValueChange = onSampleDesignation,
                label = { Text("Désignation FR") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (presetKey.channel == VisioProChannel.PRINT || document.templateId == "ail_social") {
                OutlinedTextField(
                    value = document.sampleDesignationAr,
                    onValueChange = onSampleDesignationAr,
                    label = { Text("Désignation AR") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
        VisioProDesignLayerKind.PRICE -> {
            OutlinedTextField(
                value = PriceFormatter.formatNumber(document.samplePrice),
                onValueChange = onSamplePrice,
                label = { Text("Prix échantillon (DA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        VisioProDesignLayerKind.CODE -> {
            OutlinedTextField(
                value = document.sampleCode,
                onValueChange = onSampleCode,
                label = { Text("Code article") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        else -> Unit
    }
}

@Composable
private fun RatioSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(format(value), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
}

@Composable
private fun PositionControls(
    onNudge: (Float, Float) -> Unit,
    onFineNudge: (Float, Float) -> Unit,
) {
    Text("Position", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    Text("Pas normal 1 % · fin 0,5 %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onNudge(0f, -0.01f) }, modifier = Modifier.weight(1f)) { Text("↑") }
        OutlinedButton(onClick = { onNudge(0f, 0.01f) }, modifier = Modifier.weight(1f)) { Text("↓") }
        OutlinedButton(onClick = { onNudge(-0.01f, 0f) }, modifier = Modifier.weight(1f)) { Text("←") }
        OutlinedButton(onClick = { onNudge(0.01f, 0f) }, modifier = Modifier.weight(1f)) { Text("→") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onFineNudge(0f, -0.01f) }, modifier = Modifier.weight(1f)) { Text("↑ fine") }
        OutlinedButton(onClick = { onFineNudge(0f, 0.01f) }, modifier = Modifier.weight(1f)) { Text("↓ fine") }
        OutlinedButton(onClick = { onFineNudge(-0.01f, 0f) }, modifier = Modifier.weight(1f)) { Text("← fine") }
        OutlinedButton(onClick = { onFineNudge(0.01f, 0f) }, modifier = Modifier.weight(1f)) { Text("→ fine") }
    }
}

@Composable
private fun ColorRow(label: String, colorInt: Int, onPick: (Int) -> Unit) {
    val swatches = listOf(
        0xFF4A0E0E.toInt(), 0xFFB71C1C.toInt(), 0xFFE53935.toInt(), 0xFF01579B.toInt(),
        0xFF0288D1.toInt(), 0xFF2E7D32.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
        0xFFFFD54F.toInt(), 0xFFBF360C.toInt(), 0xFF3E2723.toInt(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            swatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(swatch))
                        .border(
                            width = if (swatch == colorInt) 3.dp else 1.dp,
                            color = if (swatch == colorInt) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onPick(swatch) },
                )
            }
        }
    }
}

private fun layerIcon(layer: VisioProDesignLayerKind): String = when (layer) {
    VisioProDesignLayerKind.BACKGROUND -> "🎨"
    VisioProDesignLayerKind.HEADER -> "▬"
    VisioProDesignLayerKind.PHOTO -> "🖼"
    VisioProDesignLayerKind.DESIGNATION -> "Aa"
    VisioProDesignLayerKind.PRICE -> "₿"
    VisioProDesignLayerKind.CODE -> "#"
}

private fun formatHubDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRENCH)
    return fmt.format(Date(millis))
}
