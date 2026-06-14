package com.oasismall.oasisai.ui.screens.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.PromoService
import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PromoViewModel(
    private val repository: OasisRepository,
    private val promoService: PromoService,
) : ViewModel() {
    val promoBatches = repository.observePromoBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alerts = promoService.observePendingAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { promoService.refreshAlerts() }
    }

    fun dismissAlert(alertId: Long) {
        viewModelScope.launch { promoService.dismissAlert(alertId) }
    }
}
