package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayLearnPhase
import com.oasismall.oasisai.domain.paray.ParayLearnQueueStats
import com.oasismall.oasisai.domain.paray.ParayLearnRecord
import com.oasismall.oasisai.domain.paray.ParayLearnSessionProduct
import com.oasismall.oasisai.domain.paray.ParayLearnEngine
import com.oasismall.oasisai.domain.paray.ParayPackagingVariantEvent
import com.oasismall.oasisai.domain.paray.ParayVisualSimilarity
import com.oasismall.oasisai.domain.paray.VisualFeatureExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParayMainUiState(
    val selectedTab: Int = 0,
    val stats: ParayLearnQueueStats? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class ParayMainViewModel(
    private val repository: OasisRepository,
    private val paray: ParayAgent,
) : ViewModel() {
    private val queue = paray.learnQueue(repository)
    private val _uiState = MutableStateFlow(ParayMainUiState())
    val uiState: StateFlow<ParayMainUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { queue.buildStats() }
                .onSuccess { stats ->
                    _uiState.update { it.copy(stats = stats, loading = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load queue")
                    }
                }
        }
    }
}

data class ParayLearnSessionUiState(
    val product: ParayLearnSessionProduct? = null,
    val record: ParayLearnRecord? = null,
    val phase: ParayLearnPhase = ParayLearnPhase.IDLE,
    val instruction: String = "",
    val frontSimilarity: Float = 0f,
    val progressFront: Boolean = false,
    val progressLeft: Boolean = false,
    val progressRight: Boolean = false,
    val progressBack: Boolean = false,
    val cameraActive: Boolean = false,
    val loading: Boolean = true,
    val preloadComplete: Boolean = false,
    val complete: Boolean = false,
    val mismatch: Boolean = false,
    val error: String? = null,
)

