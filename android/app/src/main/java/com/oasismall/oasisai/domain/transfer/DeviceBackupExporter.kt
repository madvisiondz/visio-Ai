package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.BuildConfig
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.settings.BackupSecurityStore
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.util.TaskProgress
import com.oasismall.oasisai.util.writeTextAtomic
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceBackupExportResult(
    val savedUri: Uri,
    val zipFileName: String,
    val fileCount: Int,
    val articleCount: Int,
    val encrypted: Boolean = false,
)

class DeviceBackupExporter(
    private val context: Context,
    private val repository: OasisRepository,
    private val visioProCatalogConfigStore: VisioProCatalogConfigStore,
    private val backupSecurityStore: BackupSecurityStore,
) {
    suspend fun exportFullBackup(
        outputUri: Uri,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): DeviceBackupExportResult {
        UserExportStorage.cleanupStaleExportCache(context.cacheDir)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val zipName = "VisioAi_backup_$stamp.zip"
        val workDir = File(context.cacheDir, "device-backup-$stamp").apply {
            deleteRecursively()
            mkdirs()
        }
        val zipCache = File(context.cacheDir, zipName)
        try {
        val filesDir = context.filesDir

        onProgress?.invoke(TaskProgress("Exporting database", 5))
        val tables = repository.exportDatabaseTables()
        val databaseJson = BackupEntityJson.writeDatabase(tables, filesDir)
        File(workDir, "database.json").writeTextAtomic(databaseJson.toString(2))

        onProgress?.invoke(TaskProgress("Exporting settings", 15))
        val settingsDir = File(workDir, "settings").apply { mkdirs() }
        copyIfExists(File(filesDir, "important_rayons.json"), File(settingsDir, "important_rayons.json"))
        copyIfExists(File(filesDir, "visio_pro_memory.json"), File(settingsDir, "visio_pro_memory.json"))
        writeVisioProCatalogByBarcode(settingsDir, tables.articles)

        onProgress?.invoke(TaskProgress("Copying image folders", 25))
        val filesRoot = File(workDir, "files").apply { mkdirs() }
        var copiedFiles = 0
        BACKUP_FILE_DIRS.forEachIndexed { index, dirName ->
            val source = File(filesDir, dirName)
            if (source.exists()) {
                copiedFiles += copyDirectory(source, File(filesRoot, dirName))
            }
            val pct = 25 + ((index + 1) * 40 / BACKUP_FILE_DIRS.size)
            onProgress?.invoke(TaskProgress("Copying $dirName", pct))
        }

        val manifest = JSONObject().apply {
            put("formatVersion", BackupEntityJson.FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("exportedAt", System.currentTimeMillis())
            put("articleCount", tables.articles.size)
            put("pngCount", File(filesRoot, "product_images").listFiles()?.count { it.extension.equals("png", true) } ?: 0)
            put("fileDirs", JSONArrayString(BACKUP_FILE_DIRS))
        }
        File(workDir, "manifest.json").writeTextAtomic(manifest.toString(2))

        onProgress?.invoke(TaskProgress("Creating ZIP archive", 70))
        ZipArchive.zipDirectory(workDir, zipCache) { progress ->
            val shifted = 70 + (progress.normalizedPercent * 22 / 100)
            onProgress?.invoke(progress.copy(percent = shifted))
        }
        workDir.deleteRecursively()

        val outputZip = if (backupSecurityStore.isEncryptionEnabled()) {
            val password = backupSecurityStore.getPassword()?.toCharArray()
                ?: error("Backup encryption is enabled but no password is set in Settings")
            onProgress?.invoke(TaskProgress("Encrypting backup", 94))
            val encrypted = File(context.cacheDir, "$zipName.enc")
            BackupCrypto.encrypt(zipCache, encrypted, password)
            password.fill('\u0000')
            zipCache.delete()
            encrypted
        } else {
            zipCache
        }

        onProgress?.invoke(TaskProgress("Saving to chosen location", 96))
        UserExportStorage.copyFileToUri(context, outputZip, outputUri)

        onProgress?.invoke(TaskProgress("Backup complete", 100))
        return DeviceBackupExportResult(
            savedUri = outputUri,
            zipFileName = zipName,
            fileCount = copiedFiles,
            articleCount = tables.articles.size,
            encrypted = backupSecurityStore.isEncryptionEnabled(),
        )
        } finally {
            workDir.deleteRecursively()
            zipCache.delete()
            File(context.cacheDir, "$zipName.enc").delete()
        }
    }

    private suspend fun writeVisioProCatalogByBarcode(
        settingsDir: File,
        articles: List<com.oasismall.oasisai.data.db.entity.ArticleEntity>,
    ) {
        val byId = articles.associateBy { it.id }
        val root = JSONObject()
        VisioProCategory.entries.forEach { category ->
            val config = visioProCatalogConfigStore.getCategoryConfig(category)
            val enabled = config.enabledIds.mapNotNull { byId[it]?.barcode }
            val pending = config.pendingIds.mapNotNull { byId[it]?.barcode }
            if (enabled.isNotEmpty() || pending.isNotEmpty()) {
                root.put(
                    category.name,
                    JSONObject()
                        .put("enabledBarcodes", org.json.JSONArray(enabled))
                        .put("pendingBarcodes", org.json.JSONArray(pending)),
                )
            }
        }
        File(settingsDir, "visio_pro_catalog_barcodes.json").writeTextAtomic(root.toString(2))
    }

    companion object {
        val BACKUP_FILE_DIRS = listOf(
            "product_images",
            "visio_pro_photos",
            "visio_pro_designs",
            "bulk_images",
            "exports",
        )

        fun copyIfExists(source: File, dest: File) {
            if (source.isFile) {
                dest.parentFile?.mkdirs()
                source.copyTo(dest, overwrite = true)
            }
        }

        fun copyDirectory(source: File, dest: File): Int {
            if (!source.exists()) return 0
            var count = 0
            source.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relative = file.relativeTo(source).path
                    val target = File(dest, relative)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                    count++
                }
            }
            return count
        }

        private fun JSONArrayString(items: List<String>) = org.json.JSONArray(items)
    }
}
