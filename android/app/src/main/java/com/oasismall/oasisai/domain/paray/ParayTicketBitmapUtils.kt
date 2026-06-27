package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import kotlin.math.max

/** Shared bitmap helpers for the ticket snap pipeline — minimal allocations. */
internal object ParayTicketBitmapUtils {
    private const val PREVIEW_MAX_PX = 480
    private const val SNAP_MAX_PX = 2400
    private const val BUFFER_MAX_PX = 1920

    fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Preview for UI — preserves aspect ratio (max side PREVIEW_MAX_PX). */
    fun previewCopy(source: Bitmap): Bitmap = downscale(source, PREVIEW_MAX_PX)

    fun forSnapProcessing(source: Bitmap): Bitmap = downscale(source, SNAP_MAX_PX)

    fun forCameraBuffer(source: Bitmap): Bitmap = downscale(source, BUFFER_MAX_PX)

    fun recycleIfOwned(vararg bitmaps: Bitmap?) {
        bitmaps.forEach { bmp ->
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }
}

/** Per-step timings logged in debug / slow-path warn. */
data class ParayTicketSnapMetrics(
    val rotateMs: Long = 0,
    val yellowMs: Long = 0,
    val ocrMs: Long = 0,
    val matchMs: Long = 0,
    val totalMs: Long = 0,
    val candidateCount: Int = 0,
) {
    fun logSummary() {
        com.oasismall.oasisai.util.OasisLog.i(
            com.oasismall.oasisai.util.OasisLog.Domain.Paray,
            "Ticket snap ${totalMs}ms (rotate=$rotateMs yellow=$yellowMs ocr=$ocrMs match=$matchMs candidates=$candidateCount)",
        )
    }
}
