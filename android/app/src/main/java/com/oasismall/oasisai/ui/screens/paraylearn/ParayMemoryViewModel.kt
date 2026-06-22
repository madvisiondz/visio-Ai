package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParayMemoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<ParayMemoryEntry> = emptyList(),
    val filter: ParayMemoryFilter = ParayMemoryFilter.ALL,
    val searchQuery: String = "",
) {
    val filteredEntries: List<ParayMemoryEntry>
        get() {
            val q = searchQuery.trim().lowercase()
            return entries
                .filter { entry ->
                    when (filter) {
                        ParayMemoryFilter.ALL -> true
                        ParayMemoryFilter.LEARNED -> entry.status == ParayLearnStatus.LEARNED
                        ParayMemoryFilter.PARTIAL -> entry.status == ParayLearnStatus.PARTIALLY_LEARNED
                        ParayMemoryFilter.PENDING -> entry.status == ParayLearnStatus.NOT_LEARNED
                    }
                }
                .filter { entry ->
                    if (q.isEmpty()) true
                    else entry.barcode.lowercase().contains(q) ||
                        entry.designation.lowercase().contains(q) ||
                        entry.brand?.lowercase()?.contains(q) == true
                }
        }

    val learnedCount: Int get() = entries.count { it.status == ParayLearnStatus.LEARNED }
    val partialCount: Int get() = entries.count { it.status == ParayLearnStatus.PARTIALLY_LEARNED }
    val pendingCount: Int get() = entries.count { it.status == ParayLearnStatus.NOT_LEARNED }
}

class ParayMemoryViewModel(
    private val repository: ParayMemoryRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParayMemoryUiState())
    val ui: StateFlow<ParayMemoryUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { repository.loadAll() }
                .onSuccess { list ->
                    _ui.update { it.copy(loading = false, entries = list) }
                }
                .onFailure { err ->
                    _ui.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load memory")
                    }
                }
        }
    }

    fun setFilter(filter: ParayMemoryFilter) {
        _ui.update { it.copy(filter = filter) }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
    }
}
