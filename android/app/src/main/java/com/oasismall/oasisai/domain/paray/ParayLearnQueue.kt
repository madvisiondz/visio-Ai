package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds the trusted-product learning queue from Room catalog + PNG assets. */
class ParayLearnQueue(
    private val repository: OasisRepository,
    private val learnStore: ParayLearnStore,
) {
    suspend fun buildStats(): ParayLearnQueueStats = withContext(Dispatchers.IO) {
        val ready = repository.listParayLearnReadyArticles()
        val learned = learnStore.learnedCount()
        val partial = learnStore.partiallyLearnedCount()
        ParayLearnQueueStats(
            readyCount = ready.size,
            learnedCount = learned,
            partiallyLearnedCount = partial,
            pendingCount = (ready.size - learned).coerceAtLeast(0),
        )
    }

    suspend fun listReady(): List<ArticleWithImage> = withContext(Dispatchers.IO) {
        repository.listParayLearnReadyArticles()
    }

    /** Next product not fully learned, in designation order. */
    suspend fun nextPending(): ArticleWithImage? = withContext(Dispatchers.IO) {
        listReady().firstOrNull { article ->
            learnStore.get(article.id)?.status != ParayLearnStatus.LEARNED
        }
    }

    suspend fun sessionProduct(articleId: Long): ParayLearnSessionProduct? = withContext(Dispatchers.IO) {
        val article = repository.getArticleWithImageById(articleId) ?: return@withContext null
        val path = article.imagePath?.takeIf { it.isNotBlank() } ?: return@withContext null
        if (article.barcode.isBlank()) return@withContext null
        ParayLearnSessionProduct(
            articleId = article.id,
            barcode = article.barcode,
            designation = article.designation,
            pngPath = path,
            record = learnStore.get(article.id),
        )
    }
}
