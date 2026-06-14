package com.oasismall.oasisai.domain.paray

/**
 * What PARAY remembers about one article's appearance.
 */
data class ProductVisualSignature(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    /** Visible cutout width ÷ height */
    val shapeAspect: Float,
    /** Product pixel area ÷ bitmap area */
    val fillRatio: Float,
    /** Top 3 pack colors as 0xRRGGBB */
    val dominantColors: List<Int>,
    val designationWordCount: Int,
    val designationCharCount: Int,
    val templateId: String,
    val labelPalette: List<Int>,
    val observationCount: Int,
    val lastLearnedAt: Long,
    val imageFileName: String,
)

data class ParayMatch(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    /** 0..1 confidence */
    val confidence: Float,
)
