package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.oasismall.oasisai.domain.design.ShelfA4Renderer
import com.oasismall.oasisai.util.OasisLog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Visual ticket region scout — finds Oasis shelf tickets without requiring pure yellow.
 *
 * Strategies (scored, best wins):
 * 1. Warm/cream price block (relaxed color — aged/faded tickets OK)
 * 2. Magenta/pink price anchor (Oasis printed price color)
 * 3. Text-density band (edge-rich designation area)
 * 4. Layout card (product left + text right — print aspect ratio)
 * 5. Center fallback (full ticket in frame)
 */
object ParayTicketRegionScout {
    private const val GRID = 8
    private const val MIN_AREA_RATIO = 0.008f

    data class TicketRegions(
        val ticketRect: Rect,
        val textRect: Rect,
        val textCrop: Bitmap,
        val productCrop: Bitmap?,
        val strategy: String,
        val confidence: Float,
    )

    private data class Candidate(
        val rect: Rect,
        val textRect: Rect,
        val strategy: String,
        val score: Float,
    )

    fun locate(bitmap: Bitmap): TicketRegions? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 80 || h < 80) return null

        val sample = ParayTicketBitmapUtils.downscale(bitmap, 720)
        val owned = sample !== bitmap
        val scaleX = w.toFloat() / sample.width
        val scaleY = h.toFloat() / sample.height

        val candidates = buildList {
            addAll(scoutWarmBlocks(sample))
            addAll(scoutPriceAnchors(sample))
            addAll(scoutTextBands(sample))
            addAll(scoutLayoutCards(sample))
            add(centerFallback(sample))
        }.map { c ->
            c.copy(
                rect = scaleRect(c.rect, scaleX, scaleY, w, h),
                textRect = scaleRect(c.textRect, scaleX, scaleY, w, h),
            )
        }.filter { it.score > 0.15f && validateCandidate(it, w, h) }

        if (owned && !sample.isRecycled) sample.recycle()

        val best = candidates.maxByOrNull { it.score } ?: return null
        OasisLog.d(OasisLog.Domain.Paray, "Ticket scout: ${best.strategy} score=${"%.2f".format(best.score)}")

        val textCrop = ParayShelfYellowDetector.crop(bitmap, best.textRect)
        val productRect = productRectLeftOf(best.textRect, w)
        val productCrop = if (productRect.width() >= 24 && productRect.height() >= 24) {
            ParayShelfYellowDetector.crop(bitmap, productRect)
        } else {
            null
        }

        return TicketRegions(
            ticketRect = best.rect,
            textRect = best.textRect,
            textCrop = textCrop,
            productCrop = productCrop,
            strategy = best.strategy,
            confidence = best.score.coerceIn(0f, 1f),
        )
    }

    private fun scoutWarmBlocks(bitmap: Bitmap): List<Candidate> =
        findColorBlobs(bitmap, ::isWarmTicketTone, "warm-block")

    private fun scoutPriceAnchors(bitmap: Bitmap): List<Candidate> =
        findColorBlobs(bitmap, ::isPriceAnchorTone, "price-anchor")

    private fun scoutTextBands(bitmap: Bitmap): List<Candidate> {
        val w = bitmap.width
        val h = bitmap.height
        val cols = (w + GRID - 1) / GRID
        val rows = (h + GRID - 1) / GRID
        val edge = FloatArray(cols * rows)
        for (ry in 1 until rows - 1) {
            for (rx in 1 until cols - 1) {
                val cx = min(rx * GRID + GRID / 2, w - 1)
                val cy = min(ry * GRID + GRID / 2, h - 1)
                val lum = luminance(bitmap.getPixel(cx, cy))
                val lumR = luminance(bitmap.getPixel(min(cx + GRID, w - 1), cy))
                edge[ry * cols + rx] = abs(lum - lumR).toFloat()
            }
        }
        return findHighDensityRects(bitmap, edge, cols, rows, w, h, GRID, "text-band", aspectWide = true)
    }

    private fun scoutLayoutCards(bitmap: Bitmap): List<Candidate> {
        val w = bitmap.width
        val h = bitmap.height
        val targetAspect = ShelfA4Renderer.ticketAspectRatio
        val roi = Rect((w * 0.05f).toInt(), (h * 0.08f).toInt(), (w * 0.95f).toInt(), (h * 0.92f).toInt())
        val cardW = roi.width()
        val cardH = (cardW / targetAspect).toInt().coerceIn(roi.height() / 4, roi.height())
        val top = roi.top + (roi.height() - cardH) / 2
        val card = Rect(roi.left, top, roi.left + cardW, top + cardH)
        val yellowW = (cardW * (ShelfA4Renderer.YELLOW_W_MM / (ShelfA4Renderer.IMAGE_W_MM + ShelfA4Renderer.YELLOW_W_MM))).toInt()
        val textRect = Rect(card.right - yellowW, card.top, card.right, card.bottom)
        val score = layoutScore(bitmap, card) * 0.85f
        return listOf(Candidate(card, textRect, "layout-card", score))
    }

    private fun centerFallback(bitmap: Bitmap): Candidate {
        val w = bitmap.width
        val h = bitmap.height
        val rect = Rect((w * 0.06f).toInt(), (h * 0.10f).toInt(), (w * 0.94f).toInt(), (h * 0.90f).toInt())
        val textW = (rect.width() * 0.38f).toInt().coerceAtLeast(60)
        val textRect = Rect(rect.right - textW, rect.top, rect.right, rect.bottom)
        return Candidate(rect, textRect, "center-fallback", 0.35f)
    }

    private fun findColorBlobs(bitmap: Bitmap, predicate: (Int) -> Boolean, strategy: String): List<Candidate> {
        val w = bitmap.width
        val h = bitmap.height
        val cols = (w + GRID - 1) / GRID
        val rows = (h + GRID - 1) / GRID
        val cells = BooleanArray(cols * rows)
        for (ry in 0 until rows) {
            val cy = min(ry * GRID + GRID / 2, h - 1)
            for (rx in 0 until cols) {
                val cx = min(rx * GRID + GRID / 2, w - 1)
                cells[ry * cols + rx] = predicate(bitmap.getPixel(cx, cy))
            }
        }
        return connectedComponents(bitmap, cells, cols, rows, w, h, GRID, strategy)
    }

    private fun connectedComponents(
        bitmap: Bitmap,
        cells: BooleanArray,
        cols: Int,
        rows: Int,
        w: Int,
        h: Int,
        gridPx: Int,
        strategy: String,
    ): List<Candidate> {
        val visited = BooleanArray(cells.size)
        val out = ArrayList<Candidate>()
        for (start in cells.indices) {
            if (!cells[start] || visited[start]) continue
            var minRx = cols
            var minRy = rows
            var maxRx = 0
            var maxRy = 0
            var count = 0
            val q = ArrayDeque<Int>()
            q.add(start)
            visited[start] = true
            while (q.isNotEmpty()) {
                val idx = q.removeFirst()
                val ry = idx / cols
                val rx = idx % cols
                count++
                minRx = min(minRx, rx)
                minRy = min(minRy, ry)
                maxRx = max(maxRx, rx)
                maxRy = max(maxRy, ry)
                for ((nx, ny) in neighbors4(rx, ry, cols, rows)) {
                    val n = ny * cols + nx
                    if (!cells[n] || visited[n]) continue
                    visited[n] = true
                    q.add(n)
                }
            }
            if (count < 3) continue
            val gridW = (maxRx - minRx + 1) * gridPx
            val gridH = (maxRy - minRy + 1) * gridPx
            if (strategy == "warm-block") {
                if (gridW < w * 0.14f) continue
                if (gridH < h * 0.10f) continue
                val blobAspect = gridW.toFloat() / gridH.coerceAtLeast(1)
                if (blobAspect < 0.45f) continue
            }
            val rect = padRect(
                Rect(minRx * gridPx, minRy * gridPx, min((maxRx + 1) * gridPx, w), min((maxRy + 1) * gridPx, h)),
                w,
                h,
            )
            val textW = (rect.width() * 0.42f).toInt().coerceAtLeast(48)
            val textRect = Rect(rect.right - textW, rect.top, rect.right, rect.bottom)
            val density = colorDensity(bitmap, rect, if (strategy == "price-anchor") ::isPriceAnchorTone else ::isWarmTicketTone)
            var score = (count.toFloat() / (cols * rows).toFloat() * 8f + density) / 2f
            if (strategy == "warm-block") {
                score *= edgeDensity(bitmap, textRect).coerceIn(0.15f, 1f)
            }
            out.add(Candidate(rect, textRect, strategy, score.coerceIn(0f, 1f)))
        }
        return out
    }

    private fun findHighDensityRects(
        bitmap: Bitmap,
        grid: FloatArray,
        cols: Int,
        rows: Int,
        w: Int,
        h: Int,
        gridPx: Int,
        strategy: String,
        aspectWide: Boolean,
    ): List<Candidate> {
        val threshold = grid.average().toFloat() * 1.4f
        val cells = BooleanArray(grid.size) { grid[it] >= threshold }
        return connectedComponents(bitmap, cells, cols, rows, w, h, gridPx, strategy).map { c ->
            val aspect = c.rect.width().toFloat() / c.rect.height().coerceAtLeast(1)
            val aspectBonus = if (aspectWide && aspect > 1.2f) 0.15f else 0f
            c.copy(score = (c.score + aspectBonus).coerceIn(0f, 1f))
        }
    }

    private fun layoutScore(bitmap: Bitmap, card: Rect): Float {
        val warm = colorDensity(bitmap, card, ::isWarmTicketTone)
        val edge = edgeDensity(bitmap, card)
        val price = colorDensity(bitmap, card, ::isPriceAnchorTone)
        return (warm * 0.35f + edge * 0.35f + price * 0.30f).coerceIn(0f, 1f)
    }

    private fun colorDensity(bitmap: Bitmap, rect: Rect, predicate: (Int) -> Boolean): Float {
        val step = max(4, min(rect.width(), rect.height()) / 20)
        var hits = 0
        var n = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                if (predicate(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))) hits++
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0f else hits.toFloat() / n.toFloat()
    }

    private fun edgeDensity(bitmap: Bitmap, rect: Rect): Float {
        val step = max(4, min(rect.width(), rect.height()) / 16)
        var sum = 0f
        var n = 0
        var y = rect.top + step
        while (y < rect.bottom - step) {
            var x = rect.left + step
            while (x < rect.right - step) {
                val l = luminance(bitmap.getPixel(x, y))
                val r = luminance(bitmap.getPixel(x + step, y))
                sum += abs(l - r)
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0f else (sum / n / 80f).coerceIn(0f, 1f)
    }

    /** Relaxed warm/cream/amber — NOT pure #FFE500. */
    private fun isWarmTicketTone(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val maxC = max(r, max(g, b)).toFloat()
        if (maxC < 70f) return false
        if (r > 140 && g > 110 && b < 140 && (r - b) > 25) return true
        if (r > 120 && g > 100 && b < 100) return true
        return r / maxC > 0.72f && g / maxC > 0.62f && b / maxC < 0.65f
    }

    /** Magenta/pink printed price on Oasis tickets. */
    private fun isPriceAnchorTone(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 120 && b > 80 && g < r * 0.75f && g < b * 0.9f && (r + b) > g * 2.2f
    }

    private fun productRectLeftOf(textRect: Rect, bitmapWidth: Int): Rect {
        val ratio = ShelfA4Renderer.IMAGE_W_MM / ShelfA4Renderer.YELLOW_W_MM
        val productW = (textRect.width() * ratio).toInt().coerceAtLeast(40)
        val left = (textRect.left - productW).coerceAtLeast(0)
        return Rect(left, textRect.top, textRect.left, textRect.bottom)
    }

    private fun validateCandidate(candidate: Candidate, fw: Int, fh: Int): Boolean {
        if (!validateRect(candidate.rect, fw, fh)) return false
        return validateTextRect(candidate.textRect, fw, fh)
    }

    private fun validateTextRect(textRect: Rect, fw: Int, fh: Int): Boolean {
        if (textRect.width() < (fw * 0.12f).toInt().coerceAtLeast(56)) return false
        if (textRect.height() < (fh * 0.08f).toInt().coerceAtLeast(40)) return false
        val aspect = textRect.width().toFloat() / textRect.height().coerceAtLeast(1)
        if (aspect < 0.35f || aspect > 3.5f) return false
        return true
    }

    private fun validateRect(rect: Rect, fw: Int, fh: Int): Boolean {
        val area = rect.width() * rect.height()
        if (area < fw * fh * MIN_AREA_RATIO) return false
        if (rect.width() < 48 || rect.height() < 32) return false
        val aspect = rect.width().toFloat() / rect.height().coerceAtLeast(1)
        if (aspect < 0.28f || aspect > 5.5f) return false
        return true
    }

    private fun scaleRect(r: Rect, sx: Float, sy: Float, maxW: Int, maxH: Int): Rect =
        padRect(
            Rect(
                (r.left * sx).toInt(),
                (r.top * sy).toInt(),
                (r.right * sx).toInt().coerceIn(1, maxW),
                (r.bottom * sy).toInt().coerceIn(1, maxH),
            ),
            maxW,
            maxH,
        )

    private fun padRect(rect: Rect, maxW: Int, maxH: Int): Rect {
        val px = (rect.width() * 0.04f).toInt().coerceAtLeast(2)
        val py = (rect.height() * 0.04f).toInt().coerceAtLeast(2)
        return Rect(
            (rect.left - px).coerceAtLeast(0),
            (rect.top - py).coerceAtLeast(0),
            (rect.right + px).coerceAtMost(maxW),
            (rect.bottom + py).coerceAtMost(maxH),
        )
    }

    private fun neighbors4(rx: Int, ry: Int, cols: Int, rows: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(4)
        if (rx > 0) out.add(rx - 1 to ry)
        if (rx < cols - 1) out.add(rx + 1 to ry)
        if (ry > 0) out.add(rx to ry - 1)
        if (ry < rows - 1) out.add(rx to ry + 1)
        return out
    }

    private fun luminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
