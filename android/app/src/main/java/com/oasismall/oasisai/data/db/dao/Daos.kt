package com.oasismall.oasisai.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import kotlinx.coroutines.flow.Flow

data class ArticleWithImage(
    val id: Long,
    val barcode: String,
    val designation: String,
    val normalizedName: String,
    val price: Double,
    val previousPrice: Double?,
    val reference: String?,
    val category: String?,
    val brand: String?,
    val stock: Double?,
    val unit: String?,
    val changeStatus: String,
    val isActive: Boolean,
    val needsTicketUpdate: Boolean,
    val rawData: String?,
    val imagePath: String?,
    val imageStatus: String?,
    val imageCreatedAt: Long?,
    val imageLastSentAt: Long?,
)

data class PreselectionWithArticle(
    val preselectionId: Long,
    val articleId: Long,
    val sortOrder: Int,
    val note: String?,
    val intendedTemplateType: String?,
    val designation: String,
    val barcode: String,
    val price: Double,
    val previousPrice: Double?,
    val codeart: String?,
    val category: String?,
    val imagePath: String?,
    val imageCreatedAt: Long?,
    val imageLastSentAt: Long?,
    val copyCount: Int = 1,
)

data class ImageHistoryItem(
    val articleId: Long,
    val designation: String,
    val barcode: String,
    val price: Double,
    val imagePath: String,
    val imageCreatedAt: Long,
    val imageLastSentAt: Long?,
)

data class WorkflowHistoryItem(
    val id: Long,
    val eventType: String,
    val articleId: Long?,
    val designationSnapshot: String?,
    val barcodeSnapshot: String?,
    val detail: String?,
    val createdAt: Long,
)

data class PrintBatchWithItems(
    val batch: PrintBatchEntity,
    val items: List<PrintBatchItemEntity>,
)

data class PhoneSyncCatalogRow(
    val barcode: String,
    val codeart: String?,
    val designation: String,
    val hasImage: Boolean,
)

data class PhoneSyncPushSourceRow(
    val articleId: Long,
    val barcode: String,
    val codeart: String?,
    val designation: String,
    val price: Double,
    val imagePath: String,
)

data class PhoneSyncAlternatePair(
    val alternateBarcode: String,
    val primaryBarcode: String,
)

