package com.oasismall.oasisai.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.ArticlePanelMeta
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.hasAppGalleryImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    private val _result = MutableStateFlow<ArticleWithImage?>(null)
    val result: StateFlow<ArticleWithImage?> = _result.asStateFlow()

    private val _panelMeta = MutableStateFlow<ArticlePanelMeta?>(null)
    val panelMeta: StateFlow<ArticlePanelMeta?> = _panelMeta.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val _linkedViaAlternate = MutableStateFlow(false)
    val linkedViaAlternate: StateFlow<Boolean> = _linkedViaAlternate.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _lastScannedBarcode = MutableStateFlow<String?>(null)
    val lastScannedBarcode: StateFlow<String?> = _lastScannedBarcode.asStateFlow()

    private var debounceBarcode: String? = null
    private var debounceTimeMs = 0L

    fun onBarcodeScanned(barcode: String, fromCamera: Boolean = false) {
        viewModelScope.launch {
            val trimmed = barcode.trim()
            if (trimmed.isEmpty()) return@launch

            if (fromCamera) {
                val now = System.currentTimeMillis()
                if (trimmed == debounceBarcode && now - debounceTimeMs < SCAN_DEBOUNCE_MS) return@launch
                debounceBarcode = trimmed
                debounceTimeMs = now
            }

            _lastScannedBarcode.value = trimmed
            lookup(trimmed)
        }
    }

    private suspend fun lookup(trimmed: String) {
        val resolved = repository.resolveScannedBarcode(trimmed)
        val article = resolved?.article
        if (article == null) {
            _notFound.value = true
            _result.value = null
            _panelMeta.value = null
            _scannedBarcode.value = trimmed
            _linkedViaAlternate.value = false
            repository.logBarcodeSearch(trimmed, null)
            return
        }
        _notFound.value = false
        _result.value = article
        _scannedBarcode.value = trimmed
        _linkedViaAlternate.value = resolved?.let { !it.primary } == true
        _panelMeta.value = repository.getArticlePanelMeta(article.id)
        repository.logBarcodeSearch(trimmed, article.id)
    }

    fun reset() {
        _result.value = null
        _panelMeta.value = null
        _scannedBarcode.value = null
        _linkedViaAlternate.value = false
        _notFound.value = false
        _lastScannedBarcode.value = null
        debounceBarcode = null
        debounceTimeMs = 0L
    }

    fun addToShareCart(article: ArticleWithImage) {
        if (!article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.SHARE, CartSourceTags.SCANNER, article.barcode)
        }
    }

    fun addToPhotoshootCart(article: ArticleWithImage) {
        if (article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.PHOTOSHOOT, CartSourceTags.SCANNER, article.barcode)
        }
    }

    fun addToDesignCart(article: ArticleWithImage) {
        if (!article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.DESIGN, CartSourceTags.SCANNER, article.barcode)
        }
    }

    fun removeSubBarcode(barcode: String) {
        val articleId = _result.value?.id ?: return
        viewModelScope.launch {
            repository.unlinkAlternateBarcode(articleId, barcode)
            _panelMeta.value = repository.getArticlePanelMeta(articleId)
        }
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 2_000L
    }
}
