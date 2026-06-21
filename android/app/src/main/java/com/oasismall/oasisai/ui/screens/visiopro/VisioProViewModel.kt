package com.oasismall.oasisai.ui.screens.visiopro

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignStore
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProChannel
import com.oasismall.oasisai.domain.visiopro.VisioProExporter
import com.oasismall.oasisai.domain.visiopro.VisioProPhotoStore
import com.oasismall.oasisai.domain.visiopro.VisioProPreset
import com.oasismall.oasisai.domain.visiopro.VisioProPresetCatalog
import com.oasismall.oasisai.domain.visiopro.VisioProPresetUi
import com.oasismall.oasisai.domain.visiopro.VisioProPriceResolver
import com.oasismall.oasisai.domain.visiopro.VisioProPriceSource
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.domain.visiopro.VisioProPrintA4Renderer
import com.oasismall.oasisai.domain.visiopro.VisioProRenderFacade
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.util.PriceFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VisioProUiState(
    val category: VisioProCategory? = null,
    val channel: VisioProChannel = VisioProChannel.SOCIAL,
    val presets: List<VisioProPresetUi> = emptyList(),
    val selectedPresetId: String? = null,
    val priceInput: String = "",
    val priceSource: VisioProPriceSource = VisioProPriceSource.NONE,
    val previewBitmap: android.graphics.Bitmap? = null,
    val exportMessage: String? = null,
    val isExporting: Boolean = false,
    val isLoading: Boolean = false,
    val usesDailyPhoto: Boolean = false,
    val hasArticlePhoto: Boolean = false,
    val photoTakenAt: Long? = null,
    val isFvPrintQuad: Boolean = false,
    val printModified: Boolean = false,
    val printQueueSize: Int = 0,
    /** When false, article editor sheet is closed. */
    val editorOpen: Boolean = false,
)

