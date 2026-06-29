package com.oasismall.oasisai.ui.screens.checkshoot

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.data.repository.SubBarcodeInfo
import com.oasismall.oasisai.domain.GalleryPngAssignService
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.agent.AgentPersistedSession
import com.oasismall.oasisai.domain.agent.AgentSessionStore
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalOptions
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import com.oasismall.oasisai.ui.components.CartSourceTags
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

enum class CheckShootPhase {
    SCANNING,
    PROCESSING,
    RESULT,
}

data class CheckShootScan(
    val barcode: String,
    val articleId: Long? = null,
    val designation: String? = null,
    val price: Double? = null,
    val lastPriceChangedAt: Long? = null,
    val lastPrintedAt: Long? = null,
    val lastPrintedPrice: Double? = null,
    val rayon: String? = null,
    val needsTicketUpdate: Boolean = false,
    val changeStatus: String = "UNCHANGED",
    val codeart: String? = null,
    val existingImagePath: String? = null,
    val inGestiumCatalog: Boolean = false,
    val linkedViaAlternate: Boolean = false,
    val linkedViaBodyKey: Boolean = false,
) {
    val hasShareablePng: Boolean get() = !existingImagePath.isNullOrBlank()
}

data class AssetPreviewState(
    val barcode: String,
    val designation: String?,
    val articleId: Long?,
    val cutoutPath: String? = null,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val saving: Boolean = false,
)

data class SuffixMatchState(
    val scannedBarcode: String,
    val editableBarcode: String,
    val candidates: List<ArticleWithImage>,
    val searching: Boolean = false,
)

