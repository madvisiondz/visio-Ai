package com.oasismall.oasisai.domain.design

import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.model.ArticleChangeStatus

/** One row in Design print history detail — live catalog merged with print snapshot. */
data class DesignBatchItemUi(
    val batchItemId: Long,
    val articleId: Long?,
    val designation: String,
    val barcode: String,
    val variantBarcode: String,
    val price: Double,
    val previousPrice: Double?,
    val priceAtPrint: Double,
    val copyCount: Int,
    val isPromoTicket: Boolean,
    val promoPrice: Double?,
    val promoOriginalPrice: Double?,
    val imagePath: String?,
    val changeStatus: String,
    val needsTicketUpdate: Boolean,
    val excludedFromReprint: Boolean = false,
) {
    val hasCatalogChange: Boolean
        get() = needsTicketUpdate ||
            changeStatus == ArticleChangeStatus.PRICE_CHANGED.name ||
            changeStatus == ArticleChangeStatus.NEW.name ||
            changeStatus == ArticleChangeStatus.RENAMED.name ||
            kotlin.math.abs(price - priceAtPrint) > 0.009

    fun toPreselection(sortOrder: Int, preselectionId: Long = -sortOrder.toLong()): PreselectionWithArticle =
        PreselectionWithArticle(
            preselectionId = preselectionId,
            articleId = articleId ?: 0L,
            sortOrder = sortOrder,
            addedAt = System.currentTimeMillis(),
            note = null,
            intendedTemplateType = null,
            variantBarcode = variantBarcode,
            designation = designation,
            barcode = barcode,
            price = price,
            previousPrice = previousPrice,
            codeart = null,
            category = null,
            imagePath = imagePath,
            imageCreatedAt = null,
            imageLastSentAt = null,
            copyCount = copyCount.coerceIn(1, 99),
            isPromoTicket = isPromoTicket,
            promoPrice = promoPrice,
            promoOriginalPrice = promoOriginalPrice,
            changeStatus = changeStatus,
            needsTicketUpdate = needsTicketUpdate,
        )
}

fun PrintBatchItemEntity.toPreselectionFromSnapshot(sortOrder: Int): PreselectionWithArticle =
    PreselectionWithArticle(
        preselectionId = id,
        articleId = articleId ?: 0L,
        sortOrder = sortOrder,
        addedAt = System.currentTimeMillis(),
        note = null,
        intendedTemplateType = null,
        variantBarcode = variantBarcodeSnapshot,
        designation = designationSnapshot,
        barcode = barcodeSnapshot,
        price = priceSnapshot,
        previousPrice = null,
        codeart = null,
        category = null,
        imagePath = imageSnapshotPath,
        imageCreatedAt = null,
        imageLastSentAt = null,
        copyCount = copyCountSnapshot.coerceIn(1, 99),
        isPromoTicket = isPromoSnapshot,
        promoPrice = promoPriceSnapshot,
        promoOriginalPrice = promoOriginalSnapshot,
    )
