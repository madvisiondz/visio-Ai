package com.oasismall.oasisai.domain.paray

/** Recognition curiosity events — observation only, no user prompts. */
enum class ParayRecognitionEventType {
    RECOGNITION_FAILURE,
    LOW_CONFIDENCE_MATCH,
    MANUAL_CORRECTION,
    PACKAGING_DRIFT,
    UNKNOWN_BARCODE,
}

/** Lightweight observation — no images, frames, user text, or personal data. */
data class ParayRecognitionEvent(
    val type: ParayRecognitionEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val articleId: Long? = null,
    val barcode: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    val designation: String? get() = metadata["designation"]
}

/** Unknown or weakly resolved barcodes PARAY keeps seeing. */
data class ParayUnknownProductRecord(
    val barcode: String,
    val eventCount: Int = 0,
    val correctionCount: Int = 0,
    val lastSeenAt: Long = 0L,
    val lastDesignation: String? = null,
    val lastArticleId: Long? = null,
)

/** Aggregated failure pattern — keyed by event type or product (discovery engine). */
data class ParayFailurePatternRecord(
    val key: String,
    val eventType: ParayRecognitionEventType,
    val count: Int = 0,
    val lastAt: Long = 0L,
    val sampleBarcode: String? = null,
    val sampleDesignation: String? = null,
)

data class ParayRecognitionProductRank(
    val barcode: String,
    val designation: String?,
    val count: Int,
)

data class ParayRecognitionFailureRank(
    val eventType: String,
    val label: String,
    val count: Int,
)

data class ParayRecognitionApplyResult(
    val newDiscovery: Boolean = false,
    val blindSpotWarning: Boolean = false,
)
