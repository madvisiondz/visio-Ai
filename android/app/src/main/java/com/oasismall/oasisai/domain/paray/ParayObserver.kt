package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Lightweight curiosity engine — reacts to change, never scans the full catalog repeatedly.
 * Observation only: no learning, no recognition, no AI.
 */
class ParayObserver(
    private val home: ParayHome,
    private val repository: OasisRepository,
    private val store: ParayObserverStore = ParayObserverStore(home),
    private val knowledgeObserver: ParayKnowledgeObserver? = null,
) {
    suspend fun onTrigger(
        trigger: ParayObserverTrigger,
        context: ParayObserverContext = ParayObserverContext(),
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = store.readState()

        if (trigger == ParayObserverTrigger.PARAY_LEARN_COMPLETED) {
            if (context.articleId != null) {
                store.appendEvent(
                    type = "paray_learn",
                    trigger = trigger,
                    data = JSONObject().put("articleId", context.articleId),
                )
            }
            store.writeState(previous.copy(lastObservationRun = now))
            val fingerprint = loadFingerprint()
            knowledgeObserver?.onCatalogChange(
                ParayKnowledgeInput(
                    trigger = trigger,
                    context = context,
                    fingerprint = fingerprint,
                ),
            )
            refreshObserverSummary(store.readState(), store.readKnowledge(), changesDetected = false)
            return@withContext
        }

        val fingerprint = loadFingerprint()

        val importChanged = trigger == ParayObserverTrigger.CSV_IMPORT_COMPLETED ||
            (context.importId != null && context.importId != previous.lastImportId) ||
            fingerprint.lastImportId != previous.lastImportId
        val pngChanged = fingerprint.pngCount != previous.lastPngCount
            || fingerprint.missingPngCount != previous.lastMissingPngCount
        val articleChanged = fingerprint.articleCount != previous.lastArticleCount

        if (!importChanged && !pngChanged && !articleChanged) {
            store.writeState(
                previous.copy(lastObservationRun = now),
            )
            refreshObserverSummary(store.readState(), store.readKnowledge(), changesDetected = false)
            return@withContext
        }

        var knowledge = store.readKnowledge()
        val summaries = mutableListOf<String>()

        if (importChanged) {
            val csvKnowledge = observeCsvImport(fingerprint, context, trigger)
            knowledge = knowledge.copy(
                lastCsvImportId = csvKnowledge.importId,
                lastCsvNewCount = csvKnowledge.newCount,
                lastCsvPriceChanges = csvKnowledge.priceChanges,
                lastCsvRenamed = csvKnowledge.renamed,
                lastCsvRemoved = csvKnowledge.removed,
            )
            summaries += csvKnowledge.summaryLine
        }

        if (pngChanged || trigger == ParayObserverTrigger.PNG_REINDEX_COMPLETED ||
            trigger == ParayObserverTrigger.PNG_LOAD_COMPLETED
        ) {
            val pngKnowledge = observePngChange(previous, fingerprint, trigger)
            knowledge = knowledge.copy(
                lastPngLinkedCount = pngKnowledge.linkedCount,
                lastPngGainedEstimate = pngKnowledge.gainedEstimate,
                lastPngLostEstimate = pngKnowledge.lostEstimate,
                lastMissingPngCount = pngKnowledge.missingCount,
            )
            summaries += pngKnowledge.summaryLine
        }

        if (articleChanged && !importChanged) {
            val articleKnowledge = observeArticleMetadata(fingerprint, trigger, context)
            knowledge = knowledge.copy(lastActiveArticleCount = articleKnowledge.activeCount)
            summaries += articleKnowledge.summaryLine
        }

        if (summaries.isNotEmpty()) {
            knowledge = knowledge.copy(
                lastChangeSummary = summaries.joinToString(" · "),
                updatedAt = now,
            )
            store.writeKnowledge(knowledge)
        }

        store.writeState(
            ParayObserverState(
                lastArticleCount = fingerprint.articleCount,
                lastPngCount = fingerprint.pngCount,
                lastMissingPngCount = fingerprint.missingPngCount,
                lastImportId = fingerprint.lastImportId,
                lastImportTimestamp = fingerprint.lastImportTimestamp,
                lastObservationRun = now,
                lastKnowledgeRefresh = if (summaries.isNotEmpty()) now else previous.lastKnowledgeRefresh,
            ),
        )

        knowledgeObserver?.onCatalogChange(
            ParayKnowledgeInput(
                trigger = trigger,
                context = context,
                fingerprint = fingerprint,
                importChanged = importChanged,
                pngChanged = pngChanged,
                articleChanged = articleChanged,
            ),
        )
        refreshObserverSummary(store.readState(), knowledge, changesDetected = summaries.isNotEmpty())
    }

    fun readKnowledge(): ParayObserverKnowledge = store.readKnowledge()

    private fun refreshObserverSummary(
        state: ParayObserverState,
        knowledge: ParayObserverKnowledge,
        changesDetected: Boolean,
    ) {
        store.writeSummary(
            ParayObserverSummary(
                lastObservationAt = state.lastObservationRun,
                lastKnowledgeRefresh = state.lastKnowledgeRefresh,
                catalogChangesDetected = changesDetected || knowledge.lastChangeSummary.isNotBlank(),
                lastChangeSummary = knowledge.lastChangeSummary,
                newProducts = knowledge.lastCsvNewCount,
                priceChanges = knowledge.lastCsvPriceChanges,
                renamedProducts = knowledge.lastCsvRenamed,
                removedProducts = knowledge.lastCsvRemoved,
                lastImportId = knowledge.lastCsvImportId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun loadFingerprint(): ParayObserverFingerprint {
        val latest = repository.getLatestImport()
        return ParayObserverFingerprint(
            articleCount = repository.countActiveArticles(),
            pngCount = repository.countLinkedPngs(),
            missingPngCount = repository.countMissingImages(),
            lastImportId = latest?.id ?: 0L,
            lastImportTimestamp = latest?.importedAt ?: 0L,
        )
    }

    private data class CsvObservation(
        val importId: Long,
        val newCount: Int,
        val priceChanges: Int,
        val renamed: Int,
        val removed: Int,
        val summaryLine: String,
    )

    private suspend fun observeCsvImport(
        fingerprint: ParayObserverFingerprint,
        context: ParayObserverContext,
        trigger: ParayObserverTrigger,
    ): CsvObservation {
        val importId = context.importId ?: fingerprint.lastImportId
        val summary = context.importSummary
        val importEntity = if (summary != null) null else repository.getImportById(importId)
        val counts = summary?.displayCounts
        val newCount = counts?.newCount ?: importEntity?.newCount ?: 0
        val priceChanges = counts?.priceChangedCount ?: importEntity?.priceChangedCount ?: 0
        val renamed = counts?.renamedCount ?: importEntity?.renamedCount ?: 0
        val removed = counts?.removedCount ?: importEntity?.removedCount ?: 0

        store.appendEvent(
            type = "csv_import",
            trigger = trigger,
            data = JSONObject()
                .put("importId", importId)
                .put("newCount", newCount)
                .put("priceChangedCount", priceChanges)
                .put("renamedCount", renamed)
                .put("removedCount", removed),
        )

        return CsvObservation(
            importId = importId,
            newCount = newCount,
            priceChanges = priceChanges,
            renamed = renamed,
            removed = removed,
            summaryLine = "Import #$importId — new $newCount, prices $priceChanges, renamed $renamed, removed $removed",
        )
    }

    private data class PngObservation(
        val linkedCount: Int,
        val gainedEstimate: Int,
        val lostEstimate: Int,
        val missingCount: Int,
        val summaryLine: String,
    )

    private fun observePngChange(
        previous: ParayObserverState,
        fingerprint: ParayObserverFingerprint,
        trigger: ParayObserverTrigger,
    ): PngObservation {
        val delta = fingerprint.pngCount - previous.lastPngCount
        val gained = delta.coerceAtLeast(0)
        val lost = (-delta).coerceAtLeast(0)
        store.appendEvent(
            type = "png_change",
            trigger = trigger,
            data = JSONObject()
                .put("linkedCount", fingerprint.pngCount)
                .put("gainedEstimate", gained)
                .put("lostEstimate", lost)
                .put("missingCount", fingerprint.missingPngCount),
        )
        return PngObservation(
            linkedCount = fingerprint.pngCount,
            gainedEstimate = gained,
            lostEstimate = lost,
            missingCount = fingerprint.missingPngCount,
            summaryLine = "PNG linked ${fingerprint.pngCount} (+$gained/−$lost), missing ${fingerprint.missingPngCount}",
        )
    }

    private data class ArticleObservation(
        val activeCount: Int,
        val summaryLine: String,
    )

    private fun observeArticleMetadata(
        fingerprint: ParayObserverFingerprint,
        trigger: ParayObserverTrigger,
        context: ParayObserverContext,
    ): ArticleObservation {
        store.appendEvent(
            type = "article_metadata",
            trigger = trigger,
            data = JSONObject()
                .put("activeCount", fingerprint.articleCount)
                .put("missingPngCount", fingerprint.missingPngCount)
                .put("articleId", context.articleId),
        )
        return ArticleObservation(
            activeCount = fingerprint.articleCount,
            summaryLine = "Articles ${fingerprint.articleCount}, missing PNG ${fingerprint.missingPngCount}",
        )
    }
}
