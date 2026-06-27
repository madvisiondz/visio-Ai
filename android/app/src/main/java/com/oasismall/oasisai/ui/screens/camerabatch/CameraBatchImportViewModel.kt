package com.oasismall.oasisai.ui.screens.camerabatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.PhotoroomStorage
import com.oasismall.oasisai.domain.visio.VisioDownloadStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingBatchRow(
    val item: CameraBatchItemEntity,
    val photoroomPng: PhotoroomStorage.PngRef?,
)

class CameraBatchImportViewModel(
    private val store: CameraBatchStore,
) : ViewModel() {
    val batchFolder = VisioDownloadStorage.displayPath(store.batchFolderName())

    private val refreshTick = MutableStateFlow(0)

    private val _photoroomPath = MutableStateFlow(store.photoroomDisplayPath())
    val photoroomPath: StateFlow<String> = _photoroomPath.asStateFlow()

    val pendingRows: StateFlow<List<PendingBatchRow>> = combine(
        store.observeAllPending(),
        refreshTick,
    ) { items, _ -> items }
        .map { items ->
            items.map { item ->
                PendingBatchRow(item, store.findPhotoroomPng(item.barcode, item.designation))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _photoroomPngCount = MutableStateFlow(0)
    val photoroomPngCount: StateFlow<Int> = _photoroomPngCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refreshPhotoroomList()
    }

    fun refreshPhotoroomList() {
        viewModelScope.launch(Dispatchers.IO) {
            store.refreshPhotoroomIndex()
            val pngs = store.listPhotoroomPngs()
            _photoroomPath.value = store.photoroomDisplayPath()
            _photoroomPngCount.value = pngs.size
            refreshTick.value++
        }
    }

    fun importOne(itemId: Long) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                store.importFromPhotoroom(itemId)
            }.fold(
                onSuccess = { msg -> _message.value = msg },
                onFailure = { e -> _message.value = e.message ?: "Import failed" },
            )
            _busy.value = false
        }
    }

    fun importManual(itemId: Long, uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                store.importFromManualPng(itemId, uri)
            }.fold(
                onSuccess = { msg -> _message.value = msg },
                onFailure = { e -> _message.value = e.message ?: "Manual import failed" },
            )
            _busy.value = false
        }
    }

    fun importAllMatched() {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) { store.importAllPending() }
            _message.value = buildString {
                append("Imported ${result.imported} → To share")
                if (result.failed > 0) append(", ${result.failed} still waiting")
                if (result.errors.isNotEmpty()) append("\n${result.errors.joinToString("\n")}")
            }
            _busy.value = false
        }
    }

    fun removePending(itemId: Long) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                store.removePendingItem(itemId)
            }.fold(
                onSuccess = { msg -> _message.value = msg },
                onFailure = { e -> _message.value = e.message ?: "Could not remove" },
            )
            _busy.value = false
        }
    }
}
