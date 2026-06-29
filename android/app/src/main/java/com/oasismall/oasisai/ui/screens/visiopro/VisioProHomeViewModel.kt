package com.oasismall.oasisai.ui.screens.visiopro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProMediaStore
import com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisioProHomeViewModel(
    private val appContext: Context,
    private val catalogService: VisioProCatalogService,
    private val printImageLinker: VisioProPrintImageLinker,
) : ViewModel() {

    private val _counts = MutableStateFlow<Map<VisioProCategory, Int>>(emptyMap())
    val counts: StateFlow<Map<VisioProCategory, Int>> = _counts.asStateFlow()

    private val _mediaInstalled = MutableStateFlow(VisioProMediaStore.isInstalled(appContext))
    val mediaInstalled: StateFlow<Boolean> = _mediaInstalled.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            runCatching { printImageLinker.runInitialScanIfNeeded() }.onSuccess { result ->
                if (result != null && result.linked > 0) {
                    _syncMessage.value =
                        "${result.linked} photo(s) Impression liée(s) au catalogue pour To share"
                }
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _mediaInstalled.value = VisioProMediaStore.isInstalled(appContext)
            _counts.value = VisioProCategory.entries.associateWith { catalogService.countForCategory(it) }
        }
    }
}
