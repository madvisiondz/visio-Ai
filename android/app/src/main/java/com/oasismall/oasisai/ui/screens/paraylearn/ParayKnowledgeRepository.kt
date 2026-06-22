package com.oasismall.oasisai.ui.screens.paraylearn

import com.oasismall.oasisai.domain.paray.ParayArticleKnowledge
import com.oasismall.oasisai.domain.paray.ParayKnowledgeStore
import com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only PARAY catalog knowledge from cached JSON — no Room aggregations. */
data class ParayKnowledgeGroupRow(
    val name: String,
    val productCount: Int,
)

data class ParayRecentlyLearnedProduct(
    val articleId: Long,
    val designation: String,
    val barcode: String,
    val brand: String?,
    val category: String?,
    val learnedAt: Long,
)

data class ParayKnowledgeUiData(
    val summary: ParayKnowledgeSummary,
    val brands: List<ParayKnowledgeGroupRow>,
    val categories: List<ParayKnowledgeGroupRow>,
    val families: List<ParayKnowledgeGroupRow>,
    val recentlyLearned: List<ParayRecentlyLearnedProduct>,
)

class ParayKnowledgeRepository(
    private val knowledgeStore: ParayKnowledgeStore,
) {
    suspend fun load(): ParayKnowledgeUiData = withContext(Dispatchers.IO) {
        val summary = knowledgeStore.readSummary()
        val articles = knowledgeStore.readArticles().values.filter { !it.removed }

        ParayKnowledgeUiData(
            summary = summary,
            brands = groupByField(articles) { it.brand?.trim().orEmpty().ifBlank { UNKNOWN_BRAND } },
            categories = groupByField(articles) { it.category?.trim().orEmpty().ifBlank { UNKNOWN_CATEGORY } },
            families = groupByField(articles) { it.family?.trim().orEmpty().ifBlank { UNKNOWN_FAMILY } },
            recentlyLearned = recentlyLearned(articles),
        )
    }

    private fun groupByField(
        articles: List<ParayArticleKnowledge>,
        keySelector: (ParayArticleKnowledge) -> String,
    ): List<ParayKnowledgeGroupRow> =
        articles
            .groupBy(keySelector)
            .map { (name, rows) -> ParayKnowledgeGroupRow(name = name, productCount = rows.size) }
            .sortedWith(
                compareByDescending<ParayKnowledgeGroupRow> { it.productCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

    private fun recentlyLearned(articles: List<ParayArticleKnowledge>): List<ParayRecentlyLearnedProduct> =
        articles
            .filter { it.learnStatus == ParayLearnStatus.LEARNED }
            .map { article ->
                val learnedAt = article.timeline.lastOrNull { it.event == LEARNED_EVENT }?.at
                    ?: article.lastModifiedAt
                ParayRecentlyLearnedProduct(
                    articleId = article.articleId,
                    designation = article.designation,
                    barcode = article.barcode,
                    brand = article.brand,
                    category = article.category,
                    learnedAt = learnedAt,
                )
            }
            .sortedByDescending { it.learnedAt }
            .take(RECENT_LIMIT)

    companion object {
        private const val UNKNOWN_BRAND = "(unknown brand)"
        private const val UNKNOWN_CATEGORY = "(unknown category)"
        private const val UNKNOWN_FAMILY = "(unknown family)"
        private const val LEARNED_EVENT = "Learned"
        private const val RECENT_LIMIT = 20
    }
}
