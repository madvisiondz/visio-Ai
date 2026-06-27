package com.oasismall.oasisai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Size
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import com.oasismall.oasisai.domain.paray.VisualFeatureExtractor
import java.io.ByteArrayOutputStream

object CameraFrameUtils {
    private const val JPEG_QUALITY_DEFAULT = 85
    private const val JPEG_QUALITY_TICKET = 95

    fun extractFeatures(imageProxy: ImageProxy): VisualFeatureExtractor.Features? {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return null
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }
        return VisualFeatureExtractor.extract(bitmap, content)
    }

    /** Converts CameraX YUV frame to bitmap — correct stride handling (fixes stripe/stretch artifacts). */
    fun imageProxyToBitmap(imageProxy: ImageProxy, highQuality: Boolean = false): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null
        val nv21 = yuv420888ToNv21(imageProxy) ?: return null
        val yuv = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        val quality = if (highQuality) JPEG_QUALITY_TICKET else JPEG_QUALITY_DEFAULT
        yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), quality, out)
        val jpeg = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    /**
     * Pick the main back camera (largest sensor + standard focal length), not ultrawide/macro.
     */
    fun bestBackCameraSelector(provider: ProcessCameraProvider): CameraSelector {
        val backs = provider.availableCameraInfos.filter { info ->
            val c2 = Camera2CameraInfo.from(info)
            c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        if (backs.size <= 1) return CameraSelector.DEFAULT_BACK_CAMERA

        val best = backs.maxByOrNull { scoreCamera(it) } ?: return CameraSelector.DEFAULT_BACK_CAMERA
        return CameraSelector.Builder()
            .addCameraFilter { cameras ->
                cameras.filter { it == best }.ifEmpty { cameras }
            }
            .build()
    }

    fun highResolutionSelector(): ResolutionSelector =
        ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(3840, 2160),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

    fun standardResolutionSelector(): ResolutionSelector =
        ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

    private fun scoreCamera(info: CameraInfo): Long {
        val c2 = Camera2CameraInfo.from(info)
        val map = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
        val maxArea = sizes.maxOfOrNull { it.width.toLong() * it.height } ?: 0L
        val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: 4.5f
        val focalBonus = when {
            focal in 3.2f..8.5f -> 2_000_000_000L
            focal < 2.8f -> -500_000_000L
            else -> 500_000_000L
        }
        return maxArea + focalBonus
    }

    /**
     * Proper YUV_420_888 → NV21 with row/pixel stride — naive plane concat causes vertical stripes.
     */
    private fun yuv420888ToNv21(image: ImageProxy): ByteArray? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        copyLumaPlane(image.planes[0], width, height, nv21, 0)
        copyChromaPlanes(image.planes[1], image.planes[2], width, height, nv21, ySize)
        return nv21
    }

    private fun copyLumaPlane(
        plane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int,
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var outputPos = offset
        if (pixelStride == 1 && rowStride == width) {
            buffer.position(0)
            buffer.get(out, outputPos, width * height)
            return
        }
        for (row in 0 until height) {
            var inputPos = row * rowStride
            for (col in 0 until width) {
                out[outputPos++] = buffer.get(inputPos)
                inputPos += pixelStride
            }
        }
    }

    private fun copyChromaPlanes(
        uPlane: ImageProxy.PlaneProxy,
        vPlane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int,
    ) {
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var outputPos = offset
        if (uPixelStride == 2 && vPixelStride == 2 && uRowStride == width && vRowStride == width) {
            vBuffer.position(0)
            val vuSize = chromaWidth * chromaHeight * 2
            vBuffer.get(out, outputPos, vuSize.coerceAtMost(vBuffer.remaining()))
            return
        }

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                out[outputPos++] = vBuffer.get(vIndex)
                out[outputPos++] = uBuffer.get(uIndex)
            }
        }
    }
}
