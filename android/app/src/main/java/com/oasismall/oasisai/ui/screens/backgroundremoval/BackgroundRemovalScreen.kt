package com.oasismall.oasisai.ui.screens.backgroundremoval

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemovalScreen(
    viewModel: BackgroundRemovalViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.setSourceFromUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remove background") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ui.modelReady) {
                Text(
                    "Background model not found. Install the latest OasisAI-debug.apk from the project folder.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ui.article?.let {
                Text(it.designation, style = MaterialTheme.typography.titleMedium)
                Text("Barcode: ${it.barcode}", style = MaterialTheme.typography.bodySmall)
            }
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (ui.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(ui.progressLabel ?: "Processing…", style = MaterialTheme.typography.bodySmall)
            }
            when (ui.step) {
                BgRemovalStep.PICK -> {
                    Text(
                        "Pick a product photo (camera gallery or file). Processing stays fully offline on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Import photo")
                    }
                }
                BgRemovalStep.ADJUST -> {
                    ui.sourcePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Source",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.LightGray),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text("Crop trim (optional)", style = MaterialTheme.typography.labelMedium)
                    CropSlider("Left", ui.cropLeft) { v ->
                        viewModel.setCrop(v, ui.cropTop, ui.cropRight, ui.cropBottom)
                    }
                    CropSlider("Top", ui.cropTop) { v ->
                        viewModel.setCrop(ui.cropLeft, v, ui.cropRight, ui.cropBottom)
                    }
                    CropSlider("Right", 1f - ui.cropRight) { v ->
                        viewModel.setCrop(ui.cropLeft, ui.cropTop, 1f - v, ui.cropBottom)
                    }
                    CropSlider("Bottom", 1f - ui.cropBottom) { v ->
                        viewModel.setCrop(ui.cropLeft, ui.cropTop, ui.cropRight, 1f - v)
                    }
                    Text("Mask threshold", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = ui.maskThreshold,
                        onValueChange = viewModel::setMaskThreshold,
                        valueRange = 0.2f..0.8f,
                    )
                    Text("Edge smooth: ${ui.edgeSmooth}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = ui.edgeSmooth.toFloat(),
                        onValueChange = { viewModel.setEdgeSmooth(it.toInt()) },
                        valueRange = 0f..6f,
                        steps = 5,
                    )
                    Button(
                        onClick = viewModel::removeBackground,
                        enabled = ui.modelReady && !ui.isProcessing && ui.sourcePath != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove background")
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Change photo")
                    }
                }
                BgRemovalStep.PREVIEW -> {
                    Text("Before / after", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewPane(
                            label = "Original",
                            path = ui.originalBackupPath ?: ui.sourcePath,
                            modifier = Modifier.weight(1f),
                        )
                        PreviewPane(
                            label = "Cutout",
                            path = ui.previewOutputPath,
                            modifier = Modifier.weight(1f),
                            checkerboard = true,
                        )
                    }
                    Button(
                        onClick = { viewModel.acceptResult(onBack) },
                        enabled = !ui.isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Accept result")
                    }
                    OutlinedButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry")
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use original")
                    }
                }
            }
            if (ui.isProcessing && ui.step != BgRemovalStep.ADJUST) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CropSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${(value * 100).toInt()}%")
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..0.35f,
        )
    }
}

@Composable
private fun PreviewPane(
    label: String,
    path: String?,
    modifier: Modifier = Modifier,
    checkerboard: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(if (checkerboard) Color(0xFFE0E0E0) else Color.LightGray),
        ) {
            if (!path.isNullOrBlank() && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
