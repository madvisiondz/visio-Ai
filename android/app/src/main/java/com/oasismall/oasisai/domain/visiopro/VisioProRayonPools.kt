package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.util.NameNormalizer

/** Gestium rayon names used as article pools for VisioPRO list editors. */
object VisioProRayonPools {
    const val FRUITS_LEGUMES = "Fruits et Légumes"
    const val BOUCHERIE = "Boucherie"
    const val POISSONNERIE = "POISSONNERIE"

    fun sourceRayon(category: VisioProCategory): String = when (category) {
        VisioProCategory.FRUITS,
        VisioProCategory.VEGETABLES,
        -> FRUITS_LEGUMES
        VisioProCategory.BUTCHER -> BOUCHERIE
        VisioProCategory.FISH -> POISSONNERIE
    }

    fun poolHint(category: VisioProCategory): String = when (category) {
        VisioProCategory.FRUITS -> "Articles du rayon « $FRUITS_LEGUMES » affichés dans Fruits"
        VisioProCategory.VEGETABLES -> "Articles du rayon « $FRUITS_LEGUMES » affichés dans Légumes"
        VisioProCategory.BUTCHER -> "Articles du rayon « $BOUCHERIE »"
        VisioProCategory.FISH -> "Articles du rayon « $POISSONNERIE »"
    }

    fun categoriesForRayon(rayon: String?): List<VisioProCategory> {
        val normalized = NameNormalizer.normalize(rayon ?: return emptyList())
        if (normalized.isBlank()) return emptyList()
        return buildList {
            if (normalized == NameNormalizer.normalize(FRUITS_LEGUMES)) {
                add(VisioProCategory.FRUITS)
                add(VisioProCategory.VEGETABLES)
            }
            if (normalized == NameNormalizer.normalize(BOUCHERIE)) {
                add(VisioProCategory.BUTCHER)
            }
            if (normalized == NameNormalizer.normalize(POISSONNERIE)) {
                add(VisioProCategory.FISH)
            }
        }
    }

    fun matchesVisioProRayon(articleRayon: String?, targetRayon: String): Boolean =
        NameNormalizer.normalize(articleRayon ?: "") == NameNormalizer.normalize(targetRayon)
}
