package com.oasismall.oasisai.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CatalogFilter {
    ALL,
    NEEDS_TICKET,
    MISSING_IMAGE,
    NEW,
    PRICE_CHANGED,
}

class CatalogViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(CatalogFilter.ALL)
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles = _filter.flatMapLatest { filter ->
        when (filter) {
            CatalogFilter.ALL -> _query.flatMapLatest { repository.observeArticles(it) }
            CatalogFilter.NEEDS_TICKET -> repository.observeNeedsTicket()
            CatalogFilter.MISSING_IMAGE -> repository.observeMissingImagesLimited()
            CatalogFilter.NEW -> repository.observeNewArticles()
            CatalogFilter.PRICE_CHANGED -> repository.observePriceChanged()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        @OptIn(FlowPreview::class)
        _query
            .debounce(600)
            .map { it.trim() }
            .distinctUntilChanged()
            .onEach { repository.logSearchQuery(it) }
            .launchIn(viewModelScope)
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(filter: CatalogFilter) {
        _filter.value = filter
    }

    fun addToPreselection(articleId: Long) {
        viewModelScope.launch { repository.addToPreselection(articleId) }
    }
}
