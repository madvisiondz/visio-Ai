package com.oasismall.oasisai.ui.screens.visiopro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisioProHomeViewModel(
    private val catalogService: VisioProCatalogService,
) : ViewModel() {

    private val _counts = MutableStateFlow<Map<VisioProCategory, Int>>(emptyMap())
    val counts: StateFlow<Map<VisioProCategory, Int>> = _counts.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _counts.value = VisioProCategory.entries.associateWith { catalogService.countForCategory(it) }
        }
    }
}
