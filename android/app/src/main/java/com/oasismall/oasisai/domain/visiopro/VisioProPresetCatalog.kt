package com.oasismall.oasisai.domain.visiopro

/**
 * Preset catalog for VisioPRO — seven layout families (agent-maintained).
 * Social = square 1080 for Facebook; Print = portrait A5-ish for in-mall signage.
 * Fish = social only, no price on card.
 */
object VisioProPresetCatalog {

    private val butcherArticles = listOf(
        article("poulet_entier", "Poulet entier", "poulet entier", "poulet"),
        article("escalope_poulet", "Escalope poulet", "escalope poulet", "escalope"),
        article("viande_hachee", "Viande hachée", "viande hachee", "haché", "hache"),
        article("giteau_boeuf", "Gîteau bœuf", "giteau", "gîteau", "boeuf"),
        article("merguez", "Merguez", "merguez"),
        article("cotes_agneau", "Côtes agneau", "cotes agneau", "côte agneau"),
        article("filet_boeuf", "Filet bœuf", "filet boeuf", "filet bœuf"),
        article("foie_agneau", "Foie agneau", "foie agneau", "foie"),
    )

    private val fishArticles = listOf(
        article("dorade", "Dorade", "dorade"),
        article("saumon", "Saumon", "saumon", "salmon"),
        article("crevette", "Crevette", "crevette", "shrimp"),
        article("sardine", "Sardine", "sardine"),
        article("rouget", "Rouget", "rouget"),
        article("merlan", "Merlan", "merlan"),
        article("calamar", "Calamar", "calamar", "squid"),
        article("pageot", "Pageot", "pageot"),
    )

    fun defaultButcherArticles(): List<VisioProArticleDef> = butcherArticles

    fun defaultFishArticles(): List<VisioProArticleDef> = fishArticles

    private fun article(slug: String, labelFr: String, vararg keywords: String) =
        VisioProArticleDef(slug, labelFr, keywords.toList())

    private val ailSocialTheme = ailTheme("FRUITS & LÉGUMES · FACEBOOK")

    private val fruitsPrintTheme = buildFvPrintTheme("FRUITS · MAGASIN · A4×4")

    private val vegPrintTheme = buildFvPrintTheme("LÉGUMES · MAGASIN · A4×4")

    private val butcherSocialTheme = theme(
        tag = "BOUCHERIE · FACEBOOK",
        social = true,
        top = 0xFF4A0E0E.toInt(),
        bottom = 0xFFB71C1C.toInt(),
        header = 0xFF7F0000.toInt(),
        accent = 0xFFFFD54F.toInt(),
        unit = "/ kg",
    )

    private val butcherPrintTheme = theme(
        tag = "BOUCHERIE · MAGASIN",
        social = false,
        top = 0xFF3E2723.toInt(),
        bottom = 0xFF8D6E63.toInt(),
        header = 0xFF5D4037.toInt(),
        accent = 0xFFFFEB3B.toInt(),
        unit = "DA / kg",
    )

    private val fishSocialTheme = theme(
        tag = "POISSONNERIE · FACEBOOK",
        social = true,
        top = 0xFF01579B.toInt(),
        bottom = 0xFF0288D1.toInt(),
        header = 0xFF0277BD.toInt(),
        accent = 0xFF80DEEA.toInt(),
        unit = null,
        showPrice = false,
    )

    private fun buildFvPrintTheme(tag: String): VisioProTheme = VisioProTheme(
        widthPx = 3508,
        heightPx = 2480,
        backgroundTop = 0xFFFFFFFF.toInt(),
        backgroundBottom = 0xFFFFFFFF.toInt(),
        headerBand = 0,
        accent = 0,
        titleOnBand = 0,
        bodyText = 0,
        priceBackground = 0,
        priceText = 0,
        categoryTag = tag,
        showPrice = true,
        unitSuffix = null,
        templateId = "fv_print",
    )

    private fun ailTheme(tag: String): VisioProTheme = VisioProTheme(
        widthPx = 985,
        heightPx = 1311,
        backgroundTop = 0xFF000000.toInt(),
        backgroundBottom = 0xFF000000.toInt(),
        headerBand = 0,
        accent = 0,
        titleOnBand = 0,
        bodyText = 0,
        priceBackground = 0,
        priceText = 0,
        categoryTag = tag,
        showPrice = true,
        unitSuffix = null,
        templateId = "ail_social",
    )

    private fun theme(
        tag: String,
        social: Boolean,
        top: Int,
        bottom: Int,
        header: Int,
        accent: Int,
        unit: String?,
        showPrice: Boolean = true,
        templateId: String? = null,
    ): VisioProTheme {
        val (w, h) = if (social) 1080 to 1080 else 1200 to 1697
        return VisioProTheme(
            widthPx = w,
            heightPx = h,
            backgroundTop = top,
            backgroundBottom = bottom,
            headerBand = header,
            accent = accent,
            titleOnBand = 0xFFFFFFFF.toInt(),
            bodyText = 0xFFFFFFFF.toInt(),
            priceBackground = 0xFFFFFFFF.toInt(),
            priceText = 0xFF212121.toInt(),
            categoryTag = tag,
            showPrice = showPrice,
            unitSuffix = unit,
            templateId = templateId,
        )
    }

