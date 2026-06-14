package com.oasismall.oasisai.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.model.PrintBatchStatus
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatchDetailState(
    val batch: PrintBatchEntity? = null,
    val items: List<PrintBatchItemEntity> = emptyList(),
)

class PrintHistoryViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    val batches = repository.observePrintBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow(BatchDetailState())
    val detail: StateFlow<BatchDetailState> = _detail.asStateFlow()

    fun loadBatch(batchId: Long) {
        viewModelScope.launch {
            _detail.value = BatchDetailState(
                batch = repository.getPrintBatch(batchId),
                items = repository.getPrintBatchItems(batchId),
            )
        }
    }

    fun markPrinted(batchId: Long) {
        viewModelScope.launch {
            repository.updatePrintBatchStatus(batchId, PrintBatchStatus.PRINTED.name)
            loadBatch(batchId)
        }
    }

    fun markPlaced(batchId: Long) {
        viewModelScope.launch {
            repository.updatePrintBatchStatus(batchId, PrintBatchStatus.PLACED.name)
            loadBatch(batchId)
        }
    }
}