data class DashboardStats(
    val totalArticles: Int,
    val activeArticles: Int,
    val needsTicket: Int,
    val missingImages: Int,
    val preselectionCount: Int,
    val activePromos: Int,
    val expiredPromos: Int,
)

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE isActive = 1 ORDER BY designation ASC")
    fun observeAllActive(): Flow<List<ArticleEntity>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND (
            a.designation LIKE '%' || :query || '%' ESCAPE '\'
            OR a.normalizedName LIKE '%' || :query || '%' ESCAPE '\'
            OR a.barcode LIKE '%' || :query || '%' ESCAPE '\'
            OR a.codeart LIKE '%' || :query || '%' ESCAPE '\'
        )
        ORDER BY a.designation ASC
        LIMIT 200
        """,
    )
    fun searchWithImages(query: String): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND a.id = :id
        LIMIT 1
        """,
    )
    suspend fun getWithImageById(id: Long): ArticleWithImage?

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND (
            a.barcode = :barcode
            OR a.id IN (
                SELECT ab.articleId FROM article_alternate_barcodes ab WHERE ab.barcode = :barcode
            )
        )
        LIMIT 1
        """,
    )
    suspend fun getWithImageByBarcode(barcode: String): ArticleWithImage?

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND a.barcode != :excludeBarcode
          AND (
            (length(:suffix) = 10 AND length(a.barcode) >= 10 AND substr(a.barcode, -10) = :suffix)
            OR (length(:suffix) = 9 AND length(a.barcode) >= 9 AND substr(a.barcode, -9) = :suffix)
          )
        ORDER BY a.designation ASC
        LIMIT 50
        """,
    )
    suspend fun findByBarcodeSuffix(suffix: String, excludeBarcode: String): List<ArticleWithImage>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND a.barcode != :excludeBarcode
          AND length(:bodyKey) = 9
          AND length(a.barcode) >= 13
          AND substr(a.barcode, 1, 9) = :bodyKey
        ORDER BY a.designation ASC
        LIMIT 50
        """,
    )
    suspend fun findByGestiumBodyKey(bodyKey: String, excludeBarcode: String): List<ArticleWithImage>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND :partial != ''
          AND length(:partial) >= 4
          AND a.barcode != :excludeBarcode
          AND (
            (length(a.barcode) >= length(:partial) AND substr(a.barcode, -length(:partial)) = :partial)
            OR a.barcode LIKE '%' || :partial || '%'
            OR a.id IN (
                SELECT ab.articleId FROM article_alternate_barcodes ab
                WHERE ab.barcode != :excludeBarcode
                  AND ab.barcode LIKE '%' || :partial || '%'
            )
          )
        ORDER BY a.designation ASC
        LIMIT 80
        """,
    )
    suspend fun findByBarcodePartial(partial: String, excludeBarcode: String): List<ArticleWithImage>

    @Query(
        """
        SELECT a.barcode, a.codeart, a.designation,
               EXISTS (
                   SELECT 1 FROM product_images p
                   WHERE p.articleId = a.id AND p.imageStatus = 'FOUND'
                     AND p.imagePath IS NOT NULL AND p.imagePath != ''
               ) AS hasImage
        FROM articles a
        WHERE a.isActive = 1
        ORDER BY a.barcode ASC
        """,
    )
    suspend fun getPhoneSyncCatalogRows(): List<PhoneSyncCatalogRow>

    @Query(
        """
        SELECT a.id AS articleId, a.barcode, a.codeart, a.designation, a.price, p.imagePath
        FROM articles a
        INNER JOIN product_images p ON p.articleId = a.id
        WHERE a.isActive = 1
          AND p.imageStatus = 'FOUND'
          AND p.imagePath IS NOT NULL AND p.imagePath != ''
        ORDER BY p.createdAt DESC
        """,
    )
    suspend fun getPhoneSyncPushSources(): List<PhoneSyncPushSourceRow>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND a.changeStatus = 'NEW'
        ORDER BY a.designation ASC
        """,
    )
    fun observeNewArticles(): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND a.changeStatus = 'PRICE_CHANGED'
        ORDER BY a.designation ASC
        """,
    )
    fun observePriceChanged(): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1 AND a.needsTicketUpdate = 1
        ORDER BY a.designation ASC
        """,
    )
    fun observeNeedsTicket(): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND COALESCE(
                (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1),
                'MISSING'
              ) != 'FOUND'
        ORDER BY a.designation ASC
        """,
    )
    fun observeMissingImages(): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND NOT EXISTS (
                SELECT 1 FROM product_images p
                WHERE p.articleId = a.id AND p.imageStatus = 'FOUND'
                  AND p.imagePath IS NOT NULL AND p.imagePath != ''
              )
        ORDER BY a.designation ASC
        LIMIT 200
        """,
    )
    fun observeMissingImagesLimited(): Flow<List<ArticleWithImage>>

    @Query(
        """
        SELECT a.id, a.barcode, a.designation, a.normalizedName, a.price, a.previousPrice,
               a.reference, a.category, a.brand, a.stock, a.unit, a.changeStatus,
               a.isActive, a.needsTicketUpdate, a.rawData,
               (SELECT p.imagePath FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imagePath,
               (SELECT p.imageStatus FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageStatus,
               (SELECT p.createdAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT p.lastSentAt FROM product_images p WHERE p.articleId = a.id ORDER BY p.id DESC LIMIT 1) AS imageLastSentAt
        FROM articles a
        WHERE a.isActive = 1
          AND NOT EXISTS (
                SELECT 1 FROM product_images p
                WHERE p.articleId = a.id AND p.imageStatus = 'FOUND'
                  AND p.imagePath IS NOT NULL AND p.imagePath != ''
              )
          AND a.designation LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY a.designation ASC
        LIMIT 200
        """,
    )
    fun searchMissingImages(query: String): Flow<List<ArticleWithImage>>

    @Query("SELECT * FROM articles WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE normalizedName = :normalizedName")
    suspend fun getByNormalizedName(normalizedName: String): List<ArticleEntity>

    @Query("SELECT * FROM articles")
    suspend fun getAll(): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: ArticleEntity): Long

    @Update
    suspend fun update(article: ArticleEntity)

    @Query(
        """
        UPDATE articles
        SET needsTicketUpdate = 0, changeStatus = 'UNCHANGED'
        WHERE id = :articleId
        """,
    )
    suspend fun clearNeedsTicketUpdate(articleId: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE isActive = 1")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE articles SET isActive = 0, changeStatus = :status WHERE barcode NOT IN (:barcodes)")
    suspend fun markRemovedExcept(barcodes: List<String>, status: String)

    @Query("SELECT COUNT(*) FROM articles WHERE isActive = 1")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE needsTicketUpdate = 1 AND isActive = 1")
    suspend fun countNeedsTicket(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE needsTicketUpdate = 1 AND isActive = 1")
    fun observeNeedsTicketCount(): Flow<Int>
}

@Dao
interface ImportDao {
    @Insert
    suspend fun insert(importEntity: ImportEntity): Long

    @Update
    suspend fun update(importEntity: ImportEntity)

    @Query("SELECT * FROM imports ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportEntity>>

    @Query("SELECT * FROM imports ORDER BY importedAt DESC LIMIT 1")
    suspend fun getLatest(): ImportEntity?

    @Query("SELECT * FROM imports WHERE id = :id")
    suspend fun getById(id: Long): ImportEntity?
}

@Dao
interface ImportChangeDao {
    @Insert
    suspend fun insertAll(changes: List<ImportChangeEntity>)

    @Query("SELECT * FROM import_changes WHERE importId = :importId ORDER BY changeType, designation")
    fun observeByImport(importId: Long): Flow<List<ImportChangeEntity>>

    @Query("SELECT * FROM import_changes WHERE importId = :importId ORDER BY changeType, designation")
    suspend fun getByImport(importId: Long): List<ImportChangeEntity>

    @Query(
        """
        SELECT ic.* FROM import_changes ic
        INNER JOIN imports imp ON imp.id = ic.importId
        WHERE ic.changeType != 'UNCHANGED'
        ORDER BY imp.importedAt DESC, ic.changeType, ic.designation
        LIMIT :limit
        """,
    )
    fun observeRecentChanges(limit: Int): Flow<List<ImportChangeEntity>>
}

@Dao
interface ArticlePriceHistoryDao {
    @Insert
    suspend fun insert(entry: ArticlePriceHistoryEntity)

    @Insert
    suspend fun insertAll(entries: List<ArticlePriceHistoryEntity>)

    @Query("SELECT * FROM article_price_history WHERE articleId = :articleId ORDER BY changedAt DESC")
    fun observeForArticle(articleId: Long): Flow<List<ArticlePriceHistoryEntity>>
}

@Dao
interface ProductImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ProductImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ProductImageEntity>)

    @Query("DELETE FROM product_images")
    suspend fun deleteAll()

    @Query("DELETE FROM product_images WHERE articleId = :articleId")
    suspend fun deleteForArticle(articleId: Long)

    @Query("SELECT * FROM product_images WHERE articleId = :articleId LIMIT 1")
    suspend fun getForArticle(articleId: Long): ProductImageEntity?

    @Query("SELECT * FROM product_images WHERE designationKey = :key")
    suspend fun getByDesignationKey(key: String): List<ProductImageEntity>

    @Query("SELECT * FROM product_images")
    suspend fun getAll(): List<ProductImageEntity>

    @Query("UPDATE product_images SET lastSentAt = :sentAt WHERE articleId IN (:articleIds)")
    suspend fun markSent(articleIds: List<Long>, sentAt: Long)

    @Query(
        """
        SELECT a.id AS articleId, a.designation, a.barcode, a.price,
               p.imagePath, p.createdAt AS imageCreatedAt, p.lastSentAt AS imageLastSentAt
        FROM product_images p
        JOIN articles a ON a.id = p.articleId
        WHERE p.imagePath IS NOT NULL AND p.imagePath != ''
        ORDER BY COALESCE(p.lastSentAt, p.createdAt) DESC
        """,
    )
    fun observeImageHistory(): Flow<List<ImageHistoryItem>>

    @Query(
        """
        SELECT COUNT(*) FROM articles a
        WHERE a.isActive = 1
          AND NOT EXISTS (
                SELECT 1 FROM product_images p
                WHERE p.articleId = a.id AND p.imageStatus = 'FOUND'
                  AND p.imagePath IS NOT NULL AND p.imagePath != ''
              )
        """,
    )
    suspend fun countMissing(): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles a
        WHERE a.isActive = 1
          AND NOT EXISTS (
                SELECT 1 FROM product_images p
                WHERE p.articleId = a.id AND p.imageStatus = 'FOUND'
                  AND p.imagePath IS NOT NULL AND p.imagePath != ''
              )
        """,
    )
    fun observeMissingCount(): Flow<Int>
}

