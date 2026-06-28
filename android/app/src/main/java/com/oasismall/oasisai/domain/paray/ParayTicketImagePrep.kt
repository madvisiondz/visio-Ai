package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

/** Upscale + contrast for shelf-ticket OCR on yellow blocks. */
object ParayTicketImagePrep {
    private const val OCR_MIN_WIDTH_PX = 1080
    private const val HIGH_CONFIDENCE = 0.88f

    private val enhancePaint by lazy {
        val cm = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1.35f, 0f, 0f, 0f, -35f,
                    0f, 1.35f, 0f, 0f, -35f,
                    0f, 0f, 0.75f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
    }

    fun rotateToUpright(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun upscaleForOcr(bitmap: Bitmap): Bitmap {
        if (bitmap.width >= OCR_MIN_WIDTH_PX) return bitmap
        val scale = OCR_MIN_WIDTH_PX.toFloat() / bitmap.width.toFloat()
        val w = OCR_MIN_WIDTH_PX
        val h = kotlin.math.max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun enhanceForOcr(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(source, 0f, 0f, enhancePaint)
        return result
    }

    /** Second-pass recovery — stronger contrast/saturation for difficult desk shots. */
    fun boostForRecovery(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply {
            setSaturation(1.35f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.5f, 0f, 0f, 0f, -45f,
                        0f, 1.5f, 0f, 0f, -45f,
                        0f, 0f, 1.1f, 0f, -25f,
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

    /**
     * Fast path: enhanced OCR first; only fall back to raw upscale if confidence is low.
     */
    suspend fun prepareYellowCropForOcrFast(
        yellowCrop: Bitmap,
        recognize: suspend (Bitmap) -> ParayTicketReadResult?,
    ): ParayTicketReadResult? {
        val upscaled = upscaleForOcr(yellowCrop)
        val upscaledOwned = upscaled !== yellowCrop
        val enhanced = enhanceForOcr(upscaled)
        return try {
            recognize(enhanced)?.takeIf { it.confidence >= HIGH_CONFIDENCE }
                ?: recognize(upscaled)
        } finally {
            if (!enhanced.isRecycled) enhanced.recycle()
            if (upscaledOwned && !upscaled.isRecycled) upscaled.recycle()
        }
    }
}
