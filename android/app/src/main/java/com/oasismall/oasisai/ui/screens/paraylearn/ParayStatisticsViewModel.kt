package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParayStatisticsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: ParayStatisticsUiData? = null,
)

class ParayStatisticsViewModel(
    private val repository: ParayStatisticsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParayStatisticsUiState())
    val ui: StateFlow<ParayStatisticsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { repository.load() }
                .onSuccess { data ->
                    _ui.update { it.copy(loading = false, data = data) }
                }
                .onFailure { err ->
                    _ui.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load statistics")
                    }
                }
        }
    }
}
