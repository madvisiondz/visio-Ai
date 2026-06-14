package com.oasismall.oasisai.domain

data class ParsedArticleRow(
    val barcode: String,
    val designation: String,
    val price: Double,
    val codeart: String? = null,
    val reference: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val stock: Double? = null,
    val unit: String? = null,
    val rawData: String? = null,
)

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
