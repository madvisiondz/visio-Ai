package com.oasismall.oasisai.ui.screens.visiopro.designer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignStore
import com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetHubCard(
    val key: VisioProPresetDesignKey,
    val isCustomized: Boolean,
    val modifiedAt: Long?,
)

data class VisioProDesignerHubUiState(
    val cards: List<PresetHubCard> = emptyList(),
    val customizedCount: Int = 0,
    val isLoading: Boolean = true,
)

class VisioProDesignerHubViewModel(
    private val designStore: VisioProDesignStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(VisioProDesignerHubUiState())
    val ui: StateFlow<VisioProDesignerHubUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val cards = VisioProPresetDesignKey.entries.map { key ->
                val saved = designStore.load(key)
                PresetHubCard(
                    key = key,
                    isCustomized = saved != null,
                    modifiedAt = saved?.modifiedAt,
                )
            }
            _ui.update {
                it.copy(
                    cards = cards,
                    customizedCount = cards.count { c -> c.isCustomized },
                    isLoading = false,
                )
            }
        }
    }

    fun cardsForCategory(category: VisioProCategory): List<PresetHubCard> =
        _ui.value.cards.filter { it.key.category == category }
}
