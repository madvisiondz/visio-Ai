package com.oasismall.oasisai.ui.screens.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.model.PrintBatchStatus
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.design.DesignBatchItemUi
import com.oasismall.oasisai.domain.design.DesignCartExpand
import com.oasismall.oasisai.domain.design.ShelfA4Renderer
import com.oasismall.oasisai.domain.layoutagent.LayoutFitAgent
import com.oasismall.oasisai.util.DesignPriceMessage
import com.oasismall.oasisai.util.ExportShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DesignStep { HOME, READY_PRINT, BATCH_DETAIL }

enum class DesignHomeTab { QUEUE, HISTORY }

data class DesignBatchDetailState(
    val batch: PrintBatchEntity? = null,
    val items: List<DesignBatchItemUi> = emptyList(),
)

class DesignViewModel(
    private val repository: OasisRepository,
    private val layoutFitAgent: LayoutFitAgent,
) : ViewModel() {
    val items = repository.observeCart(CartType.DESIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneItems = repository.observeCart(CartType.DESIGN_DONE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val printHistory = repository.observeDesignShelfPrints(limit = 100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            items.collect { queue -> layoutFitAgent.activateDesignSession(queue.size) }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _homeTab = MutableStateFlow(DesignHomeTab.QUEUE)
    val homeTab: StateFlow<DesignHomeTab> = _homeTab.asStateFlow()

    private val _shelfPageIndex = MutableStateFlow(0)
    val shelfPageIndex: StateFlow<Int> = _shelfPageIndex.asStateFlow()

    private val _readyJpegPath = MutableStateFlow<String?>(null)
    val readyJpegPath: StateFlow<String?> = _readyJpegPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _step = MutableStateFlow(DesignStep.HOME)
    val step: StateFlow<DesignStep> = _step.asStateFlow()

    private val _batchDetail = MutableStateFlow(DesignBatchDetailState())
    val batchDetail: StateFlow<DesignBatchDetailState> = _batchDetail.asStateFlow()

    private var lastAutoSharedPath: String? = null
    private var renderJob: Job? = null

    fun clearMessage() {
        _message.value = null
    }

    fun setHomeTab(tab: DesignHomeTab) {
        _homeTab.value = tab
    }

    fun remove(preselectionId: Long) {
        viewModelScope.launch { repository.removeFromCart(preselectionId) }
    }

    fun clearDesign() {
        viewModelScope.launch { repository.clearCart(CartType.DESIGN) }
    }

    fun incrementCopy(preselectionId: Long) {
        viewModelScope.launch { repository.incrementDesignCopyCount(preselectionId) }
    }

    fun decrementCopy(preselectionId: Long) {
        viewModelScope.launch { repository.decrementDesignCopyCount(preselectionId) }
    }

    fun pullUpFromDone(preselectionId: Long) {
        viewModelScope.launch {
            repository.restoreDesignItemFromDone(preselectionId)
            _message.value = "Remis dans la file d'impression."
        }
    }

    fun sendCartInfo(context: android.content.Context) {
        val cart = items.value
        if (cart.isEmpty()) {
            _message.value = "File d'impression vide."
            return
        }
        _message.value = null
        ExportShareHelper.shareDesignCartInfo(context, cart)
        _message.value = "Info envoyée pour ${cart.size} article(s)."
    }

    fun markQueueAsSent() {
        val cart = items.value
        if (cart.isEmpty()) {
            _message.value = "Rien à marquer."
            return
        }
        viewModelScope.launch {
            val orderedIds = cart.sortedBy { it.sortOrder }.map { it.preselectionId }
            repository.moveDesignItemsToDone(orderedIds)
            _message.value = "${orderedIds.size} article(s) marqué(s) envoyé(s)."
        }
    }

    fun markPageAsPrinted(pageIndex: Int) {
        viewModelScope.launch {
            val pageIdSet = itemsForPage(pageIndex).map { it.preselectionId }.toSet()
            val orderedIds = items.value
                .filter { it.preselectionId in pageIdSet }
                .sortedBy { it.sortOrder }
                .map { it.preselectionId }
            if (orderedIds.isEmpty()) {
                _message.value = "Aucun article sur cette page."
                return@launch
            }
            repository.moveDesignItemsToDone(orderedIds)
            _message.value = "${orderedIds.size} article(s) marqué(s) imprimé(s)."
            backToHome()
        }
    }

    fun removeFromDone(preselectionId: Long) {
        viewModelScope.launch {
            repository.removeFromCart(preselectionId)
            _message.value = "Retiré de Done."
        }
    }

    fun importCheckedPrices(text: String) {
        val allQueued = items.value + doneItems.value
        if (allQueued.isEmpty()) {
            _message.value = "Aucun article dans Design ou Done."
            return
        }
        val parsed = DesignPriceMessage.parse(text)
        if (parsed.isEmpty()) {
            _message.value = "Format non reconnu — collez le message renvoyé par le PC."
            return
        }
        viewModelScope.launch {
            val byBarcode = allQueued.associateBy { it.barcode.trim() }
            var updated = 0
            var notInQueue = 0
            parsed.forEach { line ->
                val item = byBarcode[line.barcode]
                if (item == null) {
                    notInQueue++
                    return@forEach
                }
                if (repository.updateArticlePrice(item.articleId, line.price)) {
                    updated++
                }
            }
            refreshOpenBatchDetail()
            _message.value = when {
                updated == 0 -> "Aucun code-barres correspondant."
                notInQueue > 0 -> "$updated prix mis à jour ($notInQueue hors file)."
                else -> "$updated prix mis à jour dans le catalogue et Design."
            }
        }
    }

    fun openBatchDetail(batchId: Long) {
        viewModelScope.launch {
            val batch = repository.getPrintBatch(batchId) ?: return@launch
            val items = repository.enrichDesignBatchItems(repository.getPrintBatchItems(batchId))
            _batchDetail.value = DesignBatchDetailState(batch = batch, items = items)
            _step.value = DesignStep.BATCH_DETAIL
        }
    }

    fun closeBatchDetail() {
        _step.value = DesignStep.HOME
        _homeTab.value = DesignHomeTab.HISTORY
    }

    fun toggleExcludeBatchItem(batchItemId: Long) {
        _batchDetail.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.batchItemId == batchItemId) item.copy(excludedFromReprint = !item.excludedFromReprint)
                    else item
                },
            )
        }
    }

    fun sendBatchItemToDesign(batchItemId: Long) {
        viewModelScope.launch {
            val item = _batchDetail.value.items.firstOrNull { it.batchItemId == batchItemId } ?: return@launch
            repository.restoreBatchItemToDesign(item)
            _message.value = "${item.designation} → Design"
        }
    }

    fun sendBatchItemToShare(batchItemId: Long) {
        viewModelScope.launch {
            val item = _batchDetail.value.items.firstOrNull { it.batchItemId == batchItemId } ?: return@launch
            repository.restoreBatchItemToShare(item)
            _message.value = "${item.designation} → To share"
        }
    }

    fun loadBatchToDesignQueue() {
        viewModelScope.launch {
            val active = _batchDetail.value.items.filter { !it.excludedFromReprint }
            if (active.isEmpty()) {
                _message.value = "Aucun article sélectionné."
                return@launch
            }
            repository.clearCart(CartType.DESIGN)
            active.forEach { repository.restoreBatchItemToDesign(it) }
            _message.value = "${active.size} article(s) chargé(s) dans Design."
            _step.value = DesignStep.HOME
            _homeTab.value = DesignHomeTab.QUEUE
        }
    }

    fun reprintFromBatchDetail(context: android.content.Context) {
        val active = _batchDetail.value.items.filter { !it.excludedFromReprint }
        if (active.isEmpty()) {
            _message.value = "Sélectionnez au moins un article."
            return
        }
        val preselections = active.mapIndexed { index, item -> item.toPreselection(index) }
        renderAndOpenPrint(
            context = context,
            expanded = DesignCartExpand.expandCopies(preselections),
            pageIndex = 0,
            recordBatch = true,
            batchSourceItems = preselections,
        )
    }

    fun shareBatchExportFile(context: android.content.Context) {
        val path = _batchDetail.value.batch?.exportPath ?: _readyJpegPath.value
        if (path.isNullOrBlank()) {
            _message.value = "Fichier introuvable."
            return
        }
        val file = File(path)
        if (!file.exists()) {
            _message.value = "Fichier supprimé : ${file.name}"
            return
        }
        ExportShareHelper.shareJpegAsFile(context, file)
    }

    private suspend fun refreshOpenBatchDetail() {
        val batchId = _batchDetail.value.batch?.id ?: return
        val batch = repository.getPrintBatch(batchId) ?: return
        val excluded = _batchDetail.value.items.associate { it.batchItemId to it.excludedFromReprint }
        val items = repository.enrichDesignBatchItems(repository.getPrintBatchItems(batchId))
            .map { it.copy(excludedFromReprint = excluded[it.batchItemId] == true) }
        _batchDetail.value = DesignBatchDetailState(batch, items)
    }

    fun startShelfPrint(context: android.content.Context) {
        _shelfPageIndex.value = 0
        _readyJpegPath.value = null
        lastAutoSharedPath = null
        renderAndOpenPrint(context, shareableExpanded(), pageIndex = 0, recordBatch = true)
    }

    fun backToHome() {
        _step.value = DesignStep.HOME
        _readyJpegPath.value = null
    }

    fun printPage(context: android.content.Context, pageIndex: Int) {
        renderAndOpenPrint(context, shareableExpanded(), pageIndex, recordBatch = true)
    }

    fun setShelfPage(index: Int) {
        _shelfPageIndex.value = index.coerceAtLeast(0)
    }

    fun shareableExpanded(): List<PreselectionWithArticle> =
        DesignCartExpand.expandCopies(shareableItems())

    fun shareableItems(): List<PreselectionWithArticle> =
        items.value.filter { !it.imagePath.isNullOrBlank() }

    fun pageCount(): Int = ShelfA4Renderer.pageCount(shareableExpanded().size)

    fun itemsForPage(pageIndex: Int): List<PreselectionWithArticle> =
        shareableExpanded()
            .drop(pageIndex * ShelfA4Renderer.CAPACITY)
            .take(ShelfA4Renderer.CAPACITY)

    private fun renderAndOpenPrint(
        context: android.content.Context,
        expanded: List<PreselectionWithArticle>,
        pageIndex: Int,
        recordBatch: Boolean,
        batchSourceItems: List<PreselectionWithArticle>? = null,
    ) {
        if (expanded.isEmpty()) {
            _message.value = "Ajoutez des articles depuis To share."
            return
        }
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _isRendering.value = true
            runCatching {
                withContext(Dispatchers.Default) {
                    val dir = File(context.filesDir, "exports").also { it.mkdirs() }
                    ShelfA4Renderer.renderPage(expanded, pageIndex, dir, layoutFitAgent)
                }
            }.onSuccess { file ->
                if (recordBatch) {
                    val pagePreselections = batchSourceItems ?: items.value
                        .filter { cartRow ->
                            expanded.any { slot -> slot.preselectionId == cartRow.preselectionId }
                        }
                        .ifEmpty { expanded.distinctBy { it.preselectionId } }
                        .distinctBy { it.preselectionId }
                        .sortedBy { it.sortOrder }
                    val batchId = repository.recordDesignShelfPrint(pageIndex, file.absolutePath, pagePreselections)
                    if (batchId > 0) {
                        repository.updatePrintBatchStatus(batchId, PrintBatchStatus.PRINTED.name)
                    }
                }
                _shelfPageIndex.value = pageIndex
                _readyJpegPath.value = file.absolutePath
                _step.value = DesignStep.READY_PRINT
                _message.value = "Fichier ${file.parentFile?.name}/${file.name}"
                if (lastAutoSharedPath != file.absolutePath) {
                    ExportShareHelper.shareJpegAsFile(context, file)
                    lastAutoSharedPath = file.absolutePath
                }
            }.onFailure { e ->
                _message.value = e.message ?: "Échec export"
            }
            _isRendering.value = false
        }
    }

    fun shareReadyFile(context: android.content.Context) {
        val path = _readyJpegPath.value ?: return
        val file = File(path)
        if (file.exists()) ExportShareHelper.shareJpegAsFile(context, file)
    }

    fun updatePrice(articleId: Long, priceText: String) {
        val cleaned = priceText.trim().replace(" ", "").replace(",", ".")
        val price = cleaned.toDoubleOrNull()
        if (price == null || price < 0) {
            _message.value = "Prix invalide."
            return
        }
        viewModelScope.launch {
            if (repository.updateArticlePrice(articleId, price)) {
                _message.value = null
                refreshOpenBatchDetail()
            } else {
                _message.value = "Impossible d'enregistrer le prix."
            }
        }
    }

    fun setPromoTicket(preselectionId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setDesignPromoTicket(preselectionId, enabled)
        }
    }

    fun updatePromoPrices(preselectionId: Long, promoText: String, originalText: String) {
        val promo = promoText.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()
        val original = originalText.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()
        if (promo == null || original == null || promo < 0 || original < 0) {
            _message.value = "Prix promo invalides."
            return
        }
        viewModelScope.launch {
            repository.updateDesignPromoPrices(preselectionId, promo, original)
        }
    }

    override fun onCleared() {
        layoutFitAgent.deactivateDesignSession()
        super.onCleared()
    }
}

private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
