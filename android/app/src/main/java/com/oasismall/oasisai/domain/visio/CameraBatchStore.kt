package com.oasismall.oasisai.domain.visio

import android.content.Context
import com.oasismall.oasisai.data.db.dao.CameraBatchDao
import com.oasismall.oasisai.data.db.entity.CameraBatchItemEntity
import com.oasismall.oasisai.data.model.CameraBatchStatus
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.PngMetadata
import com.oasismall.oasisai.util.PriceFormatter
import kotlinx.coroutines.flow.Flow
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

    fun observePendingCountToday(): Flow<Int> =
        dao.observeCountForDateAndStatus(todayDate(), CameraBatchStatus.AWAITING_PHOTOROOM.name)

    suspend fun saveTaggedShot(
        sourceJpeg: File,
        barcode: String,
        hintDesignation: String? = null,
    ): CameraBatchItemEntity {
        val trimmed = barcode.trim()
        require(trimmed.isNotBlank()) { "Barcode required" }
        val resolved = repository.resolveScannedBarcode(trimmed)
        val article = resolved?.article
        val articleEntity = article?.id?.let { repository.getArticleById(it) }
        val designation = article?.designation
            ?: hintDesignation?.trim()?.takeIf { it.isNotBlank() }
            ?: trimmed
        val folder = batchFolderName()
        val displayName = buildShotFileName(designation, trimmed)
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
            article?.price,
            article?.previousPrice,
        )
        val entity = CameraBatchItemEntity(
            batchDate = todayDate(),
            shotPath = path,
            shotFileName = displayName,
            barcode = trimmed,
            designation = designation,
            codeart = articleEntity?.codeart,
            price = article?.price?.takeIf { it > 0.0 },
            articleId = article?.id,
            status = CameraBatchStatus.AWAITING_PHOTOROOM.name,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun importFromPhotoroom(itemId: Long): Result<String> = runCatching {
        val item = dao.getById(itemId) ?: error("Batch item not found")
        val png = PhotoroomStorage.findPngForBarcode(item.barcode)
            ?: error("No PNG in ${PhotoroomStorage.DISPLAY_PATH} for barcode ${item.barcode}")
        val articleEntity = item.articleId?.let { repository.getArticleById(it) }
            ?: repository.resolveScannedBarcode(item.barcode)?.article?.id
                ?.let { repository.getArticleById(it) }
        if (articleEntity != null) {
            imageMatcher.registerCapturedImage(articleEntity, png)
            repository.removeFromCart(articleEntity.id, CartType.PHOTOSHOOT)
        } else {
            imageMatcher.registerCapturedImageByBarcode(item.barcode, png)
        }
        dao.updateStatus(item.id, CameraBatchStatus.IMPORTED.name, png.absolutePath)
        "Imported ${item.designation} → product_images"
    }

    suspend fun importAllPending(): ImportAllResult {
        val pending = dao.getForDateAndStatus(todayDate(), CameraBatchStatus.AWAITING_PHOTOROOM.name)
        var imported = 0
        val errors = mutableListOf<String>()
        pending.forEach { item ->
            importFromPhotoroom(item.id).fold(
                onSuccess = { imported++ },
                onFailure = { errors += "${item.barcode}: ${it.message}" },
            )
        }
        return ImportAllResult(imported = imported, failed = pending.size - imported, errors = errors.take(5))
    }

    fun buildShotFileName(designation: String, barcode: String): String {
        val stem = NameNormalizer.toDisplayFileStem(designation).ifBlank { PngMetadata.barcodeFileStem(barcode) }
        val bc = PngMetadata.barcodeFileStem(barcode)
        return "${stem}_$bc.jpg"
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
