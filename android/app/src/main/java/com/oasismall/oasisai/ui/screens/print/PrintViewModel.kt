package com.oasismall.oasisai.ui.screens.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.entity.PrintTemplateEntity
import com.oasismall.oasisai.domain.PrintGenerationResult
import com.oasismall.oasisai.domain.PrintGenerator
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrintUiState(
    val isGenerating: Boolean = false,
    val lastResult: PrintGenerationResult? = null,
    val isPromo: Boolean = false,
    val campaignName: String = "",
    val promoDays: Int = 7,
)

class PrintViewModel(
    private val repository: OasisRepository,
    private val printGenerator: PrintGenerator,
) : ViewModel() {
    val templates = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preselectionCount = repository.observePreselectionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(PrintUiState())
    val uiState: StateFlow<PrintUiState> = _uiState.asStateFlow()

    fun setPromo(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isPromo = enabled)
    }

    fun setCampaignName(name: String) {
        _uiState.value = _uiState.value.copy(campaignName = name)
    }

    fun setPromoDays(days: Int) {
        _uiState.value = _uiState.value.copy(promoDays = days.coerceIn(1, 90))
    }

    fun generate(template: PrintTemplateEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true)
            val items = repository.observePreselection().first()
            val now = System.currentTimeMillis()
            val promoEnd = now + _uiState.value.promoDays * 86_400_000L
            val result = printGenerator.generateFromPreselection(
                template = template,
                items = items,
                isPromo = _uiState.value.isPromo,
                promoStart = if (_uiState.value.isPromo) now else null,
                promoEnd = if (_uiState.value.isPromo) promoEnd else null,
                campaignName = _uiState.value.campaignName.ifBlank { null },
            )
            _uiState.value = _uiState.value.copy(isGenerating = false, lastResult = result)
        }
    }
}
