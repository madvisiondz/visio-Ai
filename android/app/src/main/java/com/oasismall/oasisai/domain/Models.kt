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
) {
    /** Gestium rows often omit Code-barres; codeart or normalized designation becomes the DB lookup key. */
    fun resolveImportBarcode(): ParsedArticleRow {
        if (barcode.isNotBlank()) return this
        val code = codeart?.trim().orEmpty()
        if (code.isNotBlank()) return copy(barcode = "CA:$code")
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
)

data class CsvValidation(
    val isValid: Boolean,
    val missingColumns: List<String>,
    val detectedHeaders: List<String>,
)

data class ImportDiffSummary(
    val importId: Long,
    val newCount: Int,
    val priceChangedCount: Int,
    val renamedCount: Int,
    val removedCount: Int,
    val unchangedCount: Int,
    val missingImagesCount: Int = 0,
)

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
