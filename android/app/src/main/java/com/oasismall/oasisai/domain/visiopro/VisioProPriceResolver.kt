package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.PriceFormatter
import kotlin.math.abs

class VisioProPriceResolver(
    private val repository: OasisRepository,
) {

    fun resolveFromCatalog(
        preset: VisioProPreset,
        memory: VisioProArticleMemory?,
        catalog: ArticleWithImage?,
    ): VisioProPriceResult {
        if (!preset.theme.showPrice) {
            return VisioProPriceResult(null, VisioProPriceSource.NONE)
        }
        val csvPrice = catalog?.price
        if (memory?.manualPriceOverridden == true && memory.manualPrice != null) {
            return VisioProPriceResult(
                price = memory.manualPrice,
                source = VisioProPriceSource.MANUAL,
                csvBaseline = memory.csvPriceWhenOverridden ?: csvPrice,
            )
        }
        if (csvPrice != null) {
            return VisioProPriceResult(csvPrice, VisioProPriceSource.CSV, csvPrice)
        }
        return VisioProPriceResult(null, VisioProPriceSource.NONE)
    }

    suspend fun resolveFallback(preset: VisioProPreset, memory: VisioProArticleMemory?): VisioProPriceResult {
        if (!preset.theme.showPrice) {
            return VisioProPriceResult(null, VisioProPriceSource.NONE)
        }
        val catalog = preset.article.catalogArticleId?.let { repository.getArticleWithImageById(it) }
        if (catalog != null) {
            return resolveFromCatalog(preset, memory, catalog)
        }
        val csvPrice = repository.findPriceForVisioProArticle(
            csvDesignation = preset.article.csvDesignation,
            barcodeSuffix = preset.article.barcodeSuffix,
            keywords = preset.article.designationKeywords,
        )
        if (memory?.manualPriceOverridden == true && memory.manualPrice != null) {
            return VisioProPriceResult(
                price = memory.manualPrice,
                source = VisioProPriceSource.MANUAL,
                csvBaseline = memory.csvPriceWhenOverridden ?: csvPrice,
            )
        }
        csvPrice?.let { return VisioProPriceResult(it, VisioProPriceSource.CSV, it) }
        return VisioProPriceResult(null, VisioProPriceSource.NONE)
    }

    fun formatPrice(price: Double?): String =
        price?.let { PriceFormatter.format(it) } ?: "—"

    companion object {
        fun pricesMatch(a: Double, b: Double): Boolean = abs(a - b) < 0.009
    }
}
