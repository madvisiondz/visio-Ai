package com.oasismall.oasisai.domain

import android.content.Context
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ProductImageEntity
import com.oasismall.oasisai.data.model.ImageStatus
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.PngMetadata
import com.oasismall.oasisai.util.TaskProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

data class IndexedPng(
    val file: File,
    val designationKey: String,
    val barcode: String?,
    val codeart: String?,
    val isOasisReadyModel: Boolean,
)

class ImageMatcher(
    private val context: Context,
    private val repository: OasisRepository,
) {
    private val imagesDir: File
        get() = File(context.filesDir, "product_images").also { it.mkdirs() }

    fun getImagesDirectory(): File = imagesDir

    /** Fast count — no PNG chunk reads (safe for Settings overview). */
    fun countPngFiles(): Int =
        imagesDir.listFiles()?.count { it.isFile && it.extension.equals("png", true) } ?: 0

    suspend fun syncImagesForArticles(
        articles: List<ArticleEntity>,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val activeArticles = articles.filter { it.isActive }
        if (activeArticles.isEmpty()) {
            onProgress?.invoke(TaskProgress("Import CSV first — no articles in database", 100))
            return@withContext
        }
        val total = activeArticles.size
        onProgress?.invoke(TaskProgress("Indexing PNG files", 5))
        val indexed = scanPngFilesIndexed(onProgress)
        val byCodeart = indexed
            .filter { !it.codeart.isNullOrBlank() }
            .groupBy { it.codeart.orEmpty() }
        val byBarcode = indexed
            .filter { !it.barcode.isNullOrBlank() }
            .groupBy { it.barcode.orEmpty() }
        val byDesignation = indexed.groupBy { it.designationKey }
        val existingImages = repository.getProductImagesSnapshot().associateBy { it.articleId }
        val imageRows = ArrayList<ProductImageEntity>(total)

        activeArticles.forEachIndexed { index, article ->
            coroutineContext.ensureActive()
            val matchEntries = findMatchEntries(article, byCodeart, byBarcode, byDesignation)
            val chosen = matchEntries.firstOrNull()
            val status = when {
                matchEntries.isEmpty() -> ImageStatus.MISSING
                matchEntries.size == 1 -> ImageStatus.FOUND
                else -> ImageStatus.MULTIPLE_MATCHES
            }
            val existing = existingImages[article.id]
            val createdAt = chosen?.file?.lastModified()?.takeIf { it > 0L }
                ?: existing?.createdAt?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            imageRows.add(
                ProductImageEntity(
                    articleId = article.id,
                    designationKey = article.normalizedName,
                    barcode = chosen?.barcode ?: article.barcode,
                    imagePath = chosen?.file?.absolutePath ?: "",
                    imageStatus = status.name,
                    createdAt = createdAt,
                    lastSentAt = existing?.lastSentAt,
                ),
            )
            if (index % 400 == 0 || index == total - 1) {
                yield()
                val percent = 10 + ((index + 1) * 75 / total.coerceAtLeast(1))
                onProgress?.invoke(TaskProgress("Matching images (${index + 1}/$total)", percent))
            }
        }
        onProgress?.invoke(TaskProgress("Saving image index", 90))
        repository.replaceProductImagesBatched(imageRows)
        onProgress?.invoke(TaskProgress("Image re-index complete", 100))
    }

    suspend fun scanPngFilesIndexed(
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): List<IndexedPng> = withContext(Dispatchers.IO) {
        val files = imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", true) }
            .orEmpty()
        val total = files.size.coerceAtLeast(1)
        val out = ArrayList<IndexedPng>(files.size)
        files.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            out.add(indexPngFile(file))
            if (index % 50 == 0 || index == files.lastIndex) {
                yield()
                val pct = 5 + (index * 10 / total)
                onProgress?.invoke(TaskProgress("Reading PNG tags (${index + 1}/$total)", pct))
            }
        }
        out
    }

    fun resolveTargetFile(article: ArticleEntity): File {
        val key = NameNormalizer.toFileKey(article.designation)
        val base = File(imagesDir, "$key.png")
        if (!base.exists()) return base
        val tagged = runCatching { PngMetadata.readBarcode(base) }.getOrNull()
        if (tagged == null || tagged == article.barcode) return base
        return File(imagesDir, "${key}_${article.barcode}.png")
    }

    /** @deprecated Prefer [scanPngFilesIndexed] on a background dispatcher. */
    fun scanPngFiles(): List<IndexedPng> =
        imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", true) }
            ?.map { indexPngFile(it) }
            .orEmpty()

    private fun indexPngFile(file: File): IndexedPng {
        val details = runCatching { PngMetadata.readArticleDetails(file) }.getOrNull()
            ?: PngMetadata.PngArticleDetails()
        val stem = file.nameWithoutExtension
        val designationStem = PngMetadata.stemWithoutBarcodeSuffix(stem)
        val designationKey = details.designation?.let { NameNormalizer.normalize(it) }
            ?: NameNormalizer.normalize(designationStem.replace("_", " "))
        val barcode = details.barcode
            ?: PngMetadata.extractBarcodeFromFilename(stem)
        val codeart = details.codeart
        return IndexedPng(
            file = file,
            designationKey = designationKey,
            barcode = barcode,
            codeart = codeart,
            isOasisReadyModel = ReadyPngModel.isComplete(details),
        )
    }

    suspend fun linkImageForArticle(article: ArticleEntity, indexed: List<IndexedPng>? = null) {
        val all = indexed ?: scanPngFilesIndexed()
        val matches = findMatchEntries(article, all)
        val status = when {
            matches.isEmpty() -> ImageStatus.MISSING
            matches.size == 1 -> ImageStatus.FOUND
            else -> ImageStatus.MULTIPLE_MATCHES
        }
        val chosen = matches.firstOrNull()
        repository.saveProductImage(
            ProductImageEntity(
                articleId = article.id,
                designationKey = article.normalizedName,
                barcode = chosen?.barcode ?: article.barcode,
                imagePath = chosen?.file?.absolutePath ?: "",
                imageStatus = status.name,
            ),
        )
    }

    fun findMatchesForArticle(article: ArticleEntity, indexed: List<IndexedPng>): List<File> =
        findMatchEntries(article, indexed).map { it.file }

    private fun findMatchEntries(
        article: ArticleEntity,
        indexed: List<IndexedPng>,
    ): List<IndexedPng> {
        val byCodeart = indexed.filter { !it.codeart.isNullOrBlank() }.groupBy { it.codeart.orEmpty() }
        val byBarcode = indexed.filter { !it.barcode.isNullOrBlank() }.groupBy { it.barcode.orEmpty() }
        val byDesignation = indexed.groupBy { it.designationKey }
        return findMatchEntries(article, byCodeart, byBarcode, byDesignation)
    }

    private fun findMatchEntries(
        article: ArticleEntity,
        byCodeart: Map<String, List<IndexedPng>>,
        byBarcode: Map<String, List<IndexedPng>>,
        byDesignation: Map<String, List<IndexedPng>>,
    ): List<IndexedPng> {
        val codeart = article.codeart?.trim().orEmpty()
        if (codeart.isNotEmpty()) {
            val codeartMatches = byCodeart[codeart].orEmpty()
            if (codeartMatches.isNotEmpty()) return codeartMatches.distinctBy { it.file.absolutePath }
        }
        val barcodeMatches = byBarcode[article.barcode].orEmpty()
        if (barcodeMatches.isNotEmpty()) return barcodeMatches.distinctBy { it.file.absolutePath }
        val designationMatches = byDesignation[article.normalizedName].orEmpty()
        val resolved = designationMatches.filter { entry ->
            entry.barcode == null || entry.barcode == article.barcode
        }
        if (resolved.size == 1) return listOf(resolved.first())
        if (resolved.size > 1) return resolved.distinctBy { it.file.absolutePath }
        return emptyList()
    }

    suspend fun registerCapturedImage(article: ArticleEntity, sourceFile: File): ProductImageEntity {
        val target = resolveTargetFile(article)
        return registerCapturedImageAtTarget(article, sourceFile, target)
    }

    /** Saves transparent cutout to product_images; keeps original backup path in DB. */
    suspend fun registerBackgroundRemovedImage(
        articleId: Long,
        cutoutFile: File,
        originalBackupPath: String,
    ): ProductImageEntity {
        val article = repository.getArticleById(articleId)
            ?: error("Article not found")
        val target = resolveTargetFile(article)
        return registerCapturedImageAtTarget(
            article = article,
            sourceFile = cutoutFile,
            target = target,
            originalImagePath = originalBackupPath,
        )
    }

    /** Save PNG as `{barcode}.png` when the article is not in the Gestium CSV yet. */
    suspend fun registerCapturedImageByBarcode(barcode: String, sourceFile: File): ProductImageEntity {
        val trimmed = barcode.trim()
        val article = repository.ensureBarcodeOnlyArticle(trimmed)
        val target = resolveTargetFileByBarcode(trimmed)
        sourceFile.copyTo(target, overwrite = true)
        PngMetadata.writeArticleDetails(
            file = target,
            barcode = trimmed,
            designation = trimmed,
            priceNow = null,
            priceBefore = null,
            rayon = null,
            codeart = null,
        )
        val entity = ProductImageEntity(
            articleId = article.id,
            designationKey = article.normalizedName,
            barcode = trimmed,
            imagePath = target.absolutePath,
            imageStatus = ImageStatus.FOUND.name,
            createdAt = target.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        repository.saveProductImage(entity)
        return entity
    }

    fun resolveTargetFileByBarcode(barcode: String): File {
        val key = PngMetadata.barcodeFileStem(barcode)
        return File(imagesDir, "$key.png")
    }

    private suspend fun registerCapturedImageAtTarget(
        article: ArticleEntity,
        sourceFile: File,
        target: File,
        originalImagePath: String? = null,
    ): ProductImageEntity {
        if (sourceFile.absolutePath != target.absolutePath) {
            sourceFile.copyTo(target, overwrite = true)
        }
        PngMetadata.writeArticleDetails(
            file = target,
            barcode = article.barcode,
            designation = article.designation,
            priceNow = article.price.takeIf { it > 0.0 },
            priceBefore = article.previousPrice,
            rayon = article.category,
            codeart = article.codeart,
        )
        val entity = ProductImageEntity(
            articleId = article.id,
            designationKey = article.normalizedName,
            barcode = article.barcode,
            imagePath = target.absolutePath,
            imageStatus = ImageStatus.FOUND.name,
            originalImagePath = originalImagePath,
            createdAt = target.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        repository.saveProductImage(entity)
        return entity
    }

    fun ensureArticleMetadata(file: File, article: ArticleEntity) {
        if (!file.isFile) return
        val existing = runCatching { PngMetadata.readArticleDetails(file) }.getOrNull()
        if (existing != null && ReadyPngModel.isComplete(existing)) return
        runCatching {
            PngMetadata.writeArticleDetails(
                file = file,
                barcode = article.barcode,
                designation = article.designation,
                priceNow = article.price,
                priceBefore = article.previousPrice,
                rayon = article.category,
                codeart = article.codeart,
            )
        }
    }
}
