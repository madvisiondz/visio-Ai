package com.oasismall.oasisai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.oasismall.oasisai.data.db.dao.ArticleAlternateBarcodeDao
import com.oasismall.oasisai.data.db.dao.BulkCaptureDao
import com.oasismall.oasisai.data.db.dao.ArticleDao
import com.oasismall.oasisai.data.db.dao.ArticlePriceHistoryDao
import com.oasismall.oasisai.data.db.dao.ImportChangeDao
import com.oasismall.oasisai.data.db.dao.ImportDao
import com.oasismall.oasisai.data.db.dao.PreselectionDao
import com.oasismall.oasisai.data.db.dao.PrintBatchDao
import com.oasismall.oasisai.data.db.dao.PrintTemplateDao
import com.oasismall.oasisai.data.db.dao.ProductImageDao
import com.oasismall.oasisai.data.db.dao.PromoAlertDao
import com.oasismall.oasisai.data.db.dao.WorkflowHistoryDao
import com.oasismall.oasisai.data.db.entity.ArticleAlternateBarcodeEntity
import com.oasismall.oasisai.data.db.entity.BulkCaptureEntity
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ArticlePriceHistoryEntity
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.db.entity.PreselectionItemEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.db.entity.PrintTemplateEntity
import com.oasismall.oasisai.data.db.entity.ProductImageEntity
import com.oasismall.oasisai.data.db.entity.PromoAlertEntity
import com.oasismall.oasisai.data.db.entity.WorkflowHistoryEntity

@Database(
    entities = [
        ArticleEntity::class,
        ArticleAlternateBarcodeEntity::class,
        ImportEntity::class,
        ArticlePriceHistoryEntity::class,
        ImportChangeEntity::class,
        WorkflowHistoryEntity::class,
        ProductImageEntity::class,
        PreselectionItemEntity::class,
        PrintTemplateEntity::class,
        PrintBatchEntity::class,
        PrintBatchItemEntity::class,
        PromoAlertEntity::class,
        BulkCaptureEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class OasisDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun articleAlternateBarcodeDao(): ArticleAlternateBarcodeDao
    abstract fun importDao(): ImportDao
    abstract fun importChangeDao(): ImportChangeDao
    abstract fun articlePriceHistoryDao(): ArticlePriceHistoryDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun preselectionDao(): PreselectionDao
    abstract fun printTemplateDao(): PrintTemplateDao
    abstract fun printBatchDao(): PrintBatchDao
    abstract fun promoAlertDao(): PromoAlertDao
    abstract fun workflowHistoryDao(): WorkflowHistoryDao
    abstract fun bulkCaptureDao(): BulkCaptureDao
}
