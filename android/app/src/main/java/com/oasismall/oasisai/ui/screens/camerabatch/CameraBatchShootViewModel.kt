package com.oasismall.oasisai.ui.screens.camerabatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.db.entity.BatchCameraQueueEntity
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.data.repository.SubBarcodeInfo
import com.oasismall.oasisai.domain.visio.BatchCameraQueueStore
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.VisioDownloadStorage
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.hasAppGalleryImage
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.File

enum class CameraBatchShootStep {
    SCAN,
    LOCKED,
    CREATE_DESIGNATION,
    PICK_MATCH,
    PREVIEW,
    AWAITING_PHOTOROOM,
}

data class CameraBatchLockedState(
    val barcode: String,
    val inCatalog: Boolean,
    val designation: String,
    val price: Double?,
    val imagePath: String?,
    val articleId: Long?,
    val linkedViaAlternate: Boolean = false,
    val codeart: String? = null,
    val lastPriceChangedAt: Long? = null,
    val lastPrintedAt: Long? = null,
    val subBarcodes: List<SubBarcodeInfo> = emptyList(),
)

data class SubBarcodeDialogState(val scannedBarcode: String)

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

    private val _knownArticleId = MutableStateFlow<Long?>(null)
    val knownArticleId: StateFlow<Long?> = _knownArticleId.asStateFlow()

    private val _singleArticleMode = MutableStateFlow(false)
    val singleArticleMode: StateFlow<Boolean> = _singleArticleMode.asStateFlow()

    private val _autoLaunchCamera = MutableStateFlow(false)
    val autoLaunchCamera: StateFlow<Boolean> = _autoLaunchCamera.asStateFlow()

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

    private val _subBcAcquireMode = MutableStateFlow(false)
    val subBcAcquireMode: StateFlow<Boolean> = _subBcAcquireMode.asStateFlow()

    private val _parentArticle = MutableStateFlow<ArticleWithImage?>(null)

    private val _subBarcodeConfirm = MutableStateFlow<SubBarcodeDialogState?>(null)
    val subBarcodeConfirm: StateFlow<SubBarcodeDialogState?> = _subBarcodeConfirm.asStateFlow()

    private val _deferSubBarcodeLink = MutableStateFlow(false)
    private val _pendingLinkParentId = MutableStateFlow<Long?>(null)

    fun initSession(
        queueItemId: Long?,
        articleId: Long?,
        subBcAcquire: Boolean = false,
        confirmedSubBarcode: String? = null,
    ) {
        viewModelScope.launch {
            val item = when {
                queueItemId != null -> queueStore.getById(queueItemId)
                else -> queueStore.nextPending()
            }?.takeIf { !it.done }
            _currentQueueItem.value = item
            if (item != null) {
                _message.value = "Batch capture: ${item.designation}"
            }
            if (articleId != null && articleId > 0L) {
                _knownArticleId.value = articleId
                val article = repository.getArticleWithImageById(articleId)
                if (article != null) {
                    if (subBcAcquire) {
                        _subBcAcquireMode.value = true
                        _singleArticleMode.value = true
                        _parentArticle.value = article
                        _locked.value = null
                        _step.value = CameraBatchShootStep.SCAN
                        _message.value = "Scan new flavor barcode for ${article.designation}"
                        confirmedSubBarcode?.trim()?.takeIf { it.length >= 8 }?.let { sub ->
                            val err = repository.validateSubBarcodeLink(article.id, article.barcode, sub)
                            if (err != null) {
                                _message.value = err
                            } else {
                                lockForSubBarcodeShoot(sub)
                            }
                        }
                    } else {
                        _singleArticleMode.value = true
                        lockFromArticle(article)
                        _autoLaunchCamera.value = true
                        _message.value = "Ready — shoot ${article.designation}"
                    }
                } else {
                    _message.value = "Article not found — scan barcode."
                }
            }
        }
    }

    fun consumeAutoLaunchCamera() {
        _autoLaunchCamera.value = false
    }

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.length < 8) return
        if (_subBcAcquireMode.value && _parentArticle.value != null && _locked.value == null) {
            viewModelScope.launch { promptSubBarcodeConfirm(trimmed) }
            return
        }
        if (_locked.value != null) return
        viewModelScope.launch { lockBarcode(trimmed) }
    }

    private suspend fun promptSubBarcodeConfirm(scannedBarcode: String) {
        val parent = _parentArticle.value ?: return
        val err = repository.validateSubBarcodeLink(parent.id, parent.barcode, scannedBarcode)
        if (err != null) {
            _message.value = err
            return
        }
        _subBarcodeConfirm.value = SubBarcodeDialogState(scannedBarcode)
    }

    fun confirmSubBarcodeAdd() {
        val pending = _subBarcodeConfirm.value ?: return
        _subBarcodeConfirm.value = null
        viewModelScope.launch { lockForSubBarcodeShoot(pending.scannedBarcode) }
    }

    fun declineSubBarcodeAdd() {
        _subBarcodeConfirm.value = null
    }

    private suspend fun lockForSubBarcodeShoot(subBarcode: String) {
        val parent = _parentArticle.value ?: _pendingLinkParentId.value?.let { repository.getArticleWithImageById(it) }
            ?: return
        _deferSubBarcodeLink.value = true
        _pendingLinkParentId.value = parent.id
        val meta = repository.getArticlePanelMeta(parent.id)
        _locked.value = CameraBatchLockedState(
            barcode = subBarcode,
            inCatalog = true,
            designation = parent.designation,
            price = parent.price.takeIf { it > 0.0 },
            imagePath = parent.imagePath?.takeIf { File(it).exists() },
            articleId = parent.id,
            linkedViaAlternate = true,
            codeart = meta.codeart,
            lastPriceChangedAt = meta.lastPriceChangedAt,
            lastPrintedAt = meta.lastPrintedAt,
            subBarcodes = meta.subBarcodes,
        )
        _step.value = CameraBatchShootStep.LOCKED
        _autoLaunchCamera.value = true
        _message.value = "Shoot photo for sub-barcode $subBarcode — saved after PhotoRoom import"
    }

    fun removeSubBarcode(barcode: String) {
        val parentId = _parentArticle.value?.id ?: _locked.value?.articleId ?: return
        viewModelScope.launch {
            val err = repository.unlinkAlternateBarcode(parentId, barcode)
            if (err != null) {
                _message.value = err
                return@launch
            }
            val meta = repository.getArticlePanelMeta(parentId)
            _locked.value = _locked.value?.copy(subBarcodes = meta.subBarcodes)
            _message.value = "Removed sub-barcode $barcode"
        }
    }

    private suspend fun enrichLockedState(
        article: ArticleWithImage,
        barcode: String,
        inCatalog: Boolean,
        linkedViaAlternate: Boolean,
    ): CameraBatchLockedState {
        val meta = repository.getArticlePanelMeta(article.id)
        return CameraBatchLockedState(
            barcode = barcode,
            inCatalog = inCatalog,
            designation = article.designation,
            price = article.price.takeIf { it > 0.0 },
            imagePath = article.imagePath?.takeIf { File(it).exists() },
            articleId = article.id,
            linkedViaAlternate = linkedViaAlternate,
            codeart = meta.codeart,
            lastPriceChangedAt = meta.lastPriceChangedAt,
            lastPrintedAt = meta.lastPrintedAt,
            subBarcodes = meta.subBarcodes,
        )
    }

    private suspend fun lockFromArticle(article: ArticleWithImage) {
        _locked.value = enrichLockedState(article, article.barcode, inCatalog = true, linkedViaAlternate = false)
        _step.value = CameraBatchShootStep.LOCKED
    }

    private suspend fun lockBarcode(barcode: String) {
        val resolved = repository.resolveScannedBarcode(barcode)
        val article = resolved?.article
            ?: _knownArticleId.value?.let { repository.getArticleWithImageById(it) }
        val queueHint = _currentQueueItem.value?.designation
        _locked.value = if (article != null) {
            enrichLockedState(
                article = article,
                barcode = barcode,
                inCatalog = resolved != null,
                linkedViaAlternate = resolved != null && !resolved.primary,
            )
        } else {
            CameraBatchLockedState(
                barcode = barcode,
                inCatalog = false,
                designation = queueHint ?: barcode,
                price = null,
                imagePath = null,
                articleId = _knownArticleId.value,
            )
        }
        _step.value = CameraBatchShootStep.LOCKED
        _message.value = null
    }

    fun unlockForNextScan() {
        _pendingJpeg.value?.delete()
        _pendingJpeg.value = null
        _designationInput.value = ""
        _pickedMatch.value = null
        _deferSubBarcodeLink.value = false
        _pendingLinkParentId.value = null
        if (_subBcAcquireMode.value) {
            _locked.value = null
            _step.value = CameraBatchShootStep.SCAN
            _message.value = "Scan next flavor barcode"
            return
        }
        _locked.value = null
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
            val err = repository.validateSubBarcodeLink(match.id, match.barcode, locked.barcode)
            if (err != null) {
                _message.value = err
                return@launch
            }
            _pickedMatch.value = null
            _pendingLinkParentId.value = match.id
            lockForSubBarcodeShoot(locked.barcode)
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
        if (_singleArticleMode.value) {
            _autoLaunchCamera.value = true
        }
    }

    fun confirmAndSave() {
        val jpeg = _pendingJpeg.value ?: return
        val locked = _locked.value ?: return
        val pendingLink = _deferSubBarcodeLink.value
        val linkParentId = _pendingLinkParentId.value ?: _parentArticle.value?.id
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    store.saveTaggedShot(
                        sourceJpeg = jpeg,
                        barcode = locked.barcode,
                        hintDesignation = _currentQueueItem.value?.designation ?: locked.designation,
                        hintArticleId = locked.articleId ?: _knownArticleId.value,
                        pendingSubBarcodeLink = pendingLink && linkParentId != null,
                        linkParentArticleId = if (pendingLink) linkParentId else null,
                    )
                }
            }.fold(
                onSuccess = { item ->
                    _currentQueueItem.value?.id?.let { queueStore.markDone(it) }
                    _pendingJpeg.value = null
                    _deferSubBarcodeLink.value = false
                    _pendingLinkParentId.value = null
                    if (_singleArticleMode.value) {
                        _step.value = CameraBatchShootStep.AWAITING_PHOTOROOM
                        _message.value = if (pendingLink) {
                            "Saved ${item.shotFileName}. Edit in PhotoRoom, import — sub-barcode links after import."
                        } else {
                            "Saved ${item.shotFileName}. Edit in PhotoRoom, then open PhotoRoom import."
                        }
                    } else {
                        val next = queueStore.nextPending()
                        _currentQueueItem.value = next
                        unlockForNextScan()
                        _message.value = if (next != null) {
                            "Saved ${item.shotFileName} — next: ${next.designation}"
                        } else {
                            "Saved ${item.shotFileName} — queue complete"
                        }
                    }
                },
                onFailure = { e ->
                    _message.value = e.message ?: "Could not save shot."
                },
            )
            _saving.value = false
        }
    }

    fun shootAgainSameArticle() {
        if (_subBcAcquireMode.value) {
            _locked.value = null
            _deferSubBarcodeLink.value = false
            _pendingLinkParentId.value = null
            _step.value = CameraBatchShootStep.SCAN
            _message.value = "Scan next flavor barcode"
            return
        }
        _step.value = CameraBatchShootStep.LOCKED
        _autoLaunchCamera.value = true
        _message.value = "Shoot again — same article."
    }

    fun showMessage(value: String) {
        _message.value = value
    }
}
