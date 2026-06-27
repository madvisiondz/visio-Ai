package com.oasismall.oasisai.ui.screens.article

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.ArticlePanelMeta
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.GalleryPngAssignService
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.hasAppGalleryImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArticleDetailViewModel(
    private val repository: OasisRepository,
    private val galleryPngAssign: GalleryPngAssignService,
) : ViewModel() {
    private val _article = MutableStateFlow<ArticleWithImage?>(null)
    val article: StateFlow<ArticleWithImage?> = _article.asStateFlow()

    private val _meta = MutableStateFlow<ArticlePanelMeta?>(null)
    val meta: StateFlow<ArticlePanelMeta?> = _meta.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load(articleId: Long) {
        viewModelScope.launch {
            _article.value = repository.getArticleWithImageById(articleId)
            _meta.value = repository.getArticlePanelMeta(articleId)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun assignPng(uri: Uri, articleId: Long) {
        viewModelScope.launch {
            galleryPngAssign.assignPngToArticle(uri, articleId, subBarcode = null, cartType = null)
                .fold(
                    onSuccess = { msg ->
                        _message.value = msg
                        load(articleId)
                    },
                    onFailure = { e -> _message.value = e.message ?: "Could not assign PNG" },
                )
        }
    }

    fun addToShareCart() {
        val article = _article.value ?: return
        if (!article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.SHARE, CartSourceTags.ARTICLE)
        }
    }

    fun addToPhotoshootCart() {
        val article = _article.value ?: return
        if (article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.PHOTOSHOOT, CartSourceTags.ARTICLE)
        }
    }

    fun addToDesignCart() {
        val article = _article.value ?: return
        if (!article.hasAppGalleryImage()) return
        viewModelScope.launch {
            repository.addToCart(article.id, CartType.DESIGN, CartSourceTags.ARTICLE)
        }
    }

    fun markTicketVerified() {
        viewModelScope.launch {
            val id = _article.value?.id ?: return@launch
            repository.markTicketVerified(id)
            load(id)
        }
    }

    fun removeSubBarcode(barcode: String) {
        val articleId = _article.value?.id ?: return
        viewModelScope.launch {
            repository.unlinkAlternateBarcode(articleId, barcode)
            load(articleId)
        }
    }
}
