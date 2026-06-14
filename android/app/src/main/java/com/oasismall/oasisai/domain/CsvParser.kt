package com.oasismall.oasisai.domain

import com.oasismall.oasisai.util.NameNormalizer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CsvParser {
    private val barcodeHeaders = setOf(
        "code-barres", "code barres", "code_barres", "barcode", "code_barre", "ean", "gtin", "code barre",
    )
    private val designationHeaders = setOf(
        "designation", "désignation", "libelle", "libellé", "name", "article", "description", "nom",
    )
    private val priceHeaders = setOf("price", "prix", "prix_vente", "prix vente", "pv", "prix de vente")
    private val referenceHeaders = setOf("reference", "référence", "ref")
    private val categoryHeaders = setOf("category", "categorie", "catégorie", "rayon", "famille")
    private val brandHeaders = setOf("brand", "marque")
    private val stockHeaders = setOf("stock", "stock reel", "stock réel", "qte", "quantite", "quantité")
    private val unitHeaders = setOf("unit", "unite", "unité", "uom", "unite de mesure (vente)")

    private val fallbackCharsets = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("Windows-1252"),
        StandardCharsets.ISO_8859_1,
    )

    fun parseWithFallback(input: InputStream): CsvParseResult {
        val bytes = input.readBytes()
        var lastResult = CsvParseResult(emptyList(), emptyList(), ',', 0)
        for (charset in fallbackCharsets) {
            val result = parse(ByteArrayInputStream(bytes), charset)
            lastResult = result
            val validation = validate(result.headers)
            if (validation.isValid && result.rows.isNotEmpty()) return result
        }
        return lastResult
    }

    fun validate(headers: List<String>): CsvValidation {
        val columnMap = buildColumnMap(headers)
        val missing = buildList {
            if (!columnMap.containsKey("barcode")) add("barcode (Code-barres)")
            if (!columnMap.containsKey("designation")) add("designation (Désignation)")
            if (!columnMap.containsKey("price")) add("price (Prix de vente TTC)")
        }
        return CsvValidation(missing.isEmpty(), missing, headers)
    }

    fun parse(input: InputStream, charset: Charset = Charsets.UTF_8): CsvParseResult {
        val text = input.bufferedReader(charset).readText()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return CsvParseResult(emptyList(), emptyList(), ',', 0)
        }

        val delimiter = detectDelimiter(lines.first())
        val rawHeaders = splitLine(lines.first(), delimiter).map { it.replace("\"", "").trim() }
        val headers = rawHeaders.map { normalizeHeader(it) }
        val columnMap = buildColumnMap(headers)

        var skipped = 0
        val rows = lines.drop(1).mapNotNull { line ->
            val cells = splitLine(line, delimiter)
            if (cells.size < 2) {
                skipped++
                null
            } else {
                parseRow(cells, columnMap, rawHeaders)?.also {
                    if (it.barcode.isBlank() || it.designation.isBlank()) skipped++
                } ?: run {
                    skipped++
                    null
                }
            }
        }.filter { it.barcode.isNotBlank() && it.designation.isBlank().not() }

        return CsvParseResult(rows, headers, delimiter, skipped)
    }

    private fun detectDelimiter(header: String): Char {
        val semicolon = header.count { it == ';' }
        val comma = header.count { it == ',' }
        val tab = header.count { it == '\t' }
        return when {
            tab >= semicolon && tab >= comma -> '\t'
            semicolon >= comma -> ';'
            else -> ','
        }
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun normalizeHeader(header: String): String {
        return NameNormalizer.normalize(header.replace("\"", ""))
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildColumnMap(headers: List<String>): Map<String, Int> {
        fun findIndex(candidates: Set<String>): Int? {
            headers.forEachIndexed { index, header ->
                if (candidates.any { candidate -> header.contains(candidate) }) return index
            }
            return null
        }

        return buildMap {
            findIndex(barcodeHeaders)?.let { put("barcode", it) }
            findIndex(designationHeaders)?.let { put("designation", it) }
            findPriceIndex(headers)?.let { put("price", it) }
            findCodeartIndex(headers)?.let { put("codeart", it) }
            findIndex(referenceHeaders)?.let { put("reference", it) }
            findIndex(categoryHeaders)?.let { put("category", it) }
            findIndex(brandHeaders)?.let { put("brand", it) }
            findIndex(stockHeaders)?.let { put("stock", it) }
            findIndex(unitHeaders)?.let { put("unit", it) }
        }
    }

    private fun findCodeartIndex(headers: List<String>): Int? {
        headers.forEachIndexed { index, header ->
            if (header == "code") return index
        }
        return null
    }

    private fun findPriceIndex(headers: List<String>): Int? {
        headers.forEachIndexed { index, header ->
            if (header.contains("prix de vente") && header.contains("ttc")) return index
        }
        headers.forEachIndexed { index, header ->
            if (header.contains("prix de vente") && header.contains("ht")) return index
        }
        headers.forEachIndexed { index, header ->
            if (header.contains("prix de vente") || header.contains("prix")) return index
        }
        return null
    }

    private fun parseFrenchNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace("\"", "")
            .replace("\u00A0", " ")
            .replace(" ", "")
            .replace(",", ".")
            .filter { it.isDigit() || it == '.' || it == '-' }
        return cleaned.toDoubleOrNull()
    }

    private fun parseRow(
        cells: List<String>,
        columnMap: Map<String, Int>,
        headers: List<String>,
    ): ParsedArticleRow? {
        val barcodeIdx = columnMap["barcode"] ?: return null
        val designationIdx = columnMap["designation"] ?: return null
        val priceIdx = columnMap["price"] ?: return null

        val barcode = cells.getOrNull(barcodeIdx)?.trim()?.replace("\"", "") ?: return null
        val designation = cells.getOrNull(designationIdx)?.trim()?.replace("\"", "") ?: return null
        val price = parseFrenchNumber(cells.getOrNull(priceIdx)) ?: return null

        return ParsedArticleRow(
            barcode = barcode,
            designation = designation,
            price = price,
            codeart = columnMap["codeart"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            reference = columnMap["reference"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            category = columnMap["category"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            brand = columnMap["brand"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            stock = columnMap["stock"]?.let { parseFrenchNumber(cells.getOrNull(it)) },
            unit = columnMap["unit"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            rawData = buildRawData(cells, headers),
        )
    }

    private fun buildRawData(cells: List<String>, headers: List<String>): String {
        return cells.mapIndexedNotNull { index, cell ->
            val value = cell.trim().replace("\"", "")
            val label = headers.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"
            if (value.isBlank()) null else "$label: $value"
        }.joinToString("\n")
    }
}
