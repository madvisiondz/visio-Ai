package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Frame quality for ticket tap — sharpness + practical ticket visual signal
 * (warm paper, price anchor, text edges — NOT pure yellow required).
 */
object ParayTicketFrameQuality {
    private const val SAMPLE_MAX = 240
    private const val GRID = 12

    data class Report(
        val composite: Float,
        val sharpness: Float,
        val ticketSignal: Float,
    ) {
        val label: String get() = labelFor(composite)
        val readyToSnap: Boolean get() = composite >= MIN_SNAP_QUALITY
    }

    fun evaluate(bitmap: Bitmap): Report {
        val sample = ParayTicketBitmapUtils.downscale(bitmap, SAMPLE_MAX)
        val owned = sample !== bitmap
        val sharpness = measureSharpness(sample)
        val ticket = measureTicketSignal(sample)
        if (owned && !sample.isRecycled) sample.recycle()
        val composite = (0.55f * sharpness + 0.45f * ticket).coerceIn(0f, 1f)
        return Report(composite, sharpness, ticket)
    }

    fun labelFor(score: Float): String = when {
        score >= 0.72f -> "Excellent — tap to capture"
        score >= 0.55f -> "Good — tap to capture"
        score >= 0.38f -> "Fair — move closer to ticket"
        else -> "Point at ticket…"
    }

    private fun measureSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return 0f
        val stepX = max(1, w / GRID)
        val stepY = max(1, h / GRID)
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        var y = stepY
        while (y < h - stepY) {
            var x = stepX
            while (x < w - stepX) {
                val c = luminance(bitmap.getPixel(x, y))
                val lap = abs(
                    4 * c -
                        luminance(bitmap.getPixel(x - stepX, y)) -
                        luminance(bitmap.getPixel(x + stepX, y)) -
                        luminance(bitmap.getPixel(x, y - stepY)) -
                        luminance(bitmap.getPixel(x, y + stepY)),
                )
                sum += lap
                sumSq += lap * lap
                n++
                x += stepX
            }
            y += stepY
        }
        if (n == 0) return 0f
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        return (variance / 2_500.0).toFloat().coerceIn(0f, 1f)
    }

    private fun measureTicketSignal(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(4, min(w, h) / GRID)
        var warm = 0
        var price = 0
        var edge = 0
        var samples = 0
        var y = step
        while (y < h - step) {
            var x = step
            while (x < w - step) {
                val p = bitmap.getPixel(x, y)
                if (isWarmTone(p)) warm++
                if (isPriceTone(p)) price++
                val lum = luminance(p)
                val lumR = luminance(bitmap.getPixel(x + step, y))
                if (abs(lum - lumR) > 18) edge++
                samples++
                x += step
            }
            y += step
        }
        if (samples == 0) return 0f
        val warmR = warm.toFloat() / samples
        val priceR = price.toFloat() / samples
        val edgeR = edge.toFloat() / samples
        return (warmR / 0.10f * 0.35f + priceR / 0.04f * 0.25f + edgeR / 0.15f * 0.40f).coerceIn(0f, 1f)
    }

    private fun isWarmTone(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 120 && g > 95 && b < 130 && (r - b) > 20
    }

    private fun isPriceTone(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 100 && b > 70 && g < r * 0.8f
    }

    private fun luminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    const val MIN_SNAP_QUALITY = 0.28f
    const val MIN_OFFER_QUALITY = 0.18f
}