class VisioProViewModel(
    private val repository: OasisRepository,
    private val catalogService: VisioProCatalogService,
    private val designStore: VisioProDesignStore,
    private val store: VisioProStore,
    private val photoStore: VisioProPhotoStore,
    private val priceResolver: VisioProPriceResolver,
    private val exporter: VisioProExporter,
    private val renderFacade: VisioProRenderFacade,
) : ViewModel() {

    private val _ui = MutableStateFlow(VisioProUiState())
    val ui: StateFlow<VisioProUiState> = _ui.asStateFlow()

    fun openCategory(category: VisioProCategory) {
        val channel = VisioProChannel.SOCIAL
        _ui.update {
            it.copy(
                category = category,
                channel = channel,
                selectedPresetId = null,
                editorOpen = false,
                exportMessage = null,
                previewBitmap = null,
            )
        }
        loadPresets(category, channel)
    }

    fun setChannel(channel: VisioProChannel) {
        val category = _ui.value.category ?: return
        _ui.update { it.copy(channel = channel, selectedPresetId = null, editorOpen = false, exportMessage = null, previewBitmap = null) }
        loadPresets(category, channel)
    }

    fun selectPreset(presetId: String) {
        _ui.update { it.copy(selectedPresetId = presetId, editorOpen = true, exportMessage = null) }
        refreshSelectedPreset()
    }

    fun updatePriceInput(text: String) {
        _ui.update { it.copy(priceInput = text, exportMessage = null) }
    }

    fun commitManualPrice() {
        val preset = selectedPreset() ?: return
        val parsed = parsePrice(_ui.value.priceInput)
        viewModelScope.launch {
            store.setManualPrice(preset.article.slug, parsed)
            if (preset.theme.templateId == "fv_print") {
                store.setPrintModified(preset.article.slug, true)
                store.addToPrintQueue(preset.article.slug)
            }
            refreshSelectedPreset()
            reloadPresetRow(preset.id)
            refreshPrintQueueState()
        }
    }

    fun addCurrentToShare() {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            val articleId = preset.article.catalogArticleId
                ?: repository.findArticleIdForVisioPro(
                    csvDesignation = preset.article.csvDesignation,
                    barcodeSuffix = preset.article.barcodeSuffix,
                    keywords = preset.article.designationKeywords,
                )
            if (articleId == null) {
                _ui.update { it.copy(exportMessage = "Article introuvable dans le catalogue CSV") }
                return@launch
            }
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.VISIO_PRO)
            _ui.update { it.copy(exportMessage = "Ajouté à To share") }
        }
    }

    fun addCurrentToPrintQueue() {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            store.addToPrintQueue(preset.article.slug)
            store.setPrintModified(preset.article.slug, true)
            refreshPrintQueueState()
            _ui.update { it.copy(exportMessage = "Ajouté à la file A4") }
        }
    }

    fun reloadFromCsv() {
        viewModelScope.launch {
            val preset = selectedPreset() ?: return@launch
            store.setManualPrice(preset.article.slug, null)
            refreshSelectedPreset()
            reloadPresetRow(preset.id)
        }
    }

    fun onPhotoCaptured(context: Context, uri: Uri) {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            val dest = photoStore.photoFile(preset.article.slug)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            refreshSelectedPreset()
        }
    }

    fun exportCurrent() {
        val preset = selectedPreset() ?: return
        if (preset.theme.templateId == "fv_print") {
            exportA4FromQueue()
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isExporting = true, exportMessage = null) }
            val parsed = parsePrice(_ui.value.priceInput)
            if (parsed != null && preset.theme.showPrice) {
                store.setManualPrice(preset.article.slug, parsed)
            }
            val bitmap = buildPreviewBitmap(preset, parsed)
            val result = exporter.exportToGallery(bitmap, preset, preset.channel)
            result.onSuccess { name ->
                when (preset.channel) {
                    VisioProChannel.SOCIAL -> store.markSocialExport(preset.article.slug)
                    VisioProChannel.PRINT -> store.markPrintExport(preset.article.slug)
                }
                reloadPresetRow(preset.id)
                _ui.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = "Enregistré : $name",
                    )
                }
            }.onFailure { err ->
                _ui.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = err.message ?: "Échec export",
                    )
                }
            }
        }
    }

    fun exportA4FromQueue() {
        viewModelScope.launch {
            _ui.update { it.copy(isExporting = true, exportMessage = null) }
            val queue = store.getPrintQueue()
            if (queue.isEmpty()) {
                _ui.update {
                    it.copy(isExporting = false, exportMessage = "File A4 vide — modifiez un prix impression")
                }
                return@launch
            }
            val batch = queue.take(VisioProPrintA4Renderer.CAPACITY)
            val category = _ui.value.category ?: VisioProCategory.FRUITS
            val articleDefs = catalogService.resolveArticleDefs(category)
            val cards = batch.mapNotNull { slug ->
                val printPreset = VisioProPresetCatalog.printPresetBySlug(slug, articleDefs, category)
                    ?: VisioProPresetCatalog.printPresetBySlug(slug)
                    ?: return@mapNotNull null
                val resolved = priceResolver.resolve(printPreset).price
                val price = resolved ?: run {
                    if (slug == selectedPreset()?.article?.slug) parsePrice(_ui.value.priceInput) else null
                }
                buildPreviewBitmap(printPreset, price)
            }
            if (cards.isEmpty()) {
                _ui.update { it.copy(isExporting = false, exportMessage = "Aucune étiquette à composer") }
                return@launch
            }
            val page = VisioProPrintA4Renderer.composeQuad(cards)
            val label = batch.joinToString("_") { it.take(8) }
            val result = exporter.exportA4QuadToGallery(page, label)
            result.onSuccess { name ->
                store.removeFromPrintQueue(batch)
                batch.forEach { slug ->
                    store.markPrintExport(slug)
                    store.setPrintModified(slug, false)
                }
                refreshPrintQueueState()
                _ui.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = "A4 enregistré : $name (${cards.size}/4)",
                    )
                }
            }.onFailure { err ->
                _ui.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = err.message ?: "Échec export A4",
                    )
                }
            }
        }
    }

    fun clearExportMessage() {
        _ui.update { it.copy(exportMessage = null) }
    }

    private fun loadPresets(category: VisioProCategory, channel: VisioProChannel) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val articles = catalogService.resolveArticleDefs(category)
            val presets = VisioProPresetCatalog.presets(category, channel, articles)
            val memory = store.getAllMemory()
            val rows = presets.map { preset -> toPresetUi(preset, memory) }
            _ui.update {
                it.copy(
                    presets = rows,
                    selectedPresetId = null,
                    editorOpen = false,
                    previewBitmap = null,
                    isLoading = false,
                )
            }
            refreshPrintQueueState()
        }
    }

    fun clearArticleSelection() {
        _ui.update {
            it.copy(
                selectedPresetId = null,
                editorOpen = false,
                previewBitmap = null,
                exportMessage = null,
            )
        }
    }

    private suspend fun refreshPrintQueueState() {
        val queue = store.getPrintQueue()
        val preset = selectedPreset()
        val mem = preset?.let { store.getMemory(it.article.slug) }
        _ui.update {
            it.copy(
                printQueueSize = queue.size,
                printModified = mem?.printModified == true,
            )
        }
    }

    private suspend fun toPresetUi(
        preset: VisioProPreset,
        memory: Map<String, com.oasismall.oasisai.domain.visiopro.VisioProArticleMemory>,
    ): VisioProPresetUi {
        val mem = memory[preset.article.slug]
        val priceResult = priceResolver.resolve(preset)
        val lastExport = when (preset.channel) {
            VisioProChannel.SOCIAL -> mem?.lastSocialExportAt
            VisioProChannel.PRINT -> mem?.lastPrintExportAt
        }
        return VisioProPresetUi(
            preset = preset,
            price = priceResult.price,
            priceSource = priceResult.source,
            lastExportAt = lastExport,
            editablePriceText = priceResult.price?.let { PriceFormatter.formatNumber(it) } ?: "",
        )
    }

    private fun reloadPresetRow(presetId: String) {
        viewModelScope.launch {
            val memory = store.getAllMemory()
            _ui.update { state ->
                state.copy(
                    presets = state.presets.map { row ->
                        if (row.preset.id == presetId) {
                            toPresetUi(row.preset, memory)
                        } else {
                            row
                        }
                    },
                )
            }
        }
    }

    private fun refreshSelectedPreset() {
        viewModelScope.launch {
            val preset = selectedPreset() ?: return@launch
            val priceResult = priceResolver.resolve(preset)
            val priceText = when {
                _ui.value.priceInput.isNotBlank() -> _ui.value.priceInput
                priceResult.price != null -> PriceFormatter.formatNumber(priceResult.price)
                else -> ""
            }
            val effectivePrice = parsePrice(priceText) ?: priceResult.price
            val usesPhoto = preset.theme.templateId == "ail_social"
            val isFvPrint = preset.theme.templateId == "fv_print"
            val isPrint = preset.channel == VisioProChannel.PRINT
            val mem = store.getMemory(preset.article.slug)
            val userPhoto = photoStore.loadBitmap(preset.article.slug)
            val articlePhoto = when {
                usesPhoto -> userPhoto
                isPrint && userPhoto != null -> userPhoto
                else -> null
            }
            val catalogPhoto = when {
                usesPhoto -> null
                isPrint && userPhoto != null -> null
                else -> loadCatalogPhoto(preset)
            }
            val design = loadDesignForPreset(preset)
            val rendered = renderFacade.render(
                preset = preset,
                price = effectivePrice,
                articlePhoto = articlePhoto,
                catalogPhoto = catalogPhoto,
                design = design,
            )
            val queue = store.getPrintQueue()
            _ui.update {
                it.copy(
                    priceInput = priceText,
                    priceSource = priceResult.source,
                    previewBitmap = rendered,
                    usesDailyPhoto = usesPhoto || isPrint,
                    hasArticlePhoto = photoStore.hasPhoto(preset.article.slug),
                    photoTakenAt = photoStore.photoModifiedAt(preset.article.slug),
                    isFvPrintQuad = isFvPrint,
                    printModified = mem.printModified,
                    printQueueSize = queue.size,
                )
            }
        }
    }

    private suspend fun buildPreviewBitmap(preset: VisioProPreset, parsed: Double?): android.graphics.Bitmap {
        val priceResult = priceResolver.resolve(preset)
        val effectivePrice = parsed ?: priceResult.price
        val usesPhoto = preset.theme.templateId == "ail_social"
        val isPrint = preset.channel == VisioProChannel.PRINT
        val userPhoto = photoStore.loadBitmap(preset.article.slug)
        val articlePhoto = when {
            usesPhoto -> userPhoto
            isPrint && userPhoto != null -> userPhoto
            else -> null
        }
        val catalogPhoto = when {
            usesPhoto -> null
            isPrint && userPhoto != null -> null
            else -> loadCatalogPhoto(preset)
        }
        val design = loadDesignForPreset(preset)
        return renderFacade.render(preset, effectivePrice, articlePhoto, catalogPhoto, design)
    }

    private suspend fun loadDesignForPreset(preset: VisioProPreset) =
        designStore.loadForCategory(preset.category, preset.channel)

    private suspend fun loadCatalogPhoto(preset: VisioProPreset): android.graphics.Bitmap? {
        val articleImage = repository.findArticleImageForKeywords(preset.article.designationKeywords)
        return articleImage?.imagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                ?: runCatching { BitmapFactory.decodeFile(File(path).absolutePath) }.getOrNull()
        }
    }

    private fun selectedPreset(): VisioProPreset? {
        val id = _ui.value.selectedPresetId ?: return null
        return _ui.value.presets.firstOrNull { it.preset.id == id }?.preset
            ?: VisioProPresetCatalog.presetById(id)
    }

    private fun parsePrice(text: String): Double? {
        val cleaned = text.trim().replace(" ", "").replace(",", ".")
        if (cleaned.isBlank()) return null
        return cleaned.toDoubleOrNull()
    }
}
