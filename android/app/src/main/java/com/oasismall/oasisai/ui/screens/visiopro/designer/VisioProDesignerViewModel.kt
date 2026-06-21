package com.oasismall.oasisai.ui.screens.visiopro.designer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProArticleDef
import com.oasismall.oasisai.domain.visiopro.VisioProPreset
import com.oasismall.oasisai.domain.visiopro.VisioProRenderFacade
import com.oasismall.oasisai.domain.visiopro.designer.NormRectHandle
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignLayerKind
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignStore
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDefaults
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument
import com.oasismall.oasisai.domain.visiopro.designer.VisioProNormRect
import com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey
import com.oasismall.oasisai.domain.visiopro.designer.alignCenterHorizontal
import com.oasismall.oasisai.domain.visiopro.designer.alignCenterVertical
import com.oasismall.oasisai.domain.visiopro.designer.isSpatial
import com.oasismall.oasisai.domain.visiopro.designer.resize
import com.oasismall.oasisai.domain.visiopro.designer.translateSnapped
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class VisioProDesignerUiState(
    val presetKey: VisioProPresetDesignKey,
    val document: VisioProDesignerDocument,
    val selectedLayer: VisioProDesignLayerKind = VisioProDesignLayerKind.PHOTO,
    val zoom: Float = 1f,
    val previewBitmap: Bitmap? = null,
    val isPreviewLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val hasCustomSave: Boolean = false,
    val isDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showLayerFrames: Boolean = true,
    val snapGuidesEnabled: Boolean = true,
    val showSnapGuideH: Boolean = false,
    val showSnapGuideV: Boolean = false,
    val snapHint: String? = null,
)

