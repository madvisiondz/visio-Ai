package com.oasismall.oasisai.domain.paray

/** Completion status for PARAY Learn V1. */
enum class ParayLearnStatus {
    NOT_LEARNED,
    PARTIALLY_LEARNED,
    LEARNED,
}

enum class ParayViewSide {
    FRONT,
    LEFT,
    RIGHT,
    BACK,
}

enum class ParayLearnPhase {
    IDLE,
    FRONT_CONFIRM,
    CAPTURE_LEFT,
    CAPTURE_RIGHT,
    CAPTURE_BACK,
    COMPLETE,
    MISMATCH,
}

/** Lightweight visual signature for one captured view. */
data class ParayViewCapture(
    val shapeAspect: Float,
    val fillRatio: Float,
    val dominantColors: List<Int>,
    val confidence: Float = 0f,
    val imagePath: String? = null,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    fun toFeatures(): VisualFeatureExtractor.Features = VisualFeatureExtractor.Features(
        shapeAspect = shapeAspect,
        fillRatio = fillRatio,
        dominantColors = dominantColors,
    )
}

/**
 * Visual knowledge for one trusted catalog product (identity lives in Room).
 * Stored in [ParayLearnStore].
 */
data class ParayLearnRecord(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val pngFrontPath: String,
    val frontConfirmed: Boolean = false,
    val frontConfidence: Float = 0f,
    val frontCapture: ParayViewCapture? = null,
    val leftCapture: ParayViewCapture? = null,
    val rightCapture: ParayViewCapture? = null,
    val backCapture: ParayViewCapture? = null,
    val learnedAt: Long? = null,
    val version: Int = 1,
) {
    val status: ParayLearnStatus
        get() = when {
            frontConfirmed && leftCapture != null && rightCapture != null && backCapture != null ->
                ParayLearnStatus.LEARNED
            frontConfirmed || leftCapture != null || rightCapture != null || backCapture != null ->
                ParayLearnStatus.PARTIALLY_LEARNED
            else -> ParayLearnStatus.NOT_LEARNED
        }

    val learnedViewCount: Int = listOfNotNull(
        if (frontConfirmed) frontCapture else null,
        leftCapture,
        rightCapture,
        backCapture,
    ).size
}

data class ParayLearnQueueStats(
    val readyCount: Int,
    val learnedCount: Int,
    val partiallyLearnedCount: Int,
    val pendingCount: Int,
)

data class ParayLearnSessionProduct(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val pngPath: String,
    val record: ParayLearnRecord?,
)

data class ParayLearnFrameResult(
    val phase: ParayLearnPhase,
    val instruction: String,
    val frontSimilarity: Float = 0f,
    val stableFrames: Int = 0,
    val updatedRecord: ParayLearnRecord? = null,
    val progressFront: Boolean = false,
    val progressLeft: Boolean = false,
    val progressRight: Boolean = false,
    val progressBack: Boolean = false,
)
