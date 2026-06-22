package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayLearnSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParayLearnSettingsUiState(
    val settings: ParayLearnSettings = ParayLearnSettings.factoryDefaults(),
    val loading: Boolean = true,
)

class ParayLearnSettingsViewModel(
    private val paray: ParayAgent,
) : ViewModel() {
    private val store = paray.learnSettingsStore
    private val _ui = MutableStateFlow(ParayLearnSettingsUiState())
    val ui: StateFlow<ParayLearnSettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.get()
            store.settings.collect { settings ->
                _ui.value = ParayLearnSettingsUiState(settings = settings, loading = false)
            }
        }
    }

    fun updateFront(value: Float) {
        persist(_ui.value.settings.copy(frontConfirmationThreshold = value))
    }

    fun updateSide(value: Float) {
        persist(_ui.value.settings.copy(sideCaptureThreshold = value))
    }

    fun updateBack(value: Float) {
        persist(_ui.value.settings.copy(backCaptureThreshold = value))
    }

    fun resetFront() {
        val defaults = ParayLearnSettings.factoryDefaults()
        persist(_ui.value.settings.copy(frontConfirmationThreshold = defaults.frontConfirmationThreshold))
    }

    fun resetSide() {
        val defaults = ParayLearnSettings.factoryDefaults()
        persist(_ui.value.settings.copy(sideCaptureThreshold = defaults.sideCaptureThreshold))
    }

    fun resetBack() {
        val defaults = ParayLearnSettings.factoryDefaults()
        persist(_ui.value.settings.copy(backCaptureThreshold = defaults.backCaptureThreshold))
    }

    fun resetAll() {
        persist(ParayLearnSettings.factoryDefaults())
    }

    private fun persist(settings: ParayLearnSettings) {
        viewModelScope.launch {
            store.save(settings)
        }
    }
}
