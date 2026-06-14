package com.oasismall.oasisai

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oasismall.oasisai.data.db.OasisDatabase
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import com.oasismall.oasisai.domain.ImportService
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayHome
import com.oasismall.oasisai.domain.paray.ParayImportManager
import com.oasismall.oasisai.domain.PrintGenerator
import com.oasismall.oasisai.domain.PromoService
import com.oasismall.oasisai.domain.ReadyPngLoader
import com.oasismall.oasisai.domain.bulk.BulkCaptureStore
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
            )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val repository: OasisRepository by lazy { OasisRepository(database) }
    val imageMatcher: ImageMatcher by lazy { ImageMatcher(this, repository) }
    val readyPngLoader: ReadyPngLoader by lazy { ReadyPngLoader(imageMatcher) }
    val importService: ImportService by lazy { ImportService(repository, imageMatcher) }
    val printGenerator: PrintGenerator by lazy { PrintGenerator(this, repository) }
    val promoService: PromoService by lazy { PromoService(repository) }
    val backgroundRemovalService: BackgroundRemovalService by lazy { BackgroundRemovalService(this) }
    val parayHome: ParayHome by lazy { ParayHome(this) }
    val paray: ParayAgent by lazy { ParayAgent(this, parayHome) }
    val parayImportManager: ParayImportManager by lazy { ParayImportManager(this) }
    val bulkCaptureStore: BulkCaptureStore by lazy {
        BulkCaptureStore(this, database.bulkCaptureDao())
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
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
