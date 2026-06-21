package com.oasismall.oasisai

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oasismall.oasisai.data.db.OasisDatabase
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import com.oasismall.oasisai.domain.flavors.SubBarcodeRegistry
import com.oasismall.oasisai.domain.flavors.SubBarcodeFlavorService
import com.oasismall.oasisai.domain.ImportService
import com.oasismall.oasisai.domain.settings.ImportantRayonsStore
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayHome
import com.oasismall.oasisai.domain.paray.ParayImportManager
import com.oasismall.oasisai.domain.PrintGenerator
import com.oasismall.oasisai.domain.PromoService
import com.oasismall.oasisai.domain.ReadyPngLoader
import com.oasismall.oasisai.domain.bulk.BulkCaptureStore
import com.oasismall.oasisai.domain.visio.BatchCameraQueueStore
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.ProductImagesExporter
import com.oasismall.oasisai.domain.visiopro.VisioProExporter
import com.oasismall.oasisai.domain.visiopro.VisioProAilRenderer
import com.oasismall.oasisai.domain.visiopro.VisioProPhotoStore
import com.oasismall.oasisai.domain.visiopro.VisioProPriceResolver
import com.oasismall.oasisai.domain.visiopro.VisioProRenderFacade
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignStore
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.domain.visiopro.VisioProTemplateAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OasisApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: OasisDatabase by lazy {
        Room.databaseBuilder(this, OasisDatabase::class.java, "oasis_ai.db")
            .addMigrations(
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
            )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val backgroundTaskManager: com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager by lazy {
        com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager()
    }
    val importantRayonsStore: ImportantRayonsStore by lazy { ImportantRayonsStore(this) }
    val subBarcodeRegistry: SubBarcodeRegistry by lazy { SubBarcodeRegistry(this) }
    val repository: OasisRepository by lazy {
        OasisRepository(database, importantRayonsStore, subBarcodeRegistry, filesDir)
    }
    val imageMatcher: ImageMatcher by lazy { ImageMatcher(this, repository) }
    val subBarcodeFlavorService: SubBarcodeFlavorService by lazy {
        SubBarcodeFlavorService(this, repository, imageMatcher)
    }
    val readyPngLoader: ReadyPngLoader by lazy { ReadyPngLoader(imageMatcher) }
    val importService: ImportService by lazy {
        ImportService(repository, imageMatcher, subBarcodeFlavorService)
    }
    val printGenerator: PrintGenerator by lazy { PrintGenerator(this, repository) }
    val promoService: PromoService by lazy { PromoService(repository) }
    val backgroundRemovalService: BackgroundRemovalService by lazy { BackgroundRemovalService(this) }
    val parayHome: ParayHome by lazy { ParayHome(this) }
    val paray: ParayAgent by lazy { ParayAgent(this, parayHome) }
    val parayImportManager: ParayImportManager by lazy { ParayImportManager(this) }
    val bulkCaptureStore: BulkCaptureStore by lazy {
        BulkCaptureStore(this, database.bulkCaptureDao())
    }
    val batchCameraQueueStore: BatchCameraQueueStore by lazy {
        BatchCameraQueueStore(database.batchCameraQueueDao())
    }
    val cameraBatchStore: CameraBatchStore by lazy {
        CameraBatchStore(this, database.cameraBatchDao(), repository, imageMatcher)
    }
    val productImagesExporter: ProductImagesExporter by lazy {
        ProductImagesExporter(this, imageMatcher)
    }
    val visioProStore: VisioProStore by lazy { VisioProStore(this) }
    val visioProCatalogConfigStore: VisioProCatalogConfigStore by lazy { VisioProCatalogConfigStore(this) }
    val visioProDesignStore: VisioProDesignStore by lazy {
        VisioProDesignStore(this, visioProTemplateAssets)
    }
    val visioProCatalogService: VisioProCatalogService by lazy {
        VisioProCatalogService(repository, visioProCatalogConfigStore)
    }
    val visioProPhotoStore: VisioProPhotoStore by lazy { VisioProPhotoStore(this) }
    val visioProExporter: VisioProExporter by lazy { VisioProExporter(this) }
    val visioProTemplateAssets: VisioProTemplateAssets by lazy { VisioProTemplateAssets(this) }
    val visioProAilRenderer: VisioProAilRenderer by lazy { VisioProAilRenderer(visioProTemplateAssets) }
    val visioProRenderFacade: VisioProRenderFacade by lazy {
        VisioProRenderFacade(visioProAilRenderer, visioProTemplateAssets)
    }
    val visioProPriceResolver: VisioProPriceResolver by lazy {
        VisioProPriceResolver(repository)
    }
    val gestiumCatalogPurge: com.oasismall.oasisai.domain.transfer.GestiumCatalogPurge by lazy {
        com.oasismall.oasisai.domain.transfer.GestiumCatalogPurge(
            repository, imageMatcher, visioProCatalogConfigStore, subBarcodeFlavorService,
        )
    }
    val deviceBackupExporter: com.oasismall.oasisai.domain.transfer.DeviceBackupExporter by lazy {
        com.oasismall.oasisai.domain.transfer.DeviceBackupExporter(this, repository, visioProCatalogConfigStore)
    }
    val deviceBackupImporter: com.oasismall.oasisai.domain.transfer.DeviceBackupImporter by lazy {
        com.oasismall.oasisai.domain.transfer.DeviceBackupImporter(
            this, repository, imageMatcher, importantRayonsStore, visioProCatalogConfigStore,
        )
    }
    val visioProBundleExporter: com.oasismall.oasisai.domain.transfer.VisioProBundleExporter by lazy {
        com.oasismall.oasisai.domain.transfer.VisioProBundleExporter(
            this, visioProCatalogService, visioProCatalogConfigStore, visioProStore,
        )
    }
    val visioProPrintImageLinker: com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker by lazy {
        com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker(
            this, repository, visioProCatalogService, visioProPhotoStore, imageMatcher,
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            com.oasismall.oasisai.domain.transfer.UserExportStorage.cleanupStaleExportCache(cacheDir)
            repository.seedDefaultTemplates()
            promoService.refreshAlerts()
        }
    }
}