class CheckShootViewModel(
    context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val bgService: BackgroundRemovalService,
    private val galleryPngAssign: GalleryPngAssignService,
) : ViewModel() {
    private val sessionStore = AgentSessionStore(context.applicationContext)

    private val _scan = MutableStateFlow<CheckShootScan?>(null)
    val scan: StateFlow<CheckShootScan?> = _scan.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _phase = MutableStateFlow(CheckShootPhase.SCANNING)
    val phase: StateFlow<CheckShootPhase> = _phase.asStateFlow()

    private val _preview = MutableStateFlow<AssetPreviewState?>(null)
    val preview: StateFlow<AssetPreviewState?> = _preview.asStateFlow()

    private val _suffixMatch = MutableStateFlow<SuffixMatchState?>(null)
    val suffixMatch: StateFlow<SuffixMatchState?> = _suffixMatch.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _subBcMode = MutableStateFlow(false)
    val subBcMode: StateFlow<Boolean> = _subBcMode.asStateFlow()

    private val _subBarcodes = MutableStateFlow<List<SubBarcodeInfo>>(emptyList())
    val subBarcodes: StateFlow<List<SubBarcodeInfo>> = _subBarcodes.asStateFlow()

    private val _subBarcodeConfirm = MutableStateFlow<String?>(null)
    val subBarcodeConfirm: StateFlow<String?> = _subBarcodeConfirm.asStateFlow()

    private val _openSubBcBatchShoot = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 1)
    val openSubBcBatchShoot: SharedFlow<Pair<Long, String>> = _openSubBcBatchShoot.asSharedFlow()

    val modelReady: Boolean get() = bgService.isModelReady()

    private var debounceBarcode: String? = null
    private var debounceTimeMs = 0L
    private var stableBarcode: String? = null
    private var stableReadCount = 0
    private var pendingCaptureFile: File? = null
    private var returnArticleId: Long? = null

    private val _returnToArticle = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val returnToArticle: SharedFlow<Long> = _returnToArticle.asSharedFlow()

    fun setReturnArticleId(articleId: Long?) {
        returnArticleId = articleId
        viewModelScope.launch {
            if (articleId != null) {
                sessionStore.clear()
            } else {
                restorePersistedSession()
            }
        }
    }

    fun applyPrefillBarcode(barcode: String, lock: Boolean = true) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            refreshScan(trimmed)
            if (lock) lockScan()
        }
    }

    fun submitManualBarcode(barcode: String) {
        if (_phase.value != CheckShootPhase.SCANNING) return
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        stableBarcode = null
        stableReadCount = 0
        debounceBarcode = null
        processBarcodeInput(trimmed, manual = true)
    }

    fun onBarcodeScanned(barcode: String) {
        if (_phase.value != CheckShootPhase.SCANNING) return
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        if (trimmed == stableBarcode) {
            stableReadCount++
        } else {
            stableBarcode = trimmed
            stableReadCount = 1
        }
        viewModelScope.launch {
            val resolved = repository.resolveScannedBarcode(trimmed)
            val minReads = if (resolved != null) STABLE_READS_CATALOG else STABLE_READS_UNKNOWN
            if (stableReadCount < minReads) return@launch
            if (!_isLocked.value && !_subBcMode.value && _scan.value != null) {
                val current = _scan.value!!
                val articleId = resolved?.article?.id
                if (articleId != null && articleId == current.articleId) return@launch
                if (trimmed == current.barcode) return@launch
            }
            val debounceMs = if (resolved?.article?.id != null && resolved.article.id == _scan.value?.articleId) {
                SCAN_DEBOUNCE_SAME_ARTICLE_MS
            } else {
                SCAN_DEBOUNCE_MS
            }
            if (trimmed == debounceBarcode && now - debounceTimeMs < debounceMs) return@launch
            debounceBarcode = trimmed
            debounceTimeMs = now
            processBarcodeInput(trimmed, manual = false)
        }
    }

    private fun processBarcodeInput(trimmed: String, manual: Boolean) {
        if (_suffixMatch.value != null) return
        if (_subBcMode.value && _isLocked.value) {
            viewModelScope.launch { handleSubBarcodeScanned(trimmed) }
            return
        }
        if (_isLocked.value && !_subBcMode.value) return
        if (manual && _scan.value != null && !_isLocked.value) {
            _scan.value = null
        }
        viewModelScope.launch { refreshScan(trimmed) }
    }

    fun toggleSubBcAcquisition() {
        if (!_isLocked.value) return
        val locked = _scan.value ?: return
        if (_subBcMode.value) {
            _subBcMode.value = false
            _message.value = "SUB-BC mode off — main barcode still locked."
            return
        }
        viewModelScope.launch {
            val articleId = locked.articleId ?: resolveArticleId(locked)
            refreshSubBarcodes(articleId)
            _subBcMode.value = true
            _message.value = "SUB-BC: scan flavor/color barcodes for ${locked.designation ?: locked.barcode}"
        }
    }

    private suspend fun handleSubBarcodeScanned(subBarcode: String) {
        val locked = _scan.value ?: return
        val articleId = locked.articleId ?: resolveArticleId(locked)
        val error = repository.validateSubBarcodeLink(articleId, locked.barcode, subBarcode)
        if (error != null) {
            _message.value = error
            return
        }
        _subBarcodeConfirm.value = subBarcode
    }

    fun confirmSubBarcodeAdd() {
        val pending = _subBarcodeConfirm.value ?: return
        _subBarcodeConfirm.value = null
        val locked = _scan.value ?: return
        viewModelScope.launch {
            val articleId = locked.articleId ?: resolveArticleId(locked)
            _openSubBcBatchShoot.emit(articleId to pending)
        }
    }

    fun linkSubBarcodeOnly() {
        val pending = _subBarcodeConfirm.value ?: return
        _subBarcodeConfirm.value = null
        val locked = _scan.value ?: return
        viewModelScope.launch {
            val articleId = locked.articleId ?: resolveArticleId(locked)
            val err = repository.linkSubBarcodeToMainArticle(
                articleId = articleId,
                mainBarcode = locked.barcode,
                subBarcode = pending,
                imagePath = locked.existingImagePath,
            )
            if (err != null) {
                _message.value = err
            } else {
                refreshSubBarcodes(articleId)
                _message.value = "Linked $pending — same product, alternate barcode (no photo)"
            }
        }
    }

    fun assignPng(uri: Uri) {
        val locked = _scan.value ?: return
        val articleId = locked.articleId ?: return
        viewModelScope.launch {
            galleryPngAssign.assignPngToArticle(uri, articleId, subBarcode = null, cartType = CartType.SHARE)
                .fold(
                    onSuccess = { msg ->
                        _message.value = msg
                        val updated = repository.getArticleWithImageById(articleId)
                        _scan.value = locked.copy(existingImagePath = updated?.imagePath)
                    },
                    onFailure = { e -> _message.value = e.message ?: "Could not assign PNG" },
                )
        }
    }

    fun declineSubBarcodeAdd() {
        _subBarcodeConfirm.value = null
    }

    fun removeSubBarcode(barcode: String) {
        viewModelScope.launch {
            val locked = _scan.value ?: return@launch
            val articleId = locked.articleId ?: resolveArticleId(locked)
            val error = repository.unlinkAlternateBarcode(articleId, barcode)
            if (error != null) {
                _message.value = error
                return@launch
            }
            refreshSubBarcodes(articleId)
            _message.value = "Removed sub-barcode $barcode"
        }
    }

    private suspend fun refreshSubBarcodes(articleId: Long) {
        _subBarcodes.value = repository.getAlternateBarcodesForArticle(articleId)
    }

    fun lockScan() {
        val current = _scan.value ?: return
        viewModelScope.launch {
            _message.value = null
            _suffixMatch.value = null
            if (current.inGestiumCatalog) {
                _isLocked.value = true
                current.articleId?.let { refreshSubBarcodes(it) }
                persistSession()
            } else {
                _message.value = "Not in catalog — search in Articles tab"
            }
        }
    }

    fun openSuffixMatchPicker() {
        val barcode = _scan.value?.barcode ?: return
        viewModelScope.launch { openSuffixMatchPickerInternal(barcode) }
    }

    private suspend fun openSuffixMatchPickerInternal(scannedBarcode: String) {
        val digits = com.oasismall.oasisai.util.BarcodeSuffixMatcher.digitsOnly(scannedBarcode)
        val bodyKey = com.oasismall.oasisai.util.BarcodeSuffixMatcher.gestiumBodyKey(scannedBarcode)
        val initialQuery = bodyKey
            ?: com.oasismall.oasisai.util.BarcodeSuffixMatcher
                .primarySuffixLabel(scannedBarcode).ifBlank { digits }
        val matches = if (bodyKey != null) {
            repository.findArticlesByGestiumBodyKey(scannedBarcode)
        } else {
            repository.findArticlesByBarcodePartial(initialQuery, scannedBarcode)
        }
        _suffixMatch.value = SuffixMatchState(
            scannedBarcode = scannedBarcode,
            editableBarcode = digits,
            candidates = matches,
        )
    }

    fun updateSuffixEditableBarcode(value: String) {
        val digits = value.filter { it.isDigit() }
        _suffixMatch.value = _suffixMatch.value?.copy(editableBarcode = digits)
    }

    fun trimSuffixPrefixDigits(count: Int) {
        val state = _suffixMatch.value ?: return
        if (state.editableBarcode.length > count) {
            _suffixMatch.value = state.copy(editableBarcode = state.editableBarcode.drop(count))
        }
    }

    fun searchSuffixMatches() {
        val state = _suffixMatch.value ?: return
        if (state.editableBarcode.length < 4) {
            _message.value = "Enter at least 4 digits to search."
            return
        }
        viewModelScope.launch {
            _suffixMatch.value = state.copy(searching = true)
            val matches = repository.findArticlesByBarcodePartial(
                state.editableBarcode,
                state.scannedBarcode,
            )
            _suffixMatch.value = state.copy(candidates = matches, searching = false)
        }
    }

    fun selectSuffixMatch(article: ArticleWithImage) {
        val barcode = _scan.value?.barcode ?: return
        viewModelScope.launch {
            repository.linkAlternateBarcode(article.id, barcode)
            refreshScan(barcode)
            _suffixMatch.value = null
            _isLocked.value = true
            refreshSubBarcodes(article.id)
            persistSession()
            _message.value = "Linked to ${article.designation}"
        }
    }

    fun dismissSuffixMatch() {
        _suffixMatch.value = null
    }

    fun unlockForNextScan() {
        _isLocked.value = false
        _subBcMode.value = false
        _subBarcodes.value = emptyList()
        _scan.value = null
        _phase.value = CheckShootPhase.SCANNING
        _preview.value = null
        _suffixMatch.value = null
        pendingCaptureFile = null
        debounceBarcode = null
        debounceTimeMs = 0L
        stableBarcode = null
        stableReadCount = 0
        _message.value = null
        sessionStore.clear()
    }

    fun dismissPopup() {
        if (_isLocked.value || _phase.value != CheckShootPhase.SCANNING) return
        if (_suffixMatch.value != null) return
        _scan.value = null
        stableBarcode = null
        stableReadCount = 0
        debounceBarcode = null
        debounceTimeMs = 0L
    }

    fun addToShareCart() {
        val current = _scan.value ?: return
        if (!current.hasShareablePng) {
            _message.value = "No PNG in Oasis for this article."
            return
        }
        viewModelScope.launch {
            val articleId = resolveArticleId(current)
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.CHECK_SHOOT)
            _message.value = "Added to To share."
            finishActionAndUnlock()
        }
    }

    fun addToDesignCart() {
        val current = _scan.value ?: return
        if (!current.hasShareablePng) {
            _message.value = "No PNG in Oasis for this article."
            return
        }
        viewModelScope.launch {
            val articleId = resolveArticleId(current)
            repository.addToCart(articleId, CartType.DESIGN, CartSourceTags.CHECK_SHOOT)
            _message.value = "Added to Design queue."
            finishActionAndUnlock()
        }
    }

    private fun finishActionAndUnlock() {
        if (_isLocked.value) unlockForNextScan()
    }

    fun prepareCreateAsset(onReady: () -> Unit) {
        val current = _scan.value ?: return
        if (!_isLocked.value) return
        if (!modelReady) {
            _message.value = "Cutout model not bundled in this APK — install a full build from your PC."
            return
        }
        viewModelScope.launch {
            resolveArticleId(current)
            _message.value = null
            onReady()
        }
    }

    fun onCaptureFinished(success: Boolean, captureFile: File?) {
        if (!success || captureFile == null || !captureFile.exists()) {
            _message.value = "Capture cancelled."
            return
        }
        pendingCaptureFile = captureFile
        val current = _scan.value ?: return
        _preview.value = AssetPreviewState(
            barcode = current.barcode,
            designation = current.designation,
            articleId = current.articleId,
            progress = 0.05f,
            progressLabel = "Photo captured",
        )
        runBackgroundRemoval(captureFile)
    }

    fun cancelAssetFlow() {
        _phase.value = CheckShootPhase.SCANNING
        _preview.value = null
        pendingCaptureFile = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    private fun runBackgroundRemoval(source: File) {
        val state = _preview.value ?: return
        val options = BackgroundRemovalOptions()
        viewModelScope.launch {
            _phase.value = CheckShootPhase.PROCESSING
            _preview.value = state.copy(progress = 0.1f, progressLabel = "Preparing…")
            val result = bgService.removeBackground(source, options) { label ->
                val p = progressForLabel(label)
                _preview.value = _preview.value?.copy(progress = p, progressLabel = label)
            }
            if (result.success && result.outputPngPath != null) {
                _preview.value = state.copy(
                    cutoutPath = result.outputPngPath,
                    progress = 1f,
                    progressLabel = "Background removed",
                )
                _phase.value = CheckShootPhase.RESULT
                saveCutoutAndFinish(result.outputPngPath, result.originalPath)
            } else {
                _phase.value = CheckShootPhase.SCANNING
                _preview.value = null
                pendingCaptureFile = null
                _message.value = result.errorMessage ?: "Background removal failed."
            }
        }
    }

    private fun saveCutoutAndFinish(cutoutPath: String, originalBackupPath: String) {
        val state = _preview.value ?: return
        viewModelScope.launch {
            _preview.value = state.copy(saving = true, progressLabel = "Saving PNG to article…")
            runCatching {
                val articleId = state.articleId ?: repository.ensureUnknownArticle(
                    barcode = state.barcode,
                    designation = state.designation ?: state.barcode,
                ).id
                imageMatcher.registerBackgroundRemovedImage(
                    articleId = articleId,
                    cutoutFile = File(cutoutPath),
                    originalBackupPath = originalBackupPath,
                )
                repository.addToCart(articleId, CartType.SHARE, CartSourceTags.CHECK_SHOOT)
                refreshScan(state.barcode)
            }.onSuccess {
                val returnId = returnArticleId
                _preview.value = null
                pendingCaptureFile = null
                if (returnId != null) {
                    unlockForNextScan()
                    _returnToArticle.tryEmit(returnId)
                } else {
                    _phase.value = CheckShootPhase.SCANNING
                    _isLocked.value = true
                    persistSession()
                    _message.value = "PNG saved, added to To share."
                }
            }.onFailure { e ->
                _phase.value = CheckShootPhase.SCANNING
                _preview.value = null
                pendingCaptureFile = null
                _message.value = e.message ?: "Could not save PNG."
            }
        }
    }

    private suspend fun refreshScan(barcode: String) {
        val resolved = repository.resolveScannedBarcode(barcode)
        val article = resolved?.article
        val existing = article?.imagePath
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { File(it).exists() }
        val lastPriceChange = article?.id?.let { repository.getLatestPriceChange(it) }
        val meta = article?.id?.let { repository.getArticlePanelMeta(it) }
        _scan.value = CheckShootScan(
            barcode = barcode,
            articleId = article?.id,
            designation = article?.designation,
            price = article?.price,
            lastPriceChangedAt = lastPriceChange?.changedAt ?: meta?.lastPriceChangedAt,
            lastPrintedAt = meta?.lastPrintedAt,
            lastPrintedPrice = meta?.lastPrintedPrice,
            rayon = article?.rayon,
            needsTicketUpdate = article?.needsTicketUpdate == true,
            changeStatus = article?.changeStatus ?: "UNCHANGED",
            codeart = meta?.codeart,
            existingImagePath = existing,
            inGestiumCatalog = resolved != null,
            linkedViaAlternate = resolved != null && !resolved.primary,
            linkedViaBodyKey = resolved?.linkedViaBodyKey == true,
        )
        if (_isLocked.value) persistSession()
    }

    private suspend fun restorePersistedSession() {
        val saved = sessionStore.load() ?: return
        refreshScan(saved.barcode)
        if (saved.locked) {
            _isLocked.value = true
        }
    }

    private fun persistSession() {
        val scan = _scan.value ?: return
        if (!_isLocked.value) {
            sessionStore.clear()
            return
        }
        sessionStore.save(
            AgentPersistedSession(
                locked = true,
                barcode = scan.barcode,
                articleId = scan.articleId,
                designation = scan.designation,
                inGestiumCatalog = scan.inGestiumCatalog,
                linkedViaAlternate = scan.linkedViaAlternate,
                existingImagePath = scan.existingImagePath,
            ),
        )
    }

    private suspend fun resolveArticleId(scan: CheckShootScan): Long =
        scan.articleId ?: repository.ensureUnknownArticle(
            barcode = scan.barcode,
            designation = scan.designation ?: scan.barcode,
        ).id

    private fun progressForLabel(label: String): Float = when {
        label.contains("Loading", ignoreCase = true) -> 0.2f
        label.contains("Cropping", ignoreCase = true) -> 0.3f
        label.contains("model", ignoreCase = true) -> 0.55f
        label.contains("mask", ignoreCase = true) -> 0.75f
        label.contains("Saving", ignoreCase = true) -> 0.9f
        else -> 0.5f
    }

    override fun onCleared() {
        bgService.close()
        super.onCleared()
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 1_500L
        private const val SCAN_DEBOUNCE_SAME_ARTICLE_MS = 400L
        private const val STABLE_READS_CATALOG = 2
        private const val STABLE_READS_UNKNOWN = 3
    }
}
