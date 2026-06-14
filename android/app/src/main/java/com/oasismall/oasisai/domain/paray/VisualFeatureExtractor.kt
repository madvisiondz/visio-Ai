package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Color
import com.oasismall.oasisai.domain.layoutagent.AppLayoutKnowledge
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import kotlin.math.max

object VisualFeatureExtractor {
    private const val ALPHA_MIN = 24
    private const val COLOR_BUCKETS = 3

    data class Features(
        val shapeAspect: Float,
        val fillRatio: Float,
        val dominantColors: List<Int>,
    )

    fun extract(bitmap: Bitmap, bounds: ContentBounds): Features {
        val cw = max(1, bounds.width)
        val ch = max(1, bounds.height)
        val shapeAspect = cw.toFloat() / ch
        val bitmapArea = max(1, bitmap.width * bitmap.height)
        val fillRatio = (cw * ch).toFloat() / bitmapArea

        val colors = sampleDominantColors(bitmap, bounds)
        return Features(shapeAspect, fillRatio, colors)
    }

    fun typographyOf(designation: String): Pair<Int, Int> {
        val words = designation.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size to designation.trim().length
    }

    fun shelfLabelPalette(): List<Int> = listOf(
        Color.parseColor("#FFE500"),
        Color.parseColor("#E60000"),
        Color.BLACK,
    )

    private fun sampleDominantColors(bitmap: Bitmap, bounds: ContentBounds): List<Int> {
        val counts = linkedMapOf<Int, Int>()
        val step = when {
            bounds.width * bounds.height > 80_000 -> 6
            bounds.width * bounds.height > 20_000 -> 4
            else -> 2
        }

        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                val a = (pixel ushr 24) and 0xFF
                if (a > ALPHA_MIN) {
                    val bucket = quantizeColor(pixel)
                    counts[bucket] = (counts[bucket] ?: 0) + 1
                }
                x += step
            }
            y += step
        }

        return counts.entries
            .sortedByDescending { it.value }
            .take(COLOR_BUCKETS)
            .map { it.key }
            .ifEmpty { listOf(Color.GRAY) }
    }

    /** 5-bit per channel quantization for stable color learning */
    private fun quantizeColor(pixel: Int): Int {
        val r = ((pixel shr 16) and 0xFF) and 0xF8
        val g = ((pixel shr 8) and 0xFF) and 0xF8
        val b = (pixel and 0xFF) and 0xF8
        return Color.rgb(r, g, b)
    }

    fun templateId(): String = AppLayoutKnowledge.SHELF_TEMPLATE_ID
}
