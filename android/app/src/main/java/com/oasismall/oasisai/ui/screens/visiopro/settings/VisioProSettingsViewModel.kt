package com.oasismall.oasisai.ui.screens.visiopro.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker
import com.oasismall.oasisai.domain.visiopro.VisioProRayonPools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisioProSettingsCategoryRow(
    val category: VisioProCategory,
    val enabledCount: Int,
    val poolCount: Int,
    val pendingCount: Int,
)

class VisioProSettingsViewModel(
    private val catalogService: VisioProCatalogService,
    private val printImageLinker: VisioProPrintImageLinker,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<VisioProSettingsCategoryRow>>(emptyList())
    val rows: StateFlow<List<VisioProSettingsCategoryRow>> = _rows.asStateFlow()

    private val _isLinkingPrintPhotos = MutableStateFlow(false)
    val isLinkingPrintPhotos: StateFlow<Boolean> = _isLinkingPrintPhotos.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
        runInitialPrintPhotoScan()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun syncPrintPhotosToCatalog() {
        viewModelScope.launch {
            _isLinkingPrintPhotos.value = true
            _message.value = null
            runCatching {
                printImageLinker.linkAllPrintPhotosToCatalog()
            }.fold(
                onSuccess = { result ->
                    _message.value = "Photos impression → catalogue : ${result.linked} liée(s) sur ${result.scanned} articles rayon"
                },
                onFailure = { err ->
                    _message.value = "${err.javaClass.simpleName}: ${err.message}"
                },
            )
            _isLinkingPrintPhotos.value = false
        }
    }

    private fun runInitialPrintPhotoScan() {
        viewModelScope.launch {
            _isLinkingPrintPhotos.value = true
            runCatching { printImageLinker.runInitialScanIfNeeded() }.fold(
                onSuccess = { result ->
                    if (result != null && result.linked > 0) {
                        _message.value =
                            "Scan initial : ${result.linked} photo(s) onglet Impression liée(s) au catalogue (To share)"
                    }
                },
                onFailure = { /* silent on background scan */ },
            )
            _isLinkingPrintPhotos.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val rows = VisioProCategory.entries.map { category ->
                val stats = catalogService.poolStats(category)
                VisioProSettingsCategoryRow(
                    category = category,
                    enabledCount = stats.enabledCount,
                    poolCount = stats.poolCount,
                    pendingCount = stats.pendingCount,
                )
            }
            _rows.value = rows
        }
    }

    fun poolHint(category: VisioProCategory): String = VisioProRayonPools.poolHint(category)
}
