package com.oasismall.oasisai.domain.backgroundremoval

import android.graphics.Bitmap
import kotlin.math.roundToInt

internal object MaskPostProcessor {

    fun applyMask(
        source: Bitmap,
        mask320: FloatArray,
        maskW: Int,
        maskH: Int,
        threshold: Float,
        edgeSmoothRadius: Int,
    ): Bitmap {
        val maskFull = resizeMaskBilinear(mask320, maskW, maskH, source.width, source.height)
        val cleaned = cleanupMask(maskFull, threshold)
        val smoothed = if (edgeSmoothRadius > 0) {
            boxBlur(cleaned, source.width, source.height, edgeSmoothRadius)
        } else {
            cleaned
        }
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val a = (smoothed[i].coerceIn(0f, 1f) * 255f).roundToInt()
            val rgb = pixels[i] and 0x00FFFFFF
            pixels[i] = (a shl 24) or rgb
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    fun cropBitmap(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (source.width * left).roundToInt().coerceIn(0, source.width - 1)
        val t = (source.height * top).roundToInt().coerceIn(0, source.height - 1)
        val r = (source.width * right).roundToInt().coerceIn(l + 1, source.width)
        val b = (source.height * bottom).roundToInt().coerceIn(t + 1, source.height)
        return Bitmap.createBitmap(source, l, t, r - l, b - t)
    }

    private fun resizeMaskBilinear(
        mask: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): FloatArray {
        val out = FloatArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y.toFloat() / dstH) * (srcH - 1)
            val y0 = sy.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceAtMost(srcH - 1)
            val fy = sy - y0
            for (x in 0 until dstW) {
                val sx = (x.toFloat() / dstW) * (srcW - 1)
                val x0 = sx.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val fx = sx - x0
                val v00 = mask[y0 * srcW + x0]
                val v10 = mask[y0 * srcW + x1]
                val v01 = mask[y1 * srcW + x0]
                val v11 = mask[y1 * srcW + x1]
                val v0 = v00 * (1 - fx) + v10 * fx
                val v1 = v01 * (1 - fx) + v11 * fx
                out[y * dstW + x] = v0 * (1 - fy) + v1 * fy
            }
        }
        return out
    }

    /** Suppress weak background pixels before edge smooth. */
    private fun cleanupMask(mask: FloatArray, threshold: Float): FloatArray {
        val floor = (threshold * 0.65f).coerceIn(0.05f, 0.5f)
        return FloatArray(mask.size) { i ->
            if (mask[i] < floor) 0f else mask[i]
        }
    }

    private fun boxBlur(mask: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = mask.copyOf()
        val out = FloatArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        sum += tmp[yy * w + xx]
                        count++
                    }
                }
                out[y * w + x] = sum / count
            }
        }
        return out
    }
}
