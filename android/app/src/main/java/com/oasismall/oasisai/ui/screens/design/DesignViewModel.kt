package com.oasismall.oasisai.ui.screens.design

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.design.DesignCartExpand
import com.oasismall.oasisai.domain.design.ShelfA4Renderer
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.util.DesignPriceMessage
import com.oasismall.oasisai.util.ExportShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DesignStep { HOME, SHELF_LAYOUT, READY_PRINT }

class DesignViewModel(
    private val repository: OasisRepository,
    private val paray: ParayAgent,
) : ViewModel() {
    val items = repository.observeCart(CartType.DESIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneItems = repository.observeCart(CartType.DESIGN_DONE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val parayLearnedCount = MutableStateFlow(paray.learnedProductCount())
    val parayName: String get() = paray.name

    init {
        viewModelScope.launch {
            items.collect { queue -> paray.activateDesignSession(queue.size) }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _shelfPageIndex = MutableStateFlow(0)
    val shelfPageIndex: StateFlow<Int> = _shelfPageIndex.asStateFlow()

    private val _readyJpegPath = MutableStateFlow<String?>(null)
    val readyJpegPath: StateFlow<String?> = _readyJpegPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _step = MutableStateFlow(DesignStep.HOME)
    val step: StateFlow<DesignStep> = _step.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun remove(articleId: Long) {
        viewModelScope.launch { repository.removeFromCart(articleId, CartType.DESIGN) }
    }

    fun clearDesign() {
        viewModelScope.launch { repository.clearCart(CartType.DESIGN) }
    }

    fun incrementCopy(articleId: Long) {
        viewModelScope.launch { repository.incrementDesignCopyCount(articleId) }
    }

    fun decrementCopy(articleId: Long) {
        viewModelScope.launch { repository.decrementDesignCopyCount(articleId) }
    }

    fun pullUpFromDone(articleId: Long) {
        viewModelScope.launch {
            repository.restoreDesignItemFromDone(articleId)
            _message.value = "Moved back to print queue."
        }
    }

    fun sendCartInfo(context: Context) {
        val cart = items.value
        if (cart.isEmpty()) {
            _message.value = "Design queue is empty."
            return
        }
        _message.value = null
        ExportShareHelper.shareDesignCartInfo(context, cart)
        viewModelScope.launch {
            val orderedIds = cart.sortedBy { it.sortOrder }.map { it.articleId }
            repository.moveDesignItemsToDone(orderedIds)
            _message.value = "Sent ${cart.size} article(s) — moved to Done."
        }
    }

    fun importCheckedPrices(text: String) {
        val allQueued = items.value + doneItems.value
        if (allQueued.isEmpty()) {
            _message.value = "No articles in Design or Done."
            return
        }
        val parsed = DesignPriceMessage.parse(text)
        if (parsed.isEmpty()) {
            _message.value = "Could not read prices — paste the message sent back from PC (same format as Send info)."
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
            _message.value = when {
                updated == 0 -> "No matching barcodes — check the pasted text."
                notInQueue > 0 -> "Updated $updated price(s) ($notInQueue barcode(s) not in queue)."
                else -> "Updated $updated price(s) in catalog and Design."
            }
        }
    }

    fun startShelfPrint(context: Context) {
        _shelfPageIndex.value = 0
        _readyJpegPath.value = null
        generateShelfJpeg(context, 0)
    }

    fun backToHome() {
        _step.value = DesignStep.HOME
        _readyJpegPath.value = null
    }

    fun printPage(context: Context, pageIndex: Int) {
        generateShelfJpeg(context, pageIndex)
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

    fun generateShelfJpeg(context: Context, pageIndex: Int) {
        val expanded = shareableExpanded()
        if (expanded.isEmpty()) {
            _message.value = "Add articles from To share first."
            return
        }
        viewModelScope.launch {
            _isRendering.value = true
            runCatching {
                withContext(Dispatchers.Default) {
                    val dir = File(context.filesDir, "exports").also { it.mkdirs() }
                    ShelfA4Renderer.renderPage(expanded, pageIndex, dir, paray)
                }
            }.onSuccess { file ->
                val pageSlots = itemsForPage(pageIndex)
                val pageItems = pageSlots.distinctBy { it.articleId }
                repository.recordDesignShelfPrint(pageIndex, file.absolutePath, pageItems)
                _shelfPageIndex.value = pageIndex
                _readyJpegPath.value = file.absolutePath
                _step.value = DesignStep.READY_PRINT
                parayLearnedCount.value = paray.learnedProductCount()
                _message.value = "A4 JPEG ready — ${file.name}"
            }.onFailure { e ->
                _message.value = e.message ?: "Export failed"
            }
            _isRendering.value = false
        }
    }

    fun onPrintShared(pageIndex: Int) {
        viewModelScope.launch {
            val pageIdSet = itemsForPage(pageIndex).map { it.articleId }.toSet()
            val orderedIds = items.value
                .filter { it.articleId in pageIdSet }
                .sortedBy { it.sortOrder }
                .map { it.articleId }
            repository.moveDesignItemsToDone(orderedIds)
            _message.value = "Printed page moved to Done (${orderedIds.size} article(s))."
        }
    }

    fun clearReadyPreview() {
        _readyJpegPath.value = null
    }

    fun updatePrice(articleId: Long, priceText: String) {
        val cleaned = priceText.trim().replace(" ", "").replace(",", ".")
        val price = cleaned.toDoubleOrNull()
        if (price == null || price < 0) {
            _message.value = "Invalid price — use numbers only (e.g. 240 or 1200)."
            return
        }
        viewModelScope.launch {
            if (repository.updateArticlePrice(articleId, price)) {
                _message.value = null
            } else {
                _message.value = "Could not save price."
            }
        }
    }

    override fun onCleared() {
        paray.deactivateDesignSession()
        super.onCleared()
    }
}
