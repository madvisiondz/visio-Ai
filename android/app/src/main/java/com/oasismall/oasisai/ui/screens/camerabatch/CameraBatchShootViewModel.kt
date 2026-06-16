package com.oasismall.oasisai.ui.screens.camerabatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.db.entity.BatchCameraQueueEntity
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.visio.BatchCameraQueueStore
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.VisioDownloadStorage
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.hasAppGalleryImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class CameraBatchShootStep {
    SCAN,
    LOCKED,
    CREATE_DESIGNATION,
    PICK_MATCH,
    PREVIEW,
}

data class CameraBatchLockedState(
    val barcode: String,
    val inCatalog: Boolean,
    val designation: String,
    val price: Double?,
    val imagePath: String?,
    val articleId: Long?,
    val linkedViaAlternate: Boolean = false,
)

class CameraBatchShootViewModel(
    private val repository: OasisRepository,
    private val store: CameraBatchStore,
    private val queueStore: BatchCameraQueueStore,
) : ViewModel() {
    val batchFolder = VisioDownloadStorage.displayPath(store.batchFolderName())
    val todayShots: StateFlow<List<CameraBatchItemEntity>> =
        store.observeToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shotCount: StateFlow<Int> = store.observePendingCountToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val queuePending: StateFlow<List<BatchCameraQueueEntity>> =
        queueStore.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _step = MutableStateFlow(CameraBatchShootStep.SCAN)
    val step: StateFlow<CameraBatchShootStep> = _step.asStateFlow()

    private val _locked = MutableStateFlow<CameraBatchLockedState?>(null)
    val locked: StateFlow<CameraBatchLockedState?> = _locked.asStateFlow()

    private val _currentQueueItem = MutableStateFlow<BatchCameraQueueEntity?>(null)
    val currentQueueItem: StateFlow<BatchCameraQueueEntity?> = _currentQueueItem.asStateFlow()

    private val _pendingJpeg = MutableStateFlow<File?>(null)
    val pendingJpeg: StateFlow<File?> = _pendingJpeg.asStateFlow()

    private val _designationInput = MutableStateFlow("")
    val designationInput: StateFlow<String> = _designationInput.asStateFlow()

    private val _pickedMatch = MutableStateFlow<ArticleWithImage?>(null)
    val pickedMatch: StateFlow<ArticleWithImage?> = _pickedMatch.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val designationMatches: StateFlow<List<ArticleWithImage>> = _designationInput
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.length < 2) flowOf(emptyList())
            else repository.observeArticles(q).map { it.take(8) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun initQueue(queueItemId: Long?) {
        viewModelScope.launch {
            val item = when {
                queueItemId != null -> queueStore.getById(queueItemId)
                else -> queueStore.nextPending()
            }?.takeIf { !it.done }
            _currentQueueItem.value = item
            if (item != null) {
                _message.value = "Batch capture: ${item.designation}"
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        if (_locked.value != null) return
        val trimmed = barcode.trim()
        if (trimmed.length < 8) return
        viewModelScope.launch { lockBarcode(trimmed) }
    }

    private suspend fun lockBarcode(barcode: String) {
        val resolved = repository.resolveScannedBarcode(barcode)
        val article = resolved?.article
        val queueHint = _currentQueueItem.value?.designation
        _locked.value = CameraBatchLockedState(
            barcode = barcode,
            inCatalog = resolved != null,
            designation = article?.designation ?: queueHint ?: barcode,
            price = article?.price?.takeIf { it > 0.0 },
            imagePath = article?.imagePath?.takeIf { File(it).exists() },
            articleId = article?.id,
            linkedViaAlternate = resolved != null && !resolved.primary,
        )
        _step.value = CameraBatchShootStep.LOCKED
        _message.value = null
    }

    fun unlockForNextScan() {
        _locked.value = null
        _pendingJpeg.value?.delete()
        _pendingJpeg.value = null
        _designationInput.value = ""
        _pickedMatch.value = null
        _step.value = CameraBatchShootStep.SCAN
    }

    fun startCreateDesignation() {
        val locked = _locked.value ?: return
        _designationInput.value = _currentQueueItem.value?.designation ?: locked.barcode
        _step.value = CameraBatchShootStep.CREATE_DESIGNATION
    }

    fun setDesignationInput(value: String) {
        _designationInput.value = value
    }

    fun pickDesignationMatch(article: ArticleWithImage) {
        _pickedMatch.value = article
        _step.value = CameraBatchShootStep.PICK_MATCH
    }

    fun proceedSubBarcode() {
        val locked = _locked.value ?: return
        val match = _pickedMatch.value ?: return
        viewModelScope.launch {
            val err = repository.linkSubBarcodeToMainArticle(
                articleId = match.id,
                mainBarcode = match.barcode,
                subBarcode = locked.barcode,
            )
            if (err != null) {
                _message.value = err
                return@launch
            }
            _message.value = "Sub-barcode linked to ${match.designation} — shoot this flavor."
            _pickedMatch.value = null
            lockBarcode(locked.barcode)
        }
    }

    fun addMatchToShareAndUnlock() {
        val match = _pickedMatch.value ?: return
        viewModelScope.launch {
            if (match.hasAppGalleryImage()) {
                repository.addToCart(match.id, CartType.SHARE, CartSourceTags.BATCH_CAMERA)
                _message.value = "Added to To share."
            } else {
                _message.value = "No PNG yet — shoot or use Proceed."
            }
            _pickedMatch.value = null
            unlockForNextScan()
        }
    }

    fun saveNewDesignation() {
        val locked = _locked.value ?: return
        val des = _designationInput.value.trim().ifBlank { locked.barcode }
        viewModelScope.launch {
            repository.ensureUnknownArticle(locked.barcode, des)
            _message.value = "Saved article: $des"
            lockBarcode(locked.barcode)
        }
    }

    fun onPhotoCaptured(success: Boolean, file: File?) {
        if (!success || file == null || !file.exists()) {
            _message.value = "Capture cancelled."
            _step.value = CameraBatchShootStep.LOCKED
            return
        }
        _pendingJpeg.value = file
        _step.value = CameraBatchShootStep.PREVIEW
    }

    fun retakePhoto() {
        _pendingJpeg.value?.delete()
        _pendingJpeg.value = null
        _step.value = CameraBatchShootStep.LOCKED
    }

    fun confirmAndSave() {
        val jpeg = _pendingJpeg.value ?: return
        val locked = _locked.value ?: return
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                store.saveTaggedShot(
                    sourceJpeg = jpeg,
                    barcode = locked.barcode,
                    hintDesignation = _currentQueueItem.value?.designation ?: locked.designation,
                )
            }.fold(
                onSuccess = { item ->
                    _currentQueueItem.value?.id?.let { queueStore.markDone(it) }
                    val next = queueStore.nextPending()
                    _currentQueueItem.value = next
                    _pendingJpeg.value = null
                    unlockForNextScan()
                    _message.value = if (next != null) {
                        "Saved ${item.shotFileName} — next: ${next.designation}"
                    } else {
                        "Saved ${item.shotFileName} — queue complete"
                    }
                },
                onFailure = { e ->
                    _message.value = e.message ?: "Could not save shot."
                },
            )
            _saving.value = false
        }
    }

    fun showMessage(value: String) {
        _message.value = value
    }
}
