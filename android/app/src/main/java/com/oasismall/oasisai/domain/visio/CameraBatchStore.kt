package com.oasismall.oasisai.domain.visio

import android.content.Context
import com.oasismall.oasisai.data.db.dao.CameraBatchDao
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.data.model.CameraBatchStatus
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.PngMetadata
import com.oasismall.oasisai.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class CameraBatchStore(
    private val context: Context,
    private val dao: CameraBatchDao,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
) {
    fun todayDate(): String = VisioDownloadStorage.todayDate()

    fun batchFolderName(date: String = todayDate()): String =
        VisioDownloadStorage.batchFolderName(date)

    fun observeToday(): Flow<List<CameraBatchItemEntity>> =
        dao.observeForDate(todayDate())

    fun observePendingToday(): Flow<List<CameraBatchItemEntity>> =
        dao.observeForDateAndStatus(todayDate(), CameraBatchStatus.AWAITING_PHOTOROOM.name)

    fun observeAllPending(): Flow<List<CameraBatchItemEntity>> =
        dao.observeByStatus(CameraBatchStatus.AWAITING_PHOTOROOM.name)

    fun observePendingCountToday(): Flow<Int> =
        dao.observeCountForDateAndStatus(todayDate(), CameraBatchStatus.AWAITING_PHOTOROOM.name)

    suspend fun saveTaggedShot(
        sourceJpeg: File,
        barcode: String,
        hintDesignation: String? = null,
        hintArticleId: Long? = null,
        pendingSubBarcodeLink: Boolean = false,
        linkParentArticleId: Long? = null,
    ): CameraBatchItemEntity = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        require(trimmed.isNotBlank()) { "Barcode required" }
        val resolved = repository.resolveScannedBarcode(trimmed)
        val article = resolved?.article
            ?: hintArticleId?.let { repository.getArticleWithImageById(it) }
        val articleEntity = article?.id?.let { repository.getArticleById(it) }
            ?: hintArticleId?.let { repository.getArticleById(it) }
        val designation = article?.designation
            ?: hintDesignation?.trim()?.takeIf { it.isNotBlank() }
            ?: trimmed
        val folder = batchFolderName()
        val displayName = uniqueShotFileName(folder, designation, trimmed)
        val path = VisioDownloadStorage.copyFileToFolder(
            context = context,
            folderName = folder,
            displayName = displayName,
            mimeType = "image/jpeg",
            source = sourceJpeg,
        )
        writeSidecar(
            path,
            trimmed,
            designation,
            articleEntity?.codeart,
            article?.price ?: articleEntity?.price,
            article?.previousPrice ?: articleEntity?.previousPrice,
        )
        val entity = CameraBatchItemEntity(
            batchDate = todayDate(),
            shotPath = path,
            shotFileName = displayName,
            barcode = trimmed,
            designation = designation,
            codeart = articleEntity?.codeart,
            price = article?.price?.takeIf { it > 0.0 } ?: articleEntity?.price?.takeIf { it > 0.0 },
            articleId = article?.id ?: hintArticleId,
            status = CameraBatchStatus.AWAITING_PHOTOROOM.name,
            pendingSubBarcodeLink = pendingSubBarcodeLink,
            linkParentArticleId = linkParentArticleId,
        )
        val existing = dao.getPendingByBarcode(
            todayDate(),
            trimmed,
            CameraBatchStatus.AWAITING_PHOTOROOM.name,
        )
        val id = if (existing != null) {
            runCatching { File(existing.shotPath).delete() }.getOrDefault(false)
            dao.deleteById(existing.id)
            dao.insert(entity)
        } else {
            dao.insert(entity)
        }
        entity.copy(id = id)
    }

    fun photoroomDisplayPath(): String = PhotoroomStorage.displayPath(context)

    fun refreshPhotoroomIndex() {
        PhotoroomStorage.invalidateCache()
    }

    fun listPhotoroomPngs(): List<PhotoroomStorage.PngRef> = PhotoroomStorage.listPngFiles(context)

    fun findPhotoroomPng(barcode: String, designation: String? = null): PhotoroomStorage.PngRef? =
        PhotoroomStorage.findPngForBarcode(context, barcode, designation)

    suspend fun importFromPhotoroom(itemId: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val item = dao.getById(itemId) ?: error("Batch item not found")
            val pngRef = findPhotoroomPng(item.barcode, item.designation)
                ?: error("No PNG in ${photoroomDisplayPath()}/ for ${item.barcode} (${item.designation})")
            val png = pngRef.asLocalFile(context)
            if (item.pendingSubBarcodeLink && item.linkParentArticleId != null) {
                val parent = repository.getArticleById(item.linkParentArticleId)
                    ?: error("Parent article not found")
                val imagePath = imageMatcher.registerSubBarcodeImage(item.barcode, parent, png)
                repository.linkSubBarcodeToMainArticle(
                    articleId = item.linkParentArticleId,
                    mainBarcode = parent.barcode,
                    subBarcode = item.barcode,
                    imagePath = imagePath,
                )
                if (!repository.isInCart(item.linkParentArticleId, CartType.SHARE, item.barcode)) {
                    repository.addToCart(
                        item.linkParentArticleId,
                        CartType.SHARE,
                        CartSourceTags.BATCH_CAMERA,
                        variantBarcode = item.barcode,
                    )
                }
                dao.updateStatus(item.id, CameraBatchStatus.IMPORTED.name, pngRef.pathLabel)
                return@runCatching "Sub-barcode ${item.barcode} linked with image → To share"
            }
            val articleEntity = item.articleId?.let { repository.getArticleById(it) }
                ?: repository.resolveScannedBarcode(item.barcode)?.article?.id
                    ?.let { repository.getArticleById(it) }
                ?: repository.getArticleWithImageByDesignation(item.designation)?.let {
                    repository.getArticleById(it.id)
                }
            val articleId: Long = if (articleEntity != null) {
                imageMatcher.registerCapturedImage(articleEntity, png)
                repository.removeFromCart(articleEntity.id, CartType.PHOTOSHOOT)
                articleEntity.id
            } else {
                val saved = imageMatcher.registerCapturedImageByBarcode(item.barcode, png)
                repository.removeFromCart(saved.articleId, CartType.PHOTOSHOOT)
                saved.articleId
            }
            if (!repository.isInCart(articleId, CartType.SHARE)) {
                repository.addToCart(articleId, CartType.SHARE, CartSourceTags.BATCH_CAMERA)
            }
            dao.updateStatus(item.id, CameraBatchStatus.IMPORTED.name, pngRef.pathLabel)
            "Imported ${item.designation} → gallery & To share"
        }
    }

    suspend fun importAllPending(): ImportAllResult = withContext(Dispatchers.IO) {
        refreshPhotoroomIndex()
        val pending = dao.getAllByStatus(CameraBatchStatus.AWAITING_PHOTOROOM.name)
        var imported = 0
        val errors = mutableListOf<String>()
        pending.forEach { item ->
            importFromPhotoroom(item.id).fold(
                onSuccess = { imported++ },
                onFailure = { errors += "${item.barcode}: ${it.message}" },
            )
        }
        ImportAllResult(imported = imported, failed = pending.size - imported, errors = errors.take(5))
    }

    suspend fun removePendingItem(itemId: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val item = dao.getById(itemId) ?: error("Batch item not found")
            runCatching { File(item.shotPath).delete() }
            val sidecar = File(item.shotPath.removeSuffix(".jpg") + ".visio.json")
            if (sidecar.exists()) sidecar.delete()
            dao.deleteById(itemId)
            "Removed ${item.designation} from import queue"
        }
    }

    fun buildShotFileName(designation: String, barcode: String): String {
        val stem = NameNormalizer.toDisplayFileStem(designation).ifBlank { PngMetadata.barcodeFileStem(barcode) }
        val bc = PngMetadata.barcodeFileStem(barcode)
        return "${stem}_$bc.jpg"
    }

    private fun uniqueShotFileName(folder: String, designation: String, barcode: String): String {
        val base = buildShotFileName(designation, barcode)
        val existing = VisioDownloadStorage.listFilesInFolder(context, folder, "jpg")
            .map { it.displayName.lowercase() }
            .toSet()
        if (base.lowercase() !in existing) return base
        val stem = base.removeSuffix(".jpg")
        var n = 2
        while ("${stem}_$n.jpg".lowercase() in existing) n++
        return "${stem}_$n.jpg"
    }

    private fun writeSidecar(
        jpgPath: String,
        barcode: String,
        designation: String,
        codeart: String?,
        price: Double?,
        previousPrice: Double?,
    ) {
        val json = JSONObject().apply {
            put("barcode", barcode)
            put("designation", designation)
            codeart?.let { put("codeart", it) }
            price?.let { put("price", PriceFormatter.format(it)) }
            previousPrice?.let { put("priceBefore", PriceFormatter.format(it)) }
        }
        File(jpgPath.removeSuffix(".jpg") + ".visio.json").writeText(json.toString())
    }
}

data class ImportAllResult(
    val imported: Int,
    val failed: Int,
    val errors: List<String>,
)
