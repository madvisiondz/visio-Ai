package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds the trusted-product learning queue from Room catalog + PNG assets. */
class ParayLearnQueue(
    private val repository: OasisRepository,
    private val learnStore: ParayLearnStore,
) {
    suspend fun buildStats(): ParayLearnQueueStats = withContext(Dispatchers.IO) {
        val ready = ParayLearnEligibility.filterReady(repository.listParayLearnReadyArticles())
        val learnedInReady = ready.count { article ->
            learnStore.get(article.id)?.status == ParayLearnStatus.LEARNED
        }
        val partialInReady = ready.count { article ->
            learnStore.get(article.id)?.status == ParayLearnStatus.PARTIALLY_LEARNED
        }
        val pendingInReady = ready.count { article ->
            learnStore.get(article.id)?.status != ParayLearnStatus.LEARNED
        }
        val coverage = if (ready.isEmpty()) {
            0f
        } else {
            learnedInReady.toFloat() / ready.size * 100f
        }
        ParayLearnQueueStats(
            readyCount = ready.size,
            learnedCount = learnedInReady,
            partiallyLearnedCount = partialInReady,
            pendingCount = pendingInReady,
            coveragePercent = coverage,
        )
    }

    suspend fun listReady() = withContext(Dispatchers.IO) {
        ParayLearnEligibility.filterReady(repository.listParayLearnReadyArticles())
    }

    suspend fun nextPending() = withContext(Dispatchers.IO) {
        listReady().firstOrNull { article ->
            learnStore.get(article.id)?.status != ParayLearnStatus.LEARNED
        }
    }
}
