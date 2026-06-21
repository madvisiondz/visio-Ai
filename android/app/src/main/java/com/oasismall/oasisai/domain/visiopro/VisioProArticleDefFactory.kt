package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.util.NameNormalizer

object VisioProArticleDefFactory {

    private val hardcodedByCategory: Map<VisioProCategory, List<VisioProArticleDef>> = mapOf(
        VisioProCategory.FRUITS to VisioProCsvArticles.fruits,
        VisioProCategory.VEGETABLES to VisioProCsvArticles.vegetables,
        VisioProCategory.BUTCHER to VisioProPresetCatalog.defaultButcherArticles(),
        VisioProCategory.FISH to VisioProPresetCatalog.defaultFishArticles(),
    )

    fun hardcodedDefs(category: VisioProCategory): List<VisioProArticleDef> =
        hardcodedByCategory[category].orEmpty()

    fun fromCatalogArticle(article: ArticleWithImage, category: VisioProCategory): VisioProArticleDef {
        val matched = matchHardcoded(article, category)
        if (matched != null) {
            return matched.copy(catalogArticleId = article.id)
        }
        val digits = article.barcode.filter { it.isDigit() }
        return VisioProArticleDef(
            slug = "cat_${article.id}",
            labelFr = article.designation,
            designationKeywords = listOf(article.designation, article.normalizedName),
            csvDesignation = article.designation,
            barcodeSuffix = digits.takeLast(3).takeIf { it.length == 3 },
            catalogArticleId = article.id,
        )
    }

    private fun matchHardcoded(article: ArticleWithImage, category: VisioProCategory): VisioProArticleDef? {
        val normDesignation = NameNormalizer.normalize(article.designation)
        val suffix = article.barcode.filter { it.isDigit() }.takeLast(3)
        return hardcodedByCategory[category].orEmpty().firstOrNull { def ->
            def.barcodeSuffix?.let { it == suffix } == true ||
                NameNormalizer.normalize(def.csvDesignation) == normDesignation ||
                def.designationKeywords.any { NameNormalizer.normalize(it) == normDesignation }
        }
    }
}