    private fun buildPresets(
        category: VisioProCategory,
        channel: VisioProChannel,
        articles: List<VisioProArticleDef>,
        theme: VisioProTheme,
    ): List<VisioProPreset> = articles.map { art ->
        VisioProPreset(
            id = "${category.name.lowercase()}_${channel.name.lowercase()}_${art.slug}",
            category = category,
            channel = channel,
            article = art,
            theme = theme,
        )
    }

    private val allPresets: List<VisioProPreset> = buildList {
        addAll(buildPresets(VisioProCategory.FRUITS, VisioProChannel.SOCIAL, VisioProCsvArticles.fruits, ailSocialTheme))
        addAll(buildPresets(VisioProCategory.FRUITS, VisioProChannel.PRINT, VisioProCsvArticles.fruits, fruitsPrintTheme))
        addAll(buildPresets(VisioProCategory.VEGETABLES, VisioProChannel.SOCIAL, VisioProCsvArticles.vegetables, ailSocialTheme))
        addAll(buildPresets(VisioProCategory.VEGETABLES, VisioProChannel.PRINT, VisioProCsvArticles.vegetables, vegPrintTheme))
        addAll(buildPresets(VisioProCategory.BUTCHER, VisioProChannel.SOCIAL, butcherArticles, butcherSocialTheme))
        addAll(buildPresets(VisioProCategory.BUTCHER, VisioProChannel.PRINT, butcherArticles, butcherPrintTheme))
        addAll(buildPresets(VisioProCategory.FISH, VisioProChannel.SOCIAL, fishArticles, fishSocialTheme))
    }

    fun presets(
        category: VisioProCategory,
        channel: VisioProChannel,
        articles: List<VisioProArticleDef>,
    ): List<VisioProPreset> {
        val theme = themeFor(category, channel) ?: return emptyList()
        return buildPresets(category, channel, articles, theme)
    }

    fun themeFor(category: VisioProCategory, channel: VisioProChannel): VisioProTheme? = when {
        category == VisioProCategory.FRUITS && channel == VisioProChannel.SOCIAL -> ailSocialTheme
        category == VisioProCategory.FRUITS && channel == VisioProChannel.PRINT -> fruitsPrintTheme
        category == VisioProCategory.VEGETABLES && channel == VisioProChannel.SOCIAL -> ailSocialTheme
        category == VisioProCategory.VEGETABLES && channel == VisioProChannel.PRINT -> vegPrintTheme
        category == VisioProCategory.BUTCHER && channel == VisioProChannel.SOCIAL -> butcherSocialTheme
        category == VisioProCategory.BUTCHER && channel == VisioProChannel.PRINT -> butcherPrintTheme
        category == VisioProCategory.FISH && channel == VisioProChannel.SOCIAL -> fishSocialTheme
        else -> null
    }

    fun all(): List<VisioProPreset> = allPresets

    fun presets(category: VisioProCategory, channel: VisioProChannel): List<VisioProPreset> =
        allPresets.filter { it.category == category && it.channel == channel }

    fun presetById(id: String): VisioProPreset? = allPresets.firstOrNull { it.id == id }

    fun presetById(id: String, articles: List<VisioProArticleDef>, category: VisioProCategory, channel: VisioProChannel): VisioProPreset? {
        val slugSuffix = id.substringAfterLast('_', "")
        val art = articles.firstOrNull { it.slug == slugSuffix } ?: return null
        val theme = themeFor(category, channel) ?: return null
        return VisioProPreset(
            id = id,
            category = category,
            channel = channel,
            article = art,
            theme = theme,
        )
    }

    fun printPresetBySlug(slug: String): VisioProPreset? =
        allPresets.firstOrNull {
            it.article.slug == slug &&
                it.channel == VisioProChannel.PRINT &&
                it.theme.templateId == "fv_print"
        }

    fun printPresetBySlug(
        slug: String,
        articles: List<VisioProArticleDef>,
        category: VisioProCategory,
    ): VisioProPreset? {
        val art = articles.firstOrNull { it.slug == slug } ?: return null
        val theme = themeFor(category, VisioProChannel.PRINT) ?: return null
        if (theme.templateId != "fv_print") return null
        return VisioProPreset(
            id = "${category.name.lowercase()}_print_$slug",
            category = category,
            channel = VisioProChannel.PRINT,
            article = art,
            theme = theme,
        )
    }

    fun channelsFor(category: VisioProCategory): List<VisioProChannel> =
        if (category == VisioProCategory.FISH) {
            listOf(VisioProChannel.SOCIAL)
        } else {
            listOf(VisioProChannel.SOCIAL, VisioProChannel.PRINT)
        }

    /** Seven preset families (for docs / agent maintenance). */
    fun layoutFamilies(): List<String> = listOf(
        "1 — Fruits · réseaux sociaux",
        "2 — Fruits · impression magasin",
        "3 — Légumes · réseaux sociaux",
        "4 — Légumes · impression magasin",
        "5 — Boucherie · réseaux sociaux",
        "6 — Boucherie · impression magasin",
        "7 — Poissonnerie · réseaux sociaux (sans prix)",
    )
}
