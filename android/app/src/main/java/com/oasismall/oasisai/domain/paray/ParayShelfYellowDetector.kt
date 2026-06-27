package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.oasismall.oasisai.util.OasisLog
import kotlin.math.max
import kotlin.math.min

/**
 * Finds Oasis shelf ticket yellow blocks (#FFE500) using largest connected blob —
 * rejects thin strips / wood-grain false positives.
 */
object ParayShelfYellowDetector {
    private const val MIN_BOX_PX = 48
    private const val GRID_PX = 8
    private const val MIN_YELLOW_DENSITY = 0.22f
    private const val MIN_FRAME_AREA_RATIO = 0.012f

    data class TicketCrops(
        val yellowRect: Rect,
        val yellowCrop: Bitmap,
        val productCrop: Bitmap?,
    )

    fun findLargestYellowRect(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < MIN_BOX_PX || h < MIN_BOX_PX) return null

        val sampleMax = 640
        val sample = ParayTicketBitmapUtils.downscale(bitmap, sampleMax)
        val owned = sample !== bitmap
        val rect = findOnBitmap(sample)?.let { mapRectToFull(it, sample.width, sample.height, w, h) }
        if (owned && !sample.isRecycled) sample.recycle()
        rect?.let { validateRect(bitmap, it) } ?: return null
        OasisLog.d(OasisLog.Domain.Paray, "Ticket yellow: ${rect.width()}x${rect.height()} on ${w}x${h}")
        return rect
    }

    fun detectAndCrop(bitmap: Bitmap): TicketCrops? {
        val yellowRect = findLargestYellowRect(bitmap) ?: return null
        val yellowCrop = crop(bitmap, yellowRect)
        val productRect = productRectLeftOf(yellowRect, bitmap.width)
        val productCrop = if (productRect.width() >= 24 && productRect.height() >= 24) {
            crop(bitmap, productRect)
        } else {
            null
        }
        return TicketCrops(yellowRect, yellowCrop, productCrop)
    }

    fun crop(bitmap: Bitmap, rect: Rect): Bitmap =
        Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())

    /** Product PNG strip — left of yellow block (Oasis shelf layout ratio). */
    fun productRectLeftOf(yellowRect: Rect, bitmapWidth: Int): Rect {
        val ratio = com.oasismall.oasisai.domain.design.ShelfA4Renderer.IMAGE_W_MM /
            com.oasismall.oasisai.domain.design.ShelfA4Renderer.YELLOW_W_MM
        val productW = (yellowRect.width() * ratio).toInt().coerceAtLeast(MIN_BOX_PX / 2)
        val left = (yellowRect.left - productW).coerceAtLeast(0)
        return Rect(left, yellowRect.top, yellowRect.left, yellowRect.bottom)
    }

    private fun findOnBitmap(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        val cols = (w + GRID_PX - 1) / GRID_PX
        val rows = (h + GRID_PX - 1) / GRID_PX
        if (cols < 2 || rows < 2) return null

        val cells = BooleanArray(cols * rows)
        for (ry in 0 until rows) {
            val cy = min(ry * GRID_PX + GRID_PX / 2, h - 1)
            for (rx in 0 until cols) {
                val cx = min(rx * GRID_PX + GRID_PX / 2, w - 1)
                cells[ry * cols + rx] = isShelfYellow(bitmap.getPixel(cx, cy))
            }
        }

        var bestArea = 0
        var bestRect: Rect? = null
        val visited = BooleanArray(cols * rows)

        for (start in cells.indices) {
            if (!cells[start] || visited[start]) continue
            var minRx = cols
            var minRy = rows
            var maxRx = 0
            var maxRy = 0
            var area = 0
            val queue = ArrayDeque<Int>()
            queue.add(start)
            visited[start] = true
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                val ry = idx / cols
                val rx = idx % cols
                area++
                minRx = min(minRx, rx)
                minRy = min(minRy, ry)
                maxRx = max(maxRx, rx)
                maxRy = max(maxRy, ry)
                for (neighbor in neighbors(rx, ry, cols, rows)) {
                    val nIdx = neighbor.second * cols + neighbor.first
                    if (!cells[nIdx] || visited[nIdx]) continue
                    visited[nIdx] = true
                    queue.add(nIdx)
                }
            }
            if (area < 4) continue
            val left = minRx * GRID_PX
            val top = minRy * GRID_PX
            val right = min((maxRx + 1) * GRID_PX, w)
            val bottom = min((maxRy + 1) * GRID_PX, h)
            val candidate = Rect(left, top, right, bottom)
            val candidateArea = candidate.width() * candidate.height()
            if (candidateArea > bestArea) {
                bestArea = candidateArea
                bestRect = candidate
            }
        }
        return bestRect?.let { padRect(it, w, h) }
    }

    private fun neighbors(rx: Int, ry: Int, cols: Int, rows: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(4)
        if (rx > 0) out.add(rx - 1 to ry)
        if (rx < cols - 1) out.add(rx + 1 to ry)
        if (ry > 0) out.add(rx to ry - 1)
        if (ry < rows - 1) out.add(rx to ry + 1)
        return out
    }

    private fun padRect(rect: Rect, maxW: Int, maxH: Int): Rect {
        val padX = (rect.width() * 0.04f).toInt().coerceAtLeast(2)
        val padY = (rect.height() * 0.04f).toInt().coerceAtLeast(2)
        return Rect(
            (rect.left - padX).coerceAtLeast(0),
            (rect.top - padY).coerceAtLeast(0),
            (rect.right + padX).coerceAtMost(maxW),
            (rect.bottom + padY).coerceAtMost(maxH),
        )
    }

    private fun mapRectToFull(sampleRect: Rect, sw: Int, sh: Int, fw: Int, fh: Int): Rect {
        val sx = fw.toFloat() / sw.toFloat()
        val sy = fh.toFloat() / sh.toFloat()
        return Rect(
            (sampleRect.left * sx).toInt().coerceIn(0, fw - 1),
            (sampleRect.top * sy).toInt().coerceIn(0, fh - 1),
            (sampleRect.right * sx).toInt().coerceIn(1, fw),
            (sampleRect.bottom * sy).toInt().coerceIn(1, fh),
        ).let { padRect(it, fw, fh) }
    }

    private fun validateRect(bitmap: Bitmap, rect: Rect): Rect? {
        val fw = bitmap.width
        val fh = bitmap.height
        if (rect.width() < (fw * 0.06f).toInt().coerceAtLeast(MIN_BOX_PX)) return null
        if (rect.height() < (fh * 0.04f).toInt().coerceAtLeast(MIN_BOX_PX)) return null
        val areaRatio = rect.width().toFloat() * rect.height().toFloat() / (fw.toFloat() * fh.toFloat())
        if (areaRatio < MIN_FRAME_AREA_RATIO) return null
        val aspect = rect.width().toFloat() / rect.height().toFloat().coerceAtLeast(1f)
        if (aspect > 6f || aspect < 0.15f) return null
        if (yellowDensity(bitmap, rect) < MIN_YELLOW_DENSITY) return null
        return rect
    }

    private fun yellowDensity(bitmap: Bitmap, rect: Rect): Float {
        val step = max(3, min(rect.width(), rect.height()) / 24)
        var hits = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                if (isShelfYellow(bitmap.getPixel(x, y))) hits++
                samples++
                x += step
            }
            y += step
        }
        if (samples == 0) return 0f
        return hits.toFloat() / samples.toFloat()
    }

    private fun isShelfYellow(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        if (r > 170 && g > 135 && b < 110 && (r + g) > b * 2 + 80) return true
        val maxC = max(r, max(g, b)).toFloat()
        if (maxC < 90f) return false
        val rn = r / maxC
        val gn = g / maxC
        val bn = b / maxC
        return rn > 0.80f && gn > 0.70f && bn < 0.50f && (r - b) > 50
    }
}
