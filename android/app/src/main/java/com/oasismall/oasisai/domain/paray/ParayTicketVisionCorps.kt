package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.oasismall.oasisai.util.OasisLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PARAY Ticket Vision Corps — orchestrates region scout + OCR corps + challenge recovery.
 *
 * Challenge matrix:
 * | Challenge              | Solution                          |
 * |------------------------|-----------------------------------|
 * | Faded/non-yellow paper | Warm + layout + text-band scouts  |
 * | Magenta price only     | Price-anchor scout                |
 * | Bad crop               | Full-frame OCR corps fallback     |
 * | Glare/blur/dim         | 6 preprocessing variants          |
 * | OCR disagreement       | Multi-pass consensus voting       |
 * | Wrong region           | 5 strategies scored, best wins    |
 */
object ParayTicketVisionCorps {
    data class Analysis(
        val regions: ParayTicketRegionScout.TicketRegions,
        val ocr: ParayTicketOcrCorps.Result,
        val recoveryUsed: Boolean,
    )

    suspend fun analyze(
        frame: Bitmap,
        ocrCorps: ParayTicketOcrCorps,
        onProgress: suspend (String) -> Unit = {},
    ): Analysis? = withContext(Dispatchers.Default) {
        onProgress("Visual scout — warm block, price anchor, text band, layout…")
        var regions = ParayTicketRegionScout.locate(frame)
        if (regions == null) {
            OasisLog.w(OasisLog.Domain.Paray, "Vision corps: scout failed — full-frame OCR only")
            onProgress("Scout uncertain — full-frame OCR corps…")
        } else {
            onProgress("Ticket found (${regions.strategy}) — OCR corps…")
        }

        onProgress("OCR corps — multi-pass ML Kit…")
        var ocr = ocrCorps.read(
            textCrop = regions?.textCrop,
            fullFrame = frame,
            includeRecovery = true,
        )

        var recoveryUsed = false
        if (ocr == null || ocr.read.confidence < 0.65f) {
            onProgress("Recovery — enhanced scan…")
            recoveryUsed = true
            val boosted = ParayTicketImagePrep.boostForRecovery(frame)
            val retryRegions = if (boosted !== frame) {
                ParayTicketRegionScout.locate(boosted)
            } else {
                regions
            }
            val retryOcr = ocrCorps.read(
                textCrop = retryRegions?.textCrop,
                fullFrame = if (boosted !== frame) boosted else frame,
                includeRecovery = true,
            )
            if (boosted !== frame && !boosted.isRecycled) boosted.recycle()
            retryRegions?.textCrop?.takeIf { it !== regions?.textCrop }?.recycle()
            retryRegions?.productCrop?.takeIf { it !== regions?.productCrop }?.recycle()
            if (retryOcr != null && (ocr == null || retryOcr.corpsConfidence > ocr.corpsConfidence)) {
                regions?.textCrop?.recycle()
                regions?.productCrop?.recycle()
                regions = retryRegions
                ocr = retryOcr
            } else {
                retryRegions?.textCrop?.recycle()
                retryRegions?.productCrop?.recycle()
            }
        }

        val finalOcr = ocr ?: return@withContext null
        val finalRegions = regions ?: buildFullFrameRegions(frame)

        Analysis(finalRegions, finalOcr, recoveryUsed)
    }

    private fun buildFullFrameRegions(frame: Bitmap): ParayTicketRegionScout.TicketRegions {
        val w = frame.width
        val h = frame.height
        val rect = android.graphics.Rect(0, 0, w, h)
        return ParayTicketRegionScout.TicketRegions(
            ticketRect = rect,
            textRect = rect,
            textCrop = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false),
            productCrop = null,
            strategy = "full-frame",
            confidence = 0.4f,
        )
    }
}
