package com.oasismall.oasisai.domain.transfer

import com.oasismall.oasisai.data.db.entity.ArticleAlternateBarcodeEntity
import com.oasismall.oasisai.data.db.entity.ArticleEntity
import com.oasismall.oasisai.data.db.entity.ArticlePriceHistoryEntity
import com.oasismall.oasisai.data.db.entity.BatchCameraQueueEntity
import com.oasismall.oasisai.data.db.entity.BulkCaptureEntity
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.data.db.entity.ImportChangeEntity
import com.oasismall.oasisai.data.db.entity.ImportEntity
import com.oasismall.oasisai.data.db.entity.PreselectionItemEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.db.entity.PrintTemplateEntity
import com.oasismall.oasisai.data.db.entity.ProductImageEntity
import com.oasismall.oasisai.data.db.entity.PromoAlertEntity
import com.oasismall.oasisai.data.db.entity.WorkflowHistoryEntity
import com.oasismall.oasisai.data.repository.DatabaseExportTables
import com.oasismall.oasisai.data.repository.remapStoredPath
import com.oasismall.oasisai.data.repository.toStoredPath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupEntityJson {
    const val FORMAT_VERSION = 1

    fun writeDatabase(tables: DatabaseExportTables, filesDir: File): JSONObject =
        JSONObject().apply {
            put("articles", tables.articles.map { articleJson(it) })
            put("alternateBarcodes", tables.alternateBarcodes.map { alternateJson(it, filesDir) })
            put("imports", tables.imports.map { importJson(it) })
            put("importChanges", tables.importChanges.map { importChangeJson(it) })
            put("priceHistory", tables.priceHistory.map { priceHistoryJson(it) })
            put("productImages", tables.productImages.map { productImageJson(it, filesDir) })
            put("preselectionItems", tables.preselectionItems.map { preselectionJson(it) })
            put("printTemplates", tables.printTemplates.map { printTemplateJson(it) })
            put("printBatches", tables.printBatches.map { printBatchJson(it, filesDir) })
            put("printBatchItems", tables.printBatchItems.map { printBatchItemJson(it, filesDir) })
            put("promoAlerts", tables.promoAlerts.map { promoAlertJson(it) })
            put("workflowHistory", tables.workflowHistory.map { workflowJson(it) })
            put("bulkCaptures", tables.bulkCaptures.map { bulkCaptureJson(it, filesDir) })
            put("cameraBatchItems", tables.cameraBatchItems.map { cameraBatchJson(it, filesDir) })
            put("batchCameraQueue", tables.batchCameraQueue.map { batchQueueJson(it) })
        }

    fun readDatabase(root: JSONObject, filesDir: File): DatabaseExportTables = DatabaseExportTables(
        articles = root.optJSONArray("articles").toList { articleFromJson(it) },
        alternateBarcodes = root.optJSONArray("alternateBarcodes").toList { alternateFromJson(it, filesDir) },
        imports = root.optJSONArray("imports").toList { importFromJson(it) },
        importChanges = root.optJSONArray("importChanges").toList { importChangeFromJson(it) },
        priceHistory = root.optJSONArray("priceHistory").toList { priceHistoryFromJson(it) },
        productImages = root.optJSONArray("productImages").toList { productImageFromJson(it, filesDir) },
        preselectionItems = root.optJSONArray("preselectionItems").toList { preselectionFromJson(it) },
        printTemplates = root.optJSONArray("printTemplates").toList { printTemplateFromJson(it) },
        printBatches = root.optJSONArray("printBatches").toList { printBatchFromJson(it, filesDir) },
        printBatchItems = root.optJSONArray("printBatchItems").toList { printBatchItemFromJson(it, filesDir) },
        promoAlerts = root.optJSONArray("promoAlerts").toList { promoAlertFromJson(it) },
        workflowHistory = root.optJSONArray("workflowHistory").toList { workflowFromJson(it) },
        bulkCaptures = root.optJSONArray("bulkCaptures").toList { bulkCaptureFromJson(it, filesDir) },
        cameraBatchItems = root.optJSONArray("cameraBatchItems").toList { cameraBatchFromJson(it, filesDir) },
        batchCameraQueue = root.optJSONArray("batchCameraQueue").toList { batchQueueFromJson(it) },
    )

    private fun articleJson(a: ArticleEntity) = JSONObject().apply {
        put("id", a.id)
        put("barcode", a.barcode)
        put("designation", a.designation)
        put("normalizedName", a.normalizedName)
        put("price", a.price)
        put("previousPrice", a.previousPrice)
        put("codeart", a.codeart)
        put("reference", a.reference)
        put("category", a.category)
        put("rayon", a.rayon)
        put("famille", a.famille)
        put("brand", a.brand)
        put("stock", a.stock)
        put("unit", a.unit)
        put("rawData", a.rawData)
        put("sourceImportId", a.sourceImportId)
        put("lastSeenAt", a.lastSeenAt)
        put("changeStatus", a.changeStatus)
        put("isActive", a.isActive)
        put("needsTicketUpdate", a.needsTicketUpdate)
    }

    private fun articleFromJson(o: JSONObject) = ArticleEntity(
        id = o.getLong("id"),
        barcode = o.getString("barcode"),
        designation = o.getString("designation"),
        normalizedName = o.getString("normalizedName"),
        price = o.getDouble("price"),
        previousPrice = o.optDouble("previousPrice").takeIf { o.has("previousPrice") && !o.isNull("previousPrice") },
        codeart = o.optString("codeart").takeIf { it.isNotBlank() },
        reference = o.optString("reference").takeIf { it.isNotBlank() },
        category = o.optString("category").takeIf { it.isNotBlank() },
        rayon = o.optString("rayon").takeIf { it.isNotBlank() },
        famille = o.optString("famille").takeIf { it.isNotBlank() },
        brand = o.optString("brand").takeIf { it.isNotBlank() },
        stock = o.optDouble("stock").takeIf { o.has("stock") && !o.isNull("stock") },
        unit = o.optString("unit").takeIf { it.isNotBlank() },
        rawData = o.optString("rawData").takeIf { it.isNotBlank() },
        sourceImportId = o.optLong("sourceImportId").takeIf { it > 0L },
        lastSeenAt = o.optLong("lastSeenAt", System.currentTimeMillis()),
        changeStatus = o.optString("changeStatus", "UNCHANGED"),
        isActive = o.optBoolean("isActive", true),
        needsTicketUpdate = o.optBoolean("needsTicketUpdate", false),
    )

    private fun alternateJson(a: ArticleAlternateBarcodeEntity, filesDir: File) = JSONObject().apply {
        put("id", a.id)
        put("articleId", a.articleId)
        put("barcode", a.barcode)
        put("addedAt", a.addedAt)
        put("imagePath", a.imagePath?.let { toStoredPath(it, filesDir) })
    }

    private fun alternateFromJson(o: JSONObject, filesDir: File) = ArticleAlternateBarcodeEntity(
        id = o.getLong("id"),
        articleId = o.getLong("articleId"),
        barcode = o.getString("barcode"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        imagePath = remapStoredPath(o.optString("imagePath"), filesDir).takeIf { it.isNotBlank() },
    )

    private fun importJson(i: ImportEntity) = JSONObject().apply {
        put("id", i.id)
        put("fileName", i.fileName)
        put("importedAt", i.importedAt)
        put("rowCount", i.rowCount)
        put("status", i.status)
        put("newCount", i.newCount)
        put("priceChangedCount", i.priceChangedCount)
        put("removedCount", i.removedCount)
        put("renamedCount", i.renamedCount)
    }

    private fun importFromJson(o: JSONObject) = ImportEntity(
        id = o.getLong("id"),
        fileName = o.getString("fileName"),
        importedAt = o.optLong("importedAt", System.currentTimeMillis()),
        rowCount = o.optInt("rowCount", 0),
        status = o.getString("status"),
        newCount = o.optInt("newCount", 0),
        priceChangedCount = o.optInt("priceChangedCount", 0),
        removedCount = o.optInt("removedCount", 0),
        renamedCount = o.optInt("renamedCount", 0),
    )

    private fun importChangeJson(c: ImportChangeEntity) = JSONObject().apply {
        put("id", c.id)
        put("importId", c.importId)
        put("articleId", c.articleId)
        put("barcode", c.barcode)
        put("designation", c.designation)
        put("changeType", c.changeType)
        put("oldValue", c.oldValue)
        put("newValue", c.newValue)
    }

    private fun importChangeFromJson(o: JSONObject) = ImportChangeEntity(
        id = o.getLong("id"),
        importId = o.getLong("importId"),
        articleId = o.optLong("articleId").takeIf { it > 0L },
        barcode = o.getString("barcode"),
        designation = o.getString("designation"),
        changeType = o.getString("changeType"),
        oldValue = o.optString("oldValue").takeIf { it.isNotBlank() },
        newValue = o.optString("newValue").takeIf { it.isNotBlank() },
    )

    private fun priceHistoryJson(h: ArticlePriceHistoryEntity) = JSONObject().apply {
        put("id", h.id)
        put("articleId", h.articleId)
        put("oldPrice", h.oldPrice)
        put("newPrice", h.newPrice)
        put("importId", h.importId)
        put("changedAt", h.changedAt)
    }

    private fun priceHistoryFromJson(o: JSONObject) = ArticlePriceHistoryEntity(
        id = o.getLong("id"),
        articleId = o.getLong("articleId"),
        oldPrice = o.getDouble("oldPrice"),
        newPrice = o.getDouble("newPrice"),
        importId = o.getLong("importId"),
        changedAt = o.optLong("changedAt", System.currentTimeMillis()),
    )

    private fun productImageJson(i: ProductImageEntity, filesDir: File) = JSONObject().apply {
        put("id", i.id)
        put("articleId", i.articleId)
        put("designationKey", i.designationKey)
        put("barcode", i.barcode)
        put("imagePath", toStoredPath(i.imagePath, filesDir))
        put("imageStatus", i.imageStatus)
        put("originalImagePath", i.originalImagePath?.let { toStoredPath(it, filesDir) })
        put("createdAt", i.createdAt)
        put("lastSentAt", i.lastSentAt)
    }

    private fun productImageFromJson(o: JSONObject, filesDir: File) = ProductImageEntity(
        id = o.getLong("id"),
        articleId = o.getLong("articleId"),
        designationKey = o.getString("designationKey"),
        barcode = o.optString("barcode").takeIf { it.isNotBlank() },
        imagePath = remapStoredPath(o.getString("imagePath"), filesDir),
        imageStatus = o.getString("imageStatus"),
        originalImagePath = remapStoredPath(o.optString("originalImagePath"), filesDir).takeIf { it.isNotBlank() },
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        lastSentAt = o.optLong("lastSentAt").takeIf { it > 0L },
    )

    private fun preselectionJson(p: PreselectionItemEntity) = JSONObject().apply {
        put("id", p.id)
        put("articleId", p.articleId)
        put("cartType", p.cartType)
        put("variantBarcode", p.variantBarcode)
        put("addedAt", p.addedAt)
        put("sortOrder", p.sortOrder)
        put("note", p.note)
        put("intendedTemplateType", p.intendedTemplateType)
        put("copyCount", p.copyCount)
        put("isPromoTicket", p.isPromoTicket)
        put("promoPrice", p.promoPrice)
        put("promoOriginalPrice", p.promoOriginalPrice)
    }

    private fun preselectionFromJson(o: JSONObject) = PreselectionItemEntity(
        id = o.getLong("id"),
        articleId = o.getLong("articleId"),
        cartType = o.getString("cartType"),
        variantBarcode = o.optString("variantBarcode", ""),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        sortOrder = o.optInt("sortOrder", 0),
        note = o.optString("note").takeIf { it.isNotBlank() },
        intendedTemplateType = o.optString("intendedTemplateType").takeIf { it.isNotBlank() },
        copyCount = o.optInt("copyCount", 1),
        isPromoTicket = o.optBoolean("isPromoTicket", false),
        promoPrice = o.optDouble("promoPrice").takeIf { o.has("promoPrice") && !o.isNull("promoPrice") },
        promoOriginalPrice = o.optDouble("promoOriginalPrice")
            .takeIf { o.has("promoOriginalPrice") && !o.isNull("promoOriginalPrice") },
    )

    private fun printTemplateJson(t: PrintTemplateEntity) = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("type", t.type)
        put("size", t.size)
        put("capacity", t.capacity)
        put("layoutConfig", t.layoutConfig)
    }

    private fun printTemplateFromJson(o: JSONObject) = PrintTemplateEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        type = o.getString("type"),
        size = o.getString("size"),
        capacity = o.getInt("capacity"),
        layoutConfig = o.optString("layoutConfig", "{}"),
    )

    private fun printBatchJson(b: PrintBatchEntity, filesDir: File) = JSONObject().apply {
        put("id", b.id)
        put("templateId", b.templateId)
        put("templateName", b.templateName)
        put("createdAt", b.createdAt)
        put("exportPath", toStoredPath(b.exportPath, filesDir))
        put("previewPath", b.previewPath?.let { toStoredPath(it, filesDir) })
        put("isPromo", b.isPromo)
        put("promoStart", b.promoStart)
        put("promoEnd", b.promoEnd)
        put("campaignName", b.campaignName)
        put("status", b.status)
        put("itemCount", b.itemCount)
        put("pageIndex", b.pageIndex)
    }

    private fun printBatchFromJson(o: JSONObject, filesDir: File) = PrintBatchEntity(
        id = o.getLong("id"),
        templateId = o.optLong("templateId").takeIf { it > 0L },
        templateName = o.getString("templateName"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        exportPath = remapStoredPath(o.getString("exportPath"), filesDir),
        previewPath = remapStoredPath(o.optString("previewPath"), filesDir).takeIf { it.isNotBlank() },
        isPromo = o.optBoolean("isPromo", false),
        promoStart = o.optLong("promoStart").takeIf { it > 0L },
        promoEnd = o.optLong("promoEnd").takeIf { it > 0L },
        campaignName = o.optString("campaignName").takeIf { it.isNotBlank() },
        status = o.getString("status"),
        itemCount = o.optInt("itemCount", 0),
        pageIndex = o.optInt("pageIndex", 0),
    )

    private fun printBatchItemJson(i: PrintBatchItemEntity, filesDir: File) = JSONObject().apply {
        put("id", i.id)
        put("batchId", i.batchId)
        put("articleId", i.articleId)
        put("designationSnapshot", i.designationSnapshot)
        put("priceSnapshot", i.priceSnapshot)
        put("barcodeSnapshot", i.barcodeSnapshot)
        put("imageSnapshotPath", i.imageSnapshotPath?.let { toStoredPath(it, filesDir) })
        put("sortOrder", i.sortOrder)
        put("copyCountSnapshot", i.copyCountSnapshot)
        put("isPromoSnapshot", i.isPromoSnapshot)
        put("promoPriceSnapshot", i.promoPriceSnapshot)
        put("promoOriginalSnapshot", i.promoOriginalSnapshot)
        put("variantBarcodeSnapshot", i.variantBarcodeSnapshot)
    }

    private fun printBatchItemFromJson(o: JSONObject, filesDir: File) = PrintBatchItemEntity(
        id = o.getLong("id"),
        batchId = o.getLong("batchId"),
        articleId = o.optLong("articleId").takeIf { it > 0L },
        designationSnapshot = o.getString("designationSnapshot"),
        priceSnapshot = o.getDouble("priceSnapshot"),
        barcodeSnapshot = o.getString("barcodeSnapshot"),
        imageSnapshotPath = remapStoredPath(o.optString("imageSnapshotPath"), filesDir).takeIf { it.isNotBlank() },
        sortOrder = o.optInt("sortOrder", 0),
        copyCountSnapshot = o.optInt("copyCountSnapshot", 1),
        isPromoSnapshot = o.optBoolean("isPromoSnapshot", false),
        promoPriceSnapshot = o.optDouble("promoPriceSnapshot")
            .takeIf { o.has("promoPriceSnapshot") && !o.isNull("promoPriceSnapshot") },
        promoOriginalSnapshot = o.optDouble("promoOriginalSnapshot")
            .takeIf { o.has("promoOriginalSnapshot") && !o.isNull("promoOriginalSnapshot") },
        variantBarcodeSnapshot = o.optString("variantBarcodeSnapshot", ""),
    )

    private fun promoAlertJson(a: PromoAlertEntity) = JSONObject().apply {
        put("id", a.id)
        put("batchId", a.batchId)
        put("alertDate", a.alertDate)
        put("status", a.status)
        put("message", a.message)
    }

    private fun promoAlertFromJson(o: JSONObject) = PromoAlertEntity(
        id = o.getLong("id"),
        batchId = o.getLong("batchId"),
        alertDate = o.getLong("alertDate"),
        status = o.getString("status"),
        message = o.optString("message", ""),
    )

    private fun workflowJson(w: WorkflowHistoryEntity) = JSONObject().apply {
        put("id", w.id)
        put("eventType", w.eventType)
        put("articleId", w.articleId)
        put("designationSnapshot", w.designationSnapshot)
        put("barcodeSnapshot", w.barcodeSnapshot)
        put("detail", w.detail)
        put("createdAt", w.createdAt)
    }

    private fun workflowFromJson(o: JSONObject) = WorkflowHistoryEntity(
        id = o.getLong("id"),
        eventType = o.getString("eventType"),
        articleId = o.optLong("articleId").takeIf { it > 0L },
        designationSnapshot = o.optString("designationSnapshot").takeIf { it.isNotBlank() },
        barcodeSnapshot = o.optString("barcodeSnapshot").takeIf { it.isNotBlank() },
        detail = o.optString("detail").takeIf { it.isNotBlank() },
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun bulkCaptureJson(c: BulkCaptureEntity, filesDir: File) = JSONObject().apply {
        put("barcode", c.barcode)
        put("imagePath", toStoredPath(c.imagePath, filesDir))
        put("capturedAt", c.capturedAt)
        put("replaced", c.replaced)
        put("syncStatus", c.syncStatus)
    }

    private fun bulkCaptureFromJson(o: JSONObject, filesDir: File) = BulkCaptureEntity(
        barcode = o.getString("barcode"),
        imagePath = remapStoredPath(o.getString("imagePath"), filesDir),
        capturedAt = o.optLong("capturedAt", System.currentTimeMillis()),
        replaced = o.optBoolean("replaced", false),
        syncStatus = o.optString("syncStatus", "PENDING"),
    )

    private fun cameraBatchJson(c: CameraBatchItemEntity, filesDir: File) = JSONObject().apply {
        put("id", c.id)
        put("batchDate", c.batchDate)
        put("shotPath", toStoredPath(c.shotPath, filesDir))
        put("shotFileName", c.shotFileName)
        put("barcode", c.barcode)
        put("designation", c.designation)
        put("codeart", c.codeart)
        put("price", c.price)
        put("articleId", c.articleId)
        put("capturedAt", c.capturedAt)
        put("status", c.status)
        put("photoroomPath", c.photoroomPath?.let { toStoredPath(it, filesDir) })
        put("pendingSubBarcodeLink", c.pendingSubBarcodeLink)
        put("linkParentArticleId", c.linkParentArticleId)
    }

    private fun cameraBatchFromJson(o: JSONObject, filesDir: File) = CameraBatchItemEntity(
        id = o.getLong("id"),
        batchDate = o.getString("batchDate"),
        shotPath = remapStoredPath(o.getString("shotPath"), filesDir),
        shotFileName = o.getString("shotFileName"),
        barcode = o.getString("barcode"),
        designation = o.getString("designation"),
        codeart = o.optString("codeart").takeIf { it.isNotBlank() },
        price = o.optDouble("price").takeIf { o.has("price") && !o.isNull("price") },
        articleId = o.optLong("articleId").takeIf { it > 0L },
        capturedAt = o.optLong("capturedAt", System.currentTimeMillis()),
        status = o.getString("status"),
        photoroomPath = remapStoredPath(o.optString("photoroomPath"), filesDir).takeIf { it.isNotBlank() },
        pendingSubBarcodeLink = o.optBoolean("pendingSubBarcodeLink", false),
        linkParentArticleId = o.optLong("linkParentArticleId").takeIf { it > 0L },
    )

    private fun batchQueueJson(q: BatchCameraQueueEntity) = JSONObject().apply {
        put("id", q.id)
        put("designation", q.designation)
        put("sortOrder", q.sortOrder)
        put("done", q.done)
        put("createdAt", q.createdAt)
    }

    private fun batchQueueFromJson(o: JSONObject) = BatchCameraQueueEntity(
        id = o.getLong("id"),
        designation = o.getString("designation"),
        sortOrder = o.optInt("sortOrder", 0),
        done = o.optBoolean("done", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private inline fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                add(mapper(obj))
            }
        }
    }
}
