package com.oasismall.oasisai.domain.layoutagent

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the visible product region in a cutout PNG (ignores transparent margins).
 */
data class ContentBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = max(0, right - left)
    val height: Int get() = max(0, bottom - top)
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

object ProductContentBounds {
    private const val ALPHA_THRESHOLD = 24

    fun detect(bitmap: Bitmap): ContentBounds {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return ContentBounds(0, 0, 0, 0)

        val step = when {
            w * h > 2_000_000 -> 4
            w * h > 500_000 -> 2
            else -> 1
        }

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var found = false

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha > ALPHA_THRESHOLD) {
                    found = true
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
                x += step
            }
            y += step
        }

        if (!found) return ContentBounds(0, 0, w, h)
        val pad = step
        return ContentBounds(
            left = (minX - pad).coerceAtLeast(0),
            top = (minY - pad).coerceAtLeast(0),
            right = (maxX + pad + 1).coerceAtMost(w),
            bottom = (maxY + pad + 1).coerceAtMost(h),
        )
    }
}
