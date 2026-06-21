package com.oasismall.oasisai.domain.visiopro.designer

import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProChannel

/** One editable preset family — applied to all articles in that category + channel. */
enum class VisioProPresetDesignKey(
    val labelFr: String,
    val category: VisioProCategory,
    val channel: VisioProChannel,
) {
    FRUITS_SOCIAL("Fruits · réseaux sociaux", VisioProCategory.FRUITS, VisioProChannel.SOCIAL),
    FRUITS_PRINT("Fruits · impression magasin", VisioProCategory.FRUITS, VisioProChannel.PRINT),
    VEGETABLES_SOCIAL("Légumes · réseaux sociaux", VisioProCategory.VEGETABLES, VisioProChannel.SOCIAL),
    VEGETABLES_PRINT("Légumes · impression magasin", VisioProCategory.VEGETABLES, VisioProChannel.PRINT),
    BUTCHER_SOCIAL("Boucherie · réseaux sociaux", VisioProCategory.BUTCHER, VisioProChannel.SOCIAL),
    BUTCHER_PRINT("Boucherie · impression magasin", VisioProCategory.BUTCHER, VisioProChannel.PRINT),
    FISH_SOCIAL("Poissonnerie · réseaux sociaux", VisioProCategory.FISH, VisioProChannel.SOCIAL),
    ;

    val storageKey: String get() = name

    companion object {
        fun from(category: VisioProCategory, channel: VisioProChannel): VisioProPresetDesignKey? =
            entries.firstOrNull { it.category == category && it.channel == channel }
    }
}

enum class VisioProDesignLayerKind(val labelFr: String) {
    BACKGROUND("Fond"),
    HEADER("Bandeau"),
    PHOTO("Photo"),
    DESIGNATION("Désignation"),
    PRICE("Prix"),
    CODE("Code"),
}
