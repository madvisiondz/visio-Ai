package com.oasismall.oasisai.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.ui.components.ScanResultPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onArticleClick: (Long) -> Unit,
    onCreateAsset: (String) -> Unit,
) {
    val context = LocalContext.current
    var manualBarcode by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val result by viewModel.result.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val lastScanned by viewModel.lastScannedBarcode.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Barcode Scanner") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Point camera at barcode or type manually to verify shelf tickets.")
            }

            item {
                if (hasCameraPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        BarcodeCameraPreview(
                            enabled = true,
                            onBarcodeDetected = { viewModel.onBarcodeScanned(it, fromCamera = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                        )
                    }
                    lastScanned?.let {
                        Text("Last scan: $it", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Camera permission required for live scanning.")
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Allow camera")
                            }
                        }
                    }
                }
            }

            item {
                Text("Manual entry", style = MaterialTheme.typography.titleSmall)
            }

            item {
                OutlinedTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barcode") },
                    singleLine = true,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onBarcodeScanned(manualBarcode) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Look up") }
                    Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear")
                    }
                }
            }

            if (notFound) {
                item {
                    Text(
                        "Article not found in local database.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            result?.let { article ->
                item {
                    ScanResultPanel(
                        article = article,
                        onAddToShare = { viewModel.addToShareCart(article) },
                        onMarkVerified = { viewModel.markTicketVerified(article.id) },
                        onOpenDetail = { onArticleClick(article.id) },
                        onCreateAsset = { onCreateAsset(article.barcode) },
                    )
                }
            }
        }
    }
}
