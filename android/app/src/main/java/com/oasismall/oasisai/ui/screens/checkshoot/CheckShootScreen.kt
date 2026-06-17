package com.oasismall.oasisai.ui.screens.checkshoot

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import com.oasismall.oasisai.data.repository.SubBarcodeInfo
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.data.model.AgentCaptureMode
import com.oasismall.oasisai.ui.components.ArticleActionPanel
import com.oasismall.oasisai.ui.components.ArticlePanelData
import com.oasismall.oasisai.ui.components.OpenCameraBatchButton
import com.oasismall.oasisai.ui.screens.scanner.BarcodeCameraPreview
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.createCheckShootCaptureUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CheckShootScreen(
    viewModel: CheckShootViewModel,
    onBack: () -> Unit,
    prefillBarcode: String = "",
    startCapture: Boolean = false,
    returnArticleId: Long? = null,
    onReturnToArticle: (Long) -> Unit = {},
    onOpenCameraBatch: (articleId: Long?) -> Unit = {},
    onOpenSubBarcodeBatchShoot: (articleId: Long, subBarcode: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val suffixMatch by viewModel.suffixMatch.collectAsStateWithLifecycle()
    val paraySuggest by viewModel.paraySuggest.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val subBcMode by viewModel.subBcMode.collectAsStateWithLifecycle()
    val subBarcodes by viewModel.subBarcodes.collectAsStateWithLifecycle()
    val subBarcodeConfirm by viewModel.subBarcodeConfirm.collectAsStateWithLifecycle()
    val agentMode by viewModel.agentMode.collectAsStateWithLifecycle()
    val bulkScan by viewModel.bulkScan.collectAsStateWithLifecycle()
    val bulkCount by viewModel.bulkCaptureCount.collectAsStateWithLifecycle()
    val isBulk = agentMode == AgentCaptureMode.BULK
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var captureLaunched by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        viewModel.onCaptureFinished(success, pendingCaptureFile)
        pendingCaptureFile = null
    }

    fun launchCamera(forParayTeach: Boolean = false, bulkReady: Boolean = false) {
        val startCapture = {
            val pair = createCheckShootCaptureUri(context)
            if (pair == null) {
                viewModel.showMessage("Could not prepare camera file.")
            } else {
                pendingCaptureFile = pair.second
                takePictureLauncher.launch(pair.first)
            }
        }
        when {
            bulkReady -> startCapture()
            forParayTeach -> viewModel.prepareParayTeachCapture { startCapture() }
            else -> viewModel.prepareCreateAsset { startCapture() }
        }
    }

    LaunchedEffect(returnArticleId) {
        viewModel.setReturnArticleId(returnArticleId)
    }

    LaunchedEffect(Unit) {
        viewModel.returnToArticle.collect { articleId ->
            onReturnToArticle(articleId)
        }
    }

    LaunchedEffect(prefillBarcode) {
        if (prefillBarcode.isNotBlank()) {
            viewModel.applyPrefillBarcode(prefillBarcode, lock = true)
        }
    }

    LaunchedEffect(prefillBarcode, startCapture, isLocked, scan, captureLaunched) {
        if (startCapture && !captureLaunched && isLocked && scan != null && phase == CheckShootPhase.SCANNING) {
            captureLaunched = true
            launchCamera()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openSubBcBatchShoot.collect { (articleId, subBarcode) ->
            onOpenSubBarcodeBatchShoot(articleId, subBarcode)
        }
    }

    subBarcodeConfirm?.let { sub ->
        AlertDialog(
            onDismissRequest = viewModel::declineSubBarcodeAdd,
            title = { Text("Add sub-barcode?") },
            text = {
                Text(
                    "Link barcode $sub to this article? You will shoot a photo — " +
                        "the sub-barcode is saved after PhotoRoom import.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSubBarcodeAdd) { Text("Add & shoot") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineSubBarcodeAdd) { Text("Cancel") }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCameraPermission && phase == CheckShootPhase.SCANNING) {
            BarcodeCameraPreview(
                enabled = true,
                barcodeScanEnabled = when {
                    isBulk -> bulkScan == null
                    else -> {
                        val holdCard = scan != null && !isLocked
                        !holdCard && (!isLocked || subBcMode) && paraySuggest == null && suffixMatch == null
                    }
                },
                onBarcodeDetected = viewModel::onBarcodeScanned,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!hasCameraPermission && phase == CheckShootPhase.SCANNING) {
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", color = Color.White)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "AGENT",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (isBulk) "Bulk: $bulkCount saved · Download/BULK"
                        else "PARAY ${viewModel.parayLearnedCount} learned",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !isBulk,
                    onClick = { viewModel.setAgentMode(AgentCaptureMode.SMART) },
                    label = { Text("Smart") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = isBulk,
                    onClick = { viewModel.setAgentMode(AgentCaptureMode.BULK) },
                    label = { Text("Bulk") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (scan == null && bulkScan == null && phase == CheckShootPhase.SCANNING && hasCameraPermission) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (isBulk) "Bulk — scan barcode → take photo or skip → next scan"
                    else "Scan barcode → PARAY suggests match → lock → shoot or teach",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        message?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(msg, modifier = Modifier.padding(12.dp))
            }
        }

        if (isBulk) {
            bulkScan?.let { bulk ->
                BulkAgentPopup(
                    state = bulk,
                    modelReady = viewModel.modelReady,
                    onTakeOrReplace = {
                        viewModel.prepareBulkCapture(replaced = bulk.hasImage) {
                            launchCamera(bulkReady = true)
                        }
                    },
                    onSkip = viewModel::skipBulkScan,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }

        if (!isBulk) paraySuggest?.let { state ->
            ParaySuggestionSheet(
                state = state,
                onSelectSuggestion = viewModel::selectParaySuggestion,
                onSelectVisualMatch = viewModel::selectParayVisualMatch,
                onTeachParayLook = { launchCamera(forParayTeach = true) },
                onManualSearch = viewModel::openManualSuffixSearch,
                onDesignationQueryChange = viewModel::updateParayDesignationQuery,
                onSearchDesignation = viewModel::searchParayByDesignation,
                onSelectDesignationMatch = viewModel::selectParayDesignationMatch,
                onCancel = viewModel::dismissParaySuggestions,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
            )
        }

        if (!isBulk) suffixMatch?.let { match ->
            CheckShootSuffixPicker(
                state = match,
                onEditableBarcodeChange = viewModel::updateSuffixEditableBarcode,
                onTrimPrefix = viewModel::trimSuffixPrefixDigits,
                onSearch = viewModel::searchSuffixMatches,
                onSelectArticle = viewModel::selectSuffixMatch,
                onCancel = viewModel::dismissSuffixMatch,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
            )
        }

        if (!isBulk && phase == CheckShootPhase.SCANNING && suffixMatch == null && paraySuggest == null) {
            scan?.let { current ->
                CheckShootArticlePopup(
                    scan = current,
                    isLocked = isLocked,
                    subBcMode = subBcMode,
                    subBarcodes = subBarcodes,
                    modelReady = viewModel.modelReady,
                    onLock = viewModel::lockScan,
                    onUnlock = viewModel::unlockForNextScan,
                    onDismiss = viewModel::dismissPopup,
                    onAddToShare = viewModel::addToShareCart,
                    onAddToDesign = viewModel::addToDesignCart,
                    onCreateAsset = { launchCamera() },
                    onToggleSubBc = viewModel::toggleSubBcAcquisition,
                    onOpenCameraBatch = onOpenCameraBatch,
                    onRemoveSubBarcode = if (isLocked) viewModel::removeSubBarcode else null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }

        if (isBulk && phase == CheckShootPhase.PROCESSING) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Removing background…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        CheckShootAssetOverlay(
            phase = phase,
            preview = preview,
            onCancel = viewModel::cancelAssetFlow,
        )
    }
}

@Composable
private fun CheckShootArticlePopup(
    scan: CheckShootScan,
    isLocked: Boolean,
    subBcMode: Boolean,
    subBarcodes: List<SubBarcodeInfo>,
    modelReady: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    onAddToShare: () -> Unit,
    onAddToDesign: () -> Unit,
    onCreateAsset: () -> Unit,
    onToggleSubBc: () -> Unit,
    onOpenCameraBatch: (articleId: Long?) -> Unit,
    onRemoveSubBarcode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cornerFabSize = 52.dp
    val fabOverlap = 13.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cornerFabSize - fabOverlap, end = cornerFabSize - fabOverlap),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
            ),
        ) {
            ArticleActionPanel(
                data = ArticlePanelData(
                    articleId = scan.articleId,
                    barcode = scan.barcode,
                    designation = scan.designation ?: scan.barcode,
                    price = scan.price,
                    imagePath = scan.existingImagePath,
                    codeart = scan.codeart,
                    lastPriceChangedAt = scan.lastPriceChangedAt,
                    lastPrintedAt = scan.lastPrintedAt,
                    subBarcodes = subBarcodes,
                    inGestiumCatalog = scan.inGestiumCatalog,
                    linkedViaAlternate = scan.linkedViaAlternate,
                    linkedViaBodyKey = scan.linkedViaBodyKey,
                    isLocked = isLocked,
                    subBcMode = subBcMode,
                ),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp),
                scrollable = true,
                maxHeight = 400.dp,
                modelReady = modelReady,
                showLockControls = !isLocked,
                externalLockButton = true,
                onLock = onLock,
                onUnlock = onUnlock,
                onDismiss = onDismiss,
                onCreateAsset = if (isLocked) onCreateAsset else null,
                onAddToShare = if (isLocked && scan.hasShareablePng) onAddToShare else null,
                onAddToDesign = if (isLocked && scan.hasShareablePng) onAddToDesign else null,
                onToggleSubBc = if (isLocked && scan.inGestiumCatalog) onToggleSubBc else null,
                onOpenCameraBatch = if (isLocked && scan.inGestiumCatalog) onOpenCameraBatch else null,
                onRemoveSubBarcode = onRemoveSubBarcode,
            )
        }

        FloatingActionButton(
            onClick = if (isLocked) onUnlock else onLock,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerFabSize)
                .offset(x = fabOverlap, y = -fabOverlap),
            shape = CircleShape,
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (isLocked) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = if (isLocked) "Unlock — scan next" else "Lock this article",
            )
        }
    }
}
