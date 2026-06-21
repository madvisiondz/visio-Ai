package com.oasismall.oasisai.ui.screens.visiopro.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProRayonPools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisioProListEditorUiState(
    val category: VisioProCategory,
    val poolArticles: List<ArticleWithImage> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val orderedIds: List<Long> = emptyList(),
    val pendingIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val showOrderSheet: Boolean = false,
) {
    val filteredPool: List<ArticleWithImage>
        get() {
            val q = searchQuery.trim().lowercase()
            val base = if (q.isBlank()) poolArticles else {
                poolArticles.filter {
                    it.designation.lowercase().contains(q) || it.barcode.contains(q)
                }
            }
            val pending = pendingIds
            return base.sortedWith(
                compareBy<ArticleWithImage> { it.id !in pending }
                    .thenBy { it.designation.lowercase() },
            )
        }

    val orderedSelectedArticles: List<ArticleWithImage>
        get() {
            val byId = poolArticles.associateBy { it.id }
            return orderedIds.filter { it in selectedIds }.mapNotNull { byId[it] }
        }
}

class VisioProListEditorViewModel(
    private val category: VisioProCategory,
    private val catalogService: VisioProCatalogService,
) : ViewModel() {

    private val _ui = MutableStateFlow(VisioProListEditorUiState(category = category))
    val ui: StateFlow<VisioProListEditorUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, message = null) }
            val state = catalogService.livePoolState(category)
            val enabled = state.enabledIds
            val pending = state.pendingIds.toSet()
            _ui.update {
                it.copy(
                    poolArticles = state.pool,
                    orderedIds = enabled + state.pendingIds.filter { id -> id !in enabled },
                    selectedIds = enabled.toSet(),
                    pendingIds = pending,
                    isLoading = false,
                )
            }
        }
    }

    fun setSearchQuery(value: String) {
        _ui.update { it.copy(searchQuery = value) }
    }

    fun toggleArticle(articleId: Long, checked: Boolean) {
        _ui.update { state ->
            val selected = state.selectedIds.toMutableSet()
            val ordered = state.orderedIds.toMutableList()
            val pending = state.pendingIds.toMutableSet()
            if (checked) {
                selected.add(articleId)
                pending.remove(articleId)
                if (articleId !in ordered) ordered.add(articleId)
            } else {
                selected.remove(articleId)
                ordered.remove(articleId)
                pending.add(articleId)
            }
            state.copy(selectedIds = selected, orderedIds = ordered, pendingIds = pending)
        }
    }

    fun openOrderSheet() {
        _ui.update { it.copy(showOrderSheet = true) }
    }

    fun dismissOrderSheet() {
        _ui.update { it.copy(showOrderSheet = false) }
    }

    fun applyOrder(newOrder: List<Long>) {
        _ui.update { state ->
            val selected = state.selectedIds
            val tail = state.orderedIds.filter { it !in newOrder && it in selected }
            state.copy(
                orderedIds = newOrder.filter { it in selected } + tail,
                showOrderSheet = false,
            )
        }
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, message = null) }
            val state = _ui.value
            val enabled = state.orderedIds.filter { it in state.selectedIds }
            val pending = state.poolArticles.map { it.id }.filter { it !in state.selectedIds }
            catalogService.saveCategoryConfig(
                category = category,
                enabledIds = enabled,
                pendingIds = pending,
            )
            _ui.update {
                it.copy(
                    orderedIds = enabled + pending.filter { id -> id !in enabled },
                    selectedIds = enabled.toSet(),
                    pendingIds = pending.toSet(),
                    isSaving = false,
                    message = "${enabled.size} actifs · ${pending.size} en attente sur ${state.poolArticles.size} du rayon",
                )
            }
            onSaved()
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            catalogService.resetToDefaults(category)
            load()
            _ui.update { it.copy(message = "Liste par défaut restaurée") }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun poolHint(): String = VisioProRayonPools.poolHint(category)
}
