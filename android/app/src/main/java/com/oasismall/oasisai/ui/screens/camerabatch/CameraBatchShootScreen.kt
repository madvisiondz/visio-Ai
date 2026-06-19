package com.oasismall.oasisai.ui.screens.camerabatch

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.ui.components.ArticleActionPanel
import com.oasismall.oasisai.ui.components.ArticlePanelData
import com.oasismall.oasisai.ui.components.ManualBarcodeEntrySection
import com.oasismall.oasisai.ui.screens.scanner.BarcodeCameraPreview
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.createCheckShootCaptureUri
import com.oasismall.oasisai.util.hasAppGalleryImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBatchShootScreen(
    viewModel: CameraBatchShootViewModel,
    queueItemId: Long?,
    articleId: Long?,
    subBcAcquire: Boolean = false,
    confirmedSubBarcode: String? = null,
    onBack: () -> Unit,
    onDoneShooting: () -> Unit,
    onOpenPhotoroomImport: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(queueItemId, articleId, subBcAcquire, confirmedSubBarcode) {
        viewModel.initSession(queueItemId, articleId, subBcAcquire, confirmedSubBarcode)
    }

    val step by viewModel.step.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val currentQueue by viewModel.currentQueueItem.collectAsStateWithLifecycle()
    val queuePending by viewModel.queuePending.collectAsStateWithLifecycle()
    val pendingJpeg by viewModel.pendingJpeg.collectAsStateWithLifecycle()
    val designationInput by viewModel.designationInput.collectAsStateWithLifecycle()
    val designationMatches by viewModel.designationMatches.collectAsStateWithLifecycle()
    val pickedMatch by viewModel.pickedMatch.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val shotCount by viewModel.shotCount.collectAsStateWithLifecycle()
    val autoLaunchCamera by viewModel.autoLaunchCamera.collectAsStateWithLifecycle()
    val subBcAcquireMode by viewModel.subBcAcquireMode.collectAsStateWithLifecycle()
    val singleArticleMode by viewModel.singleArticleMode.collectAsStateWithLifecycle()
    val subBarcodeConfirm by viewModel.subBarcodeConfirm.collectAsStateWithLifecycle()
    val parentDesignation = locked?.designation

    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
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
        viewModel.onPhotoCaptured(success, pendingCaptureFile)
        pendingCaptureFile = null
    }

    fun launchCamera() {
        val pair = createCheckShootCaptureUri(context) ?: run {
            viewModel.showMessage("Could not prepare camera file.")
            return
        }
        pendingCaptureFile = pair.second
        takePictureLauncher.launch(pair.first)
    }

    LaunchedEffect(autoLaunchCamera, step, hasCameraPermission) {
        if (autoLaunchCamera && step == CameraBatchShootStep.LOCKED && hasCameraPermission) {
            viewModel.consumeAutoLaunchCamera()
            launchCamera()
        }
    }

    subBarcodeConfirm?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::declineSubBarcodeAdd,
            title = { Text("Add sub-barcode?") },
            text = {
                Text(
                    "Link barcode ${pending.scannedBarcode} to ${parentDesignation ?: "this article"}? " +
                        "Shoot a photo next — saved after PhotoRoom import.",
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera batch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            currentQueue?.let { item ->
                val index = queuePending.indexOfFirst { it.id == item.id }.let {
                    if (it >= 0) it + 1 else 1
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Batch list $index/${queuePending.size}", style = MaterialTheme.typography.labelLarge)
                        Text(item.designation, fontWeight = FontWeight.SemiBold)
                        Text("Scan barcode first, then shoot", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                "Folder: ${viewModel.batchFolder}/ · $shotCount awaiting PhotoRoom",
                style = MaterialTheme.typography.bodySmall,
            )
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            when (step) {
                CameraBatchShootStep.SCAN -> {
                    Text(
                        if (subBcAcquireMode) "Scan new flavor barcode (sub-barcode)"
                        else "Scan barcode to lock",
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!hasCameraPermission) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                    } else {
                        BarcodeCameraPreview(
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            enabled = true,
                            onBarcodeDetected = viewModel::onBarcodeScanned,
                        )
                        ManualBarcodeEntrySection(
                            onSubmit = viewModel::submitManualBarcode,
                            hint = if (subBcAcquireMode) {
                                "Type the new flavor barcode when the camera can't read it."
                            } else {
                                "Type barcode digits when the camera can't read the label."
                            },
                        )
                    }
                }
                CameraBatchShootStep.LOCKED -> {
                    locked?.let { scan -> LockedCard(scan, viewModel::removeSubBarcode) }
                    Button(onClick = ::launchCamera, modifier = Modifier.fillMaxWidth()) {
                        Text("Shoot product photo")
                    }
                    if (locked?.inCatalog == false) {
                        OutlinedButton(onClick = viewModel::startCreateDesignation, modifier = Modifier.fillMaxWidth()) {
                            Text("Create designation (not in CSV)")
                        }
                    }
                    OutlinedButton(onClick = viewModel::unlockForNextScan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Text(" Unlock — scan next")
                    }
                }
                CameraBatchShootStep.CREATE_DESIGNATION -> {
                    Text("Type designation — matches from CSV appear below", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = designationInput,
                        onValueChange = viewModel::setDesignationInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Designation") },
                        singleLine = false,
                        minLines = 2,
                    )
                    designationMatches.forEach { article ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.pickDesignationMatch(article) },
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(article.designation, fontWeight = FontWeight.Medium)
                                Text(article.barcode, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Button(onClick = viewModel::saveNewDesignation, modifier = Modifier.fillMaxWidth()) {
                        Text("Save as new article")
                    }
                    OutlinedButton(onClick = { viewModel.unlockForNextScan() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                CameraBatchShootStep.PICK_MATCH -> {
                    pickedMatch?.let { match -> MatchPickerCard(match, viewModel) }
                }
                CameraBatchShootStep.PREVIEW -> {
                    pendingJpeg?.let { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = "Captured",
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::confirmAndSave,
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (saving) "Saving…" else "Proceed — save shot")
                        }
                        OutlinedButton(onClick = viewModel::retakePhoto, modifier = Modifier.weight(1f)) {
                            Text("Retake")
                        }
                    }
                }
                CameraBatchShootStep.AWAITING_PHOTOROOM -> {
                    locked?.let { scan -> LockedCard(scan, viewModel::removeSubBarcode) }
                    Text(
                        "JPEG saved with barcode metadata. Remove background in PhotoRoom, save PNG to your PhotoRoom folder, then import.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenPhotoroomImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Open PhotoRoom import")
                    }
                    OutlinedButton(onClick = viewModel::shootAgainSameArticle, modifier = Modifier.fillMaxWidth()) {
                        Text("Shoot again — same article")
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }

            if (step == CameraBatchShootStep.SCAN && shotCount > 0 && !singleArticleMode) {
                OutlinedButton(onClick = onDoneShooting, modifier = Modifier.fillMaxWidth()) {
                    Text("Done shooting → PhotoRoom import ($shotCount)")
                }
            }
        }
    }
}

@Composable
private fun LockedCard(scan: CameraBatchLockedState, onRemoveSubBarcode: ((String) -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArticleActionPanel(
            data = ArticlePanelData(
                articleId = scan.articleId,
                barcode = scan.barcode,
                designation = scan.designation,
                price = scan.price,
                imagePath = scan.imagePath,
                codeart = scan.codeart,
                lastPriceChangedAt = scan.lastPriceChangedAt,
                lastPrintedAt = scan.lastPrintedAt,
                subBarcodes = scan.subBarcodes,
                inGestiumCatalog = scan.inCatalog,
                linkedViaAlternate = scan.linkedViaAlternate,
                isLocked = true,
            ),
            modifier = Modifier.padding(14.dp),
            scrollable = true,
            maxHeight = 360.dp,
            onRemoveSubBarcode = onRemoveSubBarcode,
        )
    }
}

@Composable
private fun MatchPickerCard(match: ArticleWithImage, viewModel: CameraBatchShootViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CSV match", style = MaterialTheme.typography.labelLarge)
            Text(match.designation, fontWeight = FontWeight.SemiBold)
            Text("Main barcode: ${match.barcode}", style = MaterialTheme.typography.bodySmall)
            Text(PriceFormatter.format(match.price), color = MaterialTheme.colorScheme.primary)
            if (match.hasAppGalleryImage() && !match.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(match.imagePath),
                    contentDescription = match.designation,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                "Proceed = same article, different flavor (sub-barcode). Your scan becomes an alternate barcode.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = viewModel::proceedSubBarcode, modifier = Modifier.fillMaxWidth()) {
                Text("Proceed — shoot sub-barcode photo")
            }
            OutlinedButton(onClick = viewModel::addMatchToShareAndUnlock, modifier = Modifier.fillMaxWidth()) {
                Text("Add to To share & unlock")
            }
            OutlinedButton(onClick = viewModel::unlockForNextScan, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
