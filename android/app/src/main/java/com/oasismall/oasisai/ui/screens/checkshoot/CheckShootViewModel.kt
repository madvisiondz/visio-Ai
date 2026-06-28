package com.oasismall.oasisai.ui.screens.checkshoot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.AgentCaptureMode
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.data.repository.SubBarcodeInfo
import com.oasismall.oasisai.domain.GalleryPngAssignService
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalOptions
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import com.oasismall.oasisai.domain.bulk.BulkCaptureStore
import com.oasismall.oasisai.domain.paray.CheckShootPersistedSession
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayMatch
import com.oasismall.oasisai.domain.paray.ParaySuggestion
import com.oasismall.oasisai.domain.paray.ParayTicketAssessment
import com.oasismall.oasisai.domain.paray.ParayTicketAdvisor
import com.oasismall.oasisai.domain.paray.ParayTicketCropLearnStore
import com.oasismall.oasisai.domain.paray.ParayTicketMatch
import com.oasismall.oasisai.domain.paray.ParayTicketMatchTier
import com.oasismall.oasisai.domain.paray.ParayTicketReadResult
import com.oasismall.oasisai.domain.paray.ParayTicketReader
import com.oasismall.oasisai.domain.paray.ParayTicketSnapResult
import com.oasismall.oasisai.domain.paray.TicketSnapPhase
import com.oasismall.oasisai.domain.paray.TicketSnapStep
import com.oasismall.oasisai.ui.components.CartSourceTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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