@Dao
interface WorkflowHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: WorkflowHistoryEntity): Long

    @Query(
        """
        SELECT id, eventType, articleId, designationSnapshot, barcodeSnapshot, detail, createdAt
        FROM workflow_history
        ORDER BY createdAt DESC, id DESC
        LIMIT 500
        """,
    )
    fun observeLatest(): Flow<List<WorkflowHistoryItem>>
}

@Dao
interface PreselectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PreselectionItemEntity): Long

    @Query("DELETE FROM preselection_items WHERE articleId = :articleId AND cartType = :cartType")
    suspend fun remove(articleId: Long, cartType: String)

    @Query("DELETE FROM preselection_items WHERE cartType = :cartType")
    suspend fun clear(cartType: String)

    @Query(
        """
        SELECT p.id AS preselectionId, p.articleId, p.sortOrder, p.note, p.intendedTemplateType,
               p.copyCount,
               a.designation, a.barcode, a.price, a.previousPrice, a.codeart, a.category,
               (SELECT i.imagePath FROM product_images i WHERE i.articleId = a.id ORDER BY i.id DESC LIMIT 1) AS imagePath,
               (SELECT i.createdAt FROM product_images i WHERE i.articleId = a.id ORDER BY i.id DESC LIMIT 1) AS imageCreatedAt,
               (SELECT i.lastSentAt FROM product_images i WHERE i.articleId = a.id ORDER BY i.id DESC LIMIT 1) AS imageLastSentAt
        FROM preselection_items p
        JOIN articles a ON a.id = p.articleId
        WHERE p.cartType = :cartType
        ORDER BY p.sortOrder ASC, p.addedAt ASC
        """,
    )
    fun observeWithArticles(cartType: String): Flow<List<PreselectionWithArticle>>

    @Query("SELECT COUNT(*) FROM preselection_items WHERE cartType = :cartType")
    suspend fun count(cartType: String): Int

    @Query("SELECT COUNT(*) FROM preselection_items WHERE cartType = :cartType")
    fun observeCount(cartType: String): Flow<Int>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM preselection_items WHERE articleId = :articleId AND cartType = :cartType)",
    )
    suspend fun isInCart(articleId: Long, cartType: String): Boolean

    @Query(
        "SELECT * FROM preselection_items WHERE articleId = :articleId AND cartType = :cartType LIMIT 1",
    )
    suspend fun getItem(articleId: Long, cartType: String): PreselectionItemEntity?

    @Query(
        "UPDATE preselection_items SET copyCount = :copyCount WHERE articleId = :articleId AND cartType = :cartType",
    )
    suspend fun updateCopyCount(articleId: Long, cartType: String, copyCount: Int)

    @Query(
        """
        SELECT id FROM preselection_items
        WHERE cartType = :cartType
        ORDER BY addedAt ASC, sortOrder ASC
        LIMIT :limit
        """,
    )
    suspend fun oldestPreselectionIds(cartType: String, limit: Int): List<Long>

    @Query("DELETE FROM preselection_items WHERE id IN (:ids)")
    suspend fun removeByPreselectionIds(ids: List<Long>)

    @Query(
        "SELECT * FROM preselection_items WHERE cartType = :cartType ORDER BY sortOrder ASC, addedAt ASC",
    )
    suspend fun getAllInCart(cartType: String): List<PreselectionItemEntity>
}

