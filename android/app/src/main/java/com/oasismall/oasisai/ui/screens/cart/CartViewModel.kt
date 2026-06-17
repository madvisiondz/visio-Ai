package com.oasismall.oasisai.ui.screens.cart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.ExportShareHelper
import com.oasismall.oasisai.util.PngShareHelper
import com.oasismall.oasisai.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class CartViewModel(
    val cartType: CartType,
    private val repository: OasisRepository,
) : ViewModel() {
    val items = repository.observeCart(cartType)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPreselectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPreselectionIds: StateFlow<Set<Long>> = _selectedPreselectionIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun toggleSelection(preselectionId: Long) {
        _selectedPreselectionIds.value = _selectedPreselectionIds.value.let { selected ->
            if (preselectionId in selected) selected - preselectionId else selected + preselectionId
        }
    }

    fun selectAllShareable() {
        _selectedPreselectionIds.value = items.value
            .filter { !it.imagePath.isNullOrBlank() }
            .map { it.preselectionId }
            .toSet()
    }

    fun clearSelection() {
        _selectedPreselectionIds.value = emptySet()
    }

    fun remove(preselectionId: Long) {
        _selectedPreselectionIds.value = _selectedPreselectionIds.value - preselectionId
        viewModelScope.launch { repository.removeFromCart(preselectionId) }
    }

    fun clear() {
        _selectedPreselectionIds.value = emptySet()
        viewModelScope.launch { repository.clearCart(cartType) }
    }

    fun markSelectedSent() {
        val ids = items.value
            .filter { it.preselectionId in _selectedPreselectionIds.value }
            .map { it.articleId }
        viewModelScope.launch {
            repository.markProductImagesSent(ids)
            _selectedPreselectionIds.value = emptySet()
        }
    }

    fun shareableInCart(): List<PreselectionWithArticle> =
        items.value.filter { !it.imagePath.isNullOrBlank() }

    fun addAllToDesign() {
        addToDesign(shareableInCart())
    }

    fun addToDesign(selectedItems: List<PreselectionWithArticle>) {
        val shareable = selectedItems.filter { !it.imagePath.isNullOrBlank() }
        if (shareable.isEmpty()) {
            _message.value = "No PNG files with images to add."
            return
        }
        viewModelScope.launch {
            var added = 0
            shareable.forEach { item ->
                val variant = item.variantBarcode.takeIf { it.isNotEmpty() }
                if (!repository.isInCart(item.articleId, CartType.DESIGN, variant)) {
                    repository.addToCart(item.articleId, CartType.DESIGN, item.note, variant)
                    added++
                }
            }
            _selectedPreselectionIds.value = emptySet()
            _message.value = if (added > 0) {
                "Added $added article(s) to Design — open Design tab → Shelf labels."
            } else {
                "Already in Design queue."
            }
        }
    }

    fun shareAllPngFiles(context: Context) {
        sharePngFiles(context, shareableInCart())
    }

    fun shareSelectedPngFiles(context: Context, selectedItems: List<PreselectionWithArticle>) {
        sharePngFiles(context, selectedItems)
    }

    private fun sharePngFiles(context: Context, itemsToShare: List<PreselectionWithArticle>) {
        val shareableItems = itemsToShare.filter { !it.imagePath.isNullOrBlank() }
        if (shareableItems.isEmpty()) {
            _message.value = "No PNG files to share."
            return
        }
        _message.value = "Preparing ${shareableItems.size} PNG file(s) (all at once, no zip)…"
        viewModelScope.launch {
            runCatching {
                val summary = buildBulkShareSummary(shareableItems)
                withContext(Dispatchers.IO) {
                    ExportShareHelper.sharePngFiles(context, shareableItems, summary)
                }
                repository.markProductImagesSent(shareableItems.map { it.articleId })
                _selectedPreselectionIds.value = emptySet()
                val names = shareableItems.joinToString(", ") { PngShareHelper.targetFileName(it) }
                "Sharing ${shareableItems.size} file(s) as documents: $names. In Telegram they must appear as files (not photo preview)."
            }.onSuccess { msg ->
                _message.value = msg
            }.onFailure { err ->
                _message.value = "Share failed: ${err.javaClass.simpleName}: ${err.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(value: String) {
        _message.value = value
    }

    private fun buildBulkShareSummary(items: List<PreselectionWithArticle>): String {
        val header = "Oasis AI — ${items.size} product PNG(s). Metadata is embedded in each file.\n"
        val lines = items.mapIndexed { index, item ->
            buildList {
                add("${index + 1}. ${item.designation}")
                add("   Price: ${PriceFormatter.format(item.price)}")
                item.codeart?.takeIf { it.isNotBlank() }?.let { add("   Code: $it") }
                add("   Barcode: ${item.barcode}")
                item.category?.takeIf { it.isNotBlank() }?.let { add("   Rayon: $it") }
            }.joinToString("\n")
        }
        return header + lines.joinToString("\n\n")
    }
}
