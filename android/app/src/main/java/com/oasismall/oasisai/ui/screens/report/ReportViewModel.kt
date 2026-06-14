package com.oasismall.oasisai.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReportCsvChangeRow(
    val change: ImportChangeEntity,
    val importFileName: String,
    val importDate: Long,
)

data class ReportDesignPrintRow(
    val batch: PrintBatchEntity,
    val items: List<PrintBatchItemEntity>,
)

data class ReportUiState(
    val latestImport: ImportEntity? = null,
    val previousImport: ImportEntity? = null,
    val csvChanges: List<ReportCsvChangeRow> = emptyList(),
    val designPrints: List<ReportDesignPrintRow> = emptyList(),
) {
    val csvChangesByType: Map<String, List<ReportCsvChangeRow>> =
        csvChanges.groupBy { it.change.changeType }

    val latestImportSummary: String
        get() = latestImport?.let { imp ->
            "${imp.fileName} — +${imp.newCount} new, ${imp.priceChangedCount} price, ${imp.renamedCount} renamed, ${imp.removedCount} removed"
        } ?: "No CSV import yet"

    val previousImportSummary: String
        get() = previousImport?.let { imp ->
            "${imp.fileName} (${formatDate(imp.importedAt)})"
        } ?: "No previous import"
}

class ReportViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    val state: StateFlow<ReportUiState> = combine(
        repository.observeImports(),
        repository.observeRecentCsvChanges(),
        repository.observeDesignShelfPrints(),
    ) { imports, changes, designBatches ->
        val importById = imports.associateBy { it.id }
        ReportUiState(
            latestImport = imports.firstOrNull(),
            previousImport = imports.getOrNull(1),
            csvChanges = changes.map { change ->
                val imp = importById[change.importId]
                ReportCsvChangeRow(
                    change = change,
                    importFileName = imp?.fileName ?: "Import #${change.importId}",
                    importDate = imp?.importedAt ?: 0L,
                )
            },
            designPrints = designBatches.map { batch ->
                ReportDesignPrintRow(
                    batch = batch,
                    items = emptyList(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    suspend fun loadPrintItems(batchId: Long): List<PrintBatchItemEntity> =
        repository.getPrintBatchItems(batchId)
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
