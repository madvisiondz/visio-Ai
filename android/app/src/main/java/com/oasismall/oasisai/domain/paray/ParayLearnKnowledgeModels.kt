package com.oasismall.oasisai.domain.paray

/**
 * Shape / color signature bundle stored on learn records and brand-family index.
 */
data class ParayVisualSignatures(
    val shapeAspect: Float = 0f,
    val fillRatio: Float = 0f,
    val dominantColors: List<Int> = emptyList(),
    val source: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
)

/** V1: log-only packaging variant hint — future: Update / Create variant / Ignore. */
enum class ParayPackagingVariantAction {
    LOGGED_ONLY,
    UPDATE_EXISTING,
    CREATE_VARIANT,
    IGNORE,
}

data class ParayPackagingVariantEvent(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val similarity: Float,
    val threshold: Float,
    val message: String = "Potential packaging variant detected",
    val action: ParayPackagingVariantAction = ParayPackagingVariantAction.LOGGED_ONLY,
    val detectedAt: Long = System.currentTimeMillis(),
)

/** Future: skip right capture when left/right geometry is symmetric. Not used in V1. */
data class ParaySymmetricSideHint(
    val eligible: Boolean,
    val skippedSide: ParayViewSide? = null,
)

/** Brand / family relationship knowledge (architecture for future recognition). */
data class ParayBrandFamilyEntry(
    val brand: String,
    val family: String,
    val productCount: Int = 0,
    val brandSignature: ParayVisualSignatures? = null,
    val familySignature: ParayVisualSignatures? = null,
)
