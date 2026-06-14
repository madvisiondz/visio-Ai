package com.oasismall.oasisai.domain

import com.oasismall.oasisai.util.PngMetadata

/**
 * Canonical ready PNG format produced by `scripts/update_image_assets_metadata.py`
 * (Oasis IMAGE ASSETS / Gestium feed). Same tEXt keys as Windows PNG Properties Details.
 */
object ReadyPngModel {
    const val SOURCE_LABEL = "Oasis IMAGE ASSETS (2026-05-31 feed)"

    /** Required tEXt keys for a fully tagged marketing PNG. */
    val REQUIRED_TEXT_KEYS = listOf(
        PngMetadata.KEY_BARCODE,
        PngMetadata.KEY_CODEART,
        PngMetadata.KEY_DESIGNATION,
        PngMetadata.KEY_PRICE_NOW,
    )

    fun isComplete(details: PngMetadata.PngArticleDetails): Boolean =
        !details.barcode.isNullOrBlank() &&
            !details.codeart.isNullOrBlank() &&
            !details.designation.isNullOrBlank() &&
            details.priceNow != null

    fun summaryLine(details: PngMetadata.PngArticleDetails): String {
        val code = details.codeart.orEmpty().ifBlank { "?" }
        val bc = details.barcode.orEmpty().ifBlank { "?" }
        val des = details.designation?.take(40).orEmpty()
        return "Code $code · $bc · $des"
    }
}