class ParayLearnSessionViewModel(
    private val repository: OasisRepository,
    private val paray: ParayAgent,
    private val parayObserver: com.oasismall.oasisai.domain.paray.ParayObserver,
    private val workflowTracker: com.oasismall.oasisai.domain.paray.ParayWorkflowTracker,
) : ViewModel() {
    private val queue = paray.learnQueue(repository)
    private val preload = paray.learnPreload(repository)
    private var learnSettings = paray.learnSettingsStore.current()
    private var engine = paray.learnEngine(learnSettings)
    private val _uiState = MutableStateFlow(ParayLearnSessionUiState())
    val uiState: StateFlow<ParayLearnSessionUiState> = _uiState.asStateFlow()

    private var pngFeatures: VisualFeatureExtractor.Features? = null
    private var lastFrameFeatures: VisualFeatureExtractor.Features? = null
    private var stableFrameCount = 0
    private var mismatchFrameCount = 0
    private var loggedVariantForArticle: Long? = null

    fun startNextProduct() {
        viewModelScope.launch {
            reloadEngine()
            loggedVariantForArticle = null
            _uiState.update { it.copy(loading = true, error = null, complete = false, mismatch = false) }
            val next = queue.nextPending()
            if (next == null) {
                _uiState.update {
                    it.copy(loading = false, complete = true, instruction = "All products learned")
                }
                return@launch
            }
            loadProduct(next.id)
        }
    }

    fun loadProduct(articleId: Long) {
        viewModelScope.launch {
            reloadEngine()
            loggedVariantForArticle = null
            _uiState.update { it.copy(loading = true, error = null) }
            val context = preload.load(articleId)
            if (context == null) {
                _uiState.update { it.copy(loading = false, error = "Product not ready for learning") }
                return@launch
            }

            pngFeatures = context.pngFeatures
            val record = context.record
            val phase = engine.initialPhase(record)
            resetFrameCounters()
            _uiState.update {
                it.copy(
                    product = context.product,
                    record = record,
                    phase = phase,
                    instruction = when (phase) {
                        ParayLearnPhase.COMPLETE -> "Already learned"
                        ParayLearnPhase.FRONT_CONFIRM -> "Show Front Side — match official PNG"
                        else -> "Front confirmed — continue learning"
                    },
                    progressFront = record.frontConfirmed,
                    progressLeft = record.leftCapture != null,
                    progressRight = record.rightCapture != null,
                    progressBack = record.backCapture != null,
                    loading = false,
                    preloadComplete = context.preloadComplete,
                    cameraActive = phase != ParayLearnPhase.COMPLETE,
                    complete = phase == ParayLearnPhase.COMPLETE,
                )
            }
        }
    }

    fun onFrameFeatures(features: VisualFeatureExtractor.Features) {
        val state = _uiState.value
        val record = state.record ?: return
        val png = pngFeatures ?: return
        if (state.phase == ParayLearnPhase.COMPLETE || state.phase == ParayLearnPhase.MISMATCH) return

        if (lastFrameFeatures != null &&
            ParayVisualSimilarity.score(features, lastFrameFeatures!!) >=
            ParayLearnEngine.FRAME_STABILITY_SIMILARITY
        ) {
            stableFrameCount++
        } else {
            stableFrameCount = 1
        }
        lastFrameFeatures = features

        when (state.phase) {
            ParayLearnPhase.FRONT_CONFIRM -> {
                val result = engine.processFrontConfirm(
                    record = record,
                    pngFeatures = png,
                    frameFeatures = features,
                    stableFrames = stableFrameCount,
                    mismatchFrames = mismatchFrameCount,
                )
                if (result.frontSimilarity < learnSettings.frontMismatchCutoff()) {
                    mismatchFrameCount++
                }
                result.packagingVariantHint?.let { hint ->
                    maybeLogPackagingVariant(record, result.frontSimilarity, hint)
                }
                applyFrameResult(result)
            }
            ParayLearnPhase.CAPTURE_LEFT,
            ParayLearnPhase.CAPTURE_RIGHT,
            ParayLearnPhase.CAPTURE_BACK,
            -> {
                val prior = engine.priorSideFeatures(record, png)
                val result = engine.processSideCapture(
                    record = record,
                    phase = state.phase,
                    pngFeatures = png,
                    frameFeatures = features,
                    priorFeatures = prior,
                    stableFrames = stableFrameCount,
                )
                applyFrameResult(result)
            }
            else -> Unit
        }
    }

    fun retryFront() {
        mismatchFrameCount = 0
        loggedVariantForArticle = null
        resetFrameCounters()
        _uiState.update {
            it.copy(
                phase = ParayLearnPhase.FRONT_CONFIRM,
                mismatch = false,
                instruction = "Show Front Side — match official PNG",
                cameraActive = true,
            )
        }
    }

    fun skipProduct() {
        startNextProduct()
    }

    private fun maybeLogPackagingVariant(
        record: ParayLearnRecord,
        similarity: Float,
        message: String,
    ) {
        if (loggedVariantForArticle == record.articleId) return
        loggedVariantForArticle = record.articleId
        paray.logPackagingVariant(
            ParayPackagingVariantEvent(
                articleId = record.articleId,
                barcode = record.barcode,
                designation = record.designation,
                similarity = similarity,
                threshold = learnSettings.frontConfirmationThreshold,
                message = message,
            ),
        )
    }

    private fun applyFrameResult(result: com.oasismall.oasisai.domain.paray.ParayLearnFrameResult) {
        val updated = result.updatedRecord
        if (updated != null) {
            paray.learnStore.put(updated)
            if (result.phase == ParayLearnPhase.COMPLETE) {
                paray.saveLearnRecord(updated)
                workflowTracker.recordFeature(
                    com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.PARAY_LEARN_SESSION,
                )
                viewModelScope.launch {
                    parayObserver.onTrigger(
                        com.oasismall.oasisai.domain.paray.ParayObserverTrigger.PARAY_LEARN_COMPLETED,
                        com.oasismall.oasisai.domain.paray.ParayObserverContext(articleId = updated.articleId),
                    )
                }
            }
        }
        if (result.phase != _uiState.value.phase && result.phase != ParayLearnPhase.FRONT_CONFIRM) {
            resetFrameCounters()
        }
        _uiState.update {
            it.copy(
                phase = result.phase,
                instruction = result.instruction,
                frontSimilarity = result.frontSimilarity,
                record = updated ?: it.record,
                progressFront = result.progressFront,
                progressLeft = result.progressLeft,
                progressRight = result.progressRight,
                progressBack = result.progressBack,
                mismatch = result.phase == ParayLearnPhase.MISMATCH,
                cameraActive = result.phase != ParayLearnPhase.MISMATCH &&
                    result.phase != ParayLearnPhase.COMPLETE,
                complete = result.phase == ParayLearnPhase.COMPLETE,
            )
        }
    }

    private suspend fun reloadEngine() {
        learnSettings = paray.loadLearnSettings()
        engine = paray.learnEngine(learnSettings)
    }

    private fun resetFrameCounters() {
        stableFrameCount = 0
        mismatchFrameCount = 0
        lastFrameFeatures = null
    }
}
