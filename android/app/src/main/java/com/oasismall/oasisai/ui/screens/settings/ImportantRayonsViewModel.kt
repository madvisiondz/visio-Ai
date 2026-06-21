package com.oasismall.oasisai.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.visiopro.VisioProRayonPools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportantRayonsUiState(
    val allRayons: List<String> = emptyList(),
    val selectedRayons: Set<String> = emptySet(),
    val configured: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
)

class ImportantRayonsViewModel(
    private val repository: OasisRepository,
) : ViewModel() {

    private val _localSelection = MutableStateFlow<Set<String>?>(null)

    val ui: StateFlow<ImportantRayonsUiState> = combine(
        repository.observeAllDistinctRayons(),
        repository.importantRayonsConfig,
        _localSelection,
    ) { allRayons, config, local ->
        val selected = local ?: config.selectedRayons.ifEmpty {
            defaultSuggestedRayons(allRayons)
        }
        ImportantRayonsUiState(
            allRayons = allRayons,
            selectedRayons = selected,
            configured = config.configured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportantRayonsUiState())

    fun toggleRayon(rayon: String, checked: Boolean) {
        val current = _localSelection.value ?: ui.value.selectedRayons
        _localSelection.value = if (checked) current + rayon else current - rayon
    }

    fun selectAll() {
        _localSelection.value = ui.value.allRayons.toSet()
    }

    fun selectVisioProDefaults() {
        _localSelection.value = defaultSuggestedRayons(ui.value.allRayons)
    }

    fun clearAll() {
        _localSelection.value = emptySet()
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val selected = _localSelection.value ?: ui.value.selectedRayons
            repository.saveImportantRayons(selected)
            _localSelection.value = null
            onSaved()
        }
    }

    private fun defaultSuggestedRayons(allRayons: List<String>): Set<String> {
        val targets = listOf(
            VisioProRayonPools.FRUITS_LEGUMES,
            VisioProRayonPools.BOUCHERIE,
            VisioProRayonPools.POISSONNERIE,
        )
        return allRayons.filter { rayon ->
            targets.any { target ->
                com.oasismall.oasisai.util.NameNormalizer.normalize(rayon) ==
                    com.oasismall.oasisai.util.NameNormalizer.normalize(target)
            }
        }.toSet()
    }
}
