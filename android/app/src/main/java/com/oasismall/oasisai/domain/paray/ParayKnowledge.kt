package com.oasismall.oasisai.domain.paray

/**
 * PARAY — Oasis on-device visual agent.
 * Learns product shape, color, typography, and label design for future camera recognition.
 */
object ParayKnowledge {
    const val AGENT_NAME = "PARAY"
    const val VERSION = "1.0"

    const val motto = "I live at home. I work at Oasis."

    val mission = """
        PARAY learns how each Oasis article looks: cutout shape, dominant pack colors,
        designation typography, and shelf label design. Later PARAY will recognize products
        directly from the camera without scanning a barcode first.
    """.trimIndent()

    val learningSignals = listOf(
        "shape_aspect" to "Width ÷ height of visible cutout (alpha bbox)",
        "fill_ratio" to "Product pixels ÷ full image area",
        "dominant_colors" to "Top pack colors (RGB) from cutout region",
        "designation_words" to "Word count + length — typography pressure",
        "label_palette" to "Shelf template: yellow #FFE500, price red #E60000, black type",
        "template_id" to "Which print template (shelf_10up, freezer, …)",
    )

    val futureCapabilities = listOf(
        "Camera frame → article match (offline)",
        "Combine with barcode when available for higher confidence",
        "GPU-accelerated embedding model (TFLite + NNAPI)",
        "Promo / freezer template visual memory",
    )
}
