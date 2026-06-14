package com.oasismall.oasisai.ui.screens.parayimport

import android.content.Context
import androidx.lifecycle.ViewModel
import com.oasismall.oasisai.domain.paray.ParayImportManager
import kotlinx.coroutines.flow.StateFlow

class ParayImportViewModel(
    private val importManager: ParayImportManager,
) : ViewModel() {
    val uiState: StateFlow<com.oasismall.oasisai.domain.paray.ParayImportUiState> = importManager.state

    fun startImport(context: Context) {
        importManager.startIfPending(context)
    }

    fun refreshNeural() {
        importManager.refreshNeural()
    }
}
