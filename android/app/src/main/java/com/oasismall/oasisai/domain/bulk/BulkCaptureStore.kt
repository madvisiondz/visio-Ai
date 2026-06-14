package com.oasismall.oasisai.domain.bulk

import android.content.Context
import com.oasismall.oasisai.data.db.dao.BulkCaptureDao
import com.oasismall.oasisai.data.db.entity.BulkCaptureEntity
import com.oasismall.oasisai.util.PngMetadata
import kotlinx.coroutines.flow.Flow
import java.io.File

class BulkCaptureStore(
    private val context: Context,
    private val dao: BulkCaptureDao,
) {
    /** Legacy in-app copies (read-only fallback for older bulk saves). */
    private val legacyImagesDir: File =
        File(context.filesDir, "bulk_images")

    private val productImagesDir: File =
        File(context.filesDir, "product_images")

    fun downloadBulkDir(): File = BulkDownloadStorage.bulkDir()

    fun targetFile(barcode: String): File = BulkDownloadStorage.pngFile(barcode)

    /** PNG path: Download/BULK, bulk_captures row, legacy app storage, or product_images/{barcode}.png */
    suspend fun findExistingImagePath(barcode: String): String? {
        val trimmed = barcode.trim()
        BulkDownloadStorage.findPngPath(context, trimmed)?.let { return it }
        dao.getByBarcode(trimmed)?.imagePath
            ?.takeIf { File(it).exists() }
            ?.let { return it }
        val legacy = File(legacyImagesDir, "${PngMetadata.barcodeFileStem(trimmed)}.png")
        if (legacy.exists()) return legacy.absolutePath
        val legacyProduct = File(productImagesDir, "${PngMetadata.barcodeFileStem(trimmed)}.png")
        if (legacyProduct.exists()) return legacyProduct.absolutePath
        return null
    }

    suspend fun saveCutout(barcode: String, cutoutFile: File, replaced: Boolean): BulkCaptureEntity {
        val trimmed = barcode.trim()
        val path = BulkDownloadStorage.savePng(context, trimmed, cutoutFile)
        BulkDownloadStorage.appendBarcodeToTracker(context, trimmed, replaced)
        val entity = BulkCaptureEntity(
            barcode = trimmed,
            imagePath = path,
            capturedAt = System.currentTimeMillis(),
            replaced = replaced,
            syncStatus = "PENDING",
        )
        dao.upsert(entity)
        return entity
    }

    fun observeCaptureCount(): Flow<Int> = dao.observeCount()
}
