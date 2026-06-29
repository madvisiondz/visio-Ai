package com.oasismall.oasisai.domain

import android.content.Context
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ArticlePriceHistoryEntity
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.model.ImportStatus
import com.oasismall.oasisai.data.db.dao.ArticleImportSnapshot
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.OasisLog
import com.oasismall.oasisai.util.TaskProgress
import java.io.InputStream

class ImportService(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    @Suppress("unused")
    private val subBarcodeFlavorService: com.oasismall.oasisai.domain.flavors.SubBarcodeFlavorService,
) {
    suspend fun importFromStream(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): ImportResult {
        onProgress?.invoke(TaskProgress("Parsing CSV file", 5))
        return importFromParseResult(CsvParser.parseWithFallback(inputStream), fileName, onProgress)
    }

    suspend fun importFromParseResult(
        parseResult: CsvParseResult,
        fileName: String,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): ImportResult {
        return try {
            onProgress?.invoke(TaskProgress("Validating CSV columns", 8))
            val validation = CsvParser.validate(parseResult.headers)
            if (!validation.isValid) {
                return ImportResult(
                    false,
                    errorMessage = "Missing columns: ${validation.missingColumns.joinToString(", ")}",
                )
            }
            if (parseResult.rows.isEmpty()) {
                return ImportResult(false, errorMessage = "No valid rows found. Check CSV columns.")
            }

            onProgress?.invoke(TaskProgress("Creating import record", 12))
            val importId = repository.createImport(
                ImportEntity(
                    fileName = fileName,
                    status = ImportStatus.PENDING.name,
                    rowCount = parseResult.rows.size,
                ),
            )

            onProgress?.invoke(TaskProgress("Loading catalog snapshot", 10))
            val catalogMaps = buildImportCatalogMaps(repository.getArticlesImportSnapshot())
            val alternateSubBarcodes = repository.getAlternateSubBarcodeSet()
            val existingByBarcode = catalogMaps.byBarcode
            val existingByCodeart = catalogMaps.byCodeart
            val existingByNormalizedName = catalogMaps.byNormalizedName
            val incomingBarcodes = parseResult.rows.map { it.barcode }.toSet()
            val incomingCodearts = parseResult.rows
                .mapNotNull { it.codeart?.trim()?.takeIf { it.isNotEmpty() } }
                .toSet()
            val incomingNormalizedNames = parseResult.rows
                .map { NameNormalizer.normalize(it.designation) }
                .toSet()

            val rayonConfig = repository.getImportantRayonsConfig()
            val scopedToImportantRayons =
                rayonConfig.configured && rayonConfig.selectedRayons.isNotEmpty()
            var scopedRowCount = 0

            var newCount = 0
            var priceChangedCount = 0
            var renamedCount = 0
            var unchangedCount = 0
            var scopedNewCount = 0
            var scopedPriceChangedCount = 0
            var scopedRenamedCount = 0
            var scopedRemovedCount = 0
            val changes = mutableListOf<ImportChangeEntity>()
            val priceHistory = mutableListOf<ArticlePriceHistoryEntity>()
            val articlesToSave = mutableListOf<ArticleEntity>()
            val newImportBarcodes = mutableSetOf<String>()
            val totalRows = parseResult.rows.size.coerceAtLeast(1)

            parseResult.rows.forEachIndexed { index, row ->
                val normalized = NameNormalizer.normalize(row.designation)
                val inImportantRayon = repository.matchesImportantRayon(row.rayon, rayonConfig)
                if (scopedToImportantRayons && inImportantRayon) scopedRowCount++

                val existing = existingByBarcode[row.barcode]
                    ?: row.codeart?.trim()?.takeIf { it.isNotEmpty() }?.let { existingByCodeart[it] }
                    ?: existingByNormalizedName[normalized]

                if (existing == null) {
                    newCount++
                    newImportBarcodes.add(row.barcode)
                    if (inImportantRayon) scopedNewCount++
                    val article = row.toEntity(normalized, importId).copy(
                        changeStatus = ArticleChangeStatus.NEW.name,
                        needsTicketUpdate = true,
                    )
                    articlesToSave.add(article)
                    changes.add(
                        ImportChangeEntity(
                            importId = importId,
                            articleId = null,
                            barcode = row.barcode,
                            designation = row.designation,
                            changeType = ImportChangeType.NEW.name,
                            newValue = row.price.toString(),
                        ),
                    )
                } else {
                    val priceChanged = existing.price != row.price
                    val renamed = existing.normalizedName != normalized
                    val reappeared = !existing.isActive
                    val status = when {
                        reappeared -> ArticleChangeStatus.NEW
                        priceChanged -> ArticleChangeStatus.PRICE_CHANGED
                        renamed -> ArticleChangeStatus.RENAMED
                        else -> ArticleChangeStatus.UNCHANGED
                    }

                    if (priceChanged) {
                        priceChangedCount++
                        if (inImportantRayon) scopedPriceChangedCount++
                    }
                    if (renamed) {
                        renamedCount++
                        if (inImportantRayon) scopedRenamedCount++
                    }
                    if (!priceChanged && !renamed && !reappeared) {
                        unchangedCount++
                    } else {
                        val updated = existing.toUpdatedEntity(
                            row = row,
                            normalized = normalized,
                            importId = importId,
                            status = status,
                            priceChanged = priceChanged,
                            reactivate = reappeared,
                        )
                        articlesToSave.add(updated)

                        if (priceChanged) {
                            priceHistory.add(
                                ArticlePriceHistoryEntity(
                                    articleId = existing.id,
                                    oldPrice = existing.price,
                                    newPrice = row.price,
                                    importId = importId,
                                ),
                            )
                            changes.add(
                                ImportChangeEntity(
                                    importId = importId,
                                    articleId = existing.id,
                                    barcode = row.barcode,
                                    designation = row.designation,
                                    changeType = ImportChangeType.PRICE_CHANGED.name,
                                    oldValue = existing.price.toString(),
                                    newValue = row.price.toString(),
                                ),
                            )
                        } else if (renamed) {
                            changes.add(
                                ImportChangeEntity(
                                    importId = importId,
                                    articleId = existing.id,
                                    barcode = row.barcode,
                                    designation = row.designation,
                                    changeType = ImportChangeType.RENAMED.name,
                                    oldValue = existing.designation,
                                    newValue = row.designation,
                                ),
                            )
                        }
                    }
                    // Unchanged rows are counted only — no DB write (avoids ~20k updates per import).
                }
                if (index == 0 || index % 500 == 0 || index == totalRows - 1) {
                    val percent = 15 + ((index + 1) * 45 / totalRows)
                    onProgress?.invoke(
                        TaskProgress(
                            "Comparing CSV rows (${index + 1}/$totalRows)",
                            percent,
                        ),
                    )
                }
            }

            onProgress?.invoke(TaskProgress("Detecting removed articles", 62))
            val removed = existingByBarcode.values.filter { article ->
                if (!article.isActive) return@filter false
                if (article.barcode in alternateSubBarcodes) return@filter false
                if (article.barcode in incomingBarcodes) return@filter false
                val codeart = article.codeart?.trim().orEmpty()
                if (codeart.isNotEmpty() && codeart in incomingCodearts) return@filter false
                if (article.normalizedName in incomingNormalizedNames) return@filter false
                true
            }
            val removedCount = removed.size
            removed.forEach { article ->
                if (repository.matchesImportantRayon(article.rayon, rayonConfig)) {
                    scopedRemovedCount++
                }
                articlesToSave.add(
                    article.toRemovedEntity(importId),
                )
                changes.add(
                    ImportChangeEntity(
                        importId = importId,
                        articleId = article.id,
                        barcode = article.barcode,
                        designation = article.designation,
                        changeType = ImportChangeType.REMOVED.name,
                        oldValue = article.price.toString(),
                    ),
                )
            }

            onProgress?.invoke(TaskProgress("Saving articles and change log", 75))
            val savedArticles = repository.saveImportResults(
                articles = articlesToSave,
                priceHistory = priceHistory,
                changes = changes,
                importUpdate = ImportEntity(
                    id = importId,
                    fileName = fileName,
                    importedAt = System.currentTimeMillis(),
                    rowCount = parseResult.rows.size,
                    status = ImportStatus.COMPLETED.name,
                    newCount = newCount,
                    priceChangedCount = priceChangedCount,
                    removedCount = removedCount,
                    renamedCount = renamedCount,
                ),
            )

            val newArticlesForImages = savedArticles.filter { it.isActive && it.barcode in newImportBarcodes }
            if (newArticlesForImages.isNotEmpty()) {
                onProgress?.invoke(TaskProgress("Re-indexing product images", 90))
                imageMatcher.upsertImagesForArticles(newArticlesForImages) { progress ->
                    val shiftedPercent = 90 + (progress.normalizedPercent * 8 / 100)
                    onProgress?.invoke(progress.copy(percent = shiftedPercent))
                }
            }
            val missingImages = repository.countMissingImages()

            // Sub-barcode links live in Room + registry — full PNG re-scan belongs in Settings → Sync sub-PNGs.
            if (!scopedToImportantRayons) {
                scopedRowCount = parseResult.rows.size
            }

            onProgress?.invoke(TaskProgress("Import complete", 100))
            val result = ImportResult(
                success = true,
                importId = importId,
                summary = ImportDiffSummary(
                    importId = importId,
                    newCount = newCount,
                    priceChangedCount = priceChangedCount,
                    renamedCount = renamedCount,
                    removedCount = removedCount,
                    unchangedCount = unchangedCount,
                    missingImagesCount = missingImages,
                    scopedToImportantRayons = scopedToImportantRayons,
                    importantRayonsCount = rayonConfig.selectedRayons.size,
                    scopedRowCount = scopedRowCount,
                    scopedNewCount = scopedNewCount,
                    scopedPriceChangedCount = scopedPriceChangedCount,
                    scopedRenamedCount = scopedRenamedCount,
                    scopedRemovedCount = scopedRemovedCount,
                ),
            )
            result
        } catch (e: Exception) {
            OasisLog.e(OasisLog.Domain.Import, "CSV import failed", e)
            ImportResult(false, errorMessage = e.message ?: "Import failed")
        }
    }

    suspend fun importSample(
        context: Context,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): ImportResult {
        context.assets.open("sample_articles.csv").use { stream ->
            return importFromStream(stream, "sample_articles.csv", onProgress)
        }
    }

    private fun ParsedArticleRow.toEntity(normalized: String, importId: Long) = ArticleEntity(
        barcode = barcode,
        designation = designation,
        normalizedName = normalized,
        price = price,
        codeart = codeart,
        reference = reference,
        category = category,
        rayon = rayon,
        famille = famille,
        brand = brand,
        stock = stock,
        unit = unit,
        rawData = null,
        sourceImportId = importId,
        changeStatus = ArticleChangeStatus.NEW.name,
        needsTicketUpdate = true,
    )
}

