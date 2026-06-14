package com.oasismall.oasisai.domain

import android.content.Context
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ArticlePriceHistoryEntity
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.model.ImportStatus
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.TaskProgress
import java.io.InputStream

class ImportService(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
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

            val existingByBarcode = repository.getAllArticles().associateBy { it.barcode }
            val incomingBarcodes = parseResult.rows.map { it.barcode }.toSet()

            var newCount = 0
            var priceChangedCount = 0
            var renamedCount = 0
            var unchangedCount = 0
            val changes = mutableListOf<ImportChangeEntity>()
            val priceHistory = mutableListOf<ArticlePriceHistoryEntity>()
            val articlesToSave = mutableListOf<ArticleEntity>()
            val totalRows = parseResult.rows.size.coerceAtLeast(1)

            parseResult.rows.forEachIndexed { index, row ->
                val normalized = NameNormalizer.normalize(row.designation)
                val existing = existingByBarcode[row.barcode]

                if (existing == null) {
                    newCount++
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
                    val status = when {
                        priceChanged -> ArticleChangeStatus.PRICE_CHANGED
                        renamed -> ArticleChangeStatus.RENAMED
                        else -> ArticleChangeStatus.UNCHANGED
                    }

                    if (priceChanged) priceChangedCount++
                    if (renamed) renamedCount++
                    if (!priceChanged && !renamed) unchangedCount++

                    val updated = existing.copy(
                        designation = row.designation,
                        normalizedName = normalized,
                        price = row.price,
                        previousPrice = if (priceChanged) existing.price else existing.previousPrice,
                        codeart = row.codeart ?: existing.codeart,
                        reference = row.reference ?: existing.reference,
                        category = row.category ?: existing.category,
                        brand = row.brand ?: existing.brand,
                        stock = row.stock ?: existing.stock,
                        unit = row.unit ?: existing.unit,
                        rawData = row.rawData ?: existing.rawData,
                        sourceImportId = importId,
                        lastSeenAt = System.currentTimeMillis(),
                        changeStatus = status.name,
                        isActive = true,
                        needsTicketUpdate = priceChanged || status == ArticleChangeStatus.NEW,
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
                    } else {
                        changes.add(
                            ImportChangeEntity(
                                importId = importId,
                                articleId = existing.id,
                                barcode = row.barcode,
                                designation = row.designation,
                                changeType = ImportChangeType.UNCHANGED.name,
                            ),
                        )
                    }
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
            val removed = existingByBarcode.values.filter { it.barcode !in incomingBarcodes && it.isActive }
            val removedCount = removed.size
            removed.forEach { article ->
                articlesToSave.add(
                    article.copy(
                        isActive = false,
                        changeStatus = ArticleChangeStatus.REMOVED.name,
                        sourceImportId = importId,
                    ),
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
            repository.saveImportResults(
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

            onProgress?.invoke(TaskProgress("Re-indexing product images", 90))
            imageMatcher.syncImagesForArticles(repository.getAllArticles()) { progress ->
                val shiftedPercent = 90 + (progress.normalizedPercent * 8 / 100)
                onProgress?.invoke(progress.copy(percent = shiftedPercent))
            }
            val missingImages = repository.countMissingImages()

            onProgress?.invoke(TaskProgress("Import complete", 100))
            ImportResult(
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
                ),
            )
        } catch (e: Exception) {
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
        brand = brand,
        stock = stock,
        unit = unit,
        rawData = rawData,
        sourceImportId = importId,
        changeStatus = ArticleChangeStatus.NEW.name,
        needsTicketUpdate = true,
    )
}
