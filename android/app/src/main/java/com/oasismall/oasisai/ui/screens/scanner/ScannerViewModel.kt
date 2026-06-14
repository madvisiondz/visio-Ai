package com.oasismall.oasisai.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.CartType
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
        val article = repository.getArticleWithImageByBarcode(trimmed)
        if (article == null) {
            _notFound.value = true
            _result.value = null
            repository.logBarcodeSearch(trimmed, null)
            return
        }
        _notFound.value = false
        _result.value = article
        repository.logBarcodeSearch(trimmed, article.id)
    }

    fun reset() {
        _result.value = null
        _notFound.value = false
        _lastScannedBarcode.value = null
        debounceBarcode = null
        debounceTimeMs = 0L
    }

    fun addToShareCart(article: ArticleWithImage) {
        if (!article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.SHARE, CartSourceTags.SCANNER)
        }
    }

    fun markTicketVerified(articleId: Long) {
        viewModelScope.launch {
            repository.markTicketVerified(articleId)
            _result.value = repository.getArticleWithImageById(articleId)
        }
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 2_000L
    }
}
