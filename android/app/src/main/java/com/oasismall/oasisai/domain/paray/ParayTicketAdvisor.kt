package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.PriceFormatter

/**
 * PARAY responsibility: assess shelf tickets from barcode scans and log verification events.
 *
 * Phase 1: yellow-block OCR + barcode; compare catalog vs last print snapshot.
 * PARAY reads designation from printed shelf tickets (#FFE500 block) via ML Kit.
 */
class ParayTicketAdvisor(
    private val repository: OasisRepository,
    private val store: ParayTicketStore,
) {
    suspend fun assess(
        article: ArticleWithImage?,
        barcode: String,
        lastPrintedPrice: Double?,
        ocrShelfPrice: Double? = null,
        fusion: ParayTicketFusionBreakdown? = null,
        ocrDesignation: String? = null,
    ): ParayTicketAssessment {
        if (article == null) {
            return ParayTicketAssessment(
                status = ParayTicketStatus.NOT_IN_CATALOG,
                message = "Not in catalog — PARAY could not match this ticket.",
                ocrDesignation = ocrDesignation,
            )
        }
        val catalogPrice = article.price
        val rayon = article.rayon?.trim()?.takeIf { it.isNotEmpty() }
        val rayonHint = rayon?.let { " · Rayon: $it" }.orEmpty()

        val fusionSuffix = fusion?.let {
            " · PARAY ${it.probabilityPercent}% (text ${pct(it.designationScore)} · price ${pct(it.priceScore)} · image ${pct(it.imageScore)})"
        }.orEmpty()

        if (lastPrintedPrice == null) {
            val msg = if (article.needsTicketUpdate) {
                "Never printed here but catalog flags ticket update — print ${PriceFormatter.format(catalogPrice)}$rayonHint$fusionSuffix"
            } else {
                "No print record — catalog ${PriceFormatter.format(catalogPrice)}$rayonHint$fusionSuffix"
            }
            return ParayTicketAssessment(
                status = ParayTicketStatus.NEVER_PRINTED,
                catalogPrice = catalogPrice,
                lastPrintedPrice = null,
                rayon = rayon,
                needsTicketUpdate = article.needsTicketUpdate,
                message = msg,
                matchProbability = fusion?.probability,
                fusion = fusion,
                ocrDesignation = ocrDesignation,
            )
        }

        val priceMatches = kotlin.math.abs(catalogPrice - lastPrintedPrice) < 0.005
        val ocrMismatch = ocrShelfPrice?.let { kotlin.math.abs(it - catalogPrice) >= 0.01 } == true
        return when {
            article.needsTicketUpdate || !priceMatches || ocrMismatch -> {
                val printed = PriceFormatter.format(lastPrintedPrice)
                val catalog = PriceFormatter.format(catalogPrice)
                val ocrHint = ocrShelfPrice?.let { " · camera read ${PriceFormatter.format(it)}" }.orEmpty()
                ParayTicketAssessment(
                    status = ParayTicketStatus.STALE,
                    catalogPrice = catalogPrice,
                    lastPrintedPrice = lastPrintedPrice,
                    rayon = rayon,
                    needsTicketUpdate = article.needsTicketUpdate,
                    message = "Replace ticket — shelf $printed vs catalog $catalog$ocrHint$rayonHint$fusionSuffix",
                    matchProbability = fusion?.probability,
                    fusion = fusion,
                    ocrDesignation = ocrDesignation,
                )
            }
            else -> ParayTicketAssessment(
                status = ParayTicketStatus.MATCH,
                catalogPrice = catalogPrice,
                lastPrintedPrice = lastPrintedPrice,
                rayon = rayon,
                needsTicketUpdate = false,
                message = "Ticket OK — ${PriceFormatter.format(catalogPrice)} matches last print$rayonHint$fusionSuffix",
                matchProbability = fusion?.probability,
                fusion = fusion,
                ocrDesignation = ocrDesignation,
            )
        }
    }

    private fun pct(score: Float): String = "${(score * 100f).toInt()}%"

    fun onScan(
        barcode: String,
        article: ArticleWithImage?,
        assessment: ParayTicketAssessment,
    ) {
        store.recordScan(
            barcode = barcode,
            articleId = article?.id,
            designation = article?.designation,
            assessment = assessment,
        )
    }

    fun onVerified(
        barcode: String,
        article: ArticleWithImage?,
        assessment: ParayTicketAssessment,
    ) {
        store.recordVerified(
            barcode = barcode,
            articleId = article?.id,
            designation = article?.designation,
            assessment = assessment,
        )
    }
}
