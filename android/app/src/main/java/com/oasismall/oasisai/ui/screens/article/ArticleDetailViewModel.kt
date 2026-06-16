package com.oasismall.oasisai.ui.screens.article

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

class ArticleDetailViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    private val _article = MutableStateFlow<ArticleWithImage?>(null)
    val article: StateFlow<ArticleWithImage?> = _article.asStateFlow()

    fun load(articleId: Long) {
        viewModelScope.launch {
            _article.value = repository.getArticleWithImageById(articleId)
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

    fun markTicketVerified() {
        viewModelScope.launch {
            val id = _article.value?.id ?: return@launch
            repository.markTicketVerified(id)
            _article.value = repository.getArticleWithImageById(id)
        }
    }
}
