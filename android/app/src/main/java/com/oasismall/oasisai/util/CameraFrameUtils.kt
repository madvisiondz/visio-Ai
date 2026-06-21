package com.oasismall.oasisai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import com.oasismall.oasisai.domain.paray.VisualFeatureExtractor
import java.io.ByteArrayOutputStream

object CameraFrameUtils {
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

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null
        val nv21 = yuv420888ToNv21(imageProxy) ?: return null
        val yuv = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 75, out)
        val jpeg = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }
}
