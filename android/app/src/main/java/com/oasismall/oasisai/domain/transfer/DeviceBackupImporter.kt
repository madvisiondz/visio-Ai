package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.settings.BackupSecurityStore
import com.oasismall.oasisai.domain.settings.ImportantRayonsStore
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProCategoryConfig
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class DeviceBackupImportResult(
    val articleCount: Int,
    val filesRestored: Int,
)

class DeviceBackupImporter(
    private val context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val importantRayonsStore: ImportantRayonsStore,
    private val visioProCatalogConfigStore: VisioProCatalogConfigStore,
    private val backupSecurityStore: BackupSecurityStore,
) {
    suspend fun importFromUri(
        uri: Uri,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): DeviceBackupImportResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(TaskProgress("Reading backup file", 5))
        val rawFile = File(context.cacheDir, "import_backup_raw")
        context.contentResolver.openInputStream(uri)?.use { input ->
            rawFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read backup file")

        val zipFile = File(context.cacheDir, "import_backup.zip")
        if (BackupCrypto.isEncrypted(rawFile)) {
            val password = backupSecurityStore.getPassword()?.toCharArray()
                ?: error("Encrypted backup — set the backup password in Settings first")
            onProgress?.invoke(TaskProgress("Decrypting backup", 10))
            try {
                BackupCrypto.decrypt(rawFile, zipFile, password)
            } finally {
                password.fill('\u0000')
            }
            rawFile.delete()
        } else {
            rawFile.renameTo(zipFile) || run {
                rawFile.copyTo(zipFile, overwrite = true)
                rawFile.delete()
            }
        }

        val extractDir = File(context.cacheDir, "import_backup_extract").apply {
            deleteRecursively()
            mkdirs()
        }
        onProgress?.invoke(TaskProgress("Extracting backup", 15))
        ZipArchive.unzip(zipFile, extractDir) { progress ->
            val shifted = 15 + (progress.normalizedPercent * 20 / 100)
            onProgress?.invoke(progress.copy(percent = shifted))
        }
        zipFile.delete()

        val manifestFile = File(extractDir, "manifest.json")
        if (!manifestFile.exists()) error("Invalid backup — missing manifest.json")
        val manifest = JSONObject(manifestFile.readText())
        val formatVersion = manifest.optInt("formatVersion", 0)
        if (formatVersion != BackupEntityJson.FORMAT_VERSION) {
            error("Unsupported backup format v$formatVersion")
        }

        val filesDir = context.filesDir
        onProgress?.invoke(TaskProgress("Restoring files", 40))
        val filesRoot = File(extractDir, "files")
        var filesRestored = 0
        if (filesRoot.exists()) {
            filesRoot.listFiles()?.forEach { child ->
                val target = File(filesDir, child.name)
                target.deleteRecursively()
                filesRestored += DeviceBackupExporter.copyDirectory(child, target)
            }
        }

        onProgress?.invoke(TaskProgress("Restoring database", 60))
        val databaseFile = File(extractDir, "database.json")
        if (!databaseFile.exists()) error("Invalid backup — missing database.json")
        val tables = BackupEntityJson.readDatabase(JSONObject(databaseFile.readText()), filesDir)
        repository.restoreDatabaseTables(tables, filesDir)

        onProgress?.invoke(TaskProgress("Restoring settings", 80))
        val settingsDir = File(extractDir, "settings")
        restoreSettings(settingsDir, filesDir)

        extractDir.deleteRecursively()
        imageMatcher.invalidatePngCache()

        onProgress?.invoke(TaskProgress("Import complete", 100))
        DeviceBackupImportResult(
            articleCount = tables.articles.size,
            filesRestored = filesRestored,
        )
    }

    private suspend fun restoreSettings(settingsDir: File, filesDir: File) {
        DeviceBackupExporter.copyIfExists(
            File(settingsDir, "important_rayons.json"),
            File(filesDir, "important_rayons.json"),
        )
        DeviceBackupExporter.copyIfExists(
            File(settingsDir, "visio_pro_memory.json"),
            File(filesDir, "visio_pro_memory.json"),
        )
        val catalogFile = File(settingsDir, "visio_pro_catalog_barcodes.json")
        if (catalogFile.exists()) {
            val byBarcode = repository.getAllArticles().associateBy { it.barcode }
            val root = JSONObject(catalogFile.readText())
            VisioProCategory.entries.forEach { category ->
                val obj = root.optJSONObject(category.name) ?: return@forEach
                val enabled = parseBarcodeArray(obj.optJSONArray("enabledBarcodes"))
                    .mapNotNull { byBarcode[it]?.id }
                val pending = parseBarcodeArray(obj.optJSONArray("pendingBarcodes"))
                    .mapNotNull { byBarcode[it]?.id }
                visioProCatalogConfigStore.setCategoryConfig(
                    category,
                    VisioProCategoryConfig(enabledIds = enabled, pendingIds = pending),
                )
            }
        }
        importantRayonsStore.refreshFromDisk()
    }

    private fun parseBarcodeArray(arr: org.json.JSONArray?): List<String> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
}
