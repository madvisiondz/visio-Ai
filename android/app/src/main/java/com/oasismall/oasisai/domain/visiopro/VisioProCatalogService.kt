package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository

data class VisioProPoolSyncResult(
    val category: VisioProCategory,
    val poolCount: Int,
    val enabledCount: Int,
    val pendingCount: Int,
)

data class VisioProLivePoolState(
    val pool: List<ArticleWithImage>,
    val enabledIds: List<Long>,
    val pendingIds: List<Long>,
)

data class VisioProCategoryCatalog(
    val defs: List<VisioProArticleDef>,
    val articlesById: Map<Long, com.oasismall.oasisai.data.db.dao.ArticleWithImage>,
)

class VisioProCatalogService(
    private val repository: OasisRepository,
    private val configStore: VisioProCatalogConfigStore,
) {

    suspend fun resolveArticleDefs(category: VisioProCategory): List<VisioProArticleDef> =
        resolveCategoryCatalog(category).defs

    /** Single DB round-trip for list + prices. */
    suspend fun resolveCategoryCatalog(category: VisioProCategory): VisioProCategoryCatalog {
        val ids = currentOrderedIds(category)
        if (ids.isEmpty()) return VisioProCategoryCatalog(emptyList(), emptyMap())
        val byId = repository.getArticlesWithImagesByIds(ids).associateBy { it.id }
        val defs = ids.mapNotNull { id ->
            byId[id]?.let { VisioProArticleDefFactory.fromCatalogArticle(it, category) }
        }
        return VisioProCategoryCatalog(defs = defs, articlesById = byId)
    }

    suspend fun countForCategory(category: VisioProCategory): Int =
        currentOrderedIds(category).size

    suspend fun poolArticles(category: VisioProCategory): List<ArticleWithImage> =
        repository.listArticlesInRayon(VisioProRayonPools.sourceRayon(category))

    suspend fun getCategoryConfig(category: VisioProCategory): VisioProCategoryConfig =
        configStore.getCategoryConfig(category)

    suspend fun currentOrderedIds(category: VisioProCategory): List<Long> {
        val enabled = configStore.getCategoryConfig(category).enabledIds
        if (enabled.isNotEmpty()) return enabled
        return defaultOrderedIds(category)
    }

    suspend fun pendingIds(category: VisioProCategory): List<Long> =
        configStore.getCategoryConfig(category).pendingIds

    suspend fun saveCategoryConfig(
        category: VisioProCategory,
        enabledIds: List<Long>,
        pendingIds: List<Long> = emptyList(),
    ) {
        val enabledSet = enabledIds.distinct()
        configStore.setCategoryConfig(
            category,
            VisioProCategoryConfig(
                enabledIds = enabledSet,
                pendingIds = pendingIds.filter { it !in enabledSet }.distinct(),
            ),
        )
    }

    suspend fun saveOrderedIds(category: VisioProCategory, ids: List<Long>) {
        val pending = configStore.getCategoryConfig(category).pendingIds.filter { it !in ids }
        saveCategoryConfig(category, ids, pending)
    }

    suspend fun resetToDefaults(category: VisioProCategory) {
        configStore.clearCategory(category)
    }

    /** After every CSV import — reload rayon pools from DB; keep enabled order; surface the rest as pending. */
    suspend fun syncRayonPoolsAfterCsvImport(): List<VisioProPoolSyncResult> =
        VisioProCategory.entries.map { reconcileCategoryFromPool(it) }

    /** Live pool for list editor — does not persist; pending = pool minus enabled. */
    suspend fun livePoolState(category: VisioProCategory): VisioProLivePoolState {
        val pool = poolArticles(category)
        val poolIds = pool.map { it.id }
        val poolSet = poolIds.toSet()
        val config = configStore.getCategoryConfig(category)
        val enabled = resolveEnabledIds(category, config, poolSet)
        val enabledSet = enabled.toSet()
        val pending = poolIds.filter { it !in enabledSet }
        return VisioProLivePoolState(pool = pool, enabledIds = enabled, pendingIds = pending)
    }

    suspend fun poolStats(category: VisioProCategory): VisioProPoolSyncResult {
        val state = livePoolState(category)
        return VisioProPoolSyncResult(
            category = category,
            poolCount = state.pool.size,
            enabledCount = state.enabledIds.size,
            pendingCount = state.pendingIds.size,
        )
    }

    private suspend fun reconcileCategoryFromPool(category: VisioProCategory): VisioProPoolSyncResult {
        val state = livePoolState(category)
        configStore.setCategoryConfig(
            category,
            VisioProCategoryConfig(
                enabledIds = state.enabledIds,
                pendingIds = state.pendingIds,
            ),
        )
        return VisioProPoolSyncResult(
            category = category,
            poolCount = state.pool.size,
            enabledCount = state.enabledIds.size,
            pendingCount = state.pendingIds.size,
        )
    }

    private suspend fun resolveEnabledIds(
        category: VisioProCategory,
        config: VisioProCategoryConfig,
        poolSet: Set<Long>,
    ): List<Long> {
        val previousEnabled = when {
            config.enabledIds.isNotEmpty() -> config.enabledIds
            else -> defaultOrderedIds(category)
        }
        return previousEnabled.filter { it in poolSet }
    }

    private suspend fun defaultOrderedIds(category: VisioProCategory): List<Long> {
        val defs = VisioProArticleDefFactory.hardcodedDefs(category)
        return defs.mapNotNull { def ->
            repository.findArticleIdForVisioPro(
                csvDesignation = def.csvDesignation,
                barcodeSuffix = def.barcodeSuffix,
                keywords = def.designationKeywords,
            )
        }
    }
}
