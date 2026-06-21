package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.PriceFormatter

class VisioProPriceResolver(
    private val repository: OasisRepository,
    private val store: VisioProStore,
) {

    suspend fun resolve(preset: VisioProPreset): VisioProPriceResult {
        if (!preset.theme.showPrice) {
            return VisioProPriceResult(null, VisioProPriceSource.NONE)
        }

        val memory = store.getMemory(preset.article.slug)
        preset.article.catalogArticleId?.let { articleId ->
            repository.getArticleWithImageById(articleId)?.price?.let {
                return resolveWithManual(memory, it, preset.channel)
            }
        }
        val csvPrice = repository.findPriceForVisioProArticle(
            csvDesignation = preset.article.csvDesignation,
            barcodeSuffix = preset.article.barcodeSuffix,
            keywords = preset.article.designationKeywords,
        )

        return when (preset.channel) {
            VisioProChannel.SOCIAL -> {
                memory.manualPrice?.let {
                    return VisioProPriceResult(it, VisioProPriceSource.MANUAL)
                }
                csvPrice?.let {
                    return VisioProPriceResult(it, VisioProPriceSource.CSV)
                }
                VisioProPriceResult(null, VisioProPriceSource.NONE)
            }
            VisioProChannel.PRINT -> {
                csvPrice?.let {
                    return VisioProPriceResult(it, VisioProPriceSource.CSV)
                }
                memory.manualPrice?.let {
                    return VisioProPriceResult(it, VisioProPriceSource.SOCIAL_MEMORY)
                }
                VisioProPriceResult(null, VisioProPriceSource.NONE)
            }
        }
    }

    private fun resolveWithManual(
        memory: VisioProArticleMemory,
        csvPrice: Double,
        channel: VisioProChannel,
    ): VisioProPriceResult = when (channel) {
        VisioProChannel.SOCIAL -> {
            memory.manualPrice?.let { VisioProPriceResult(it, VisioProPriceSource.MANUAL) }
                ?: VisioProPriceResult(csvPrice, VisioProPriceSource.CSV)
        }
        VisioProChannel.PRINT -> VisioProPriceResult(csvPrice, VisioProPriceSource.CSV)
    }

    fun formatPrice(price: Double?): String =
        price?.let { PriceFormatter.format(it) } ?: "—"
}
