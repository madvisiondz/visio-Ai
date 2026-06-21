package com.oasismall.oasisai.ui.screens.paraylearn

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oasismall.oasisai.domain.paray.VisualFeatureExtractor
import com.oasismall.oasisai.util.CameraFrameUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Live camera for PARAY Learn — delivers visual features from each analyzed frame. */
@Composable
fun ParayLearnCameraPreview(
    enabled: Boolean,
    onFrameFeatures: (VisualFeatureExtractor.Features) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFrame by rememberUpdatedState(onFrameFeatures)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose { }

        var boundProvider: ProcessCameraProvider? = null
        val disposed = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener(
            {
                if (disposed.get()) return@addListener
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                boundProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy: ImageProxy ->
                    val features = CameraFrameUtils.extractFeatures(proxy)
                    proxy.close()
                    if (features != null) {
                        mainExecutor.execute { onFrame(features) }
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
            runCatching { boundProvider?.unbindAll() }
            cameraExecutor.shutdown()
        }
    }
}
