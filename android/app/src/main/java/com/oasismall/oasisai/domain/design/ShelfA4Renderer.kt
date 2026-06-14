package com.oasismall.oasisai.domain.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.util.PriceFormatter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * In-app shelf layout — **2×6 = 12 tickets** on landscape A4.
 *
 * Math (297 × 210 mm): six rows × 35 mm = 210 mm (no row gaps).
 * Yellow block slightly narrower (92 mm vs 100 mm); column gutter 8 mm.
 */
object ShelfA4Renderer {
    const val COLS = 2
    const val ROWS = 6
    const val CAPACITY = COLS * ROWS

    /** Landscape A4 @ ~150 DPI */
    const val PAGE_W = 1754
    const val PAGE_H = 1240

    const val JPEG_QUALITY = 80

    private const val PAGE_W_MM = 297f
    private const val PAGE_H_MM = 210f

    /** Yellow price block — slightly narrower than classic 10 cm to fit 12-up */
    const val YELLOW_W_MM = 92f
    /** Row height: 210 mm ÷ 6 rows (no inter-ticket gaps) */
    const val YELLOW_H_MM = 35f

    private const val ROW_GAP_MM = 0f
    private const val COL_GAP_MM = 8f
    private const val IMAGE_PAD_MM = 1.5f

    private val pxPerMm = PAGE_W / PAGE_W_MM

    private val slotWmm = (PAGE_W_MM - COL_GAP_MM) / COLS
    private val imageWmm = slotWmm - YELLOW_W_MM

    val IMAGE_W_MM: Float
        get() = imageWmm

    val ticketAspectRatio: Float
        get() = slotWmm / YELLOW_H_MM

    private val YELLOW = Color.parseColor("#FFE500")
    private val PRICE_RED = Color.parseColor("#E60000")

    fun renderPage(
        items: List<PreselectionWithArticle>,
        pageIndex: Int,
        exportsDir: File,
        paray: ParayAgent,
    ): File {
        val chunk = items.drop(pageIndex * CAPACITY).take(CAPACITY)
        val bitmap = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        chunk.forEachIndexed { index, item ->
            val col = index % COLS
            val row = index / COLS
            drawShelfTicket(canvas, col, row, item, paray)
        }

        val name = "shelf_a4_p${pageIndex + 1}_${System.currentTimeMillis()}.jpg"
        val out = File(exportsDir, name)
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
        bitmap.recycle()
        return out
    }

    fun pageCount(itemCount: Int): Int =
        if (itemCount <= 0) 0 else (itemCount + CAPACITY - 1) / CAPACITY

    private fun drawShelfTicket(
        canvas: Canvas,
        col: Int,
        row: Int,
        item: PreselectionWithArticle,
        paray: ParayAgent,
    ) {
        val ticketX = mmToPx(col * (slotWmm + COL_GAP_MM))
        val ticketY = mmToPx(row * (YELLOW_H_MM + ROW_GAP_MM))
        val labelH = mmToPx(YELLOW_H_MM)
        val imageW = mmToPx(imageWmm)
        val yellowW = mmToPx(YELLOW_W_MM)

        val imageRect = RectF(ticketX, ticketY, ticketX + imageW, ticketY + labelH)
        canvas.drawRect(imageRect, Paint().apply { color = Color.WHITE })
        drawProductImage(canvas, item, imageRect, paray)

        val yellowRect = RectF(ticketX + imageW, ticketY, ticketX + imageW + yellowW, ticketY + labelH)
        canvas.drawRect(yellowRect, Paint().apply { color = YELLOW })

        drawDesignation(canvas, item.designation, yellowRect)
        drawPriceBlock(canvas, item.price, yellowRect)
    }

    private fun drawDesignation(canvas: Canvas, designation: String, yellowRect: RectF) {
        val pad = mmToPx(1.8f)
        val maxW = yellowRect.width() - pad * 2
        val maxH = yellowRect.height() * 0.38f
        val upper = designation.uppercase()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var low = mmToPx(2f)
        var high = maxH
        var bestSize = low
        var bestLines = listOf(upper)

        repeat(32) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val lines = wrapText(upper, paint, maxW)
            val lineH = mid * 1.08f
            val blockH = min(lines.size, 3) * lineH
            val fits = lines.size <= 3 &&
                blockH <= maxH &&
                lines.take(3).all { line -> paint.measureText(line) <= maxW }
            if (fits) {
                bestSize = mid
                bestLines = lines
                low = mid + mmToPx(0.08f)
            } else {
                high = mid - mmToPx(0.08f)
            }
        }

        paint.textSize = bestSize
        paint.textAlign = Paint.Align.CENTER
        val centerX = yellowRect.left + yellowRect.width() / 2f
        val lineH = bestSize * 1.08f
        val startY = yellowRect.top + pad + bestSize
        bestLines.take(3).forEachIndexed { i, line ->
            canvas.drawText(line, centerX, startY + i * lineH, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPriceBlock(canvas: Canvas, price: Double, yellowRect: RectF) {
        val numberText = PriceFormatter.formatNumber(price)
        val daText = "DA"

        var priceSize = yellowRect.height() * 0.52f
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRICE_RED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val daPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRICE_RED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        repeat(40) {
            pricePaint.textSize = priceSize
            daPaint.textSize = priceSize * 0.42f
            val gap = priceSize * 0.08f
            val totalW = pricePaint.measureText(numberText) + gap + daPaint.measureText(daText)
            if (totalW <= yellowRect.width() - mmToPx(3f)) return@repeat
            priceSize -= 2f
        }

        val gap = priceSize * 0.08f
        val totalW = pricePaint.measureText(numberText) + gap + daPaint.measureText(daText)
        val startX = yellowRect.left + (yellowRect.width() - totalW) / 2f
        val baselineY = yellowRect.top + yellowRect.height() * 0.8f

        canvas.drawText(numberText, startX, baselineY, pricePaint)
        canvas.drawText(daText, startX + pricePaint.measureText(numberText) + gap, baselineY, daPaint)
    }

    private fun drawProductImage(
        canvas: Canvas,
        item: PreselectionWithArticle,
        rect: RectF,
        paray: ParayAgent,
    ) {
        val path = item.imagePath
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (!file.isFile) return

        val pad = mmToPx(IMAGE_PAD_MM)
        val inner = RectF(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad)
        if (inner.width() <= 0f || inner.height() <= 0f) return

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calculateSampleSize(
            opts.outWidth,
            opts.outHeight,
            inner.width().toInt(),
            inner.height().toInt(),
        )
        opts.inJustDecodeBounds = false
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

        canvas.drawRect(inner, Paint().apply { color = Color.WHITE })
        paray.drawProductInShelfSlot(canvas, bmp, item, inner)
        bmp.recycle()
    }

    private fun mmToPx(mm: Float): Float = mm * pxPerMm

    private fun calculateSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (w / sample > reqW * 2 || h / sample > reqH * 2) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text.take(28)) }
    }
}
