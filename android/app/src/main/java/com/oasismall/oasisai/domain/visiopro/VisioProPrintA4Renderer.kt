package com.oasismall.oasisai.domain.visiopro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Four F&V print cards on one landscape A4 (@ 300 DPI). */
object VisioProPrintA4Renderer {
    const val COLS = 2
    const val ROWS = 2
    const val CAPACITY = COLS * ROWS

    const val PAGE_W = 3508
    const val PAGE_H = 2480

    private const val PAD_PX = 24

    fun composeQuad(cardBitmaps: List<Bitmap>): Bitmap {
        val cards = cardBitmaps.take(CAPACITY)
        val page = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val cellW = (PAGE_W - PAD_PX * (COLS + 1)) / COLS
        val cellH = (PAGE_H - PAD_PX * (ROWS + 1)) / ROWS

        for (index in 0 until CAPACITY) {
            val col = index % COLS
            val row = index / COLS
            val left = PAD_PX + col * (cellW + PAD_PX)
            val top = PAD_PX + row * (cellH + PAD_PX)
            val dest = RectF(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat())
            val card = cards.getOrNull(index)
            if (card != null) {
                drawFit(canvas, card, dest)
            } else {
                val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEEEEE") }
                canvas.drawRect(dest, empty)
            }
        }
        return page
    }

    private fun drawFit(canvas: Canvas, source: Bitmap, dest: RectF) {
        val scale = minOf(dest.width() / source.width, dest.height() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val left = dest.centerX() - w / 2f
        val top = dest.centerY() - h / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), null)
    }
}
