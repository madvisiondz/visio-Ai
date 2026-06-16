package com.oasismall.oasisai.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.data.model.CameraBatchStatus

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["normalizedName"]),
        Index(value = ["designation"]),
        Index(value = ["sourceImportId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceImportId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val designation: String,
    val normalizedName: String,
    val price: Double,
    val previousPrice: Double? = null,
    /** Gestium article code (CSV column Code). */
    val codeart: String? = null,
    val reference: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val stock: Double? = null,
    val unit: String? = null,
    /** All parsed CSV columns as display text for full article detail. */
    val rawData: String? = null,
    val sourceImportId: Long? = null,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val changeStatus: String = ArticleChangeStatus.UNCHANGED.name,
    val isActive: Boolean = true,
    val needsTicketUpdate: Boolean = false,
)

@Entity(tableName = "imports")
data class ImportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val importedAt: Long = System.currentTimeMillis(),
    val rowCount: Int = 0,
    val status: String,
    val newCount: Int = 0,
    val priceChangedCount: Int = 0,
    val removedCount: Int = 0,
    val renamedCount: Int = 0,
)

@Entity(
    tableName = "article_price_history",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["importId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("articleId"), Index("importId")],
)
data class ArticlePriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val oldPrice: Double,
    val newPrice: Double,
    val importId: Long,
    val changedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "import_changes",
    foreignKeys = [
        ForeignKey(
            entity = ImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["importId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("importId"), Index("articleId")],
)
data class ImportChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importId: Long,
    val articleId: Long?,
    val barcode: String,
    val designation: String,
    val changeType: String,
    val oldValue: String? = null,
    val newValue: String? = null,
)

@Entity(
    tableName = "workflow_history",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("articleId"), Index("eventType"), Index("createdAt")],
)
data class WorkflowHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val articleId: Long? = null,
    val designationSnapshot: String? = null,
    val barcodeSnapshot: String? = null,
    val detail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "product_images",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId"], unique = true), Index("designationKey")],
)
data class ProductImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val designationKey: String,
    /** Barcode stored in PNG metadata (and optionally filename suffix). */
    val barcode: String? = null,
    val imagePath: String,
    val imageStatus: String,
    /** Backup of photo before on-device background removal; never overwritten. */
    val originalImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSentAt: Long? = null,
)

@Entity(
    tableName = "preselection_items",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId", "cartType"], unique = true)],
)
data class PreselectionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val cartType: String,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val note: String? = null,
    val intendedTemplateType: String? = null,
    /** Shelf labels to print per article (Design queue only). */
    val copyCount: Int = 1,
)

@Entity(tableName = "print_templates")
data class PrintTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val size: String,
    val capacity: Int,
    val layoutConfig: String = "{}",
)

@Entity(
    tableName = "print_batches",
    foreignKeys = [
        ForeignKey(
            entity = PrintTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("createdAt")],
)
data class PrintBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val templateName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val exportPath: String,
    val previewPath: String? = null,
    val isPromo: Boolean = false,
    val promoStart: Long? = null,
    val promoEnd: Long? = null,
    val campaignName: String? = null,
    val status: String,
    val itemCount: Int = 0,
)

@Entity(
    tableName = "print_batch_items",
    foreignKeys = [
        ForeignKey(
            entity = PrintBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("batchId"), Index("articleId")],
)
data class PrintBatchItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val articleId: Long?,
    val designationSnapshot: String,
    val priceSnapshot: Double,
    val barcodeSnapshot: String,
    val imageSnapshotPath: String? = null,
    val sortOrder: Int = 0,
)

/** Extra barcodes for one article (same product suffix, different store prefix). */
@Entity(
    tableName = "article_alternate_barcodes",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index("articleId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ArticleAlternateBarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val barcode: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "promo_alerts",
    foreignKeys = [
        ForeignKey(
            entity = PrintBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("batchId"), Index("alertDate")],
)
data class PromoAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val alertDate: Long,
    val message: String,
    val status: String,
)

/** Bulk AGENT captures — barcode-keyed PNGs for later server sync. */
@Entity(
    tableName = "bulk_captures",
    indices = [Index("capturedAt"), Index("syncStatus")],
)
data class BulkCaptureEntity(
    @PrimaryKey val barcode: String,
    val imagePath: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val replaced: Boolean = false,
    val syncStatus: String = "PENDING",
)

/** Camera batch shoot — raw JPEG in Download/VisioAi/Batch_images[date], awaiting PhotoRoom cutout. */
@Entity(
    tableName = "camera_batch_items",
    indices = [Index("batchDate"), Index("status"), Index("barcode")],
)
data class CameraBatchItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchDate: String,
    val shotPath: String,
    val shotFileName: String,
    val barcode: String,
    val designation: String,
    val codeart: String? = null,
    val price: Double? = null,
    val articleId: Long? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val status: String = CameraBatchStatus.AWAITING_PHOTOROOM.name,
    val photoroomPath: String? = null,
)

/** Designation lines from Batch txt not found in CSV — camera capture queue. */
@Entity(
    tableName = "batch_camera_queue",
    indices = [Index("done"), Index("sortOrder")],
)
data class BatchCameraQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val designation: String,
    val sortOrder: Int,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