class VisioProDesignerViewModel(
    private val presetKey: VisioProPresetDesignKey,
    private val designStore: VisioProDesignStore,
    private val renderFacade: VisioProRenderFacade,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        VisioProDesignerUiState(
            presetKey = presetKey,
            document = VisioProDesignerDefaults.defaultDocument(presetKey, null),
        ),
    )
    val ui: StateFlow<VisioProDesignerUiState> = _ui.asStateFlow()

    private val undoStack = ArrayDeque<VisioProDesignerDocument>()
    private val redoStack = ArrayDeque<VisioProDesignerDocument>()
    private var savedSnapshot: VisioProDesignerDocument? = null
    private var defaultDocument: VisioProDesignerDocument? = null
    private var gestureSnapshot: VisioProDesignerDocument? = null
    private var previewJob: Job? = null
    private var previewGeneration = 0

    init {
        viewModelScope.launch {
            val defaults = designStore.loadOrDefault(presetKey)
            defaultDocument = defaults
            val saved = designStore.load(presetKey)
            val doc = saved ?: defaults
            savedSnapshot = doc
            _ui.update {
                it.copy(document = doc, hasCustomSave = saved != null, isDirty = false)
            }
            refreshPreview(immediate = true)
        }
    }

    fun selectLayer(layer: VisioProDesignLayerKind) {
        _ui.update { it.copy(selectedLayer = layer, showSnapGuideH = false, showSnapGuideV = false) }
    }

    fun setZoom(value: Float) {
        _ui.update { it.copy(zoom = value.coerceIn(0.35f, 2.5f)) }
    }

    fun fitZoom() {
        _ui.update { it.copy(zoom = 1f) }
    }

    fun toggleLayerFrames() {
        _ui.update { it.copy(showLayerFrames = !it.showLayerFrames) }
    }

    fun toggleSnapGuides() {
        _ui.update { it.copy(snapGuidesEnabled = !it.snapGuidesEnabled) }
    }

    fun onGestureStart() {
        gestureSnapshot = _ui.value.document
    }

    fun onGestureEnd() {
        val before = gestureSnapshot ?: return
        gestureSnapshot = null
        if (before != _ui.value.document) {
            pushUndo(before)
        }
        syncDirtyFlag()
        _ui.update { it.copy(showSnapGuideH = false, showSnapGuideV = false, snapHint = null) }
    }

    fun moveLayer(layer: VisioProDesignLayerKind, dxNorm: Float, dyNorm: Float) {
        val rect = _ui.value.document.rectFor(layer) ?: return
        val enableSnap = _ui.value.snapGuidesEnabled
        val (newRect, snap) = rect.translateSnapped(dxNorm, dyNorm, enableSnap = enableSnap)
        _ui.update { state ->
            state.copy(
                selectedLayer = layer,
                document = state.document.withRect(layer, newRect),
                isDirty = true,
                showSnapGuideH = snap.snappedHorizontal,
                showSnapGuideV = snap.snappedVertical,
                snapHint = when {
                    snap.snappedHorizontal && snap.snappedVertical -> "Centré"
                    snap.snappedHorizontal -> "Aligné au centre (horizontal)"
                    snap.snappedVertical -> "Aligné au centre (vertical)"
                    else -> null
                },
            )
        }
        refreshPreview()
    }

    fun resizeLayer(layer: VisioProDesignLayerKind, handle: NormRectHandle, dxNorm: Float, dyNorm: Float) {
        val rect = _ui.value.document.rectFor(layer) ?: return
        val newRect = rect.resize(handle, dxNorm, dyNorm)
        _ui.update { state ->
            state.copy(
                selectedLayer = layer,
                document = state.document.withRect(layer, newRect),
                isDirty = true,
            )
        }
        refreshPreview()
    }

    fun nudgeLayer(layer: VisioProDesignLayerKind, dx: Float, dy: Float) {
        val rect = _ui.value.document.rectFor(layer) ?: return
        mutateDocument { it.withRect(layer, rect.translate(dx, dy)) }
    }

    fun alignSelectedCenterHorizontal() = alignLayer(_ui.value.selectedLayer) { it.alignCenterHorizontal() }
    fun alignSelectedCenterVertical() = alignLayer(_ui.value.selectedLayer) { it.alignCenterVertical() }

    fun resetSelectedLayerLayout() {
        val layer = _ui.value.selectedLayer
        if (!layer.isSpatial()) {
            _ui.update { it.copy(message = "Ce calque ne se déplace pas") }
            return
        }
        val defRect = defaultDocument?.rectFor(layer) ?: return
        mutateDocument { it.withRect(layer, defRect) }
        _ui.update { it.copy(message = "Position ${layer.labelFr} restaurée") }
    }

    private fun alignLayer(layer: VisioProDesignLayerKind, transform: (VisioProNormRect) -> VisioProNormRect) {
        val rect = _ui.value.document.rectFor(layer) ?: return
        mutateDocument { it.withRect(layer, transform(rect)) }
    }

    fun updateBackgroundTop(color: Int) = mutateDocument { it.copy(backgroundTop = color) }
    fun updateBackgroundBottom(color: Int) = mutateDocument { it.copy(backgroundBottom = color) }
    fun updateHeaderBand(color: Int) = mutateDocument { it.copy(headerBand = color) }
    fun updateAccent(color: Int) = mutateDocument { it.copy(accent = color) }
    fun updateDesignationColor(color: Int) = mutateDocument { it.copy(designationColor = color) }
    fun updatePriceColor(color: Int) = mutateDocument { it.copy(priceColor = color) }
    fun updateCodeColor(color: Int) = mutateDocument { it.copy(codeColor = color) }
    fun updateCategoryTag(text: String) = mutateDocument { it.copy(categoryTag = text) }
    fun updateHeaderBandHeight(ratio: Float) = mutateDocument { it.copy(headerBandHeight = ratio.coerceIn(0.06f, 0.22f)) }
    fun updateDesignationFontRatio(ratio: Float) = mutateDocument { it.copy(designationFontRatio = ratio.coerceIn(0.02f, 0.15f)) }
    fun updatePriceFontRatio(ratio: Float) = mutateDocument {
        it.copy(priceFontRatio = ratio.coerceIn(0.03f, 0.20f), priceAutoFit = false)
    }
    fun updateCodeFontRatio(ratio: Float) = mutateDocument { it.copy(codeFontRatio = ratio.coerceIn(0.015f, 0.08f)) }
    fun updateSampleDesignation(text: String) = mutateDocument { it.copy(sampleDesignation = text) }
    fun updateSampleDesignationAr(text: String) = mutateDocument { it.copy(sampleDesignationAr = text) }
    fun updateSamplePrice(text: String) {
        val parsed = text.trim().replace(" ", "").replace(",", ".").toDoubleOrNull() ?: return
        mutateDocument { it.copy(samplePrice = parsed) }
    }
    fun updateSampleCode(text: String) = mutateDocument { it.copy(sampleCode = text.trim()) }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_ui.value.document)
        val previous = undoStack.removeLast()
        _ui.update {
            it.copy(document = previous, canUndo = undoStack.isNotEmpty(), canRedo = true)
        }
        syncDirtyFlag()
        refreshPreview(immediate = true)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_ui.value.document)
        val next = redoStack.removeLast()
        _ui.update {
            it.copy(document = next, canUndo = true, canRedo = redoStack.isNotEmpty())
        }
        syncDirtyFlag()
        refreshPreview(immediate = true)
    }

    fun save() {
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, message = null) }
            designStore.save(_ui.value.document)
            savedSnapshot = _ui.value.document
            _ui.update {
                it.copy(
                    isSaving = false,
                    hasCustomSave = true,
                    isDirty = false,
                    message = "Preset enregistré · appliqué à ${presetKey.labelFr}",
                )
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            designStore.reset(presetKey)
            val doc = designStore.loadOrDefault(presetKey)
            defaultDocument = doc
            savedSnapshot = doc
            undoStack.clear()
            redoStack.clear()
            _ui.update {
                it.copy(
                    document = doc,
                    hasCustomSave = false,
                    isDirty = false,
                    canUndo = false,
                    canRedo = false,
                    message = "Preset par défaut restauré",
                )
            }
            refreshPreview(immediate = true)
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    private fun mutateDocument(block: (VisioProDesignerDocument) -> VisioProDesignerDocument) {
        pushUndo(_ui.value.document)
        _ui.update { it.copy(document = block(it.document)) }
        syncDirtyFlag()
        refreshPreview()
    }

    private fun pushUndo(before: VisioProDesignerDocument) {
        undoStack.addLast(before)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        _ui.update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun syncDirtyFlag() {
        val baseline = savedSnapshot ?: _ui.value.document
        _ui.update { it.copy(isDirty = it.document != baseline) }
    }

    private fun refreshPreview(immediate: Boolean = false) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _ui.update { it.copy(isPreviewLoading = true) }
            if (!immediate) delay(PREVIEW_DEBOUNCE_MS)
            val generation = ++previewGeneration
            val state = _ui.value
            val doc = state.document
            val bitmap = renderFacade.render(
                preset = samplePreset(state.presetKey, doc),
                price = doc.samplePrice,
                articlePhoto = null,
                catalogPhoto = null,
                design = doc,
            )
            if (generation != previewGeneration) return@launch
            _ui.update { it.copy(previewBitmap = bitmap, isPreviewLoading = false) }
        }
    }

    private fun samplePreset(key: VisioProPresetDesignKey, doc: VisioProDesignerDocument): VisioProPreset {
        val theme = VisioProDesignerDefaults.themeFromDocument(doc)
        return VisioProPreset(
            id = "designer_preview_${key.storageKey}",
            category = key.category,
            channel = key.channel,
            article = VisioProArticleDef(
                slug = "designer_preview",
                labelFr = doc.sampleDesignation,
                designationKeywords = listOf(doc.sampleDesignation),
                csvDesignation = doc.sampleDesignation,
                labelAr = doc.sampleDesignationAr,
                barcodeSuffix = doc.sampleCode,
            ),
            theme = theme,
        )
    }

    fun availableLayers(): List<VisioProDesignLayerKind> = buildList {
        add(VisioProDesignLayerKind.BACKGROUND)
        add(VisioProDesignLayerKind.HEADER)
        add(VisioProDesignLayerKind.PHOTO)
        add(VisioProDesignLayerKind.DESIGNATION)
        if (_ui.value.document.showPrice) add(VisioProDesignLayerKind.PRICE)
        if (_ui.value.document.templateId == "fv_print" || _ui.value.document.codeRect != null) {
            add(VisioProDesignLayerKind.CODE)
        }
    }

    fun spatialLayers(): List<VisioProDesignLayerKind> =
        availableLayers().filter { it.isSpatial() }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 160L
    }
}
