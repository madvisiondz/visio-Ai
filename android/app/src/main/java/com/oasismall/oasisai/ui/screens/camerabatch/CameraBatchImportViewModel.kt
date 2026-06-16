package com.oasismall.oasisai.ui.screens.camerabatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.PhotoroomStorage
import com.oasismall.oasisai.domain.visio.VisioDownloadStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PendingBatchRow(
    val item: CameraBatchItemEntity,
    val photoroomPng: File?,
)

class CameraBatchImportViewModel(
    private val store: CameraBatchStore,
) : ViewModel() {
    val batchFolder = VisioDownloadStorage.displayPath(store.batchFolderName())
    val photoroomPath = PhotoroomStorage.DISPLAY_PATH

    private val refreshTick = MutableStateFlow(0)

    val pendingRows: StateFlow<List<PendingBatchRow>> = combine(
        store.observePendingToday(),
        refreshTick,
    ) { items, _ ->
        items.map { item ->
            PendingBatchRow(item, PhotoroomStorage.findPngForBarcode(item.barcode))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _photoroomPngCount = MutableStateFlow(PhotoroomStorage.listPngFiles().size)
    val photoroomPngCount: StateFlow<Int> = _photoroomPngCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun refreshPhotoroomList() {
        _photoroomPngCount.value = PhotoroomStorage.listPngFiles().size
        refreshTick.value++
    }

    fun importOne(itemId: Long) {
        viewModelScope.launch {
            _busy.value = true
            store.importFromPhotoroom(itemId).fold(
                onSuccess = { msg ->
                    _message.value = msg
                    refreshPhotoroomList()
                },
                onFailure = { e -> _message.value = e.message ?: "Import failed" },
            )
            _busy.value = false
        }
    }

    fun importAllMatched() {
        viewModelScope.launch {
            _busy.value = true
            val result = store.importAllPending()
            _message.value = buildString {
                append("Imported ${result.imported}")
                if (result.failed > 0) append(", ${result.failed} still waiting")
                if (result.errors.isNotEmpty()) append("\n${result.errors.joinToString("\n")}")
            }
            refreshPhotoroomList()
            _busy.value = false
        }
    }
}
