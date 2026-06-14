package com.oasismall.oasisai.domain.layoutagent

/**
 * Encoded Oasis AI layout rules the agent must respect (from PROJECT.md + shelf_10up).
 * Future GPU/ML models train against these constraints — not against raw pixels alone.
 */
object AppLayoutKnowledge {
    const val SHELF_TEMPLATE_ID = "shelf_12up"

    /** Landscape A4 shelf ticket image slot (mm) — 2×6 grid, no row gaps. */
    const val SHELF_IMAGE_SLOT_W_MM = 52.5f
    const val SHELF_IMAGE_SLOT_H_MM = 35f
    const val SHELF_YELLOW_W_MM = 92f
    const val SHELF_YELLOW_H_MM = 35f
    const val SHELF_IMAGE_PAD_MM = 1.5f

    const val DESIGNATION_ZONE_RATIO = 0.38f
    const val PRICE_ZONE_BASELINE_RATIO = 0.8f

    val productRules = listOf(
        "Designation/name is primary identity; barcode is lookup only.",
        "Pre-selection workflow: catalog → To share → Design → print export.",
        "Shelf image slot: fit visible product (cutout) inside white area — both width and height.",
        "Never bleed product into yellow block or neighboring tickets.",
        "Yellow block ~9.2 cm × 3.5 cm (12 per A4, 6 rows, no gaps); designation centered; price below.",
        "Offline-first: all layout decisions run on-device.",
    )
}
