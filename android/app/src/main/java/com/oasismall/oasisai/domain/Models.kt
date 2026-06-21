package com.oasismall.oasisai.domain

import com.oasismall.oasisai.util.NameNormalizer

data class ParsedArticleRow(
    val barcode: String,
    val designation: String,
    val price: Double,
    val codeart: String? = null,
    val reference: String? = null,
    /** Gestium Catégorie column (not Famille, not Rayon). */
    val category: String? = null,
    /** Gestium Rayon column — used for Articles screen filter. */
    val rayon: String? = null,
    val famille: String? = null,
    val brand: String? = null,
    val stock: Double? = null,
    val unit: String? = null,
    val rawData: String? = null,
    val rawBarcodeWasEmpty: Boolean = false,
) {
    /** Gestium rows often omit Code-barres; codeart, reference, or designation becomes the DB lookup key. */
    fun resolveImportBarcode(): ParsedArticleRow {
        if (barcode.isNotBlank()) return this
        val code = codeart?.trim().orEmpty()
        if (code.isNotBlank()) return copy(barcode = "CA:$code")
        val ref = reference?.trim().orEmpty()
        if (ref.isNotBlank()) return copy(barcode = "REF:$ref")
        val normalized = NameNormalizer.normalize(designation)
        if (normalized.isNotBlank()) return copy(barcode = "DN:$normalized")
        return this
    }
}

data class CsvParseResult(
    val rows: List<ParsedArticleRow>,
    val headers: List<String>,
    val delimiter: Char,
    val skippedRows: Int,
    /** Rows that had empty Code-barres but were kept via Gestium Code / Référence. */
    val barcodeLessRows: Int = 0,
    /** Rows skipped because designation is unreadable (e.g. Arabic export shown as ???????). */
    val garbledDesignationRows: Int = 0,
)

data class CsvValidation(
    val isValid: Boolean,
    val missingColumns: List<String>,
    val detectedHeaders: List<String>,
)

data class ImportChangeCounts(
    val newCount: Int = 0,
    val priceChangedCount: Int = 0,
    val renamedCount: Int = 0,
    val removedCount: Int = 0,
) {
    val hasChanges: Boolean =
        newCount > 0 || priceChangedCount > 0 || renamedCount > 0 || removedCount > 0

    companion object {
        fun fromChangeTypes(types: Iterable<String>): ImportChangeCounts {
            var newCount = 0
            var priceChangedCount = 0
            var renamedCount = 0
            var removedCount = 0
            types.forEach { type ->
                when (type) {
                    com.oasismall.oasisai.data.model.ImportChangeType.NEW.name -> newCount++
                    com.oasismall.oasisai.data.model.ImportChangeType.PRICE_CHANGED.name -> priceChangedCount++
                    com.oasismall.oasisai.data.model.ImportChangeType.RENAMED.name -> renamedCount++
                    com.oasismall.oasisai.data.model.ImportChangeType.REMOVED.name -> removedCount++
                }
            }
            return ImportChangeCounts(newCount, priceChangedCount, renamedCount, removedCount)
        }
    }
}

data class ImportDiffSummary(
    val importId: Long,
    val newCount: Int,
    val priceChangedCount: Int,
    val renamedCount: Int,
    val removedCount: Int,
    val unchangedCount: Int,
    val missingImagesCount: Int = 0,
    val scopedToImportantRayons: Boolean = false,
    val importantRayonsCount: Int = 0,
    val scopedRowCount: Int = 0,
    val scopedNewCount: Int = 0,
    val scopedPriceChangedCount: Int = 0,
    val scopedRenamedCount: Int = 0,
    val scopedRemovedCount: Int = 0,
) {
    val displayCounts: ImportChangeCounts
        get() = if (scopedToImportantRayons) {
            ImportChangeCounts(scopedNewCount, scopedPriceChangedCount, scopedRenamedCount, scopedRemovedCount)
        } else {
            ImportChangeCounts(newCount, priceChangedCount, renamedCount, removedCount)
        }
}

data class ImportResult(
    val success: Boolean,
    val importId: Long? = null,
    val summary: ImportDiffSummary? = null,
    val errorMessage: String? = null,
)

data class PrintGenerationResult(
    val success: Boolean,
    val batchId: Long? = null,
    val exportPath: String? = null,
    val previewPath: String? = null,
    val errorMessage: String? = null,
)
