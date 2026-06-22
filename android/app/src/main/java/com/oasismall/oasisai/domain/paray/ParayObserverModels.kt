package com.oasismall.oasisai.domain.paray

/** Why the observer woke up — never runs continuously. */
enum class ParayObserverTrigger {
    APP_STARTUP,
    CSV_IMPORT_COMPLETED,
    PNG_REINDEX_COMPLETED,
    PNG_LOAD_COMPLETED,
    ARTICLE_CREATED,
    ARTICLE_DELETED,
    ARTICLE_UPDATED,
    PARAY_LEARN_COMPLETED,
}

/** Lightweight fingerprint persisted between observation runs. */
data class ParayObserverState(
    val lastArticleCount: Int = 0,
    val lastPngCount: Int = 0,
    val lastMissingPngCount: Int = 0,
    val lastImportId: Long = 0L,
    val lastImportTimestamp: Long = 0L,
    val lastObservationRun: Long = 0L,
    val lastKnowledgeRefresh: Long = 0L,
)

/** Optional payload — avoids re-scanning when caller already has delta data. */
data class ParayObserverContext(
    val importId: Long? = null,
    val importSummary: com.oasismall.oasisai.domain.ImportDiffSummary? = null,
    val articleId: Long? = null,
)

data class ParayObserverFingerprint(
    val articleCount: Int,
    val pngCount: Int,
    val missingPngCount: Int,
    val lastImportId: Long,
    val lastImportTimestamp: Long,
)

/** Rolling knowledge summary — separate from learn/visual indexes. */
data class ParayObserverKnowledge(
    val lastChangeSummary: String = "",
    val lastCsvImportId: Long = 0L,
    val lastCsvNewCount: Int = 0,
    val lastCsvPriceChanges: Int = 0,
    val lastCsvRenamed: Int = 0,
    val lastCsvRemoved: Int = 0,
    val lastPngLinkedCount: Int = 0,
    val lastPngGainedEstimate: Int = 0,
    val lastPngLostEstimate: Int = 0,
    val lastMissingPngCount: Int = 0,
    val lastActiveArticleCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Cached observer KPIs for PARAY Home — never built on screen open. */
data class ParayObserverSummary(
    val lastObservationAt: Long = 0L,
    val lastKnowledgeRefresh: Long = 0L,
    val catalogChangesDetected: Boolean = false,
    val lastChangeSummary: String = "",
    val newProducts: Int = 0,
    val priceChanges: Int = 0,
    val renamedProducts: Int = 0,
    val removedProducts: Int = 0,
    val lastImportId: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(state: ParayObserverState, knowledge: ParayObserverKnowledge): ParayObserverSummary {
            val hasChanges = knowledge.lastChangeSummary.isNotBlank() ||
                knowledge.lastCsvNewCount > 0 ||
                knowledge.lastCsvPriceChanges > 0
            return ParayObserverSummary(
                lastObservationAt = state.lastObservationRun,
                lastKnowledgeRefresh = state.lastKnowledgeRefresh,
                catalogChangesDetected = hasChanges,
                lastChangeSummary = knowledge.lastChangeSummary,
                newProducts = knowledge.lastCsvNewCount,
                priceChanges = knowledge.lastCsvPriceChanges,
                renamedProducts = knowledge.lastCsvRenamed,
                removedProducts = knowledge.lastCsvRemoved,
                lastImportId = knowledge.lastCsvImportId,
                updatedAt = knowledge.updatedAt,
            )
        }
    }
}

/** Cached PARAY Home display — shown instantly while background refresh runs. */
data class ParayHomeDisplayCache(
    val manifest: ParayManifest,
    val office: ParayOfficeLink,
    val neural: ParayNeuralSnapshot,
    val folders: List<ParayFolderEntry>,
    val barcodePatterns: Int,
    val cachedAt: Long = System.currentTimeMillis(),
)
