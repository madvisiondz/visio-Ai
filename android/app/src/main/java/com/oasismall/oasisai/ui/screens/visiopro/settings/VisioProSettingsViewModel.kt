package com.oasismall.oasisai.ui.screens.visiopro.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
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
) : ViewModel() {

    private val _rows = MutableStateFlow<List<VisioProSettingsCategoryRow>>(emptyList())
    val rows: StateFlow<List<VisioProSettingsCategoryRow>> = _rows.asStateFlow()

    init {
        refresh()
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