class CheckShootViewModel(
    context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val bgService: BackgroundRemovalService,
    private val paray: ParayAgent,
    private val bulkStore: BulkCaptureStore,
    private val workflowTracker: com.oasismall.oasisai.domain.paray.ParayWorkflowTracker,
    private val recognitionTracker: com.oasismall.oasisai.domain.paray.ParayRecognitionTracker,
    private val galleryPngAssign: GalleryPngAssignService,
) : ViewModel() {
    private val advisor = paray.barcodeAdvisor(repository)
    private val ticketAdvisor: ParayTicketAdvisor = paray.ticketAdvisor(repository)
    private val ticketReader: ParayTicketReader = paray.ticketReader(repository)
    private val ticketCropLearnStore = ParayTicketCropLearnStore(paray.home)
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_AGENT, Context.MODE_PRIVATE)

    private val _scan = MutableStateFlow<CheckShootScan?>(null)
    val scan: StateFlow<CheckShootScan?> = _scan.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _phase = MutableStateFlow(CheckShootPhase.SCANNING)
    val phase: StateFlow<CheckShootPhase> = _phase.asStateFlow()

    private val _preview = MutableStateFlow<AssetPreviewState?>(null)
    val preview: StateFlow<AssetPreviewState?> = _preview.asStateFlow()

    private val _paraySuggest = MutableStateFlow<ParaySuggestState?>(null)
    val paraySuggest: StateFlow<ParaySuggestState?> = _paraySuggest.asStateFlow()

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

    private val _agentMode = MutableStateFlow(loadAgentMode())
    val agentMode: StateFlow<AgentCaptureMode> = _agentMode.asStateFlow()

    private val _bulkScan = MutableStateFlow<BulkScanState?>(null)
    val bulkScan: StateFlow<BulkScanState?> = _bulkScan.asStateFlow()

    private val _ticketAssessment = MutableStateFlow<ParayTicketAssessment?>(null)
    val ticketAssessment: StateFlow<ParayTicketAssessment?> = _ticketAssessment.asStateFlow()

    private val _ticketWalkStatus = MutableStateFlow(TicketWalkStatus.READY)
    val ticketWalkStatus: StateFlow<TicketWalkStatus> = _ticketWalkStatus.asStateFlow()

    private val _ticketFeedback = MutableStateFlow<TicketCameraFeedback?>(null)
    val ticketFeedback: StateFlow<TicketCameraFeedback?> = _ticketFeedback.asStateFlow()

    private val _ticketSnapProgress = MutableStateFlow<TicketSnapUiState?>(null)
    val ticketSnapProgress: StateFlow<TicketSnapUiState?> = _ticketSnapProgress.asStateFlow()

    private val _ticketMatchTier = MutableStateFlow<ParayTicketMatchTier?>(null)
    val ticketMatchTier: StateFlow<ParayTicketMatchTier?> = _ticketMatchTier.asStateFlow()

    private val _ticketCropHelper = MutableStateFlow<TicketCropHelperState?>(null)
    val ticketCropHelper: StateFlow<TicketCropHelperState?> = _ticketCropHelper.asStateFlow()

    private val lastSnapSteps = mutableListOf<TicketSnapStepLine>()

    private val ticketSnapInFlight = AtomicBoolean(false)
    private val ticketSnapSession = AtomicLong(0L)
    private var ticketSnapJob: Job? = null
    private var lastStepEmitMs = 0L
    private var lastTicketArticleId: Long? = null
    private var lastTicketProbability: Float? = null
    private var lastTicketLockMs = 0L

    private var stableTicketKey: String? = null
    private var stableTicketCount = 0

    val bulkCaptureCount = bulkStore.observeCaptureCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val parayLearnedCount: Int get() = paray.learnedProductCount()
    val modelReady: Boolean get() = bgService.isModelReady()

    private var debounceBarcode: String? = null
    private var debounceTimeMs = 0L
    private var stableBarcode: String? = null
    private var stableReadCount = 0
    private var pendingCaptureFile: File? = null
    private var pendingTeachCapture = false
    private var teachScannedBarcode: String? = null
    private var returnArticleId: Long? = null
    private var pendingBulkBarcode: String? = null
    private var pendingBulkReplaced = false

    private val _returnToArticle = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val returnToArticle: SharedFlow<Long> = _returnToArticle.asSharedFlow()

    fun setReturnArticleId(articleId: Long?) {
        returnArticleId = articleId
        viewModelScope.launch {
            if (articleId != null) {
                paray.sessionStore.clear()
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

    fun setAgentMode(mode: AgentCaptureMode) {
        if (_agentMode.value == mode) return
        if (mode == AgentCaptureMode.BULK || mode == AgentCaptureMode.TICKET) {
            unlockForNextScan()
            _bulkScan.value = null
            pendingBulkBarcode = null
        }
        if (mode != AgentCaptureMode.TICKET) {
            cancelTicketSnap()
            _ticketAssessment.value = null
            _ticketMatchTier.value = null
            _ticketWalkStatus.value = TicketWalkStatus.READY
        } else {
            _ticketWalkStatus.value = if (_isLocked.value) TicketWalkStatus.PAUSED else TicketWalkStatus.READY
        }
        if (mode == AgentCaptureMode.SMART || mode == AgentCaptureMode.BULK) {
            debounceBarcode = null
            debounceTimeMs = 0L
            stableBarcode = null
            stableReadCount = 0
        }
        _agentMode.value = mode
        prefs.edit().putString(KEY_AGENT_MODE, mode.name).apply()
        _message.value = when (mode) {
            AgentCaptureMode.BULK -> "Bulk mode — scan barcodes, replace or skip."
            AgentCaptureMode.TICKET -> "PARAY Ticket — tap screen on yellow ticket; reads designation + price + PNG."
            AgentCaptureMode.SMART -> "Smart mode — PARAY + catalog checks."
        }
    }

    fun snapTicket(buffer: TicketCameraBuffer) {
        if (_agentMode.value != AgentCaptureMode.TICKET ||
            _isLocked.value ||
            _phase.value != CheckShootPhase.SCANNING ||
            ticketSnapInFlight.get() ||
            _ticketCropHelper.value != null ||
            _ticketWalkStatus.value == TicketWalkStatus.PROCESSING
        ) {
            return
        }
        if (!ticketSnapInFlight.compareAndSet(false, true)) return

        val snap = buffer.takeInstantSnap()
        if (snap == null) {
            ticketSnapInFlight.set(false)
            _message.value = "Camera warming up — wait a moment and tap again"
            return
        }

        _message.value = null
        TicketSnapHaptics.tapShutter(appContext)
        val fullCopy = snap.bitmap.copy(
            snap.bitmap.config ?: Bitmap.Config.ARGB_8888,
            false,
        )
        deferBitmapRecycle(snap.bitmap)

        _ticketCropHelper.value = TicketCropHelperState(
            frameBitmap = fullCopy,
            cropNorm = ticketCropLearnStore.suggestDefault(),
            frameQuality = snap.qualityScore,
        )
        _ticketWalkStatus.value = TicketWalkStatus.CROP_ADJUST
        _message.value = when {
            snap.qualityScore >= 0.55f -> "Adjust crop → Scan ticket"
            snap.qualityScore >= 0.38f -> "Hold still next time for sharper read"
            else -> "Low sharpness — widen crop box if read fails"
        }
    }

    fun confirmTicketCrop(crop: com.oasismall.oasisai.domain.paray.ParayTicketCropLearnStore.NormalizedCrop) {
        val helper = _ticketCropHelper.value ?: return
        val uiBitmap = helper.frameBitmap
        _ticketCropHelper.value = null
        ticketCropLearnStore.record(crop)
        val processingBitmap = uiBitmap.copy(
            uiBitmap.config ?: Bitmap.Config.ARGB_8888,
            false,
        )
        deferBitmapRecycle(uiBitmap)
        startTicketProcessing(processingBitmap, crop, helper.frameQuality)
    }

    /** Recycle after Compose has finished drawing — avoids recycled-bitmap crashes. */
    private fun deferBitmapRecycle(vararg bitmaps: Bitmap?) {
        val pending = bitmaps.filterNotNull().filter { !it.isRecycled }
        if (pending.isEmpty()) return
        mainHandler.postDelayed({
            pending.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }, BITMAP_RECYCLE_DEFER_MS)
    }

    fun cancelTicketCropHelper() {
        val bmp = _ticketCropHelper.value?.frameBitmap
        _ticketCropHelper.value = null
        deferBitmapRecycle(bmp)
        ticketSnapInFlight.set(false)
        if (_agentMode.value == AgentCaptureMode.TICKET && !_isLocked.value) {
            _ticketWalkStatus.value = TicketWalkStatus.READY
        }
    }

    private fun startTicketProcessing(
        bitmap: android.graphics.Bitmap,
        crop: com.oasismall.oasisai.domain.paray.ParayTicketCropLearnStore.NormalizedCrop,
        frameQuality: Float,
    ) {
        ticketSnapJob?.cancel()
        val session = ticketSnapSession.incrementAndGet()
        lastSnapSteps.clear()
        ticketSnapJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                _ticketWalkStatus.value = TicketWalkStatus.PROCESSING
                lastStepEmitMs = 0L
                withContext(Dispatchers.Main) {
                    _ticketSnapProgress.value = TicketSnapUiState(
                        phase = TicketSnapPhase.CAPTURED,
                        message = "PARAY reading ticket…",
                        frameQuality = frameQuality,
                    )
                }
                val result = runTicketSnap(bitmap, 0, frameQuality, session, crop)
                if (!bitmap.isRecycled) bitmap.recycle()
                handleTicketSnapResult(result)
            } catch (_: kotlinx.coroutines.CancellationException) {
                invalidateTicketSnapSession()
                withContext(Dispatchers.Main) {
                    resetTicketWalkPipeline()
                }
            } finally {
                ticketSnapInFlight.set(false)
                ticketSnapJob = null
            }
        }
    }

    private suspend fun handleTicketSnapResult(result: ParayTicketSnapResult?) {
        invalidateTicketSnapSession()
        val match = result?.candidates?.firstOrNull() ?: result?.match
        if (match != null) {
            result?.frame?.recycle()
            val tier = ParayTicketMatchTier.fromProbability(match.fusion.probability)
            withContext(Dispatchers.Main) {
                clearTicketSnapProgress()
                ticketSnapInFlight.set(false)
                if (tier != null) TicketSnapHaptics.match(appContext, tier)
                _ticketMatchTier.value = tier
            }
            processTicketMatch(match, tier, null)
        } else {
            result?.frame?.recycle()
            withContext(Dispatchers.Main) {
                _ticketWalkStatus.value = TicketWalkStatus.READY
                _message.value = _ticketSnapProgress.value?.error
                    ?: "No match — tap ticket again when ready"
                clearTicketSnapProgress()
            }
            TicketSnapHaptics.failed(appContext)
        }
    }

    private fun resetTicketWalkPipeline() {
        ticketSnapJob?.cancel()
        ticketSnapJob = null
        ticketSnapInFlight.set(false)
        invalidateTicketSnapSession()
        deferBitmapRecycle(_ticketCropHelper.value?.frameBitmap)
        _ticketCropHelper.value = null
        clearTicketSnapProgress()
        lastSnapSteps.clear()
        if (_agentMode.value == AgentCaptureMode.TICKET && !_isLocked.value) {
            _ticketWalkStatus.value = TicketWalkStatus.READY
        }
    }

    private fun invalidateTicketSnapSession() {
        ticketSnapSession.incrementAndGet()
    }

    private suspend fun runTicketSnap(
        bitmap: android.graphics.Bitmap,
        rotationDegrees: Int,
        frameQuality: Float,
        session: Long,
        userCrop: com.oasismall.oasisai.domain.paray.ParayTicketCropLearnStore.NormalizedCrop?,
    ): ParayTicketSnapResult? =
        ticketReader.processSnap(
            bitmap,
            rotationDegrees,
            frameQuality,
            onStep = { step -> emitSnapStep(step, session) },
            userCropNorm = userCrop,
        )

    fun cancelTicketSnap() {
        resetTicketWalkPipeline()
    }

    private suspend fun emitSnapStep(step: TicketSnapStep, session: Long) {
        if (session != ticketSnapSession.get()) return
        val isDone = step.phase == TicketSnapPhase.DONE
        val isFailure = step.phase == TicketSnapPhase.FAILED
        val terminal = isDone || isFailure
        val now = System.currentTimeMillis()
        if (!terminal && step.preview == null && now - lastStepEmitMs < UI_STEP_MIN_MS) return
        lastStepEmitMs = now
        withContext(Dispatchers.Main) {
            val prev = _ticketSnapProgress.value
            if (prev?.message == step.message && step.preview == null && prev.phase == step.phase) return@withContext
            val prior = prev?.steps.orEmpty().map { it.copy(done = true) }
            val line = TicketSnapStepLine(
                phase = step.phase,
                message = step.message,
                done = isDone,
                failed = isFailure,
            )
            val steps = if (prior.lastOrNull()?.message == step.message) prior else prior + line
            val outgoingPreview = prev?.previewBitmap?.takeIf {
                step.preview != null && it !== step.preview
            }
            _ticketSnapProgress.value = TicketSnapUiState(
                phase = step.phase,
                message = step.message,
                steps = steps,
                previewBitmap = step.preview ?: prev?.previewBitmap,
                ocrDesignation = step.ocrDesignation ?: prev?.ocrDesignation,
                ocrPrice = step.ocrPrice ?: prev?.ocrPrice,
                fusionPercent = step.fusion?.probabilityPercent ?: prev?.fusionPercent,
                matchTier = step.matchTier ?: prev?.matchTier,
                frameQuality = step.frameQuality ?: prev?.frameQuality,
                error = step.error ?: prev?.error,
            )
            deferBitmapRecycle(outgoingPreview)
            lastSnapSteps.clear()
            lastSnapSteps.addAll(steps)
        }
    }

    fun skipBulkScan() {
        _bulkScan.value = null
        _message.value = "Skipped — scan next barcode."
    }

    fun prepareBulkCapture(replaced: Boolean, onReady: () -> Unit) {
        val barcode = _bulkScan.value?.barcode ?: return
        if (!modelReady) {
            _message.value = "Cutout model not bundled in this APK — install a full build from your PC."
            return
        }
        pendingBulkBarcode = barcode
        pendingBulkReplaced = replaced
        _message.value = null
        onReady()
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
        if (_agentMode.value == AgentCaptureMode.BULK) {
            if (!manual && _bulkScan.value != null) return
            viewModelScope.launch { refreshBulkScan(trimmed) }
            return
        }
        if (_agentMode.value == AgentCaptureMode.TICKET) {
            if (!manual && _isLocked.value) return
            viewModelScope.launch {
                val read = ParayTicketReadResult(
                    source = com.oasismall.oasisai.domain.paray.ParayTicketReadSource.BARCODE,
                    barcode = trimmed,
                )
                val match = ticketReader.resolve(read)
                if (match != null) {
                    processTicketMatch(match)
                } else {
                    processTicketScanUnknown(trimmed)
                }
            }
            return
        }
        if (_paraySuggest.value != null || _suffixMatch.value != null) return
        if (_subBcMode.value && _isLocked.value) {
            viewModelScope.launch { handleSubBarcodeScanned(trimmed) }
            return
        }
        if (_isLocked.value && !_subBcMode.value) return
        if (manual && _scan.value != null && !_isLocked.value) {
            _scan.value = null
            _paraySuggest.value = null
        }
        viewModelScope.launch {
            refreshScan(trimmed)
            workflowTracker.recordFeature(com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.BARCODE_SCAN)
        }
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
                _paraySuggest.value = null
                _isLocked.value = true
                if (_agentMode.value == AgentCaptureMode.TICKET) {
                    _ticketWalkStatus.value = TicketWalkStatus.PAUSED
                    lastTicketLockMs = System.currentTimeMillis()
                }
                current.articleId?.let { refreshSubBarcodes(it) }
                persistSession()
                return@launch
            }
            openParaySuggestions(current.barcode)
        }
    }

    private suspend fun openParaySuggestions(scannedBarcode: String) {
        _paraySuggest.value = ParaySuggestState(scannedBarcode = scannedBarcode, loading = true)
        val suggestions = runCatching { advisor.suggestForUnknownBarcode(scannedBarcode) }
            .getOrElse {
                _message.value = "PARAY barcode lookup failed: ${it.message ?: "unknown error"}"
                emptyList()
            }
        _paraySuggest.value = ParaySuggestState(
            scannedBarcode = scannedBarcode,
            suggestions = suggestions,
            loading = false,
        )
        recognitionTracker.recordUnknownBarcode(scannedBarcode, com.oasismall.oasisai.domain.paray.ParayRecognitionTracker.SOURCE_AGENT)
    }

    fun selectParaySuggestion(suggestion: ParaySuggestion) {
        val barcode = _scan.value?.barcode ?: _paraySuggest.value?.scannedBarcode ?: return
        val offeredId = _paraySuggest.value?.suggestions?.firstOrNull()?.articleId
        viewModelScope.launch {
            recognitionTracker.recordManualCorrection(
                scannedBarcode = barcode,
                selectedArticleId = suggestion.articleId,
                selectedDesignation = suggestion.designation,
                offeredArticleId = offeredId,
                source = suggestion.reason,
            )
            advisor.confirmSuggestion(barcode, suggestion)
            refreshScan(barcode)
            _paraySuggest.value = null
            _isLocked.value = true
            refreshSubBarcodes(suggestion.articleId)
            persistSession()
            _message.value = "PARAY linked barcode → ${suggestion.designation}"
        }
    }

    fun selectParayVisualMatch(match: ParayMatch) {
        val barcode = _scan.value?.barcode ?: _paraySuggest.value?.scannedBarcode ?: return
        val offeredId = _paraySuggest.value?.visualMatches?.firstOrNull()?.articleId
        viewModelScope.launch {
            recognitionTracker.recordManualCorrection(
                scannedBarcode = barcode,
                selectedArticleId = match.articleId,
                selectedDesignation = match.designation,
                offeredArticleId = offeredId,
                source = "PARAY visual teach",
            )
            advisor.confirmSuggestion(
                barcode,
                ParaySuggestion(
                    articleId = match.articleId,
                    barcode = match.barcode,
                    designation = match.designation,
                    confidence = match.confidence,
                    reason = "PARAY visual teach",
                ),
            )
            refreshScan(barcode)
            _paraySuggest.value = null
            _isLocked.value = true
            persistSession()
            _message.value = "PARAY taught: ${match.designation}"
        }
    }

    fun dismissParaySuggestions() {
        _paraySuggest.value = null
    }

    fun updateParayDesignationQuery(query: String) {
        _paraySuggest.value = _paraySuggest.value?.copy(designationQuery = query)
    }

    fun searchParayByDesignation() {
        val state = _paraySuggest.value ?: return
        val q = state.designationQuery.trim()
        if (q.length < 2) {
            _message.value = "Enter at least 2 characters to search designation."
            return
        }
        viewModelScope.launch {
            _paraySuggest.value = state.copy(designationSearching = true)
            val results = repository.searchArticlesForPicker(q)
            _paraySuggest.value = state.copy(
                designationResults = results,
                designationSearching = false,
            )
            if (results.isEmpty()) {
                _message.value = "No articles match \"$q\"."
            }
        }
    }

    fun selectParayDesignationMatch(article: ArticleWithImage) {
        val barcode = _paraySuggest.value?.scannedBarcode ?: _scan.value?.barcode ?: return
        viewModelScope.launch {
            recognitionTracker.recordManualCorrection(
                scannedBarcode = barcode,
                selectedArticleId = article.id,
                selectedDesignation = article.designation,
                offeredArticleId = _paraySuggest.value?.suggestions?.firstOrNull()?.articleId,
                source = "Designation search",
            )
            advisor.confirmSuggestion(
                barcode,
                ParaySuggestion(
                    articleId = article.id,
                    barcode = article.barcode,
                    designation = article.designation,
                    confidence = 0.88f,
                    reason = "Designation search",
                    imagePath = article.imagePath,
                ),
            )
            refreshScan(barcode)
            _paraySuggest.value = null
            _isLocked.value = true
            refreshSubBarcodes(article.id)
            persistSession()
            _message.value = "Linked to ${article.designation}"
        }
    }

    fun openManualSuffixSearch() {
        val barcode = _paraySuggest.value?.scannedBarcode ?: _scan.value?.barcode ?: return
        _paraySuggest.value = null
        viewModelScope.launch { openSuffixMatchPicker(barcode) }
    }

    fun prepareParayTeachCapture(onReady: () -> Unit) {
        val barcode = _paraySuggest.value?.scannedBarcode ?: _scan.value?.barcode
        if (barcode.isNullOrBlank()) return
        teachScannedBarcode = barcode
        pendingTeachCapture = true
        val current = _paraySuggest.value ?: ParaySuggestState(scannedBarcode = barcode)
        _paraySuggest.value = current.copy(loading = true, teachingVisual = true)
        onReady()
    }

    fun onParayTeachCaptureFinished(success: Boolean, captureFile: File?) {
        if (!pendingTeachCapture) return
        pendingTeachCapture = false
        val barcode = teachScannedBarcode ?: _paraySuggest.value?.scannedBarcode ?: _scan.value?.barcode
        if (!success || captureFile == null || !captureFile.exists()) {
            resetParayTeachState(barcode, captureFile?.absolutePath)
            _message.value = "PARAY teach cancelled."
            return
        }
        viewModelScope.launch {
            val base = _paraySuggest.value ?: ParaySuggestState(scannedBarcode = barcode.orEmpty())
            _paraySuggest.value = base.copy(
                scannedBarcode = barcode ?: base.scannedBarcode,
                loading = true,
                teachingVisual = true,
                teachCapturePath = captureFile.absolutePath,
            )
            val matches = runCatching {
                withContext(Dispatchers.Default) {
                    val bitmap = decodeSampledBitmap(captureFile.absolutePath) ?: return@withContext emptyList()
                    try {
                        paray.identifyFromCamera(bitmap, topK = 5)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }.getOrElse {
                _message.value = "PARAY visual lookup failed: ${it.message ?: "unknown error"}"
                emptyList()
            }
            val after = _paraySuggest.value ?: ParaySuggestState(scannedBarcode = barcode.orEmpty())
            _paraySuggest.value = after.copy(
                visualMatches = matches,
                loading = false,
                teachingVisual = false,
                teachCapturePath = captureFile.absolutePath,
            )
            recognitionTracker.observeVisualIdentification(barcode, matches)
            if (matches.isEmpty()) {
                _message.value = "PARAY could not recognize this pack yet — import fingerprints or print shelf labels first."
            }
        }
    }

    private fun resetParayTeachState(barcode: String?, capturePath: String?) {
        val base = _paraySuggest.value ?: barcode?.let { ParaySuggestState(scannedBarcode = it) }
        _paraySuggest.value = base?.copy(
            loading = false,
            teachingVisual = false,
            teachCapturePath = capturePath,
        )
    }

    private suspend fun openSuffixMatchPicker(scannedBarcode: String) {
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
            recognitionTracker.recordManualCorrection(
                scannedBarcode = barcode,
                selectedArticleId = article.id,
                selectedDesignation = article.designation,
                offeredArticleId = null,
                source = "Manual suffix link",
            )
            advisor.confirmSuggestion(
                barcode,
                ParaySuggestion(
                    articleId = article.id,
                    barcode = article.barcode,
                    designation = article.designation,
                    confidence = 0.9f,
                    reason = "Manual suffix link",
                    imagePath = article.imagePath,
                ),
            )
            refreshScan(barcode)
            _suffixMatch.value = null
            _isLocked.value = true
            persistSession()
            _message.value = "Linked to ${article.designation}"
        }
    }

    fun dismissSuffixMatch() {
        _suffixMatch.value = null
    }

    fun unlockForNextScan() {
        ticketSnapInFlight.set(false)
        ticketSnapJob?.cancel()
        ticketSnapJob = null
        resetTicketWalkPipeline()
        _isLocked.value = false
        _subBcMode.value = false
        _subBarcodes.value = emptyList()
        _scan.value = null
        _ticketAssessment.value = null
        _ticketMatchTier.value = null
        clearTicketSnapProgress()
        _ticketFeedback.value = null
        lastTicketArticleId = null
        lastTicketProbability = null
        lastTicketLockMs = 0L
        stableTicketKey = null
        stableTicketCount = 0
        if (_agentMode.value == AgentCaptureMode.TICKET) {
            _ticketWalkStatus.value = TicketWalkStatus.READY
        }
        _phase.value = CheckShootPhase.SCANNING
        _preview.value = null
        _paraySuggest.value = null
        _suffixMatch.value = null
        pendingCaptureFile = null
        pendingTeachCapture = false
        debounceBarcode = null
        debounceTimeMs = 0L
        stableBarcode = null
        stableReadCount = 0
        _message.value = null
        paray.sessionStore.clear()
    }

    /** Called from UI when ticket camera buffer is ready after unlock / screen entry. */
    fun onTicketCameraReady() {
        if (_agentMode.value != AgentCaptureMode.TICKET || _isLocked.value) return
        val msg = _message.value ?: return
        if (msg.contains("warming up", ignoreCase = true) ||
            msg.contains("tap again", ignoreCase = true) ||
            msg.contains("frame too old", ignoreCase = true)
        ) {
            _message.value = null
        }
    }

    fun dismissPopup() {
        if (_isLocked.value || _phase.value != CheckShootPhase.SCANNING) return
        if (_paraySuggest.value != null || _suffixMatch.value != null) return
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
        if (pendingTeachCapture) {
            onParayTeachCaptureFinished(success, captureFile)
            return
        }
        if (pendingBulkBarcode != null) {
            onBulkCaptureFinished(success, captureFile)
            return
        }
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
        pendingTeachCapture = false
        pendingBulkBarcode = null
    }

    private fun onBulkCaptureFinished(success: Boolean, captureFile: File?) {
        val barcode = pendingBulkBarcode
        val replaced = pendingBulkReplaced
        pendingBulkBarcode = null
        pendingBulkReplaced = false
        if (!success || captureFile == null || !captureFile.exists() || barcode.isNullOrBlank()) {
            _message.value = "Capture cancelled."
            return
        }
        runBulkBackgroundRemoval(barcode, captureFile, replaced)
    }

    private fun runBulkBackgroundRemoval(barcode: String, source: File, replaced: Boolean) {
        viewModelScope.launch {
            _phase.value = CheckShootPhase.PROCESSING
            _message.value = "Removing background…"
            val result = bgService.removeBackground(source, BackgroundRemovalOptions()) { label ->
                _message.value = label
            }
            if (result.success && result.outputPngPath != null) {
                runCatching {
                    bulkStore.saveCutout(barcode, File(result.outputPngPath), replaced)
                }.onSuccess {
                    _bulkScan.value = null
                    _phase.value = CheckShootPhase.SCANNING
                    _message.value = "Bulk saved: Download/BULK/$barcode.png — scan next."
                }.onFailure { e ->
                    _phase.value = CheckShootPhase.SCANNING
                    _message.value = e.message ?: "Could not save bulk PNG."
                }
            } else {
                _phase.value = CheckShootPhase.SCANNING
                _message.value = result.errorMessage ?: "Background removal failed."
            }
        }
    }

    private suspend fun refreshBulkScan(barcode: String) {
        val path = bulkStore.findExistingImagePath(barcode)
            ?: repository.resolveScannedBarcode(barcode)?.article?.imagePath
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { File(it).exists() }
        _bulkScan.value = BulkScanState(
            barcode = barcode,
            imagePath = path,
            hasImage = path != null,
        )
    }

    private fun loadAgentMode(): AgentCaptureMode =
        runCatching {
            AgentCaptureMode.valueOf(
                prefs.getString(KEY_AGENT_MODE, AgentCaptureMode.SMART.name)!!,
            )
        }.getOrDefault(AgentCaptureMode.SMART)

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
                BitmapFactory.decodeFile(cutoutPath)?.let { bmp ->
                    paray.reinforceFromBitmap(
                        bitmap = bmp,
                        articleId = articleId,
                        barcode = state.barcode,
                        designation = state.designation ?: state.barcode,
                        imagePath = cutoutPath,
                    )
                    bmp.recycle()
                }
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
                    _message.value = "PNG saved, PARAY learned this pack, added to To share."
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

    private fun clearTicketSnapProgress() {
        val preview = _ticketSnapProgress.value?.previewBitmap
        _ticketSnapProgress.value = null
        deferBitmapRecycle(preview)
    }

    private suspend fun processTicketMatch(
        match: ParayTicketMatch,
        tier: ParayTicketMatchTier? = null,
        stabilizerHint: String? = null,
    ) {
        val article = match.article
        val barcode = match.barcode
        refreshScan(barcode)
        val meta = repository.getArticlePanelMeta(article.id)
        val assessment = ticketAdvisor.assess(
            article,
            barcode,
            meta.lastPrintedPrice,
            match.read.ocrPrice,
            fusion = match.fusion,
            ocrDesignation = match.read.ocrDesignation,
        )
        ticketAdvisor.onScan(barcode, article, assessment)
        recognitionTracker.recordTicketScan(barcode, article.id, article.designation, assessment)
        workflowTracker.recordFeature(com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.TICKET_VERIFY)
        withContext(Dispatchers.Main) {
            _ticketAssessment.value = assessment
            _isLocked.value = false
            _ticketWalkStatus.value = TicketWalkStatus.READY
            lastTicketArticleId = article.id
            lastTicketProbability = match.fusion.probability
            lastTicketLockMs = 0L
            val tierLabel = tier?.marketingLabel ?: "PARAY ${match.fusion.probabilityPercent}%"
            val readHint = when (match.read.source) {
                com.oasismall.oasisai.domain.paray.ParayTicketReadSource.OCR_DESIGNATION ->
                    "$tierLabel · ${match.read.ocrDesignation ?: article.designation}"
                com.oasismall.oasisai.domain.paray.ParayTicketReadSource.BARCODE -> "Barcode: $barcode"
            }
            _message.value = listOfNotNull(
                readHint,
                stabilizerHint,
                "Hold card 1s to lock",
                assessment.message,
            ).joinToString(" · ")
        }
    }

    private suspend fun processTicketScanUnknown(barcode: String) {
        refreshScan(barcode)
        val assessment = ticketAdvisor.assess(null, barcode, null)
        _ticketAssessment.value = assessment
        ticketAdvisor.onScan(barcode, null, assessment)
        recognitionTracker.recordTicketScan(barcode, null, null, assessment)
        workflowTracker.recordFeature(com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.TICKET_VERIFY)
        _isLocked.value = false
        _ticketWalkStatus.value = TicketWalkStatus.READY
        _message.value = "${assessment.message} · Hold card 1s to lock"
    }

    fun markTicketVerified() {
        val scan = _scan.value ?: return
        val articleId = scan.articleId ?: return
        val assessment = _ticketAssessment.value
        viewModelScope.launch {
            repository.markTicketVerified(articleId)
            refreshScan(scan.barcode)
            val article = repository.getArticleWithImageById(articleId)
            if (assessment != null) {
                ticketAdvisor.onVerified(scan.barcode, article, assessment)
                recognitionTracker.recordTicketVerified(
                    scan.barcode,
                    articleId,
                    article?.designation,
                    assessment,
                )
            }
            _message.value = "Ticket verified — swipe unlock for next shelf."
        }
    }

    private suspend fun restorePersistedSession() {
        val saved = paray.sessionStore.load() ?: return
        refreshScan(saved.barcode)
        if (saved.locked) {
            _isLocked.value = true
        }
    }

    private fun persistSession() {
        val scan = _scan.value ?: return
        if (!_isLocked.value) {
            paray.sessionStore.clear()
            return
        }
        paray.sessionStore.save(
            CheckShootPersistedSession(
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
        ticketReader.close()
        super.onCleared()
    }

    private fun decodeSampledBitmap(path: String, maxSide: Int = 640): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 1_500L
        private const val SCAN_DEBOUNCE_SAME_ARTICLE_MS = 400L
        private const val STABLE_READS_CATALOG = 2
        private const val STABLE_READS_UNKNOWN = 3
        private const val UI_STEP_MIN_MS = 45L
        private const val BITMAP_RECYCLE_DEFER_MS = 64L
        private const val PREFS_AGENT = "oasis_agent_prefs"
        private const val KEY_AGENT_MODE = "capture_mode"
    }
}
