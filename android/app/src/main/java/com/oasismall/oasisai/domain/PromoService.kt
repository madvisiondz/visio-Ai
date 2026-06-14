package com.oasismall.oasisai.domain

import com.oasismall.oasisai.data.db.entity.PromoAlertEntity
import com.oasismall.oasisai.data.model.PromoAlertStatus
import com.oasismall.oasisai.data.repository.OasisRepository
import java.util.concurrent.TimeUnit

class PromoService(
    private val repository: OasisRepository,
) {
    fun observePendingAlerts() = repository.observePendingPromoAlerts()

    fun observePromoBatches() = repository.observePromoBatches()

    suspend fun refreshAlerts(now: Long = System.currentTimeMillis()) {
        repository.clearPendingPromoAlerts()
        val batches = repository.getPromoBatchesSnapshot()
        val alerts = mutableListOf<PromoAlertEntity>()

        batches.forEach { batch ->
            val end = batch.promoEnd ?: return@forEach
            val daysLeft = TimeUnit.MILLISECONDS.toDays(end - now)
            val message = when {
                end < now -> "Promo expired: ${batch.campaignName ?: batch.templateName}"
                daysLeft == 0L -> "Promo expires today: ${batch.campaignName ?: batch.templateName}"
                daysLeft == 1L -> "Promo expires tomorrow: ${batch.campaignName ?: batch.templateName}"
                else -> null
            }
            if (message != null) {
                alerts.add(
                    PromoAlertEntity(
                        batchId = batch.id,
                        alertDate = now,
                        message = message,
                        status = PromoAlertStatus.PENDING.name,
                    ),
                )
            }
        }

        if (alerts.isNotEmpty()) {
            repository.insertPromoAlerts(alerts)
        }
    }

    suspend fun dismissAlert(alertId: Long) {
        repository.updatePromoAlertStatus(alertId, PromoAlertStatus.DISMISSED.name)
    }
}
