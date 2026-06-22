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
    PRELOAD,
    FRONT_CONFIRM,
    CAPTURE_LEFT,
    CAPTURE_RIGHT,
    CAPTURE_BACK,
    COMPLETE,
    MISMATCH,
}

/** Lightweight visual signature for one captured side (left / right / back only). */
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

    fun toSignatures(source: String) = ParayVisualSignatures(
        shapeAspect = shapeAspect,
        fillRatio = fillRatio,
        dominantColors = dominantColors,
        source = source,
        capturedAt = capturedAt,
    )
}

/**
 * Visual knowledge for one trusted catalog product.
 * Identity lives in Room — this file stores visual learning only.
 *
 * **Front is NOT a learned side.** Canonical front = [pngFrontPath] (official PNG).
 * [frontConfirmed] is validation only.
 */
data class ParayLearnRecord(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val brand: String? = null,
    val category: String? = null,
    val family: String? = null,
    val pngFrontPath: String,
    val frontConfirmed: Boolean = false,
    val frontConfidence: Float = 0f,
    val leftCapture: ParayViewCapture? = null,
    val rightCapture: ParayViewCapture? = null,
    val backCapture: ParayViewCapture? = null,
    val productSignature: ParayVisualSignatures? = null,
    val brandSignature: ParayVisualSignatures? = null,
    val familySignature: ParayVisualSignatures? = null,
    val packagingVariantDetected: Boolean = false,
    val symmetricSidesEligible: Boolean? = null,
    val learnedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
) {
    /** Learned sides = left + right + back only (front excluded). */
    val learnedSideCount: Int =
        listOfNotNull(leftCapture, rightCapture, backCapture).size

    val status: ParayLearnStatus
        get() = when {
            learnedSideCount >= 3 -> ParayLearnStatus.LEARNED
            learnedSideCount in 1..2 -> ParayLearnStatus.PARTIALLY_LEARNED
            else -> ParayLearnStatus.NOT_LEARNED
        }
}

data class ParayLearnQueueStats(
    val readyCount: Int,
    val learnedCount: Int,
    val partiallyLearnedCount: Int,
    val pendingCount: Int,
    /** PARAY KPI — learned / ready × 100 */
    val coveragePercent: Float,
)

data class ParayLearnSessionProduct(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val brand: String?,
    val category: String?,
    val family: String?,
    val pngPath: String,
    val learningStatus: ParayLearnStatus,
    val hasFingerprint: Boolean,
)

/** Preloaded session context — PARAY knows the product before camera opens. */
data class ParayLearnSessionContext(
    val product: ParayLearnSessionProduct,
    val record: ParayLearnRecord,
    val pngFeatures: VisualFeatureExtractor.Features,
    val hasFingerprint: Boolean,
    val fingerprintDim: Int,
    val preloadComplete: Boolean,
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
    val packagingVariantHint: String? = null,
)
