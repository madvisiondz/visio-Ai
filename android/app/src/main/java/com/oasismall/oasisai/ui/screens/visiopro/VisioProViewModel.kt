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
import com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker
import com.oasismall.oasisai.domain.visiopro.VisioProRenderFacade
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.domain.settings.ImportantRayonsConfig
import com.oasismall.oasisai.domain.settings.includesRayon
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.ui.components.hasPriceChange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.oasismall.oasisai.ui.components.CartSourceTags
import kotlin.math.abs
import com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey
import com.oasismall.oasisai.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    val designationInput: String = "",
    val designationFontRatio: Float = 0.055f,
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
    val checkedPresetIds: Set<String> = emptySet(),
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
    private val printImageLinker: VisioProPrintImageLinker,
) : ViewModel() {

    private val _ui = MutableStateFlow(VisioProUiState())
    val ui: StateFlow<VisioProUiState> = _ui.asStateFlow()

    val importantRayonsConfig = repository.importantRayonsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportantRayonsConfig())

    private var navigationPresetIds: List<String> = emptyList()
    private var previewDebounceJob: Job? = null
    private var catalogBySlug: Map<String, com.oasismall.oasisai.data.db.dao.ArticleWithImage?> = emptyMap()

    fun setNavigationPresetIds(ids: List<String>) {
        navigationPresetIds = ids
    }

    fun navigateArticle(forward: Boolean) {
        viewModelScope.launch {
            persistCurrentEdits()
            val ids = navigationPresetIds.ifEmpty { _ui.value.presets.map { it.preset.id } }
            val current = _ui.value.selectedPresetId ?: return@launch
            val index = ids.indexOf(current)
            if (index < 0) return@launch
            val nextIndex = if (forward) index + 1 else index - 1
            if (nextIndex !in ids.indices) return@launch
            selectPreset(ids[nextIndex])
        }
    }

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
                checkedPresetIds = emptySet(),
            )
        }
        loadPresets(category, channel)
    }

    fun setChannel(channel: VisioProChannel) {
        val category = _ui.value.category ?: return
        _ui.update {
            it.copy(
                channel = channel,
                selectedPresetId = null,
                editorOpen = false,
                exportMessage = null,
                previewBitmap = null,
                checkedPresetIds = emptySet(),
            )
        }
        loadPresets(category, channel)
    }

    fun selectPreset(presetId: String) {
        previewDebounceJob?.cancel()
        _ui.update { it.copy(selectedPresetId = presetId, editorOpen = true, exportMessage = null) }
        refreshSelectedPreset()
    }

    fun updatePriceInput(text: String) {
        _ui.update { it.copy(priceInput = filterVisioProPriceInput(text), exportMessage = null) }
    }

    fun updateDesignationInput(text: String) {
        _ui.update { state ->
            state.copy(
                designationInput = text,
                exportMessage = null,
                presets = state.presets.map { row ->
                    if (row.preset.id == state.selectedPresetId) {
                        row.copy(editableDesignationText = text)
                    } else {
                        row
                    }
                },
            )
        }
        schedulePreviewRefresh()
    }

    fun updateDesignationFontRatio(ratio: Float) {
        if (selectedPreset() == null) return
        val clamped = ratio.coerceIn(0.02f, 0.15f)
        _ui.update { it.copy(designationFontRatio = clamped, exportMessage = null) }
        schedulePreviewRefresh()
    }

    fun commitManualDesignation() {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            persistDesignation(preset)
            reloadPresetRow(preset.id)
            refreshSelectedPreset()
        }
    }

    fun reloadDesignationFromPreset() {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            store.setManualDesignation(preset.article.slug, null)
            store.setPresetDesignationFontRatio(preset.article.slug, preset.id, null)
            reloadPresetRow(preset.id)
            refreshSelectedPreset()
        }
    }

    fun updateInlinePrice(presetId: String, text: String) {
        val filtered = filterVisioProPriceInput(text)
        _ui.update { state ->
            state.copy(
                presets = state.presets.map { row ->
                    if (row.preset.id == presetId) row.copy(editablePriceText = filtered) else row
                },
                priceInput = if (state.selectedPresetId == presetId) filtered else state.priceInput,
            )
        }
    }

    fun commitInlinePrice(presetId: String) {
        val row = _ui.value.presets.firstOrNull { it.preset.id == presetId } ?: return
        val category = _ui.value.category ?: return
        if (!row.preset.theme.showPrice || category == VisioProCategory.FISH) return
        val parsed = parsePrice(row.editablePriceText)
        viewModelScope.launch {
            persistPriceOverride(row.preset.article.slug, parsed, row.preset)
            if (row.preset.theme.templateId == "fv_print") {
                store.setPrintModified(row.preset.article.slug, true)
                store.addToPrintQueue(row.preset.article.slug)
            }
            reloadPresetRow(presetId)
            refreshPrintQueueState()
            if (_ui.value.selectedPresetId == presetId) {
                refreshSelectedPreset()
            }
        }
    }

    fun commitManualPrice() {
        val preset = selectedPreset() ?: return
        val parsed = parsePrice(_ui.value.priceInput)
        viewModelScope.launch {
            persistPriceOverride(preset.article.slug, parsed, preset)
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
            printImageLinker.linkForArticleDef(preset.article)
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
            val hasImage = repository.getArticleWithImageById(articleId)?.imagePath?.isNotBlank() == true
            _ui.update {
                it.copy(
                    exportMessage = if (hasImage) {
                        "Ajouté à To share (photo impression liée)"
                    } else {
                        "Ajouté à To share — chargez une photo onglet Impression"
                    },
                )
            }
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
            store.clearManualPriceOverride(preset.article.slug)
            refreshSelectedPreset()
            reloadPresetRow(preset.id)
        }
    }

    fun onPhotoCaptured(context: Context, uri: Uri) {
        val preset = selectedPreset() ?: return
        viewModelScope.launch {
            val dest = photoStore.photoFile(preset.article.slug, preset.channel)
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
                persistPriceOverride(preset.article.slug, parsed, preset)
            }
            persistDesignation(preset)
            val bitmap = buildPreviewBitmap(preset, parsed, _ui.value.designationInput)
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
            val categoryCatalog = catalogService.resolveCategoryCatalog(category)
            val catalogForBatch = categoryCatalog.defs.associate { def ->
                def.slug to def.catalogArticleId?.let { categoryCatalog.articlesById[it] }
            }
            val memory = store.getAllMemory()
            val cards = batch.mapNotNull { slug ->
                val printPreset = VisioProPresetCatalog.printPresetBySlug(slug, categoryCatalog.defs, category)
                    ?: VisioProPresetCatalog.printPresetBySlug(slug)
                    ?: return@mapNotNull null
                val resolved = priceResolver.resolveFromCatalog(
                    printPreset,
                    memory[slug],
                    catalogForBatch[slug],
                ).price
                val price = resolved ?: run {
                    if (slug == selectedPreset()?.article?.slug) parsePrice(_ui.value.priceInput) else null
                }
                buildPreviewBitmap(printPreset, price, resolveDesignationForRender(printPreset))
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

    fun togglePresetChecked(presetId: String) {
        _ui.update { state ->
            val next = state.checkedPresetIds.toMutableSet()
            if (presetId in next) next.remove(presetId) else next.add(presetId)
            state.copy(checkedPresetIds = next)
        }
    }

    fun selectAllVisible(presetIds: List<String>) {
        _ui.update { it.copy(checkedPresetIds = presetIds.toSet()) }
    }

    fun clearChecked() {
        _ui.update { it.copy(checkedPresetIds = emptySet()) }
    }

    fun exportCheckedList() {
        val checked = _ui.value.checkedPresetIds
        if (checked.isEmpty()) {
            _ui.update { it.copy(exportMessage = "Cochez au moins un article") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isExporting = true, exportMessage = null) }
            val channel = _ui.value.channel
            val folderLabel = when (channel) {
                VisioProChannel.SOCIAL -> "DCIM/VisioPRO/Social"
                VisioProChannel.PRINT -> "DCIM/VisioPRO/Print"
            }
            val rows = _ui.value.presets.filter { it.preset.id in checked }
            var ok = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                rows.forEach { row ->
                    val preset = row.preset
                    val parsed = parsePrice(row.editablePriceText)
                    if (parsed != null && preset.theme.showPrice) {
                        persistPriceOverride(preset.article.slug, parsed, preset)
                    }
                    val mem = store.getMemory(preset.article.slug)
                    val designation = row.editableDesignationText.takeIf { it.isNotBlank() }
                        ?: defaultDesignationText(preset, mem)
                    val bitmap = buildPreviewBitmap(preset, parsed, designation)
                    val result = exporter.exportToGallery(bitmap, preset, channel)
                    if (result.isSuccess) {
                        when (channel) {
                            VisioProChannel.SOCIAL -> store.markSocialExport(preset.article.slug)
                            VisioProChannel.PRINT -> store.markPrintExport(preset.article.slug)
                        }
                        ok++
                    } else {
                        failed++
                    }
                    val keep = _ui.value.previewBitmap
                    if (bitmap !== keep && !bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
            rows.forEach { reloadPresetRow(it.preset.id) }
            _ui.update {
                it.copy(
                    isExporting = false,
                    checkedPresetIds = emptySet(),
                    exportMessage = when {
                        ok > 0 && failed > 0 ->
                            "$ok carte(s) → $folderLabel ($failed échec(s))"
                        ok > 0 -> "$ok carte(s) enregistrée(s) → $folderLabel"
                        else -> "Échec export — vérifiez la permission galerie"
                    },
                )
            }
        }
    }

    private fun loadPresets(category: VisioProCategory, channel: VisioProChannel) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val categoryCatalog = catalogService.resolveCategoryCatalog(category)
            val presets = VisioProPresetCatalog.presets(category, channel, categoryCatalog.defs)
            val memory = store.getAllMemory()
            catalogBySlug = categoryCatalog.defs.associate { def ->
                def.slug to def.catalogArticleId?.let { categoryCatalog.articlesById[it] }
            }
            val rayonConfig = importantRayonsConfig.value
            val rows = presets.map { preset -> toPresetUi(preset, memory, rayonConfig) }
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

    private fun schedulePreviewRefresh() {
        previewDebounceJob?.cancel()
        previewDebounceJob = viewModelScope.launch {
            delay(150)
            refreshSelectedPresetPreview()
        }
    }

    fun clearArticleSelection() {
        recyclePreviewBitmap()
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
        rayonConfig: ImportantRayonsConfig,
    ): VisioProPresetUi {
        val mem = memory[preset.article.slug]
        val catalog = catalogBySlug[preset.article.slug]
        val priceResult = priceResolver.resolveFromCatalog(preset, mem, catalog)
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
            editableDesignationText = defaultDesignationText(preset, mem),
            csvCatalogPrice = catalog?.price,
            csvBaselinePrice = priceResult.csvBaseline ?: catalog?.price,
            userOverrodePrice = priceResult.source == VisioProPriceSource.MANUAL,
            manualPriceChangedAt = mem?.manualPriceChangedAt?.takeIf { mem.manualPriceOverridden },
            previousCatalogPrice = catalog?.previousPrice?.takeIf {
                catalog.changeStatus == ArticleChangeStatus.PRICE_CHANGED.name ||
                    catalog.needsTicketUpdate
            },
            priceChangeGlow = catalog?.hasPriceChange() == true &&
                rayonConfig.includesRayon(catalog.rayon),
        )
    }

    private suspend fun resolveDesignationForRender(preset: VisioProPreset): String {
        if (preset.id == _ui.value.selectedPresetId && _ui.value.designationInput.isNotBlank()) {
            return _ui.value.designationInput.trim()
        }
        val mem = store.getMemory(preset.article.slug)
        return defaultDesignationText(preset, mem)
    }

    private fun defaultDesignationText(
        preset: VisioProPreset,
        memory: com.oasismall.oasisai.domain.visiopro.VisioProArticleMemory?,
    ): String =
        memory?.manualDesignation?.takeIf { it.isNotBlank() }
            ?: preset.article.labelAr?.takeIf { it.isNotBlank() }
            ?: preset.article.labelFr

    private suspend fun persistDesignation(preset: VisioProPreset) {
        val text = _ui.value.designationInput.trim()
        store.setManualDesignation(preset.article.slug, text.takeIf { it.isNotBlank() })
        val defaultRatio = defaultDesignationFontRatio(preset)
        val ratio = _ui.value.designationFontRatio
        if (abs(ratio - defaultRatio) < 0.0005f) {
            store.setPresetDesignationFontRatio(preset.article.slug, preset.id, null)
        } else {
            store.setPresetDesignationFontRatio(preset.article.slug, preset.id, ratio)
        }
    }

    private suspend fun persistCurrentEdits() {
        val preset = selectedPreset() ?: return
        val parsed = parsePrice(_ui.value.priceInput)
        if (parsed != null && preset.theme.showPrice) {
            persistPriceOverride(preset.article.slug, parsed, preset)
        }
        persistDesignation(preset)
    }

    private suspend fun persistPriceOverride(
        slug: String,
        parsed: Double?,
        preset: VisioProPreset,
    ) {
        val csvBaseline = catalogBySlug[slug]?.price
            ?: repository.findPriceForVisioProArticle(
                csvDesignation = preset.article.csvDesignation,
                barcodeSuffix = preset.article.barcodeSuffix,
                keywords = preset.article.designationKeywords,
            )
        when {
            parsed == null -> store.clearManualPriceOverride(slug)
            csvBaseline != null && VisioProPriceResolver.pricesMatch(parsed, csvBaseline) ->
                store.clearManualPriceOverride(slug)
            csvBaseline != null -> store.setManualPriceOverride(slug, parsed, csvBaseline)
            else -> store.setManualPriceOverride(slug, parsed, parsed)
        }
    }

    private suspend fun loadEditorFields(preset: VisioProPreset): EditorFields {
        val mem = store.getMemory(preset.article.slug)
        val catalog = catalogBySlug[preset.article.slug]
        val priceResult = priceResolver.resolveFromCatalog(preset, mem, catalog)
        val designationText = defaultDesignationText(preset, mem)
        val fontRatio = store.getPresetDesignationFontRatio(preset.article.slug, preset.id)
            ?: defaultDesignationFontRatio(preset)
        val priceText = priceResult.price?.let { PriceFormatter.formatNumber(it) } ?: ""
        return EditorFields(
            priceInput = priceText,
            designationInput = designationText,
            designationFontRatio = fontRatio,
            priceSource = priceResult.source,
            effectivePrice = priceResult.price,
            memory = mem,
        )
    }

    private data class EditorFields(
        val priceInput: String,
        val designationInput: String,
        val designationFontRatio: Float,
        val priceSource: VisioProPriceSource,
        val effectivePrice: Double?,
        val memory: com.oasismall.oasisai.domain.visiopro.VisioProArticleMemory,
    )

    private fun refreshSelectedPresetPreview() {
        viewModelScope.launch {
            val preset = selectedPreset() ?: return@launch
            val fields = loadEditorFields(preset)
            val priceText = _ui.value.priceInput.ifBlank { fields.priceInput }
            val effectivePrice = parsePrice(priceText) ?: fields.effectivePrice
            val designation = _ui.value.designationInput.ifBlank { fields.designationInput }
            val fontRatio = _ui.value.designationFontRatio
            renderAndUpdatePreview(preset, effectivePrice, designation, fontRatio, fields.memory)
        }
    }

    private suspend fun renderAndUpdatePreview(
        preset: VisioProPreset,
        effectivePrice: Double?,
        designation: String,
        fontRatio: Float,
        mem: com.oasismall.oasisai.domain.visiopro.VisioProArticleMemory,
    ) {
        val usesPhoto = preset.theme.templateId == "ail_social"
        val isPrint = preset.channel == VisioProChannel.PRINT
        val userPhoto = photoStore.loadBitmap(preset.article.slug, preset.channel)
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
            displayDesignation = designation,
            designationFontRatio = fontRatio,
        )
        val previous = _ui.value.previewBitmap
        if (previous != null && previous !== rendered && !previous.isRecycled) {
            previous.recycle()
        }
        _ui.update { it.copy(previewBitmap = rendered) }
    }

    private fun reloadPresetRow(presetId: String) {
        viewModelScope.launch {
            val memory = store.getAllMemory()
            val rayonConfig = importantRayonsConfig.value
            _ui.update { state ->
                state.copy(
                    presets = state.presets.map { row ->
                        if (row.preset.id == presetId) {
                            toPresetUi(row.preset, memory, rayonConfig)
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
            val fields = loadEditorFields(preset)
            val usesPhoto = preset.theme.templateId == "ail_social"
            val isFvPrint = preset.theme.templateId == "fv_print"
            val isPrint = preset.channel == VisioProChannel.PRINT
            renderAndUpdatePreview(
                preset = preset,
                effectivePrice = fields.effectivePrice,
                designation = fields.designationInput,
                fontRatio = fields.designationFontRatio,
                mem = fields.memory,
            )
            val queue = store.getPrintQueue()
            _ui.update {
                it.copy(
                    priceInput = fields.priceInput,
                    designationInput = fields.designationInput,
                    designationFontRatio = fields.designationFontRatio,
                    priceSource = fields.priceSource,
                    usesDailyPhoto = usesPhoto || isPrint,
                    hasArticlePhoto = photoStore.hasPhoto(preset.article.slug, preset.channel),
                    photoTakenAt = photoStore.photoModifiedAt(preset.article.slug, preset.channel),
                    isFvPrintQuad = isFvPrint,
                    printModified = fields.memory.printModified,
                    printQueueSize = queue.size,
                )
            }
        }
    }

    private fun recyclePreviewBitmap() {
        val previous = _ui.value.previewBitmap
        if (previous != null && !previous.isRecycled) {
            previous.recycle()
        }
    }

    private suspend fun buildPreviewBitmap(
        preset: VisioProPreset,
        parsed: Double?,
        displayDesignation: String? = null,
    ): android.graphics.Bitmap {
        val mem = store.getMemory(preset.article.slug)
        val catalog = catalogBySlug[preset.article.slug]
        val priceResult = priceResolver.resolveFromCatalog(preset, mem, catalog)
        val effectivePrice = parsed ?: priceResult.price
        val designation = displayDesignation?.takeIf { it.isNotBlank() }
            ?: resolveDesignationForRender(preset)
        val fontRatio = if (preset.id == _ui.value.selectedPresetId) {
            _ui.value.designationFontRatio
        } else {
            store.getPresetDesignationFontRatio(preset.article.slug, preset.id)
                ?: defaultDesignationFontRatio(preset)
        }
        val usesPhoto = preset.theme.templateId == "ail_social"
        val isPrint = preset.channel == VisioProChannel.PRINT
        val userPhoto = photoStore.loadBitmap(preset.article.slug, preset.channel)
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
        return renderFacade.render(
            preset,
            effectivePrice,
            articlePhoto,
            catalogPhoto,
            design,
            displayDesignation = designation,
            designationFontRatio = fontRatio,
        )
    }

    private suspend fun defaultDesignationFontRatio(preset: VisioProPreset): Float {
        val key = VisioProPresetDesignKey.from(preset.category, preset.channel)
        if (key != null) {
            return designStore.loadOrDefault(key).designationFontRatio
        }
        return if (preset.channel == VisioProChannel.SOCIAL) 0.11f else 0.13f
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
