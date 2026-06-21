package com.oasismall.oasisai.domain.visiopro

enum class VisioProCategory(val labelFr: String, val emoji: String) {
    FRUITS("Fruits", "🍎"),
    VEGETABLES("Légumes", "🥬"),
    BUTCHER("Boucherie", "🥩"),
    FISH("Poissonnerie", "🐟"),
}

enum class VisioProChannel(val labelFr: String) {
    SOCIAL("Réseaux sociaux"),
    PRINT("Impression magasin"),
}

enum class VisioProPriceSource {
    CSV,
    MANUAL,
    SOCIAL_MEMORY,
    NONE,
}

/** Shared between social + print presets for the same article (price memory). */
data class VisioProArticleDef(
    val slug: String,
    val labelFr: String,
    val designationKeywords: List<String>,
    val csvDesignation: String = labelFr,
    val barcodeSuffix: String? = null,
    val labelAr: String? = null,
    /** Bundled print product PNG from affichage PSD (fv_print/products/{slug}.png). */
    val printProductAsset: String? = null,
    /** Linked Gestium catalog row when list is user-configured. */
    val catalogArticleId: Long? = null,
)

data class VisioProTheme(
    val widthPx: Int,
    val heightPx: Int,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val headerBand: Int,
    val accent: Int,
    val titleOnBand: Int,
    val bodyText: Int,
    val priceBackground: Int,
    val priceText: Int,
    val categoryTag: String,
    val showPrice: Boolean,
    val unitSuffix: String?,
    /** When set, use PSD template renderer (`ail_social`, `fv_print`). `fv_print` = A4×4 batch workflow. */
    val templateId: String? = null,
)

data class VisioProPreset(
    val id: String,
    val category: VisioProCategory,
    val channel: VisioProChannel,
    val article: VisioProArticleDef,
    val theme: VisioProTheme,
)

data class VisioProArticleMemory(
    val slug: String,
    val manualPrice: Double? = null,
    val lastSocialExportAt: Long? = null,
    val lastPrintExportAt: Long? = null,
    val printModified: Boolean = false,
)

data class VisioProPriceResult(
    val price: Double?,
    val source: VisioProPriceSource,
)

data class VisioProPresetUi(
    val preset: VisioProPreset,
    val price: Double?,
    val priceSource: VisioProPriceSource,
    val lastExportAt: Long?,
    val editablePriceText: String,
)
