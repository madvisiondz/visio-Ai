package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap

/** Match confidence tier shown on the article card after snap. */
enum class ParayTicketMatchTier(val marketingLabel: String, val minProbability: Float) {
    CONFIRMED("PARAY Confirmed", 0.78f),
    HIGH("PARAY High match", 0.65f),
    PROBABLE("PARAY Probable", ParayTicketFuzzyMatcher.MIN_PROBABILITY),
    ;

    companion object {
        fun fromProbability(p: Float): ParayTicketMatchTier? = when {
            p >= CONFIRMED.minProbability -> CONFIRMED
            p >= HIGH.minProbability -> HIGH
            p >= PROBABLE.minProbability -> PROBABLE
            else -> null
        }
    }
}

/** Steps shown while PARAY processes a held ticket snap. */
enum class TicketSnapPhase {
    CAPTURED,
    QUALITY,
    ROTATE,
    FIND_YELLOW,
    CROP_YELLOW,
    READ_TEXT,
    CROP_PNG,
    FUZZY_MATCH,
    RECOVERY,
    STABILIZE,
    DONE,
    FAILED,
}

data class TicketSnapStep(
    val phase: TicketSnapPhase,
    val message: String,
    val preview: Bitmap? = null,
    val ocrDesignation: String? = null,
    val ocrPrice: Double? = null,
    val fusion: ParayTicketFusionBreakdown? = null,
    val matchTier: ParayTicketMatchTier? = null,
    val frameQuality: Float? = null,
    val error: String? = null,
)

data class ParayTicketSnapResult(
    val frame: ParayTicketFrameRead,
    val match: ParayTicketMatch?,
    val matchTier: ParayTicketMatchTier? = null,
    val frameQuality: Float = 0f,
    val recoveryPassUsed: Boolean = false,
    val candidates: List<ParayTicketMatch> = emptyList(),
)
