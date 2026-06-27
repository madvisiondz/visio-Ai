package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Preprocessing corps — generates OCR-ready variants for difficult real-world tickets
 * (dim light, faded yellow, glare, blur, off-white paper).
 */
object ParayTicketPreprocessCorps {
    enum class Variant(val label: String) {
        UPSCALE("upscale"),
        CONTRAST("contrast"),
        SHARPEN("sharpen"),
        WARM_BOOST("warm"),
        GRAY_HIGH("gray-hi"),
        RECOVERY("recovery"),
    }

    data class Prepared(val bitmap: Bitmap, val variant: Variant, val owned: Boolean)

    /** All variants for one crop — caller must recycle owned bitmaps. */
    fun allVariants(source: Bitmap, includeRecovery: Boolean = false): List<Prepared> {
        val out = ArrayList<Prepared>(6)
        val upscaled = ParayTicketImagePrep.upscaleForOcr(source)
        if (upscaled !== source) {
            out.add(Prepared(upscaled, Variant.UPSCALE, owned = true))
        } else {
            out.add(Prepared(upscaled, Variant.UPSCALE, owned = false))
        }

        out.add(owned(ParayTicketImagePrep.enhanceForOcr(upscaled), Variant.CONTRAST))
        out.add(owned(sharpen(upscaled), Variant.SHARPEN))
        out.add(owned(warmChannelBoost(upscaled), Variant.WARM_BOOST))
        out.add(owned(grayHighContrast(upscaled), Variant.GRAY_HIGH))

        if (includeRecovery) {
            out.add(owned(ParayTicketImagePrep.boostForRecovery(upscaled), Variant.RECOVERY))
        }
        return out
    }

    fun recycleAll(prepared: List<Prepared>) {
        prepared.filter { it.owned }.forEach { bmp ->
            if (!bmp.bitmap.isRecycled) bmp.bitmap.recycle()
        }
    }

    private fun owned(bitmap: Bitmap, variant: Variant) = Prepared(bitmap, variant, owned = true)

    private fun sharpen(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix(
            floatArrayOf(
                0f, -1f, 0f, 0f, 0f,
                -1f, 5f, -1f, 0f, 0f,
                0f, -1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Boost warm/cream ticket paper without requiring pure #FFE500. */
    private fun warmChannelBoost(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = Color.red(p)
            var g = Color.green(p)
            val b = Color.blue(p)
            r = (r * 1.12f).toInt().coerceIn(0, 255)
            g = (g * 1.06f).toInt().coerceIn(0, 255)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val nr = (r * 0.7 + gray * 0.3).toInt().coerceIn(0, 255)
            val ng = (g * 0.7 + gray * 0.3).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(nr, ng, (b * 0.88f).toInt().coerceIn(0, 255))
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun grayHighContrast(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.6f, 0f, 0f, 0f, -40f,
                        0f, 1.6f, 0f, 0f, -40f,
                        0f, 0f, 1.6f, 0f, -40f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
