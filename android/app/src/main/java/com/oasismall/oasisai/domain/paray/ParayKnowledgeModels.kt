package com.oasismall.oasisai.domain.paray

/** Input from [ParayObserver] when catalog change was detected — not a full-catalog scan. */
data class ParayKnowledgeInput(
    val trigger: ParayObserverTrigger,
    val context: ParayObserverContext = ParayObserverContext(),
    val fingerprint: ParayObserverFingerprint,
    val importChanged: Boolean = false,
    val pngChanged: Boolean = false,
    val articleChanged: Boolean = false,
)

data class ParayKnowledgeState(
    val lastProcessedImportId: Long = 0L,
    val lastSummaryRefresh: Long = 0L,
    val articleRecordCount: Int = 0,
    val importHistory: List<ParayImportKnowledgeRecord> = emptyList(),
)

data class ParayImportKnowledgeRecord(
    val importId: Long,
    val newCount: Int = 0,
    val priceChangedCount: Int = 0,
    val designationChangedCount: Int = 0,
    val removedCount: Int = 0,
    val observedAt: Long = System.currentTimeMillis(),
)

data class ParayArticleTimelineEvent(
    val event: String,
    val at: Long,
    val detail: String? = null,
)

/** Lightweight catalog knowledge for one article — no image data. */
data class ParayArticleKnowledge(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val brand: String? = null,
    val category: String? = null,
    val family: String? = null,
    val hasPng: Boolean = false,
    val learnStatus: ParayLearnStatus = ParayLearnStatus.NOT_LEARNED,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastPriceChangeAt: Long? = null,
    val removed: Boolean = false,
    val timeline: List<ParayArticleTimelineEvent> = emptyList(),
)

data class ParayBrandKnowledgeSummary(
    val brand: String,
    val productCount: Int = 0,
    val pngCount: Int = 0,
    val learnedCount: Int = 0,
    val missingPngCount: Int = 0,
)

data class ParayCategoryKnowledgeSummary(
    val category: String,
    val productCount: Int = 0,
    val pngCount: Int = 0,
    val learnedCount: Int = 0,
)

/** Cached KPIs for future PARAY Home — never built on screen open. */
data class ParayKnowledgeSummary(
    val totalArticles: Int = 0,
    val totalBrands: Int = 0,
    val totalCategories: Int = 0,
    val totalFamilies: Int = 0,
    val knownArticleCount: Int = 0,
    val pngCoveragePercent: Float = 0f,
    val learnCoveragePercent: Float = 0f,
    val articlesMissingPng: Int = 0,
    val articlesMissingLearn: Int = 0,
    val recentPriceChanges: Int = 0,
    val recentImportId: Long = 0L,
    val gaps: ParayKnowledgeGaps = ParayKnowledgeGaps(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ParayKnowledgeGaps(
    val missingPng: Int = 0,
    val missingLearn: Int = 0,
    val missingBrand: Int = 0,
    val missingCategory: Int = 0,
    val missingFamily: Int = 0,
)
