package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Transforms catalog changes (from [ParayObserver]) into durable knowledge records.
 * Never scans the full catalog — only import deltas, PNG link windows, and learn events.
 */
class ParayKnowledgeObserver(
    private val home: ParayHome,
    private val repository: OasisRepository,
    private val learnStore: ParayLearnStore,
    private val store: ParayKnowledgeStore = ParayKnowledgeStore(home),
) {
    suspend fun onCatalogChange(input: ParayKnowledgeInput) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var state = store.readState()
        val articles = store.readArticles()
        var latestImport: ParayImportKnowledgeRecord? = null

        when (input.trigger) {
            ParayObserverTrigger.PARAY_LEARN_COMPLETED -> {
                input.context.articleId?.let { updateArticleFromLearn(it, articles, now) }
            }
            else -> {
                if (input.importChanged) {
                    latestImport = processImport(input, articles, state, now)?.also { record ->
                        state = state.copy(
                            lastProcessedImportId = maxOf(state.lastProcessedImportId, record.importId),
                            importHistory = (state.importHistory + record).takeLast(32),
                        )
                    }
                }
                if (input.pngChanged ||
                    input.trigger == ParayObserverTrigger.PNG_REINDEX_COMPLETED ||
                    input.trigger == ParayObserverTrigger.PNG_LOAD_COMPLETED
                ) {
                    processPngChanges(input, articles, state, now)
                }
                if (input.articleChanged && !input.importChanged && input.context.articleId != null) {
                    refreshSingleArticle(input.context.articleId!!, articles, now, event = "Updated")
                }
            }
        }

        val brands = rebuildBrandSummaries(articles)
        val categories = rebuildCategorySummaries(articles)
        val summary = buildSummary(input.fingerprint, state, latestImport)

        store.writeArticles(articles)
        store.writeBrandSummaries(brands)
        store.writeCategorySummaries(categories)
        store.writeSummary(summary)
        store.writeState(
            state.copy(
                lastSummaryRefresh = now,
                articleRecordCount = articles.values.count { !it.removed },
            ),
        )
    }

    fun readSummary(): ParayKnowledgeSummary = store.readSummary()

    private suspend fun processImport(
        input: ParayKnowledgeInput,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        state: ParayKnowledgeState,
        now: Long,
    ): ParayImportKnowledgeRecord? {
        val importId = input.context.importId ?: input.fingerprint.lastImportId
        if (importId <= 0L) return null
        if (state.importHistory.any { it.importId == importId }) {
            return state.importHistory.lastOrNull { it.importId == importId }
        }

        val changes = repository.getImportChanges(importId)
            .filter { it.changeType != ImportChangeType.UNCHANGED.name }

        var newCount = 0
        var priceCount = 0
        var renamedCount = 0
        var removedCount = 0

        for (change in changes) {
            when (change.changeType) {
                ImportChangeType.NEW.name -> {
                    newCount++
                    upsertFromImportChange(change, articles, now, timelineEvent = "Created")
                }
                ImportChangeType.PRICE_CHANGED.name -> {
                    priceCount++
                    upsertFromImportChange(
                        change,
                        articles,
                        now,
                        timelineEvent = "Price Changed",
                        timelineDetail = change.oldValue?.let { old ->
                            "${old} → ${change.newValue.orEmpty()}"
                        },
                        priceChangeAt = now,
                    )
                }
                ImportChangeType.RENAMED.name -> {
                    renamedCount++
                    upsertFromImportChange(
                        change,
                        articles,
                        now,
                        timelineEvent = "Designation Changed",
                        timelineDetail = change.oldValue?.let { old ->
                            "${old} → ${change.newValue.orEmpty()}"
                        },
                    )
                }
                ImportChangeType.REMOVED.name -> {
                    removedCount++
                    markRemoved(change, articles, now)
                }
            }
        }

        return ParayImportKnowledgeRecord(
            importId = importId,
            newCount = newCount,
            priceChangedCount = priceCount,
            designationChangedCount = renamedCount,
            removedCount = removedCount,
            observedAt = now,
        )
    }

    private suspend fun processPngChanges(
        input: ParayKnowledgeInput,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        state: ParayKnowledgeState,
        now: Long,
    ) {
        val since = state.lastSummaryRefresh.takeIf { it > 0L }
            ?: (input.fingerprint.lastImportTimestamp - 1L).coerceAtLeast(0L)
        val ids = linkedSetOf<Long>()
        input.context.articleId?.let { ids.add(it) }
        ids.addAll(repository.getArticleIdsLinkedSince(since))

        for (id in ids) {
            refreshSingleArticle(id, articles, now, event = "PNG Added", onlyIfPngGained = true)
        }
    }

    private suspend fun updateArticleFromLearn(
        articleId: Long,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        now: Long,
    ) {
        val learn = learnStore.get(articleId)
        val existing = articles[articleId]
        val wasLearned = existing?.learnStatus == ParayLearnStatus.LEARNED
        val status = learn?.status ?: existing?.learnStatus ?: ParayLearnStatus.NOT_LEARNED
        refreshSingleArticle(articleId, articles, now, event = null)
        val updated = articles[articleId] ?: return
        articles[articleId] = if (status == ParayLearnStatus.LEARNED && !wasLearned) {
            updated.copy(
                learnStatus = status,
                lastModifiedAt = now,
                timeline = appendTimeline(updated.timeline, "Learned", now),
            )
        } else {
            updated.copy(learnStatus = status, lastModifiedAt = now)
        }
    }

    private suspend fun refreshSingleArticle(
        articleId: Long,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        now: Long,
        event: String?,
        onlyIfPngGained: Boolean = false,
    ) {
        val article = repository.getArticleWithImageById(articleId) ?: return
        val entity = repository.getArticleById(articleId)
        val existing = articles[articleId]
        val hadPng = existing?.hasPng == true
        val hasPng = hasLinkedPng(article)
        if (onlyIfPngGained && (hadPng || !hasPng)) return

        val built = buildArticleKnowledge(article, entity, existing, now)
        articles[articleId] = if (event != null) {
            built.copy(
                timeline = appendTimeline(built.timeline, event, now),
                lastModifiedAt = now,
            )
        } else {
            built
        }
    }

    private suspend fun upsertFromImportChange(
        change: com.oasismall.oasisai.data.db.entity.ImportChangeEntity,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        now: Long,
        timelineEvent: String,
        timelineDetail: String? = null,
        priceChangeAt: Long? = null,
    ) {
        val articleId = change.articleId
        val article = when {
            articleId != null -> repository.getArticleWithImageById(articleId)
            else -> repository.getArticleWithImageByBarcode(change.barcode)
        } ?: return
        val entity = repository.getArticleById(article.id)
        val existing = articles[article.id]
        val built = buildArticleKnowledge(article, entity, existing, now).copy(
            lastModifiedAt = now,
            lastPriceChangeAt = priceChangeAt ?: existing?.lastPriceChangeAt,
            timeline = appendTimeline(existing?.timeline.orEmpty(), timelineEvent, now, timelineDetail),
        )
        articles[article.id] = built
    }

    private fun markRemoved(
        change: com.oasismall.oasisai.data.db.entity.ImportChangeEntity,
        articles: MutableMap<Long, ParayArticleKnowledge>,
        now: Long,
    ) {
        val articleId = change.articleId ?: articles.values.firstOrNull {
            it.barcode == change.barcode
        }?.articleId
        if (articleId == null) return
        val existing = articles[articleId]
        articles[articleId] = (existing ?: ParayArticleKnowledge(
            articleId = articleId,
            barcode = change.barcode,
            designation = change.designation,
            firstSeenAt = now,
        )).copy(
            designation = change.designation,
            removed = true,
            lastModifiedAt = now,
            timeline = appendTimeline(existing?.timeline.orEmpty(), "Removed", now),
        )
    }

    private fun buildArticleKnowledge(
        article: com.oasismall.oasisai.data.db.dao.ArticleWithImage,
        entity: com.oasismall.oasisai.data.db.entity.ArticleEntity?,
        existing: ParayArticleKnowledge?,
        now: Long,
    ): ParayArticleKnowledge {
        val learn = learnStore.get(article.id)
        val hasPng = hasLinkedPng(article)
        return ParayArticleKnowledge(
            articleId = article.id,
            barcode = article.barcode,
            designation = article.designation,
            brand = article.brand?.takeIf { it.isNotBlank() },
            category = article.category?.takeIf { it.isNotBlank() },
            family = entity?.famille?.takeIf { it.isNotBlank() } ?: learn?.family,
            hasPng = hasPng,
            learnStatus = learn?.status ?: existing?.learnStatus ?: ParayLearnStatus.NOT_LEARNED,
            firstSeenAt = existing?.firstSeenAt ?: now,
            lastModifiedAt = now,
            lastPriceChangeAt = existing?.lastPriceChangeAt,
            removed = existing?.removed == true || !article.isActive,
            timeline = existing?.timeline.orEmpty(),
        )
    }

    private suspend fun buildSummary(
        fingerprint: ParayObserverFingerprint,
        state: ParayKnowledgeState,
        latestImport: ParayImportKnowledgeRecord?,
    ): ParayKnowledgeSummary {
        val totalArticles = fingerprint.articleCount
        val linkedPng = fingerprint.pngCount
        val missingPng = fingerprint.missingPngCount
        val learned = learnStore.learnedCount()
        val pngCoverage = if (totalArticles > 0) 100f * linkedPng / totalArticles else 0f
        val learnCoverage = if (linkedPng > 0) 100f * learned / linkedPng else 0f
        val recentImport = latestImport ?: state.importHistory.lastOrNull()

        return ParayKnowledgeSummary(
            totalArticles = totalArticles,
            totalBrands = repository.countDistinctBrands(),
            totalCategories = repository.countDistinctCategories(),
            totalFamilies = repository.countDistinctFamilies(),
            knownArticleCount = state.articleRecordCount,
            pngCoveragePercent = pngCoverage,
            learnCoveragePercent = learnCoverage,
            articlesMissingPng = missingPng,
            articlesMissingLearn = (linkedPng - learned).coerceAtLeast(0),
            recentPriceChanges = recentImport?.priceChangedCount ?: 0,
            recentImportId = recentImport?.importId ?: state.lastProcessedImportId,
            gaps = ParayKnowledgeGaps(
                missingPng = missingPng,
                missingLearn = (linkedPng - learned).coerceAtLeast(0),
                missingBrand = repository.countMissingBrand(),
                missingCategory = repository.countMissingCategory(),
                missingFamily = repository.countMissingFamily(),
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun rebuildBrandSummaries(
        articles: Map<Long, ParayArticleKnowledge>,
    ): Map<String, ParayBrandKnowledgeSummary> {
        val active = articles.values.filter { !it.removed }
        return active
            .groupBy { it.brand?.trim().orEmpty().ifBlank { UNKNOWN_BRAND } }
            .mapValues { (_, rows) ->
                val brand = rows.first().brand?.trim().orEmpty().ifBlank { UNKNOWN_BRAND }
                val pngCount = rows.count { it.hasPng }
                val learnedCount = rows.count { it.learnStatus == ParayLearnStatus.LEARNED }
                ParayBrandKnowledgeSummary(
                    brand = brand,
                    productCount = rows.size,
                    pngCount = pngCount,
                    learnedCount = learnedCount,
                    missingPngCount = rows.size - pngCount,
                )
            }
    }

    private fun rebuildCategorySummaries(
        articles: Map<Long, ParayArticleKnowledge>,
    ): Map<String, ParayCategoryKnowledgeSummary> {
        val active = articles.values.filter { !it.removed }
        return active
            .groupBy { it.category?.trim().orEmpty().ifBlank { UNKNOWN_CATEGORY } }
            .mapValues { (_, rows) ->
                val category = rows.first().category?.trim().orEmpty().ifBlank { UNKNOWN_CATEGORY }
                ParayCategoryKnowledgeSummary(
                    category = category,
                    productCount = rows.size,
                    pngCount = rows.count { it.hasPng },
                    learnedCount = rows.count { it.learnStatus == ParayLearnStatus.LEARNED },
                )
            }
    }

    private fun hasLinkedPng(article: com.oasismall.oasisai.data.db.dao.ArticleWithImage): Boolean =
        !article.imagePath.isNullOrBlank() && article.imageStatus == "FOUND"

    private fun appendTimeline(
        existing: List<ParayArticleTimelineEvent>,
        event: String,
        at: Long,
        detail: String? = null,
    ): List<ParayArticleTimelineEvent> =
        (existing + ParayArticleTimelineEvent(event = event, at = at, detail = detail))
            .takeLast(MAX_TIMELINE)

    companion object {
        private const val UNKNOWN_BRAND = "(unknown brand)"
        private const val UNKNOWN_CATEGORY = "(unknown category)"
        private const val MAX_TIMELINE = 24
    }
}