fun Application.oasisContainer(): OasisApp = this as OasisApp

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN rawData TEXT")
        db.execSQL("ALTER TABLE product_images ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE product_images ADD COLUMN lastSentAt INTEGER")
        db.execSQL("UPDATE product_images SET createdAt = strftime('%s','now') * 1000 WHERE createdAt = 0")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workflow_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventType TEXT NOT NULL,
                articleId INTEGER,
                designationSnapshot TEXT,
                barcodeSnapshot TEXT,
                detail TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(articleId) REFERENCES articles(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workflow_history_articleId ON workflow_history(articleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workflow_history_eventType ON workflow_history(eventType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workflow_history_createdAt ON workflow_history(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_articles_sourceImportId ON articles(sourceImportId)")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN codeart TEXT")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_images ADD COLUMN originalImagePath TEXT")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS article_alternate_barcodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                articleId INTEGER NOT NULL,
                barcode TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                FOREIGN KEY(articleId) REFERENCES articles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_article_alternate_barcodes_barcode " +
                "ON article_alternate_barcodes(barcode)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_alternate_barcodes_articleId " +
                "ON article_alternate_barcodes(articleId)",
        )
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE preselection_items ADD COLUMN copyCount INTEGER NOT NULL DEFAULT 1",
        )
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bulk_captures (
                barcode TEXT NOT NULL PRIMARY KEY,
                imagePath TEXT NOT NULL,
                capturedAt INTEGER NOT NULL,
                replaced INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bulk_captures_capturedAt ON bulk_captures(capturedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bulk_captures_syncStatus ON bulk_captures(syncStatus)")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS camera_batch_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchDate TEXT NOT NULL,
                shotPath TEXT NOT NULL,
                shotFileName TEXT NOT NULL,
                barcode TEXT NOT NULL,
                designation TEXT NOT NULL,
                codeart TEXT,
                price REAL,
                articleId INTEGER,
                capturedAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                photoroomPath TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_camera_batch_items_batchDate ON camera_batch_items(batchDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_camera_batch_items_status ON camera_batch_items(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_camera_batch_items_barcode ON camera_batch_items(barcode)")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS batch_camera_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                designation TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_camera_queue_done ON batch_camera_queue(done)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_camera_queue_sortOrder ON batch_camera_queue(sortOrder)")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE article_alternate_barcodes ADD COLUMN imagePath TEXT")
        db.execSQL(
            "ALTER TABLE camera_batch_items ADD COLUMN pendingSubBarcodeLink INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("ALTER TABLE camera_batch_items ADD COLUMN linkParentArticleId INTEGER")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE preselection_items ADD COLUMN variantBarcode TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL("DROP INDEX IF EXISTS index_preselection_items_articleId_cartType")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_preselection_items_articleId_cartType_variantBarcode
            ON preselection_items(articleId, cartType, variantBarcode)
            """.trimIndent(),
        )
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE preselection_items ADD COLUMN isPromoTicket INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("ALTER TABLE preselection_items ADD COLUMN promoPrice REAL")
        db.execSQL("ALTER TABLE preselection_items ADD COLUMN promoOriginalPrice REAL")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN rayon TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN famille TEXT")
        db.query("SELECT id, rawData FROM articles WHERE rawData IS NOT NULL AND trim(rawData) != ''").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val raw = cursor.getString(1) ?: continue
                var rayon: String? = null
                var famille: String? = null
                var category: String? = null
                raw.lineSequence().forEach { line ->
                    when {
                        line.startsWith("Rayon:") ->
                            rayon = line.removePrefix("Rayon:").trim().takeIf { it.isNotBlank() }
                        line.startsWith("Famille:") ->
                            famille = line.removePrefix("Famille:").trim().takeIf { it.isNotBlank() }
                        line.startsWith("Catégorie:") || line.startsWith("Categorie:") ->
                            category = line.removePrefix("Catégorie:")
                                .removePrefix("Categorie:")
                                .trim()
                                .takeIf { it.isNotBlank() }
                    }
                }
                if (rayon != null) {
                    db.execSQL("UPDATE articles SET rayon = ? WHERE id = ?", arrayOf(rayon, id))
                }
                if (famille != null) {
                    db.execSQL("UPDATE articles SET famille = ? WHERE id = ?", arrayOf(famille, id))
                }
                if (category != null) {
                    db.execSQL("UPDATE articles SET category = ? WHERE id = ?", arrayOf(category, id))
                }
            }
        }
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE print_batches ADD COLUMN pageIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE print_batch_items ADD COLUMN copyCountSnapshot INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE print_batch_items ADD COLUMN isPromoSnapshot INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE print_batch_items ADD COLUMN promoPriceSnapshot REAL")
        db.execSQL("ALTER TABLE print_batch_items ADD COLUMN promoOriginalSnapshot REAL")
        db.execSQL("ALTER TABLE print_batch_items ADD COLUMN variantBarcodeSnapshot TEXT NOT NULL DEFAULT ''")
    }
}