@Dao
interface PrintTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<PrintTemplateEntity>)

    @Query("SELECT * FROM print_templates ORDER BY type, name")
    fun observeAll(): Flow<List<PrintTemplateEntity>>

    @Query("SELECT * FROM print_templates WHERE id = :id")
    suspend fun getById(id: Long): PrintTemplateEntity?

    @Query("SELECT * FROM print_templates WHERE type = :type")
    suspend fun getByType(type: String): List<PrintTemplateEntity>

    @Query("SELECT COUNT(*) FROM print_templates")
    suspend fun count(): Int
}

@Dao
interface PrintBatchDao {
    @Insert
    suspend fun insert(batch: PrintBatchEntity): Long

    @Insert
    suspend fun insertItems(items: List<PrintBatchItemEntity>)

    @Query("SELECT * FROM print_batches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PrintBatchEntity>>

    @Query("SELECT * FROM print_batches WHERE id = :id")
    suspend fun getById(id: Long): PrintBatchEntity?

    @Query("SELECT * FROM print_batch_items WHERE batchId = :batchId ORDER BY sortOrder")
    suspend fun getItems(batchId: Long): List<PrintBatchItemEntity>

    @Update
    suspend fun update(batch: PrintBatchEntity)

    @Query("SELECT COUNT(*) FROM print_batches WHERE isPromo = 1 AND promoEnd >= :now")
    suspend fun countActivePromos(now: Long): Int

    @Query("SELECT COUNT(*) FROM print_batches WHERE isPromo = 1 AND promoEnd < :now")
    suspend fun countExpiredPromos(now: Long): Int

    @Query(
        """
        SELECT * FROM print_batches
        WHERE isPromo = 1 AND promoEnd IS NOT NULL
        ORDER BY promoEnd ASC
        """,
    )
    fun observePromoBatches(): Flow<List<PrintBatchEntity>>

    @Query("SELECT * FROM print_batches WHERE isPromo = 1 ORDER BY promoEnd ASC")
    suspend fun getPromoBatches(): List<PrintBatchEntity>

    @Query(
        """
        SELECT * FROM print_batches
        WHERE templateName LIKE 'Design —%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeDesignShelfPrints(limit: Int): Flow<List<PrintBatchEntity>>
}

@Dao
interface PromoAlertDao {
    @Insert
    suspend fun insert(alert: PromoAlertEntity): Long

    @Insert
    suspend fun insertAll(alerts: List<PromoAlertEntity>)

    @Query("SELECT * FROM promo_alerts WHERE status = 'PENDING' ORDER BY alertDate ASC")
    fun observePending(): Flow<List<PromoAlertEntity>>

    @Query("UPDATE promo_alerts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM promo_alerts WHERE status = 'PENDING'")
    suspend fun clearPending()
}

@Dao
interface ArticleAlternateBarcodeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ArticleAlternateBarcodeEntity): Long

    @Query("SELECT * FROM article_alternate_barcodes WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ArticleAlternateBarcodeEntity?

    @Query("SELECT * FROM article_alternate_barcodes WHERE articleId = :articleId ORDER BY addedAt ASC")
    suspend fun getByArticleId(articleId: Long): List<ArticleAlternateBarcodeEntity>

    @Query(
        """
        SELECT ab.barcode AS alternateBarcode, a.barcode AS primaryBarcode
        FROM article_alternate_barcodes ab
        INNER JOIN articles a ON a.id = ab.articleId
        WHERE a.isActive = 1
        """,
    )
    suspend fun getAllPairs(): List<PhoneSyncAlternatePair>
}

@Dao
interface BulkCaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BulkCaptureEntity)

    @Query("SELECT * FROM bulk_captures WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): BulkCaptureEntity?

    @Query("SELECT COUNT(*) FROM bulk_captures")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM bulk_captures ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<BulkCaptureEntity>>
}
