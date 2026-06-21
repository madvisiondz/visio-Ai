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
import kotlinx.coroutines.flow.combine
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

    /** `null` = all rayons (global). */
    private val _selectedRayon = MutableStateFlow<String?>(null)
    val selectedRayon: StateFlow<String?> = _selectedRayon.asStateFlow()

    val rayons: StateFlow<List<String>> = repository.observeDistinctRayons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResult: StateFlow<HomeSearchResult> = combine(_query, _selectedRayon) { q, rayon ->
        q.trim() to rayon
    }
        .flatMapLatest { (q, rayon) ->
            if (q.isBlank() && rayon == null) {
                flowOf(HomeSearchResult())
            } else {
                repository.observeArticles(q, rayon).map { articles ->
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

    fun setSelectedRayon(rayon: String?) {
        _selectedRayon.value = rayon
    }

    fun addToShareCart(articleId: Long, variantBarcode: String? = null) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.HOME, variantBarcode)
        }
    }

    fun addToPhotoshootCart(articleId: Long, variantBarcode: String? = null) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.PHOTOSHOOT, CartSourceTags.HOME, variantBarcode)
        }
    }
}
