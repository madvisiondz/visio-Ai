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
        BLACK_ON_YELLOW("black-y"),
        MAGENTA_ON_WHITE("magenta"),
        DEGLARE("deglare"),
        ADAPTIVE_BIN("adaptive"),
        RECOVERY("recovery"),
    }

    data class Prepared(val bitmap: Bitmap, val variant: Variant, val owned: Boolean)

    /** All variants for one crop — caller must recycle owned bitmaps. */
    fun allVariants(source: Bitmap, includeRecovery: Boolean = false): List<Prepared> {
        val out = ArrayList<Prepared>(10)
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
        out.add(owned(blackOnYellow(upscaled), Variant.BLACK_ON_YELLOW))
        out.add(owned(magentaOnWhite(upscaled), Variant.MAGENTA_ON_WHITE))
        out.add(owned(degleare(upscaled), Variant.DEGLARE))
        out.add(owned(adaptiveBinary(upscaled), Variant.ADAPTIVE_BIN))

        if (includeRecovery) {
            out.add(owned(ParayTicketImagePrep.boostForRecovery(upscaled), Variant.RECOVERY))
        }
        return out
    }

    /** Price-band passes — magenta + high contrast for pink digits on yellow shelf tickets. */
    fun priceBandVariants(source: Bitmap): List<Prepared> {
        val upscaled = ParayTicketImagePrep.upscaleForOcr(source)
        val ownedUpscale = upscaled !== source
        val out = ArrayList<Prepared>(5)
        if (ownedUpscale) {
            out.add(Prepared(upscaled, Variant.UPSCALE, owned = true))
        }
        out.add(owned(magentaOnWhite(upscaled), Variant.MAGENTA_ON_WHITE))
        out.add(owned(blackOnYellow(upscaled), Variant.BLACK_ON_YELLOW))
        out.add(owned(grayHighContrast(upscaled), Variant.GRAY_HIGH))
        out.add(owned(degleare(upscaled), Variant.DEGLARE))
        if (!ownedUpscale) {
            // upscaled === source — no extra owned upscale row
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

    /** Yellow paper → white, dark designation + magenta price → black (Oasis ticket typography). */
    private fun blackOnYellow(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val warm = r > 120 && g > 100 && b < 130
            val magenta = r > 130 && r > g + 22 && b < r - 10
            pixels[i] = when {
                magenta -> Color.BLACK
                warm && lum > 165 -> Color.WHITE
                lum < 95 -> Color.BLACK
                lum < 130 && !warm -> Color.BLACK
                else -> Color.WHITE
            }
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /** Pink/magenta price digits (185 DA) → black on white — survives yellow background + plastic glare. */
    private fun magentaOnWhite(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val magenta = r > 120 && r > g + 18 && b < r
            val darkText = lum < 88
            pixels[i] = when {
                magenta || darkText -> Color.BLACK
                else -> Color.WHITE
            }
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /** Suppress specular highlights from plastic shelf rails before OCR. */
    private fun degleare(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = Color.red(p)
            var g = Color.green(p)
            var b = Color.blue(p)
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum > 210) {
                val scale = 210f / lum.toFloat()
                r = (r * scale).toInt().coerceIn(0, 255)
                g = (g * scale).toInt().coerceIn(0, 255)
                b = (b * scale).toInt().coerceIn(0, 255)
            }
            pixels[i] = Color.rgb(r, g, b)
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return ParayTicketImagePrep.enhanceForOcr(result).also {
            if (it !== result && !result.isRecycled) result.recycle()
        }
    }

    /** Local adaptive threshold — helps uneven store lighting on shelf edge. */
    private fun adaptiveBinary(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (
                0.299 * Color.red(p) +
                    0.587 * Color.green(p) +
                    0.114 * Color.blue(p)
                ).toInt()
        }
        val block = 15
        val half = block / 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var count = 0
                for (dy in -half..half) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -half..half) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        sum += gray[ny * w + nx]
                        count++
                    }
                }
                val mean = sum / count.coerceAtLeast(1)
                val idx = y * w + x
                pixels[idx] = if (gray[idx] < mean - 8) Color.BLACK else Color.WHITE
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
