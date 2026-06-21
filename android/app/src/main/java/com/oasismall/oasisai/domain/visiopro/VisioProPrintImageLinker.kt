package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VisioProPrintLinkResult(
    val scanned: Int,
    val linked: Int,
    val skippedNoPhoto: Int,
    val skippedNoArticle: Int,
)

/**
 * Copies VisioPRO **print-tab** JPEGs into the main product PNG gallery so To share / Design see them.
 */
class VisioProPrintImageLinker(
    context: Context,
    private val repository: OasisRepository,
    private val catalogService: VisioProCatalogService,
    private val photoStore: VisioProPhotoStore,
    private val imageMatcher: ImageMatcher,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheDir = context.cacheDir

    suspend fun runInitialScanIfNeeded(
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): VisioProPrintLinkResult? {
        if (prefs.getBoolean(KEY_INITIAL_SCAN_DONE, false)) return null
        val result = linkAllPrintPhotosToCatalog(onProgress = onProgress)
        prefs.edit().putBoolean(KEY_INITIAL_SCAN_DONE, true).apply()
        return result
    }

    fun markInitialScanPending() {
        prefs.edit().putBoolean(KEY_INITIAL_SCAN_DONE, false).apply()
    }

    suspend fun linkAllPrintPhotosToCatalog(
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): VisioProPrintLinkResult = withContext(Dispatchers.IO) {
        var scanned = 0
        var linked = 0
        var skippedNoPhoto = 0
        val processedArticleIds = mutableSetOf<Long>()
        val categories = VisioProCategory.entries
        val poolTotal = categories.sumOf { catalogService.poolArticles(it).size }.coerceAtLeast(1)
        var poolIndex = 0

        categories.forEach { category ->
            val pool = catalogService.poolArticles(category)
            pool.forEach { articleRow ->
                poolIndex++
                scanned++
                val def = VisioProArticleDefFactory.fromCatalogArticle(articleRow, category)
                if (!photoStore.hasPhoto(def.slug, VisioProChannel.PRINT)) {
                    skippedNoPhoto++
                    reportProgress(onProgress, poolIndex, poolTotal, "Scanning ${articleRow.designation}")
                    return@forEach
                }
                if (articleRow.id in processedArticleIds) {
                    reportProgress(onProgress, poolIndex, poolTotal, "Already linked ${articleRow.designation}")
                    return@forEach
                }
                val article = repository.getArticleById(articleRow.id)
                if (article == null) {
                    reportProgress(onProgress, poolIndex, poolTotal, articleRow.designation)
                    return@forEach
                }
                if (linkPrintPhoto(article, def.slug)) {
                    linked++
                    processedArticleIds.add(article.id)
                }
                reportProgress(onProgress, poolIndex, poolTotal, "Linked ${article.designation}")
            }
        }

        var skippedNoArticle = 0
        val (orphanLinked, orphanSkipped) = linkOrphanPrintFiles(processedArticleIds) { label, index, total ->
            reportProgress(onProgress, poolIndex + index, poolTotal + total, label)
        }
        linked += orphanLinked
        skippedNoArticle += orphanSkipped

        imageMatcher.invalidatePngCache()
        onProgress?.invoke(TaskProgress("Print photos linked", 100))
        VisioProPrintLinkResult(
            scanned = scanned,
            linked = linked,
            skippedNoPhoto = skippedNoPhoto,
            skippedNoArticle = skippedNoArticle,
        )
    }

    suspend fun linkForArticleDef(def: VisioProArticleDef): Boolean {
        if (!photoStore.hasPhoto(def.slug, VisioProChannel.PRINT)) return false
        val articleId = def.catalogArticleId
            ?: repository.findArticleIdForVisioPro(
                csvDesignation = def.csvDesignation,
                barcodeSuffix = def.barcodeSuffix,
                keywords = def.designationKeywords,
            )
            ?: return false
        val article = repository.getArticleById(articleId) ?: return false
        return linkPrintPhoto(article, def.slug)
    }

    private suspend fun linkPrintPhoto(article: ArticleEntity, slug: String): Boolean {
        val jpeg = resolvePrintPhotoFile(slug) ?: return false
        val staging = File(cacheDir, "visio_pro_print_${article.id}_${System.currentTimeMillis()}.png")
        return try {
            if (!jpegToPng(jpeg, staging)) return false
            imageMatcher.registerCapturedImage(article, staging)
            true
        } catch (_: Exception) {
            false
        } finally {
            staging.delete()
        }
    }

    private fun resolvePrintPhotoFile(slug: String): File? {
        val channelFile = photoStore.photoFile(slug, VisioProChannel.PRINT)
        if (channelFile.exists() && channelFile.length() > 0L) return channelFile
        return null
    }

    /** Print-tab JPEGs whose slug maps to a catalog article outside the rayon pool query. */
    private suspend fun linkOrphanPrintFiles(
        alreadyLinked: Set<Long>,
        onItem: (label: String, index: Int, total: Int) -> Unit,
    ): Pair<Int, Int> {
        val printDir = photoStore.photoFile("_", VisioProChannel.PRINT).parentFile
        if (printDir == null || !printDir.exists()) return 0 to 0
        val files = printDir.listFiles()?.filter { it.isFile && it.extension.equals("jpg", true) }.orEmpty()
        if (files.isEmpty()) return 0 to 0
        var linked = 0
        var skippedNoArticle = 0
        val slugIndex = buildSlugIndex()
        files.forEachIndexed { index, file ->
            val slug = file.nameWithoutExtension
            val def = slugIndex[slug]
            if (def == null) {
                skippedNoArticle++
                onItem(file.name, index + 1, files.size)
                return@forEachIndexed
            }
            val articleId = def.catalogArticleId
                ?: repository.findArticleIdForVisioPro(
                    csvDesignation = def.csvDesignation,
                    barcodeSuffix = def.barcodeSuffix,
                    keywords = def.designationKeywords,
                )
            if (articleId == null || articleId in alreadyLinked) {
                skippedNoArticle++
                onItem(file.name, index + 1, files.size)
                return@forEachIndexed
            }
            val article = repository.getArticleById(articleId)
            if (article == null || !linkPrintPhoto(article, slug)) {
                skippedNoArticle++
            } else {
                linked++
            }
            onItem(file.name, index + 1, files.size)
        }
        return linked to skippedNoArticle
    }

    private suspend fun buildSlugIndex(): Map<String, VisioProArticleDef> = buildMap {
        VisioProCategory.entries.forEach { category ->
            catalogService.resolveCategoryCatalog(category).defs.forEach { def ->
                putIfAbsent(def.slug, def)
            }
            catalogService.poolArticles(category).forEach { row ->
                val def = VisioProArticleDefFactory.fromCatalogArticle(row, category)
                putIfAbsent(def.slug, def)
            }
        }
    }

    private fun jpegToPng(jpeg: File, png: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(jpeg.absolutePath) ?: return false
        png.parentFile?.mkdirs()
        png.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                bitmap.recycle()
                return false
            }
        }
        bitmap.recycle()
        return true
    }

    private fun reportProgress(
        onProgress: ((TaskProgress) -> Unit)?,
        index: Int,
        total: Int,
        label: String,
    ) {
        val pct = (index * 95 / total.coerceAtLeast(1)).coerceIn(0, 95)
        onProgress?.invoke(TaskProgress(label, pct))
    }

    companion object {
        private const val PREFS_NAME = "visio_pro_print_linker"
        private const val KEY_INITIAL_SCAN_DONE = "initial_scan_v1_done"
    }
}
