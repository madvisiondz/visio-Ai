package com.oasismall.oasisai.ui.screens.parayhome

import androidx.lifecycle.ViewModel
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayFolderEntry
import com.oasismall.oasisai.domain.paray.ParayManifest
import com.oasismall.oasisai.domain.paray.ParayNeuralSnapshot
import com.oasismall.oasisai.domain.paray.ParayOfficeLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParayHomeUiState(
    val manifest: ParayManifest? = null,
    val office: ParayOfficeLink? = null,
    val neural: ParayNeuralSnapshot? = null,
    val folders: List<ParayFolderEntry> = emptyList(),
    val barcodePatterns: Int = 0,
)

class ParayHomeViewModel(
    private val paray: ParayAgent,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParayHomeUiState())
    val ui: StateFlow<ParayHomeUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.value = ParayHomeUiState(
            manifest = paray.homeManifest(),
            office = paray.officeLink(),
            neural = paray.buildNeuralSnapshot(),
            folders = paray.homeFolders(),
            barcodePatterns = paray.learnedBarcodePatterns(),
        )
    }

    fun recordOfficeVisit(workplace: String) {
        paray.goToOffice(workplace)
        refresh()
    }
}
