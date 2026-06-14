package com.oasismall.oasisai.domain.backgroundremoval

data class BackgroundRemovalResult(
    val originalPath: String,
    val outputPngPath: String? = null,
    val success: Boolean,
    val errorMessage: String? = null,
)

data class BackgroundRemovalOptions(
    val maskThreshold: Float = 0.45f,
    val edgeSmoothRadius: Int = 2,
    val maxInputDimension: Int = 2048,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
) {
    init {
        require(maskThreshold in 0.05f..0.95f)
        require(edgeSmoothRadius in 0..8)
        require(cropLeft in 0f..1f && cropTop in 0f..1f && cropRight in 0f..1f && cropBottom in 0f..1f)
        require(cropRight > cropLeft && cropBottom > cropTop)
    }
}
