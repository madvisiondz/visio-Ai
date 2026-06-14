package com.oasismall.oasisai.data.repository

import com.oasismall.oasisai.data.db.OasisDatabase
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.db.dao.PhoneSyncAlternatePair
import com.oasismall.oasisai.data.db.dao.PhoneSyncCatalogRow
import com.oasismall.oasisai.data.db.dao.PhoneSyncPushSourceRow
import com.oasismall.oasisai.data.db.dao.DashboardStats
import com.oasismall.oasisai.data.db.dao.ImageHistoryItem
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.dao.WorkflowHistoryItem
import com.oasismall.oasisai.data.db.entity.ArticleAlternateBarcodeEntity
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ArticlePriceHistoryEntity
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.db.entity.PreselectionItemEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.db.entity.PrintTemplateEntity
import com.oasismall.oasisai.data.db.entity.ProductImageEntity
import com.oasismall.oasisai.data.db.entity.PromoAlertEntity
import com.oasismall.oasisai.data.db.entity.WorkflowHistoryEntity
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.model.PrintBatchStatus
import com.oasismall.oasisai.data.model.TemplateType
import com.oasismall.oasisai.util.BarcodeSuffixMatcher
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.SearchQuery
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class OasisRepository(private val db: OasisDatabase) {

    fun observeDashboardStats(): Flow<DashboardStats> {
        val core = combine(
            db.articleDao().observeActiveCount(),
            db.articleDao().observeNeedsTicketCount(),
            db.productImageDao().observeMissingCount(),
        ) { activeCount, needsTicket, missingImages ->
            Triple(activeCount, needsTicket, missingImages)
        }
        return combine(
            core,
            db.preselectionDao().observeCount(CartType.PHOTOSHOOT.name),
            db.preselectionDao().observeCount(CartType.SHARE.name),
            observePrintBatches(),
        ) { triple, photoshootCount, shareCount, batches ->
            val (activeCount, needsTicket, missingImages) = triple
            val now = System.currentTimeMillis()
            DashboardStats(
                totalArticles = activeCount,
                activeArticles = activeCount,
                needsTicket = needsTicket,
                missingImages = missingImages,
                preselectionCount = photoshootCount + shareCount,
                activePromos = batches.count { it.isPromo && (it.promoEnd ?: 0) >= now },
                expiredPromos = batches.count { it.isPromo && (it.promoEnd ?: Long.MAX_VALUE) < now },
            )
        }
    }

    fun observeArticles(query: String): Flow<List<ArticleWithImage>> {
        val search = SearchQuery.prepare(query) ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.articleDao().searchWithImages(search.sqlPattern).map { articles ->
            articles
                .filter { SearchQuery.matches(it, search) }
                .sortedWith(compareBy({ SearchQuery.score(it, search) }, { it.designation }))
                .take(200)
        }
    }

    suspend fun searchArticlesForPicker(query: String, limit: Int = 40): List<ArticleWithImage> {
        val search = SearchQuery.prepare(query) ?: return emptyList()
        return db.articleDao().searchWithImages(search.sqlPattern).first()
            .filter { SearchQuery.matches(it, search) }
            .sortedWith(compareBy({ SearchQuery.score(it, search) }, { it.designation }))
            .take(limit)
    }

    fun observeNeedsTicket(): Flow<List<ArticleWithImage>> = db.articleDao().observeNeedsTicket()
    fun observeMissingImages(): Flow<List<ArticleWithImage>> = db.articleDao().observeMissingImages()

    fun observeMissingImagesLimited(): Flow<List<ArticleWithImage>> =
        db.articleDao().observeMissingImagesLimited()

    fun searchMissingImages(query: String): Flow<List<ArticleWithImage>> =
        db.articleDao().searchMissingImages(SearchQuery.escapeLikePattern(query))

    fun observeMissingImageCount(): Flow<Int> = db.productImageDao().observeMissingCount()
    fun observeNewArticles(): Flow<List<ArticleWithImage>> = db.articleDao().observeNewArticles()
    fun observePriceChanged(): Flow<List<ArticleWithImage>> = db.articleDao().observePriceChanged()
    fun observeImports(): Flow<List<ImportEntity>> = db.importDao().observeAll()
    fun observeImportChanges(importId: Long): Flow<List<ImportChangeEntity>> =
        db.importChangeDao().observeByImport(importId)

    fun observeRecentCsvChanges(limit: Int = 300): Flow<List<ImportChangeEntity>> =
        db.importChangeDao().observeRecentChanges(limit)

    fun observeDesignShelfPrints(limit: Int = 50): Flow<List<PrintBatchEntity>> =
        db.printBatchDao().observeDesignShelfPrints(limit)

    fun observeCart(cartType: CartType): Flow<List<PreselectionWithArticle>> =
        db.preselectionDao().observeWithArticles(cartType.name)

    fun observeCartCount(cartType: CartType): Flow<Int> =
        db.preselectionDao().observeCount(cartType.name)

    fun observeImageHistory(): Flow<List<ImageHistoryItem>> =
        db.productImageDao().observeImageHistory()

    fun observeWorkflowHistory(): Flow<List<WorkflowHistoryItem>> =
        db.workflowHistoryDao().observeLatest()

    /** @deprecated Use observeCart — kept for legacy print screen */
    fun observePreselection(): Flow<List<PreselectionWithArticle>> =
        observeCart(CartType.SHARE)

    fun observePreselectionCount(): Flow<Int> =
        observeCartCount(CartType.SHARE).map { it }

    fun observeTemplates(): Flow<List<PrintTemplateEntity>> = db.printTemplateDao().observeAll()
    fun observePrintBatches(): Flow<List<PrintBatchEntity>> = db.printBatchDao().observeAll()
    fun observePromoBatches(): Flow<List<PrintBatchEntity>> = db.printBatchDao().observePromoBatches()
    fun observePendingPromoAlerts(): Flow<List<PromoAlertEntity>> = db.promoAlertDao().observePending()

    suspend fun getArticleById(id: Long): ArticleEntity? = db.articleDao().getById(id)
    suspend fun getArticleWithImageById(id: Long): ArticleWithImage? = db.articleDao().getWithImageById(id)
    suspend fun getArticleWithImageByBarcode(barcode: String): ArticleWithImage? {
        val trimmed = barcode.trim()
        db.articleDao().getWithImageByBarcode(trimmed)?.let { return it }
        val alt = db.articleAlternateBarcodeDao().getByBarcode(trimmed) ?: return null
        return db.articleDao().getWithImageById(alt.articleId)
    }

    /**
     * Resolve a scanned barcode to a catalog article: primary CSV, linked alternate, or unique 9-digit body match.
     */
    suspend fun resolveScannedBarcode(barcode: String): ResolvedBarcodeArticle? {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return null

        getArticleWithImageByBarcode(trimmed)?.let { article ->
            val primary = db.articleDao().getByBarcode(trimmed) != null
            return ResolvedBarcodeArticle(
                article = article,
                primary = primary,
                linkedViaBodyKey = false,
            )
        }

        val bodyMatches = findArticlesByGestiumBodyKey(trimmed)
        if (bodyMatches.size == 1) {
            val match = bodyMatches.first()
            linkAlternateBarcode(match.id, trimmed)
            return ResolvedBarcodeArticle(
                article = match,
                primary = false,
                linkedViaBodyKey = true,
            )
        }

        return null
    }
    suspend fun getArticleWithImageByDesignation(designation: String): ArticleWithImage? {
        val normalized = NameNormalizer.normalize(designation)
        if (normalized.isBlank()) return null
        val candidates = db.articleDao().getByNormalizedName(normalized)
        val picked = candidates.firstOrNull() ?: return null
        return db.articleDao().getWithImageById(picked.id)
    }
    suspend fun getArticleByBarcode(barcode: String): ArticleEntity? {
        val trimmed = barcode.trim()
        db.articleDao().getByBarcode(trimmed)?.let { return it }
        val alt = db.articleAlternateBarcodeDao().getByBarcode(trimmed) ?: return null
        return db.articleDao().getById(alt.articleId)
    }

    /** True when barcode is the primary Gestium CSV barcode (not only an alternate link). */
    suspend fun isPrimaryGestiumBarcode(barcode: String): Boolean =
        db.articleDao().getByBarcode(barcode.trim()) != null

    suspend fun findArticlesByBarcodeSuffix(scannedBarcode: String): List<ArticleWithImage> {
        val trimmed = scannedBarcode.trim()
        val suffixes = BarcodeSuffixMatcher.candidateSuffixes(trimmed)
        if (suffixes.isEmpty()) return emptyList()
        val seen = linkedSetOf<Long>()
        val results = mutableListOf<ArticleWithImage>()
        for (suffix in suffixes) {
            db.articleDao().findByBarcodeSuffix(suffix, trimmed).forEach { article ->
                if (seen.add(article.id)) results.add(article)
            }
        }
        return results
    }

    suspend fun findArticlesByBarcodePartial(partial: String, excludeBarcode: String): List<ArticleWithImage> {
        val digits = partial.filter { it.isDigit() }
        if (digits.length < 4) return emptyList()
        return db.articleDao().findByBarcodePartial(digits, excludeBarcode.trim())
    }

    /** Match catalog articles sharing the same 9-digit body (drop last 5 on scanned barcode). */
    suspend fun findArticlesByGestiumBodyKey(scannedBarcode: String): List<ArticleWithImage> {
        val key = BarcodeSuffixMatcher.gestiumBodyKey(scannedBarcode) ?: return emptyList()
        val trimmed = scannedBarcode.trim()
        val seen = linkedSetOf<Long>()
        val results = mutableListOf<ArticleWithImage>()
        for (article in db.articleDao().findByGestiumBodyKey(key, trimmed)) {
            if (BarcodeSuffixMatcher.gestiumBodyKey(article.barcode) == key && seen.add(article.id)) {
                results.add(article)
            }
        }
        return results
    }

    suspend fun linkAlternateBarcode(articleId: Long, barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        db.articleAlternateBarcodeDao().insert(
            ArticleAlternateBarcodeEntity(articleId = articleId, barcode = trimmed),
        )
        logArticleEvent(articleId, "ALT_BARCODE", "Linked alternate barcode $trimmed")
    }

    /**
     * Link a flavor/color sub-barcode to the locked main article (not in Gestium CSV).
     * @return null on success, or a short error message.
     */
    suspend fun linkSubBarcodeToMainArticle(
        articleId: Long,
        mainBarcode: String,
        subBarcode: String,
    ): String? {
        val sub = subBarcode.trim()
        val main = mainBarcode.trim()
        if (sub.isEmpty()) return "Empty barcode."
        if (sub == main) return "Same as main barcode — scan an alternate."
        db.articleDao().getByBarcode(sub)?.let { primary ->
            return if (primary.id == articleId) {
                "This is already the main CSV barcode."
            } else {
                "Barcode belongs to another article (${primary.designation})."
            }
        }
        db.articleAlternateBarcodeDao().getByBarcode(sub)?.let { existing ->
            return if (existing.articleId == articleId) {
                "Already saved as SUB-BC for this article."
            } else {
                "Barcode already linked to another article."
            }
        }
        linkAlternateBarcode(articleId, sub)
        logArticleEvent(articleId, "SUB_BC", "Sub-barcode $sub → main $main")
        return null
    }

    suspend fun getAllArticles(): List<ArticleEntity> = db.articleDao().getAll()

    /**
     * Placeholder article for Stamper when barcode is not in CSV yet.
     * Updated automatically on next Gestium import if the barcode appears.
     */
    suspend fun ensureBarcodeOnlyArticle(barcode: String): ArticleEntity =
        ensureUnknownArticle(barcode, barcode)

    /** Article not in CSV — optional custom designation / file name from keyboard. */
    suspend fun ensureUnknownArticle(barcode: String, designation: String): ArticleEntity {
        val trimmedBarcode = barcode.trim()
        val trimmedDesignation = designation.trim().ifBlank { trimmedBarcode }
        getArticleByBarcode(trimmedBarcode)?.let { existing ->
            val updated = existing.copy(
                designation = trimmedDesignation,
                normalizedName = NameNormalizer.normalize(trimmedDesignation),
            )
            db.articleDao().update(updated)
            return updated
        }
        val entity = ArticleEntity(
            barcode = trimmedBarcode,
            designation = trimmedDesignation,
            normalizedName = NameNormalizer.normalize(trimmedDesignation),
            price = 0.0,
            changeStatus = ArticleChangeStatus.NEW.name,
            isActive = true,
        )
        val id = db.articleDao().insert(entity)
        val saved = entity.copy(id = id)
        logArticleEvent(
            id,
            "BARCODE_ONLY",
            "Unknown in CSV — saved as ${NameNormalizer.toFileKey(trimmedDesignation)}.png",
        )
        return saved
    }

    suspend fun updateArticles(articles: List<ArticleEntity>) {
        db.withTransaction {
            articles.forEach { db.articleDao().update(it) }
        }
    }

    suspend fun updateArticlePrice(articleId: Long, newPrice: Double): Boolean {
        val article = db.articleDao().getById(articleId) ?: return false
        if (article.price == newPrice) return true
        db.articleDao().update(
            article.copy(
                price = newPrice,
                previousPrice = article.price,
                needsTicketUpdate = true,
            ),
        )
        logArticleEvent(articleId, "PRICE_EDIT", "Design price → $newPrice DA (was ${article.price})")
        return true
    }
    suspend fun getImportById(id: Long): ImportEntity? = db.importDao().getById(id)
    suspend fun getImportChanges(importId: Long): List<ImportChangeEntity> =
        db.importChangeDao().getByImport(importId)
    suspend fun getTemplateById(id: Long): PrintTemplateEntity? = db.printTemplateDao().getById(id)
    suspend fun getPrintBatch(id: Long): PrintBatchEntity? = db.printBatchDao().getById(id)
    suspend fun getPrintBatchItems(batchId: Long): List<PrintBatchItemEntity> =
        db.printBatchDao().getItems(batchId)
    suspend fun getPromoBatchesSnapshot(): List<PrintBatchEntity> =
        db.printBatchDao().getPromoBatches()
    suspend fun countMissingImages(): Int = db.productImageDao().countMissing()

    suspend fun createImport(entity: ImportEntity): Long = db.importDao().insert(entity)

    suspend fun saveImportResults(
        articles: List<ArticleEntity>,
        priceHistory: List<ArticlePriceHistoryEntity>,
        changes: List<ImportChangeEntity>,
        importUpdate: ImportEntity,
    ) {
        articles.forEach { article ->
            if (article.id == 0L) {
                db.articleDao().insert(article)
            } else {
                db.articleDao().update(article)
            }
        }
        if (priceHistory.isNotEmpty()) db.articlePriceHistoryDao().insertAll(priceHistory)
        if (changes.isNotEmpty()) db.importChangeDao().insertAll(changes)
        db.importDao().update(importUpdate)
    }

    suspend fun saveProductImage(image: ProductImageEntity) {
        db.productImageDao().deleteForArticle(image.articleId)
        db.productImageDao().insert(image)
        logArticleEvent(image.articleId, "IMAGE_LINKED", "Product image linked")
    }

    suspend fun replaceProductImages(images: List<ProductImageEntity>) {
        replaceProductImagesBatched(images)
    }

    suspend fun replaceProductImagesBatched(images: List<ProductImageEntity>, batchSize: Int = 800) {
        db.withTransaction {
            db.productImageDao().deleteAll()
            images.chunked(batchSize).forEach { chunk ->
                if (chunk.isNotEmpty()) db.productImageDao().insertAll(chunk)
            }
        }
    }

    suspend fun getProductImagesSnapshot(): List<ProductImageEntity> =
        db.productImageDao().getAll()

    suspend fun markProductImagesSent(articleIds: List<Long>, sentAt: Long = System.currentTimeMillis()) {
        if (articleIds.isNotEmpty()) {
            db.productImageDao().markSent(articleIds, sentAt)
            articleIds.forEach { logArticleEvent(it, "SENT", "Shared as file") }
        }
    }

    suspend fun markTicketVerified(articleId: Long) {
        db.articleDao().clearNeedsTicketUpdate(articleId)
    }

    suspend fun addToCart(articleId: Long, cartType: CartType, note: String? = null) {
        val count = db.preselectionDao().count(cartType.name)
        db.preselectionDao().insert(
            PreselectionItemEntity(
                articleId = articleId,
                cartType = cartType.name,
                sortOrder = count,
                note = note,
            ),
        )
        logArticleEvent(articleId, "ADDED_TO_${cartType.name}", "Added to ${cartType.name.lowercase()} cart")
    }

    suspend fun removeFromCart(articleId: Long, cartType: CartType) =
        db.preselectionDao().remove(articleId, cartType.name)

    suspend fun clearCart(cartType: CartType) = db.preselectionDao().clear(cartType.name)

    suspend fun isInCart(articleId: Long, cartType: CartType): Boolean =
        db.preselectionDao().isInCart(articleId, cartType.name)

    suspend fun incrementDesignCopyCount(articleId: Long) {
        val item = db.preselectionDao().getItem(articleId, CartType.DESIGN.name) ?: return
        val next = (item.copyCount + 1).coerceAtMost(99)
        db.preselectionDao().updateCopyCount(articleId, CartType.DESIGN.name, next)
    }

    suspend fun decrementDesignCopyCount(articleId: Long) {
        val item = db.preselectionDao().getItem(articleId, CartType.DESIGN.name) ?: return
        val next = (item.copyCount - 1).coerceAtLeast(1)
        db.preselectionDao().updateCopyCount(articleId, CartType.DESIGN.name, next)
    }

    suspend fun moveDesignItemsToDone(articleIds: List<Long>) {
        if (articleIds.isEmpty()) return
        val idSet = articleIds.toSet()
        db.withTransaction {
            val ordered = db.preselectionDao()
                .getAllInCart(CartType.DESIGN.name)
                .filter { it.articleId in idSet }
            ordered.forEach { item ->
                moveCartItem(item.articleId, CartType.DESIGN, CartType.DESIGN_DONE)
            }
            trimDesignDoneCart(DESIGN_DONE_MAX)
        }
    }

    private suspend fun trimDesignDoneCart(maxSize: Int) {
        val count = db.preselectionDao().count(CartType.DESIGN_DONE.name)
        val excess = count - maxSize
        if (excess <= 0) return
        val ids = db.preselectionDao().oldestPreselectionIds(CartType.DESIGN_DONE.name, excess)
        if (ids.isNotEmpty()) {
            db.preselectionDao().removeByPreselectionIds(ids)
        }
    }

    suspend fun restoreDesignItemFromDone(articleId: Long) {
        moveCartItem(articleId, CartType.DESIGN_DONE, CartType.DESIGN)
    }

    private suspend fun moveCartItem(articleId: Long, from: CartType, to: CartType) {
        val item = db.preselectionDao().getItem(articleId, from.name) ?: return
        db.preselectionDao().remove(articleId, from.name)
        db.preselectionDao().insert(
            item.copy(
                id = 0,
                cartType = to.name,
                sortOrder = item.sortOrder,
                addedAt = item.addedAt,
            ),
        )
        logArticleEvent(articleId, "DESIGN_CART_MOVE", "$from → $to")
    }

    suspend fun logSearchQuery(query: String) {
        val cleaned = query.trim()
        if (cleaned.length < 3) return
        db.workflowHistoryDao().insert(
            WorkflowHistoryEntity(
                eventType = "SEARCHED",
                detail = cleaned,
            ),
        )
    }

    suspend fun logBarcodeSearch(barcode: String, articleId: Long?) {
        val article = articleId?.let { db.articleDao().getById(it) }
        db.workflowHistoryDao().insert(
            WorkflowHistoryEntity(
                eventType = "SCANNED",
                articleId = article?.id,
                designationSnapshot = article?.designation,
                barcodeSnapshot = article?.barcode ?: barcode,
                detail = if (article == null) "Barcode not found" else "Barcode scanned",
            ),
        )
    }

    private suspend fun logArticleEvent(articleId: Long, eventType: String, detail: String? = null) {
        val article = db.articleDao().getById(articleId)
        db.workflowHistoryDao().insert(
            WorkflowHistoryEntity(
                eventType = eventType,
                articleId = articleId,
                designationSnapshot = article?.designation,
                barcodeSnapshot = article?.barcode,
                detail = detail,
            ),
        )
    }

    suspend fun addToPreselection(articleId: Long) = addToCart(articleId, CartType.SHARE)

    suspend fun removeFromPreselection(articleId: Long) = removeFromCart(articleId, CartType.SHARE)

    suspend fun clearPreselection() = clearCart(CartType.SHARE)

    suspend fun isInPreselection(articleId: Long): Boolean = isInCart(articleId, CartType.SHARE)

    suspend fun createPrintBatch(
        batch: PrintBatchEntity,
        items: List<PrintBatchItemEntity>,
    ): Long {
        val batchId = db.printBatchDao().insert(batch)
        db.printBatchDao().insertItems(items.map { it.copy(batchId = batchId) })
        return batchId
    }

    suspend fun recordDesignShelfPrint(
        pageIndex: Int,
        exportPath: String,
        items: List<PreselectionWithArticle>,
    ): Long {
        if (items.isEmpty()) return -1L
        return createPrintBatch(
            batch = PrintBatchEntity(
                templateId = null,
                templateName = "Design — Shelf A4 12-up (page ${pageIndex + 1})",
                exportPath = exportPath,
                previewPath = exportPath,
                status = PrintBatchStatus.GENERATED.name,
                itemCount = items.size,
            ),
            items = items.mapIndexed { index, item ->
                PrintBatchItemEntity(
                    batchId = 0,
                    articleId = item.articleId,
                    designationSnapshot = item.designation,
                    priceSnapshot = item.price,
                    barcodeSnapshot = item.barcode,
                    imageSnapshotPath = item.imagePath,
                    sortOrder = index,
                )
            },
        )
    }

    suspend fun updatePrintBatchStatus(batchId: Long, status: String) {
        val batch = db.printBatchDao().getById(batchId) ?: return
        db.printBatchDao().update(batch.copy(status = status))
    }

    suspend fun insertPromoAlerts(alerts: List<PromoAlertEntity>) = db.promoAlertDao().insertAll(alerts)
    suspend fun updatePromoAlertStatus(id: Long, status: String) = db.promoAlertDao().updateStatus(id, status)
    suspend fun clearPendingPromoAlerts() = db.promoAlertDao().clearPending()

    suspend fun seedDefaultTemplates() {
        if (db.printTemplateDao().count() > 0) return
        val templates = listOf(
            PrintTemplateEntity(
                name = "Shelf A4 — 10 labels",
                type = TemplateType.SHELF.name,
                size = "A4",
                capacity = 10,
            ),
            PrintTemplateEntity(
                name = "Freezer Card A4",
                type = TemplateType.FREEZER.name,
                size = "A4",
                capacity = 1,
            ),
            PrintTemplateEntity(
                name = "Podium Signage A4",
                type = TemplateType.PODIUM.name,
                size = "A4",
                capacity = 1,
            ),
            PrintTemplateEntity(
                name = "Board A3",
                type = TemplateType.BOARD.name,
                size = "A3",
                capacity = 1,
            ),
        )
        db.printTemplateDao().insertAll(templates)
    }

    suspend fun getPhoneSyncCatalogRows(): List<PhoneSyncCatalogRow> =
        db.articleDao().getPhoneSyncCatalogRows()

    suspend fun getPhoneSyncPushSources(): List<PhoneSyncPushSourceRow> =
        db.articleDao().getPhoneSyncPushSources()

    suspend fun getPhoneSyncAlternatePairs(): List<PhoneSyncAlternatePair> =
        db.articleAlternateBarcodeDao().getAllPairs()

    suspend fun getAlternateBarcodesForArticle(articleId: Long): List<String> =
        db.articleAlternateBarcodeDao().getByArticleId(articleId).map { it.barcode }

    suspend fun logPhoneSyncReceived(
        deviceName: String,
        imagesApplied: Int,
        alternatesLinked: Int,
        skipped: Int,
    ) {
        db.workflowHistoryDao().insert(
            WorkflowHistoryEntity(
                eventType = "PHONE_SYNC",
                articleId = null,
                designationSnapshot = deviceName,
                barcodeSnapshot = null,
                detail = "Received $imagesApplied PNG(s), $alternatesLinked alternate barcode(s), $skipped skipped",
            ),
        )
    }

    companion object {
        const val DESIGN_DONE_MAX = 50
    }
}

data class ResolvedBarcodeArticle(
    val article: ArticleWithImage,
    val primary: Boolean,
    val linkedViaBodyKey: Boolean,
)
