package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.db.dao.ArticleWithImage

/** How PARAY resolved a live camera frame to a catalog article. */
enum class ParayTicketReadSource {
    BARCODE,
    OCR_DESIGNATION,
}

/** Raw read from a shelf ticket frame (yellow block OCR and/or barcode). */
data class ParayTicketReadResult(
    val source: ParayTicketReadSource,
    val barcode: String? = null,
    val ocrDesignation: String? = null,
    val ocrPrice: Double? = null,
    val confidence: Float = 0f,
) {
    val stableKey: String?
        get() {
            barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val des = ocrDesignation?.trim()?.takeIf { it.length >= 3 } ?: return null
            val norm = com.oasismall.oasisai.util.NameNormalizer.normalize(des)
            val priceKey = ocrPrice?.toInt()?.toString().orEmpty()
            return "$norm|$priceKey"
        }
}

/** Catalog article matched from a ticket read — opens the same card as a barcode scan. */
data class ParayTicketMatch(
    val article: ArticleWithImage,
    val read: ParayTicketReadResult,
    val fusion: ParayTicketFusionBreakdown,
) {
    val barcode: String get() = article.barcode
    val probability: Float get() = fusion.probability
}

/** PARAY shelf-ticket verdict after barcode scan on a physical ticket. */
enum class ParayTicketStatus {
    /** Catalog price matches last print batch; no update flag. */
    MATCH,
    /** Price changed or `needsTicketUpdate` — replace shelf ticket. */
    STALE,
    /** Article in catalog but never printed from Oasis. */
    NEVER_PRINTED,
    /** Barcode not resolved to catalog article. */
    NOT_IN_CATALOG,
}

data class ParayTicketAssessment(
    val status: ParayTicketStatus,
    val catalogPrice: Double? = null,
    val lastPrintedPrice: Double? = null,
    val rayon: String? = null,
    val needsTicketUpdate: Boolean = false,
    val message: String,
    val matchProbability: Float? = null,
    val fusion: ParayTicketFusionBreakdown? = null,
    val ocrDesignation: String? = null,
)
