package com.oasismall.oasisai.ui.screens.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oasismall.oasisai.ui.screens.checkshoot.TicketCameraBuffer
import com.oasismall.oasisai.util.CameraFrameUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeCameraPreview(
    enabled: Boolean,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
    barcodeScanEnabled: Boolean = enabled,
    ticketScanEnabled: Boolean = false,
    ticketBuffer: TicketCameraBuffer? = null,
    onTicketFrame: ((android.graphics.Bitmap, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onBarcode by rememberUpdatedState(onBarcodeDetected)
    val scanActive by rememberUpdatedState(barcodeScanEnabled)
    val ticketActive by rememberUpdatedState(ticketScanEnabled)
    val buffer by rememberUpdatedState(ticketBuffer)
    val onTicket by rememberUpdatedState(onTicketFrame)
    var lastTicketFrameMs by remember { mutableLongStateOf(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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

    DisposableEffect(enabled, lifecycleOwner, ticketActive) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        var boundProvider: ProcessCameraProvider? = null
        val disposed = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val highQualityTicket = ticketActive || ticketBuffer != null

        future.addListener(
            {
                if (disposed.get()) return@addListener
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                boundProvider = provider

                val resolutionSelector = if (highQualityTicket) {
                    CameraFrameUtils.highResolutionSelector()
                } else {
                    CameraFrameUtils.standardResolutionSelector()
                }

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val cameraSelector = if (highQualityTicket) {
                    CameraFrameUtils.bestBackCameraSelector(provider)
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    if (ticketActive) {
                        val now = System.currentTimeMillis()
                        if (now - lastTicketFrameMs >= TICKET_BUFFER_INTERVAL_MS) {
                            lastTicketFrameMs = now
                            CameraFrameUtils.imageProxyToBitmap(imageProxy, highQuality = true)?.let { bitmap ->
                                when {
                                    buffer != null -> buffer!!.offer(bitmap, rotation)
                                    onTicket != null -> {
                                        val callback = onTicket
                                        mainExecutor.execute { callback?.invoke(bitmap, rotation) }
                                    }
                                    else -> bitmap.recycle()
                                }
                            }
                        }
                    }
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
                        cameraSelector,
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

private const val TICKET_BUFFER_INTERVAL_MS = 150L
