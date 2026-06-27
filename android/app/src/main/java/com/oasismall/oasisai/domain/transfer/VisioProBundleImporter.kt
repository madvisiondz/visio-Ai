package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProCategoryConfig
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VisioProBundleImportResult(
    val categoryCount: Int,
    val imageCount: Int,
    val articlesUpdated: Int,
)

/** Imports a ZIP produced by [VisioProBundleExporter] (formatVersion 1). */
class VisioProBundleImporter(
    private val context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val catalogConfigStore: VisioProCatalogConfigStore,
    private val visioProStore: VisioProStore,
) {
    suspend fun importFromUri(
        uri: Uri,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): VisioProBundleImportResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(TaskProgress("Reading VisioPRO bundle", 5))
        val zipFile = File(context.cacheDir, "import_visiopro.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read VisioPRO bundle")

        val extractDir = File(context.cacheDir, "import_visiopro_extract").apply {
            deleteRecursively()
            mkdirs()
        }
        onProgress?.invoke(TaskProgress("Extracting bundle", 15))
        ZipArchive.unzip(zipFile, extractDir) { progress ->
            val shifted = 15 + (progress.normalizedPercent * 15 / 100)
            onProgress?.invoke(progress.copy(percent = shifted))
        }
        zipFile.delete()

        val manifestFile = File(extractDir, "manifest.json")
        if (!manifestFile.exists()) error("Invalid VisioPRO bundle — missing manifest.json")
        val manifest = JSONObject(manifestFile.readText())
        if (manifest.optInt("formatVersion", 0) != VisioProBundleExporter.FORMAT_VERSION) {
            error("Unsupported VisioPRO bundle format v${manifest.optInt("formatVersion")}")
        }

        val filesDir = context.filesDir
        var imageCount = 0
        var articlesUpdated = 0

        onProgress?.invoke(TaskProgress("Restoring VisioPRO memory", 35))
        val memoryFile = File(extractDir, "visio_pro_memory.json")
        if (memoryFile.exists()) {
            val dest = File(filesDir, "visio_pro_memory.json")
            memoryFile.copyTo(dest, overwrite = true)
        }

        listOf("designs" to "visio_pro_designs", "photos" to "visio_pro_photos").forEach { (srcName, destName) ->
            val src = File(extractDir, srcName)
            if (src.exists()) {
                val dest = File(filesDir, destName)
                dest.deleteRecursively()
                imageCount += DeviceBackupExporter.copyDirectory(src, dest)
            }
        }

        val byBarcode = repository.getAllArticles().associateBy { it.barcode.trim() }
        val categoriesDir = File(extractDir, "categories")
        val categories = VisioProCategory.entries.filter { File(categoriesDir, it.name).exists() }

        categories.forEachIndexed { index, category ->
            onProgress?.invoke(TaskProgress("Importing ${category.labelFr}", 45 + (index * 40 / categories.size.coerceAtLeast(1))))
            val categoryDir = File(categoriesDir, category.name)
            restoreCategoryConfig(categoryDir, category, byBarcode)
            articlesUpdated += restoreCatalogPngs(categoryDir, byBarcode, filesDir).also { imageCount += it }
        }

        extractDir.deleteRecursively()
        imageMatcher.invalidatePngCache()

        onProgress?.invoke(TaskProgress("VisioPRO import complete", 100))
        VisioProBundleImportResult(
            categoryCount = categories.size,
            imageCount = imageCount,
            articlesUpdated = articlesUpdated,
        )
    }

    private suspend fun restoreCategoryConfig(
        categoryDir: File,
        category: VisioProCategory,
        byBarcode: Map<String, com.oasismall.oasisai.data.db.entity.ArticleEntity>,
    ) {
        val configFile = File(categoryDir, "config.json")
        if (!configFile.exists()) return
        val obj = JSONObject(configFile.readText())
        val enabled = parseBarcodeArray(obj.optJSONArray("enabledBarcodes"))
            .mapNotNull { byBarcode[it.trim()]?.id }
        val pending = parseBarcodeArray(obj.optJSONArray("pendingBarcodes"))
            .mapNotNull { byBarcode[it.trim()]?.id }
        catalogConfigStore.setCategoryConfig(
            category,
            VisioProCategoryConfig(enabledIds = enabled, pendingIds = pending),
        )

        val articlesFile = File(categoryDir, "articles.json")
        if (!articlesFile.exists()) return
        val arr = JSONArray(articlesFile.readText())
        for (i in 0 until arr.length()) {
            val row = arr.getJSONObject(i)
            val slug = row.optString("slug").trim()
            if (slug.isEmpty()) continue
            row.optDouble("manualPrice").takeIf { !row.isNull("manualPrice") }?.let { price ->
                if (row.optBoolean("manualPriceOverridden", false)) {
                    val csv = row.optDouble("price").takeIf { !row.isNull("price") } ?: price
                    visioProStore.setManualPriceOverride(slug, price, csv)
                } else {
                    visioProStore.setManualPrice(slug, price)
                }
            }
            row.optString("manualDesignation").trim().takeIf { it.isNotEmpty() }?.let { label ->
                visioProStore.setManualDesignation(slug, label)
            }
        }
    }

    private fun restoreCatalogPngs(
        categoryDir: File,
        @Suppress("UNUSED_PARAMETER") byBarcode: Map<String, com.oasismall.oasisai.data.db.entity.ArticleEntity>,
        filesDir: File,
    ): Int {
        val pngDir = File(categoryDir, "catalog_png")
        if (!pngDir.exists()) return 0
        var count = 0
        val productImages = File(filesDir, "product_images").apply { mkdirs() }
        pngDir.listFiles()?.forEach { png ->
            if (png.isFile) {
                png.copyTo(File(productImages, png.name), overwrite = true)
                count++
            }
        }
        return count
    }

    private fun parseBarcodeArray(arr: JSONArray?): List<String> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
}
