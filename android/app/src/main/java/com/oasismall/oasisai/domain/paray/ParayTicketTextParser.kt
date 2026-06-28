package com.oasismall.oasisai.domain.paray

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Extract black designation (up to 2 lines) + red/magenta price on yellow Oasis tickets.
 * ML Kit on-device OCR (offline-stable).
 */
object ParayTicketTextParser {
    private data class OcrLine(
        val text: String,
        val height: Float,
        val top: Float,
        val centerX: Float = 0f,
        val bottom: Float = 0f,
    )

    fun parse(
        text: Text,
        imageWidth: Int? = null,
        yellowBandOnly: Boolean = false,
        maxDesignationRows: Int = 2,
    ): ParayTicketReadResult? {
        val width = imageWidth ?: text.textBlocks.firstOrNull()?.boundingBox?.right ?: return parseLegacy(text)
        val lines = extractLines(text, width)
        if (lines.isEmpty()) return null

        val filtered = if (yellowBandOnly) {
            lines.filter { it.centerX >= width * 0.38f }
        } else {
            lines
        }.ifEmpty { lines }

        return parseLines(filtered, text.text, maxDesignationRows)
    }

    private fun extractLines(text: Text, width: Int): List<OcrLine> =
        text.textBlocks
            .flatMap { block ->
                block.lines.map { line ->
                    val box: Rect? = line.boundingBox
                    OcrLine(
                        text = line.text.trim(),
                        height = box?.height()?.toFloat() ?: 0f,
                        top = box?.top?.toFloat() ?: 0f,
                        bottom = box?.bottom?.toFloat() ?: 0f,
                        centerX = box?.let { (it.left + it.right) / 2f } ?: width / 2f,
                    )
                }
            }
            .filter { it.text.isNotBlank() }

    private fun parseLegacy(text: Text): ParayTicketReadResult? {
        val lines = extractLines(text, 1)
        if (lines.isEmpty()) return null
        return parseLines(lines, text.text)
    }

    /** Keep up to two designation lines; do not truncate mid-phrase on normal tickets. */
    fun trimShelfDesignation(raw: String): String {
        val cleaned = cleanupOcrDesignation(raw)
        if (cleaned.contains("\n")) {
            return cleaned.split("\n").map { cleanupOcrDesignation(it) }.filter { it.isNotBlank() }.take(2).joinToString(" ")
        }
        return cleaned
    }

    private fun parseLines(lines: List<OcrLine>, fullText: String, maxDesignationRows: Int = 2): ParayTicketReadResult? {
        val frameBottom = lines.maxOfOrNull { it.bottom } ?: 0f
        val ocrPrice = extractPrice(fullText, lines, frameBottom)
        val designation = extractDesignation(lines, ocrPrice, frameBottom, maxDesignationRows)

        if (designation == null && ocrPrice == null) return null

        val confidence = when {
            designation != null && ocrPrice != null -> 0.94f
            designation != null -> 0.80f
            else -> 0.55f
        }

        return ParayTicketReadResult(
            source = ParayTicketReadSource.OCR_DESIGNATION,
            ocrDesignation = designation,
            ocrPrice = ocrPrice,
            confidence = confidence,
        )
    }

    /** Price sits in red/magenta box at bottom of yellow band — prefer lower lines. */
    private fun extractPrice(fullText: String, lines: List<OcrLine>, frameBottom: Float): Double? {
        val bottomThreshold = if (frameBottom > 0f) frameBottom * 0.52f else 0f
        val bottomPriceLines = lines
            .filter { it.bottom >= bottomThreshold && looksLikePrice(it.text) }
            .sortedWith(compareByDescending<OcrLine> { it.height }.thenByDescending { it.bottom })

        bottomPriceLines.firstOrNull()?.text?.let { parsePriceToken(it) }?.let { return it }

        PRICE_REGEX.findAll(fullText.uppercase())
            .mapNotNull { match -> parsePriceToken(match.value) }
            .maxByOrNull { it }
            ?.let { return it }

        return lines
            .filter { looksLikePrice(it.text) }
            .maxWithOrNull(compareBy<OcrLine> { it.height }.thenByDescending { it.bottom })
            ?.text
            ?.let(::parsePriceToken)
    }

    /** Black designation: consecutive text lines above the price block. */
    private fun extractDesignation(
        lines: List<OcrLine>,
        price: Double?,
        frameBottom: Float,
        maxRows: Int = 2,
    ): String? {
        val priceTop = lines
            .filter { looksLikePrice(it.text) }
            .minOfOrNull { it.top }
            ?: frameBottom

        val desLines = lines
            .filter { line ->
                !looksLikePrice(line.text) &&
                    line.top < priceTop - 2f &&
                    line.text.length >= 2 &&
                    line.text.any { it.isLetter() }
            }
            .sortedBy { it.top }

        if (desLines.isEmpty()) {
            return lines
                .filter { !looksLikePrice(it.text) && it.text.any { c -> c.isLetter() } }
                .sortedBy { it.top }
                .take(maxRows)
                .joinToString(" ") { cleanupOcrDesignation(it.text) }
                .takeIf { it.length >= 3 }
        }

        val rows = groupDesignationRows(desLines)
        val picked = rows
            .sortedBy { row -> row.minOf { it.top } }
            .take(maxRows)
            .flatten()
            .sortedBy { it.top }

        return picked
            .joinToString(" ") { cleanupOcrDesignation(it.text) }
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.length >= 3 }
    }

    private fun groupDesignationRows(lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.top }
        val rows = mutableListOf<MutableList<OcrLine>>()
        var current = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val line = sorted[i]
            val gap = line.top - prev.bottom
            val avgH = ((line.height + prev.height) / 2f).coerceAtLeast(8f)
            if (gap <= avgH * 1.35f) {
                current.add(line)
            } else {
                rows.add(current)
                current = mutableListOf(line)
            }
        }
        rows.add(current)
        return rows
    }

    fun cleanupOcrDesignation(raw: String): String =
        raw
            .replace(Regex("(?i)^THE\\s+"), "")
            .replace(Regex("(?i)\\bDA\\b"), "")
            .replace(Regex("[|_~`\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun looksLikePrice(line: String): Boolean {
        val upper = line.uppercase().trim()
        if (PRICE_REGEX.containsMatchIn(upper)) return true
        val cleaned = upper.replace(" ", "")
        if (cleaned.endsWith("DA")) return true
        return Regex("\\d{2,4}").containsMatchIn(cleaned) &&
            cleaned.replace(Regex("[\\d.,DA\\s]"), "").length <= 2
    }

    fun parsePriceToken(raw: String): Double? {
        val digits = raw
            .uppercase()
            .replace("DA", "")
            .replace(" ", "")
            .replace(",", ".")
            .filter { it.isDigit() || it == '.' }
        return digits.toDoubleOrNull()?.takeIf { it in 1.0..999_999.0 }
    }

    private val PRICE_REGEX = Regex("""(\d{2,4})\s*D?\s*A?""", RegexOption.IGNORE_CASE)
}
