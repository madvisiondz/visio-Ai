package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap

/** Parsed shelf ticket frame — yellow OCR + optional product PNG crop (left of yellow). */
data class ParayTicketFrameRead(
    val ocr: ParayTicketReadResult,
    val productCrop: Bitmap? = null,
) {
    fun recycle() {
        productCrop?.recycle()
    }
}

/** Per-signal scores fused into PARAY ticket match probability. */
data class ParayTicketFusionBreakdown(
    val designationScore: Float,
    val priceScore: Float,
    val imageScore: Float,
    val rayonBoost: Float,
    val probability: Float,
) {
    val probabilityPercent: Int get() = (probability * 100f).toInt().coerceIn(0, 100)
}
