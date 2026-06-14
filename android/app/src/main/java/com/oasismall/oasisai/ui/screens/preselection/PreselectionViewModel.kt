package com.oasismall.oasisai.ui.screens.preselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreselectionViewModel(
    private val repository: OasisRepository,
) : ViewModel() {
    val items = repository.observePreselection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(articleId: Long) {
        viewModelScope.launch { repository.removeFromPreselection(articleId) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearPreselection() }
    }
}
