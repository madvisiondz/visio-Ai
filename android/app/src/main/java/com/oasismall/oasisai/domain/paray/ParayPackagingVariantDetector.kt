package com.oasismall.oasisai.domain.paray

import org.json.JSONObject

/**
 * V1: detect + log packaging deviation during front confirmation.
 * Future: Update Existing / Create Variant / Ignore actions.
 */
object ParayPackagingVariantDetector {
    /** Gray zone — same barcode/brand but packaging may have changed. */
    fun detect(
        similarity: Float,
        settings: ParayLearnSettings,
    ): ParayPackagingVariantEvent? {
        val low = settings.frontMismatchCutoff()
        val high = settings.frontConfirmationThreshold * 0.92f
        if (similarity in low..<high) {
            return null // handled as normal confirm retry or mismatch elsewhere
        }
        // Significant deviation while operator still presenting front
        if (similarity in (high * 0.75f)..<low) {
            return null
        }
        return null
    }

    /**
     * Called when front never reaches confirm threshold but product identity likely matches
     * (operator insists same SKU, visual drift). V1 logs only.
     */
    fun detectPersistentDrift(
        articleId: Long,
        barcode: String,
        designation: String,
        similarity: Float,
        settings: ParayLearnSettings,
        mismatchFrames: Int,
    ): ParayPackagingVariantEvent? {
        if (mismatchFrames < ParayLearnEngine.MISMATCH_FRAME_LIMIT / 2) return null
        if (similarity >= settings.frontConfirmationThreshold) return null
        if (similarity < settings.frontMismatchCutoff()) return null
        return ParayPackagingVariantEvent(
            articleId = articleId,
            barcode = barcode,
            designation = designation,
            similarity = similarity,
            threshold = settings.frontConfirmationThreshold,
            message = "Potential packaging variant detected — visual drift vs official PNG",
        )
    }
}

class ParayPackagingVariantLog(home: ParayHome) {
    private val file = home.packagingVariantLogFile

    fun append(event: ParayPackagingVariantEvent) {
        val line = JSONObject()
            .put("articleId", event.articleId)
            .put("barcode", event.barcode)
            .put("designation", event.designation)
            .put("similarity", event.similarity.toDouble())
            .put("threshold", event.threshold.toDouble())
            .put("message", event.message)
            .put("action", event.action.name)
            .put("ts", event.detectedAt)
        file.parentFile?.mkdirs()
        file.appendText(line.toString() + "\n")
    }
}
