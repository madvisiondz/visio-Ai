package com.oasismall.oasisai.ui.screens.scanner

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeCameraPreview(
    enabled: Boolean,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
    barcodeScanEnabled: Boolean = enabled,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onBarcode by rememberUpdatedState(onBarcodeDetected)
    val scanActive by rememberUpdatedState(barcodeScanEnabled)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_ITF,
                )
                .build(),
        )
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    // Camera binds once per screen; lock/unlock only toggles scanActive (never restart camera).
    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        var boundProvider: ProcessCameraProvider? = null
        val disposed = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener(
            {
                if (disposed.get()) return@addListener
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                boundProvider = provider

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (scanActive) {
                        scanBarcodes(barcodeScanner, imageProxy) { value ->
                            mainExecutor.execute { onBarcode(value) }
                        }
                    } else {
                        imageProxy.close()
                    }
                }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed.set(true)
            boundProvider?.unbindAll()
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun scanBarcodes(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: androidx.camera.core.ImageProxy,
    onFound: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                ?.let(onFound)
        }
        .addOnCompleteListener { imageProxy.close() }
}
