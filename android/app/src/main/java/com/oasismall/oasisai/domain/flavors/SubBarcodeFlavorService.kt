package com.oasismall.oasisai.domain.flavors

import android.content.Context
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.data.repository.remapStoredPath
import com.oasismall.oasisai.data.repository.toStoredPath
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.util.PngMetadata
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SubBarcodeArchiveResult(
    val registryEntries: Int,
    val fromDatabase: Int,
    val fromPngScan: Int,
)

data class SubBarcodeSyncResult(
    val scanned: Int,
    val subPngs: Int,
    val metadataUpdated: Int,
    val renamed: Int,
    val linked: Int,
    val parentMissing: Int,
    val skippedConflict: Int,
    val missingBarcode: Int,
)

class SubBarcodeFlavorService(
    context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
) {
    private val registry = SubBarcodeRegistry(context)
    private val filesDir = context.filesDir
    private val imagesDir = imageMatcher.getImagesDirectory()

    /** Call before Gestium purge — snapshots DB links + scans flavor PNGs on disk. */
    suspend fun archiveBeforePurge(): SubBarcodeArchiveResult = withContext(Dispatchers.IO) {
        val fromDb = archiveFromDatabase()
        val fromPng = scanFlavorPngsIntoRegistry()
        SubBarcodeArchiveResult(
            registryEntries = registry.load().size,
            fromDatabase = fromDb,
            fromPngScan = fromPng,
        )
    }

    /**
     * PNG-database-first sync: read sub-variant metadata from files, backfill legacy PNGs,
     * rename to designation+alt index, link alternates in catalog for scanner/search/carts.
     */
    suspend fun syncSubPngsFromMetadata(
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): SubBarcodeSyncResult = withContext(Dispatchers.IO) {
        val articles = repository.getAllArticles()
        val articlesByBarcode = articles.associateBy { it.barcode }
        val articlesById = articles.associateBy { it.id }
        val primaryByBarcode = articlesByBarcode
        val alts = repository.getAllAlternateBarcodes()
        val altBySubBarcode = alts.associateBy { it.barcode }
        val altByImagePath = alts.mapNotNull { alt ->
            alt.imagePath?.let { it to alt }
        }.toMap()
        val altIndexByParent = buildAltIndexUsageByParent()

        val pngs = imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", true) }
            .orEmpty()
        if (pngs.isEmpty()) {
            onProgress?.invoke(TaskProgress("No PNG files in gallery", 100))
            return@withContext SubBarcodeSyncResult(0, 0, 0, 0, 0, 0, 0, 0)
        }

        val altImagePaths = altByImagePath.keys
        var metadataUpdated = 0
        var renamed = 0
        var missingBarcode = 0
        val prepared = ArrayList<PreparedSubPng>(pngs.size)

        pngs.forEachIndexed { index, file ->
            if (index % 50 == 0 || index == pngs.lastIndex) {
                val pct = ((index + 1) * 30 / pngs.size.coerceAtLeast(1))
                onProgress?.invoke(TaskProgress("Scanning PNGs (${index + 1}/${pngs.size})", pct))
            }
            if (!PngMetadata.isSubVariantPng(file, altImagePaths)) return@forEachIndexed

            val details = runCatching { PngMetadata.readArticleDetails(file) }.getOrDefault(
                PngMetadata.PngArticleDetails(),
            )
            val alt = altByImagePath[file.absolutePath]
            val parentFromDb = alt?.let { articlesById[it.articleId] }

            val subBarcode = details.barcode
                ?: alt?.barcode
                ?: legacySubBarcodeFromFilename(file)
            val parentBarcode = details.parentBarcode ?: parentFromDb?.barcode
            val parentArticle = parentBarcode?.let { articlesByBarcode[it] } ?: parentFromDb

            if (subBarcode.isNullOrBlank()) {
                missingBarcode++
                return@forEachIndexed
            }
            if (parentArticle == null) {
                prepared += PreparedSubPng(
                    file = file,
                    subBarcode = subBarcode,
                    parentBarcode = parentBarcode,
                    parentArticle = null,
                    details = details,
                )
                return@forEachIndexed
            }

            val needsMetadata = details.parentBarcode.isNullOrBlank() ||
                details.barcode.isNullOrBlank() ||
                runCatching {
                    val chunks = PngMetadata.readAllTextChunks(file)
                    chunks[PngMetadata.KEY_VARIANT_TYPE] != "sub"
                }.getOrDefault(true)
            if (needsMetadata) {
                writeSubMetadata(file, subBarcode, parentArticle)
                metadataUpdated++
            }

            var currentFile = file
            if (shouldRenameSubFile(currentFile, parentArticle)) {
                val stem = PngMetadata.subVariantDesignationStem(parentArticle.designation)
                val altIndex = allocateAltIndex(parentArticle.barcode, stem, currentFile, altIndexByParent)
                val target = File(imagesDir, PngMetadata.subVariantFileName(stem, altIndex))
                if (target.absolutePath != currentFile.absolutePath) {
                    if (target.exists()) target.delete()
                    if (currentFile.renameTo(target)) {
                        currentFile = target
                        renamed++
                    }
                }
            }

            prepared += PreparedSubPng(
                file = currentFile,
                subBarcode = subBarcode,
                parentBarcode = parentArticle.barcode,
                parentArticle = parentArticle,
                details = details,
            )
        }

        var linked = 0
        var parentMissing = 0
        var skippedConflict = 0
        prepared.forEachIndexed { index, item ->
            val pct = 35 + ((index + 1) * 60 / prepared.size.coerceAtLeast(1))
            onProgress?.invoke(TaskProgress("Linking ${item.subBarcode}", pct))

            val parent = item.parentArticle
            if (parent == null || item.parentBarcode.isNullOrBlank()) {
                parentMissing++
                return@forEachIndexed
            }
            val ownPrimary = primaryByBarcode[item.subBarcode]
            if (ownPrimary != null && ownPrimary.id != parent.id) {
                skippedConflict++
                return@forEachIndexed
            }
            val existingAlt = altBySubBarcode[item.subBarcode]
            if (existingAlt?.articleId == parent.id &&
                existingAlt.imagePath == item.file.absolutePath
            ) {
                return@forEachIndexed
            }
            val err = repository.linkSubBarcodeToMainArticle(
                articleId = parent.id,
                mainBarcode = parent.barcode,
                subBarcode = item.subBarcode,
                imagePath = item.file.absolutePath,
            )
            if (err == null) linked++
        }

        if (linked > 0 || renamed > 0 || metadataUpdated > 0) {
            imageMatcher.invalidatePngCache()
        }
        onProgress?.invoke(TaskProgress("Sub-PNG sync complete", 100))
        SubBarcodeSyncResult(
            scanned = pngs.size,
            subPngs = prepared.size,
            metadataUpdated = metadataUpdated,
            renamed = renamed,
            linked = linked,
            parentMissing = parentMissing,
            skippedConflict = skippedConflict,
            missingBarcode = missingBarcode,
        )
    }

    /** Re-link flavor rows after CSV import (or manual Settings action). */
    suspend fun restoreLinkedFlavors(
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): SubBarcodeSyncResult = syncSubPngsFromMetadata(onProgress)

    suspend fun registryCount(): Int = registry.count()

    private fun buildAltIndexUsageByParent(): MutableMap<String, MutableSet<Int>> {
        val map = mutableMapOf<String, MutableSet<Int>>()
        imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", true) }
            ?.forEach { file ->
                val details = runCatching { PngMetadata.readArticleDetails(file) }.getOrDefault(
                    PngMetadata.PngArticleDetails(),
                )
                val parent = details.parentBarcode?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                val stem = details.designation?.let { PngMetadata.subVariantDesignationStem(it) }
                    ?: return@forEach
                PngMetadata.parseSubVariantAltIndex(file.nameWithoutExtension, stem)?.let { index ->
                    map.getOrPut(parent) { mutableSetOf() }.add(index)
                }
            }
        return map
    }

    private fun legacySubBarcodeFromFilename(file: File): String? {
        val stem = file.nameWithoutExtension
        if (!stem.startsWith("sub_")) return null
        return PngMetadata.extractBarcodeFromFilename(stem.removePrefix("sub_"))
    }

    private fun shouldRenameSubFile(file: File, parent: ArticleEntity): Boolean {
        val stem = file.nameWithoutExtension
        if (stem.startsWith("sub_")) return true
        if (PngMetadata.extractBarcodeFromFilename(stem) != null) return true
        val desStem = PngMetadata.subVariantDesignationStem(parent.designation)
        return PngMetadata.parseSubVariantAltIndex(stem, desStem) == null
    }

    private fun writeSubMetadata(file: File, subBarcode: String, parent: ArticleEntity) {
        PngMetadata.writeArticleDetails(
            file = file,
            barcode = subBarcode.trim(),
            designation = parent.designation,
            priceNow = parent.price.takeIf { it > 0.0 },
            priceBefore = parent.previousPrice,
            rayon = parent.category,
            codeart = parent.codeart,
            parentBarcode = parent.barcode,
            variantType = "sub",
        )
    }

    private fun allocateAltIndex(
        parentBarcode: String,
        designationStem: String,
        current: File,
        altIndexByParent: MutableMap<String, MutableSet<Int>>,
    ): Int {
        val used = altIndexByParent.getOrPut(parentBarcode) { mutableSetOf() }
        var index = 1
        while (used.contains(index)) index++
        used.add(index)
        return index
    }

    private suspend fun archiveFromDatabase(): Int {
        val articlesById = repository.getAllArticles().associateBy { it.id }
        val alts = repository.getAllAlternateBarcodes()
        alts.forEach { alt ->
            val parent = articlesById[alt.articleId] ?: return@forEach
            registry.upsert(
                SubBarcodeRegistryEntry(
                    subBarcode = alt.barcode,
                    parentBarcode = parent.barcode,
                    parentDesignation = parent.designation,
                    imageRelativePath = alt.imagePath?.let { toStoredPath(it, filesDir) },
                ),
            )
        }
        return alts.size
    }

    private suspend fun scanFlavorPngsIntoRegistry(): Int {
        if (!imagesDir.exists()) return 0
        val existingBySub = registry.load().associateBy { it.subBarcode }
        val pngs = imagesDir.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }.orEmpty()
        var touched = 0
        pngs.forEach { file ->
            if (!PngMetadata.isSubVariantPng(file)) return@forEach
            val details = runCatching { PngMetadata.readArticleDetails(file) }.getOrDefault(
                PngMetadata.PngArticleDetails(),
            )
            val subBarcode = details.barcode
                ?: legacySubBarcodeFromFilename(file)
                ?: return@forEach
            val parentBarcode = details.parentBarcode
                ?: existingBySub[subBarcode]?.parentBarcode
                ?: return@forEach
            if (subBarcode == parentBarcode) return@forEach

            registry.upsert(
                SubBarcodeRegistryEntry(
                    subBarcode = subBarcode,
                    parentBarcode = parentBarcode,
                    parentDesignation = details.designation ?: existingBySub[subBarcode]?.parentDesignation,
                    imageRelativePath = toStoredPath(file.absolutePath, filesDir),
                ),
            )
            touched++
        }
        return touched
    }

    private data class PreparedSubPng(
        val file: File,
        val subBarcode: String,
        val parentBarcode: String?,
        val parentArticle: ArticleEntity?,
        val details: PngMetadata.PngArticleDetails,
    )
}
