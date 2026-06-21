package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.util.TaskProgress
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VisioProBundleExportResult(
    val savedUri: Uri,
    val zipFileName: String,
    val categoryCount: Int,
    val imageCount: Int,
)

class VisioProBundleExporter(
    private val context: Context,
    private val catalogService: VisioProCatalogService,
    private val catalogConfigStore: VisioProCatalogConfigStore,
    private val visioProStore: VisioProStore,
) {
    suspend fun export(
        outputUri: Uri,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): VisioProBundleExportResult {
        UserExportStorage.cleanupStaleExportCache(context.cacheDir)
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val folderName = "VisioPRO_export_$stamp"
        val zipName = "$folderName.zip"
        val workDir = File(context.cacheDir, folderName).apply {
            deleteRecursively()
            mkdirs()
        }
        val zipCache = File(context.cacheDir, zipName)
        try {
            val filesDir = context.filesDir
            var imageCount = 0

            onProgress?.invoke(TaskProgress("Exporting VisioPRO memory", 5))
            DeviceBackupExporter.copyIfExists(
                File(filesDir, "visio_pro_memory.json"),
                File(workDir, "visio_pro_memory.json"),
            )

            val allMemory = visioProStore.getAllMemory()
            File(workDir, "visio_pro_memory.json").takeIf { it.exists() }
                ?: File(workDir, "visio_pro_memory.json").writeText(JSONObject().toString(2))

            val designsSource = File(filesDir, "visio_pro_designs")
            if (designsSource.exists()) {
                imageCount += DeviceBackupExporter.copyDirectory(designsSource, File(workDir, "designs"))
            }
            val photosSource = File(filesDir, "visio_pro_photos")
            if (photosSource.exists()) {
                imageCount += DeviceBackupExporter.copyDirectory(photosSource, File(workDir, "photos"))
            }

            val categoriesDir = File(workDir, "categories").apply { mkdirs() }
            val categories = VisioProCategory.entries
            categories.forEachIndexed { index, category ->
                onProgress?.invoke(TaskProgress("Exporting ${category.labelFr}", 10 + (index * 55 / categories.size)))
                val categoryDir = File(categoriesDir, category.name).apply { mkdirs() }
                val catalog = catalogService.resolveCategoryCatalog(category)
                val config = catalogConfigStore.getCategoryConfig(category)

                val articlesJson = JSONArray()
                catalog.defs.forEach { def ->
                    val article = catalog.articlesById[def.catalogArticleId]
                    val memory = allMemory[def.slug]
                    articlesJson.put(
                        JSONObject().apply {
                            put("slug", def.slug)
                            put("labelFr", def.labelFr)
                            put("barcode", article?.barcode)
                            put("designation", article?.designation ?: def.csvDesignation)
                            put("price", article?.price)
                            put("codeart", article?.codeart)
                            put("manualPrice", memory?.manualPrice)
                            put("manualPriceOverridden", memory?.manualPriceOverridden == true)
                            put("manualDesignation", memory?.manualDesignation)
                        },
                    )
                    article?.imagePath?.let { path ->
                        val source = File(path)
                        if (source.exists()) {
                            val dest = File(categoryDir, "catalog_png/${source.name}")
                            dest.parentFile?.mkdirs()
                            source.copyTo(dest, overwrite = true)
                            imageCount++
                        }
                    }
                    listOf("social", "print").forEach { channel ->
                        val photo = File(filesDir, "visio_pro_photos/$channel/${def.slug}.jpg")
                        if (photo.exists()) {
                            val dest = File(categoryDir, "photos/$channel/${def.slug}.jpg")
                            dest.parentFile?.mkdirs()
                            photo.copyTo(dest, overwrite = true)
                            imageCount++
                        }
                    }
                }

                File(categoryDir, "articles.json").writeText(articlesJson.toString(2))
                File(categoryDir, "config.json").writeText(
                    JSONObject()
                        .put("enabledBarcodes", JSONArray(config.enabledIds.mapNotNull { catalog.articlesById[it]?.barcode }))
                        .put("pendingBarcodes", JSONArray(config.pendingIds.mapNotNull { catalog.articlesById[it]?.barcode }))
                        .toString(2),
                )
            }

            File(workDir, "manifest.json").writeText(
                JSONObject()
                    .put("formatVersion", 1)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("categories", JSONArray(categories.map { it.name }))
                    .toString(2),
            )

            onProgress?.invoke(TaskProgress("Creating ZIP archive", 75))
            ZipArchive.zipDirectory(workDir, zipCache) { progress ->
                val shifted = 75 + (progress.normalizedPercent * 20 / 100)
                onProgress?.invoke(progress.copy(percent = shifted))
            }
            workDir.deleteRecursively()

            onProgress?.invoke(TaskProgress("Saving to chosen location", 96))
            UserExportStorage.copyFileToUri(context, zipCache, outputUri)

            onProgress?.invoke(TaskProgress("VisioPRO export complete", 100))
            return VisioProBundleExportResult(
                savedUri = outputUri,
                zipFileName = zipName,
                categoryCount = categories.size,
                imageCount = imageCount,
            )
        } finally {
            workDir.deleteRecursively()
            zipCache.delete()
        }
    }
}
