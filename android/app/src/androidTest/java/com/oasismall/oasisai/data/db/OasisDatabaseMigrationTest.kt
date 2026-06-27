package com.oasismall.oasisai.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class OasisDatabaseMigrationTest {

    private val testDb = "migration-test-oasis"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OasisDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate16To17_backfillsRayonFromRawData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        createV16ArticlesDatabase(context)

        val migrated = helper.runMigrationsAndValidate(
            testDb,
            16,
            17,
            false,
            OasisDatabaseMigrations.MIGRATION_16_17,
        )
        migrated.query("SELECT rayon, famille, category FROM articles WHERE barcode = 'TEST001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Boucherie", cursor.getString(0))
            assertEquals("Viande", cursor.getString(1))
            assertEquals("Frais", cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate17To18_addsPrintBatchSnapshotColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        createV17Database(context)

        val migrated = helper.runMigrationsAndValidate(
            testDb,
            17,
            18,
            false,
            OasisDatabaseMigrations.MIGRATION_17_18,
        )
        migrated.query("PRAGMA table_info(print_batch_items)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(columns.contains("copyCountSnapshot"))
            assertTrue(columns.contains("variantBarcodeSnapshot"))
        }
        migrated.close()
    }

    private fun createV16ArticlesDatabase(context: Context) {
        val path = context.getDatabasePath(testDb)
        path.parentFile?.mkdirs()
        if (path.exists()) path.delete()
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        db.version = 16
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS articles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                barcode TEXT NOT NULL,
                designation TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                price REAL NOT NULL,
                previousPrice REAL,
                codeart TEXT,
                reference TEXT,
                category TEXT,
                brand TEXT,
                stock REAL,
                unit TEXT,
                rawData TEXT,
                sourceImportId INTEGER,
                lastSeenAt INTEGER NOT NULL,
                changeStatus TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                needsTicketUpdate INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO articles (
                barcode, designation, normalizedName, price, lastSeenAt,
                changeStatus, isActive, needsTicketUpdate, rawData
            ) VALUES (
                'TEST001', 'TEST ARTICLE', 'TEST ARTICLE', 10.0, 1,
                'UNCHANGED', 1, 0,
                'Rayon: Boucherie\nFamille: Viande\nCatégorie: Frais'
            )
            """.trimIndent(),
        )
        db.close()
    }

    private fun createV17Database(context: Context) {
        createV16ArticlesDatabase(context)
        helper.runMigrationsAndValidate(
            testDb,
            16,
            17,
            false,
            OasisDatabaseMigrations.MIGRATION_16_17,
        ).close()
        val path = context.getDatabasePath(testDb)
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            path.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        db.version = 17
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS print_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                itemCount INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS print_batch_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchId INTEGER NOT NULL,
                articleId INTEGER NOT NULL,
                barcodeSnapshot TEXT NOT NULL,
                designationSnapshot TEXT NOT NULL,
                priceSnapshot REAL NOT NULL,
                imageSnapshotPath TEXT,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.close()
    }
}
