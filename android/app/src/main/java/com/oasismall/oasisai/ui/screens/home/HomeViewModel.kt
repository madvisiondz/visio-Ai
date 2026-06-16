package com.oasismall.oasisai.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.hasAppGalleryImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeSearchResult(
    val withImage: List<ArticleWithImage> = emptyList(),
    val withoutImage: List<ArticleWithImage> = emptyList(),
) {
    val total: Int get() = withImage.size + withoutImage.size
}

class HomeViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResult: StateFlow<HomeSearchResult> = _query
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(HomeSearchResult())
            } else {
                repository.observeArticles(q).map { articles ->
                    val (withImage, withoutImage) = articles.partition { it.hasAppGalleryImage() }
                    HomeSearchResult(withImage = withImage, withoutImage = withoutImage)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSearchResult())

    val shareCartCount = repository.observeCartCount(CartType.SHARE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val photoshootCartCount = repository.observeCartCount(CartType.PHOTOSHOOT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        @OptIn(FlowPreview::class)
        _query
            .debounce(900)
            .map { it.trim() }
            .distinctUntilChanged()
            .onEach { repository.logSearchQuery(it) }
            .launchIn(viewModelScope)
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun addToShareCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.HOME)
        }
    }

    fun addToPhotoshootCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.PHOTOSHOOT, CartSourceTags.HOME)
        }
    }
}
