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
    /** Gestium « Catégorie » — not Famille, not Rayon. */
    private val categoryHeaders = setOf("category", "categorie", "catégorie")
    private val rayonHeaders = setOf("rayon")
    private val familleHeaders = setOf("famille")
    private val brandHeaders = setOf("brand", "marque")
    private val stockHeaders = setOf("stock", "stock reel", "stock réel", "qte", "quantite", "quantité")
    private val unitHeaders = setOf("unit", "unite", "unité", "uom", "unite de mesure (vente)")

    private val fallbackCharsets = listOf(
        Charset.forName("Windows-1252"),
        StandardCharsets.ISO_8859_1,
        StandardCharsets.UTF_8,
    )

    fun parseWithFallback(input: InputStream): CsvParseResult {
        val bytes = input.readBytes()
        val attempts = fallbackCharsets.mapNotNull { charset ->
            runCatching {
                val result = parse(ByteArrayInputStream(bytes), charset)
                result to validate(result.headers)
            }.getOrNull()
        }
        val bestValid = attempts
            .filter { (_, validation) -> validation.isValid }
            .maxByOrNull { (result, _) -> result.rows.size }
            ?.first
        if (bestValid != null && bestValid.rows.isNotEmpty()) return bestValid
        return attempts.maxByOrNull { (result, _) -> result.rows.size }?.first
            ?: CsvParseResult(emptyList(), emptyList(), ',', 0, 0)
    }

    fun validate(headers: List<String>): CsvValidation {
        val columnMap = buildColumnMap(headers)
        val missing = buildList {
            if (!columnMap.containsKey("designation")) add("designation (Désignation)")
            if (!columnMap.containsKey("price")) add("price (Prix de vente TTC)")
            if (!columnMap.containsKey("barcode") && !columnMap.containsKey("codeart")) {
                add("barcode (Code-barres) or code (Gestium Code)")
            }
        }
        return CsvValidation(missing.isEmpty(), missing, headers)
    }

    fun parse(input: InputStream, charset: Charset = Charsets.UTF_8): CsvParseResult {
        val text = input.bufferedReader(charset).readText()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return CsvParseResult(emptyList(), emptyList(), ',', 0, 0)
        }

        val delimiter = detectDelimiter(lines.first())
        val rawHeaders = splitLine(lines.first(), delimiter).map { it.replace("\"", "").trim() }
        val headers = rawHeaders.map { normalizeHeader(it) }
        val columnMap = buildColumnMap(headers)

        var skipped = 0
        var barcodeLess = 0
        val rows = lines.drop(1).mapNotNull { line ->
            val cells = splitLine(line, delimiter)
            if (cells.size < 2) {
                skipped++
                null
            } else {
                val row = parseRow(cells, columnMap, rawHeaders)
                if (row == null) {
                    skipped++
                    null
                } else if (row.barcode.isBlank() || row.designation.isBlank()) {
                    skipped++
                    null
                } else {
                    if (row.rawBarcodeWasEmpty) barcodeLess++
                    row
                }
            }
        }

        return CsvParseResult(rows, headers, delimiter, skipped, barcodeLess)
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
                if (candidates.any { candidate -> header == candidate || header.contains(candidate) }) {
                    return index
                }
            }
            return null
        }

        fun findExactIndex(vararg names: String): Int? {
            headers.forEachIndexed { index, header ->
                if (names.any { header == it }) return index
            }
            return null
        }

        return buildMap {
            findIndex(barcodeHeaders)?.let { put("barcode", it) }
            findIndex(designationHeaders)?.let { put("designation", it) }
            findPriceIndex(headers)?.let { put("price", it) }
            findCodeartIndex(headers)?.let { put("codeart", it) }
            findIndex(referenceHeaders)?.let { put("reference", it) }
            findExactIndex("rayon")?.let { put("rayon", it) }
                ?: findIndex(rayonHeaders)?.let { put("rayon", it) }
            findExactIndex("famille")?.let { put("famille", it) }
                ?: findIndex(familleHeaders)?.let { put("famille", it) }
            findIndex(categoryHeaders)?.let { put("category", it) }
            findIndex(brandHeaders)?.let { put("brand", it) }
            findIndex(stockHeaders)?.let { put("stock", it) }
            findIndex(unitHeaders)?.let { put("unit", it) }
        }
    }

    private fun findCodeartIndex(headers: List<String>): Int? {
        headers.forEachIndexed { index, header ->
            if (
                header == "code" ||
                header == "codeart" ||
                header.contains("code art") ||
                header == "code article"
            ) {
                return index
            }
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
        val designationIdx = columnMap["designation"] ?: return null
        val priceIdx = columnMap["price"] ?: return null

        val rawBarcode = columnMap["barcode"]
            ?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") }
            .orEmpty()
        val designation = cells.getOrNull(designationIdx)?.trim()?.replace("\"", "") ?: return null
        if (designation.isBlank()) return null
        val price = parseFrenchNumber(cells.getOrNull(priceIdx)) ?: return null

        val codeart = columnMap["codeart"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "")?.takeIf { it.isNotBlank() } }
        val reference = columnMap["reference"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "")?.takeIf { it.isNotBlank() } }
        if (rawBarcode.isBlank() && codeart.isNullOrBlank() && reference.isNullOrBlank()) return null

        return ParsedArticleRow(
            barcode = rawBarcode,
            designation = designation,
            price = price,
            codeart = codeart,
            reference = reference,
            category = columnMap["category"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "")?.takeIf { it.isNotBlank() } },
            rayon = columnMap["rayon"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "")?.takeIf { it.isNotBlank() } },
            famille = columnMap["famille"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "")?.takeIf { it.isNotBlank() } },
            brand = columnMap["brand"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            stock = columnMap["stock"]?.let { parseFrenchNumber(cells.getOrNull(it)) },
            unit = columnMap["unit"]?.let { cells.getOrNull(it)?.trim()?.replace("\"", "") },
            rawData = buildRawData(cells, headers),
            rawBarcodeWasEmpty = rawBarcode.isBlank(),
        ).resolveImportBarcode()
    }

    private fun buildRawData(cells: List<String>, headers: List<String>): String {
        return cells.mapIndexedNotNull { index, cell ->
            val value = cell.trim().replace("\"", "")
            val label = headers.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"
            if (value.isBlank()) null else "$label: $value"
        }.joinToString("\n")
    }
}
