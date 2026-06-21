package com.oasismall.oasisai.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.repository.ImportChangeUiRow
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImportChangeCounts
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.data.model.CartType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportCsvChangeRow(
    val change: ImportChangeEntity,
    val importFileName: String,
    val importDate: Long,
    val article: com.oasismall.oasisai.data.db.dao.ArticleWithImage? = null,
) {
    val uiRow: ImportChangeUiRow get() = ImportChangeUiRow(change = change, article = article)
}

data class ReportDesignPrintRow(
    val batch: PrintBatchEntity,
    val items: List<PrintBatchItemEntity>,
)

data class ReportUiState(
    val latestImport: ImportEntity? = null,
    val previousImport: ImportEntity? = null,
    val latestImportCounts: ImportChangeCounts = ImportChangeCounts(),
    val previousImportCounts: ImportChangeCounts = ImportChangeCounts(),
    val csvChanges: List<ReportCsvChangeRow> = emptyList(),
    val designPrints: List<ReportDesignPrintRow> = emptyList(),
    val importantRayonsFiltered: Boolean = false,
    val importantRayonsCount: Int = 0,
) {
    val csvChangesByType: Map<String, List<ReportCsvChangeRow>> =
        csvChanges.groupBy { it.change.changeType }

    val latestImportSummary: String
        get() = latestImport?.let { imp ->
            formatImportChangeSummary(imp.fileName, latestImportCounts)
        } ?: "No CSV import yet"

    val previousImportSummary: String
        get() = previousImport?.let { imp ->
            "${formatImportChangeSummary(imp.fileName, previousImportCounts)} (${formatDate(imp.importedAt)})"
        } ?: "No previous import"
}

internal fun formatImportChangeSummary(fileName: String, counts: ImportChangeCounts): String =
    "$fileName — +${counts.newCount} new, ${counts.priceChangedCount} price, " +
        "${counts.renamedCount} renamed, ${counts.removedCount} removed"

class ReportViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportUiState> = combine(
        repository.observeImports(),
        repository.observeRecentCsvChanges().flatMapLatest { changes ->
            flow {
                val enriched = withContext(Dispatchers.IO) {
                    val rows = repository.enrichImportChanges(changes)
                    rows.map { row -> row.change to row.article }
                }
                emit(enriched)
            }
        },
        repository.observeDesignShelfPrints(),
        repository.importantRayonsConfig,
    ) { imports, enrichedChanges, designBatches, rayonConfig ->
        imports to Triple(enrichedChanges, designBatches, rayonConfig)
    }.flatMapLatest { (imports, triple) ->
        val (enrichedChanges, designBatches, rayonConfig) = triple
        flow {
            val latestTwoIds = imports.take(2).map { it.id }
            val summaries = withContext(Dispatchers.IO) {
                repository.summarizeImportsForDisplay(latestTwoIds, rayonConfig)
            }
            val importById = imports.associateBy { it.id }
            val filteredChanges = enrichedChanges.filter { (_, article) ->
                repository.matchesImportantRayon(article?.rayon, rayonConfig)
            }
            val latest = imports.firstOrNull()
            val previous = imports.getOrNull(1)
            emit(
                ReportUiState(
                    latestImport = latest,
                    previousImport = previous,
                    latestImportCounts = summaries[latest?.id] ?: ImportChangeCounts(),
                    previousImportCounts = summaries[previous?.id] ?: ImportChangeCounts(),
                    csvChanges = filteredChanges.map { (change, article) ->
                        val imp = importById[change.importId]
                        ReportCsvChangeRow(
                            change = change,
                            importFileName = imp?.fileName ?: "Import #${change.importId}",
                            importDate = imp?.importedAt ?: 0L,
                            article = article,
                        )
                    },
                    designPrints = designBatches.map { batch ->
                        ReportDesignPrintRow(
                            batch = batch,
                            items = emptyList(),
                        )
                    },
                    importantRayonsFiltered = rayonConfig.configured && rayonConfig.selectedRayons.isNotEmpty(),
                    importantRayonsCount = rayonConfig.selectedRayons.size,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    suspend fun loadPrintItems(batchId: Long): List<PrintBatchItemEntity> =
        repository.getPrintBatchItems(batchId)

    fun addToShareCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.IMPORT_CHANGE)
        }
    }

    fun addToPhotoshootCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.PHOTOSHOOT, CartSourceTags.IMPORT_CHANGE)
        }
    }
}

internal fun formatDate(epochMs: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
        .format(java.util.Date(epochMs))

internal fun changeTypeLabel(type: String): String = when (type) {
    ImportChangeType.NEW.name -> "New article"
    ImportChangeType.PRICE_CHANGED.name -> "Price changed"
    ImportChangeType.RENAMED.name -> "Renamed"
    ImportChangeType.REMOVED.name -> "Removed from CSV"
    else -> type.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

internal fun changeTypeOrder(type: String): Int = when (type) {
    ImportChangeType.PRICE_CHANGED.name -> 0
    ImportChangeType.NEW.name -> 1
    ImportChangeType.RENAMED.name -> 2
    ImportChangeType.REMOVED.name -> 3
    else -> 9
}