private fun ArticleImportSnapshot.toUpdatedEntity(
    row: ParsedArticleRow,
    normalized: String,
    importId: Long,
    status: ArticleChangeStatus,
    priceChanged: Boolean,
    reactivate: Boolean = false,
): ArticleEntity = ArticleEntity(
    id = id,
    barcode = barcode,
    designation = row.designation,
    normalizedName = normalized,
    price = row.price,
    previousPrice = if (priceChanged) price else previousPrice,
    codeart = row.codeart ?: codeart,
    reference = row.reference ?: reference,
    category = row.category ?: category,
    rayon = row.rayon ?: rayon,
    famille = row.famille ?: famille,
    brand = row.brand ?: brand,
    stock = row.stock ?: stock,
    unit = row.unit ?: unit,
    rawData = null,
    sourceImportId = importId,
    lastSeenAt = System.currentTimeMillis(),
    changeStatus = status.name,
    isActive = true,
    needsTicketUpdate = priceChanged || reactivate,
)

private fun ArticleImportSnapshot.toRemovedEntity(importId: Long): ArticleEntity = ArticleEntity(
    id = id,
    barcode = barcode,
    designation = designation,
    normalizedName = normalizedName,
    price = price,
    previousPrice = previousPrice,
    codeart = codeart,
    reference = reference,
    category = category,
    rayon = rayon,
    famille = famille,
    brand = brand,
    stock = stock,
    unit = unit,
    rawData = null,
    sourceImportId = importId,
    lastSeenAt = lastSeenAt,
    changeStatus = ArticleChangeStatus.REMOVED.name,
    isActive = false,
    needsTicketUpdate = needsTicketUpdate,
)
