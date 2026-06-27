package com.oasismall.oasisai.domain.paray

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/** Shared designation + price extraction from ML Kit text — used by all OCR passes. */
object ParayTicketTextParser {
    private data class OcrLine(
        val text: String,
        val height: Float,
        val top: Float,
    )

    fun parse(text: Text): ParayTicketReadResult? {
        val lines = text.textBlocks
            .flatMap { block ->
                block.lines.map { line ->
                    val box: Rect? = line.boundingBox
                    OcrLine(
                        text = line.text.trim(),
                        height = box?.height()?.toFloat() ?: 0f,
                        top = box?.top?.toFloat() ?: 0f,
                    )
                }
            }
            .filter { it.text.isNotBlank() }

        if (lines.isEmpty()) return null

        val fullText = text.text
        val ocrPrice = extractPrice(fullText, lines)
        val designation = extractDesignation(lines, ocrPrice)

        if (designation == null && ocrPrice == null) return null

        val confidence = when {
            designation != null && ocrPrice != null -> 0.92f
            designation != null -> 0.78f
            else -> 0.55f
        }

        return ParayTicketReadResult(
            source = ParayTicketReadSource.OCR_DESIGNATION,
            ocrDesignation = designation,
            ocrPrice = ocrPrice,
            confidence = confidence,
        )
    }

    private fun extractPrice(fullText: String, lines: List<OcrLine>): Double? {
        PRICE_REGEX.findAll(fullText.uppercase())
            .mapNotNull { match -> parsePriceToken(match.value) }
            .maxByOrNull { it }
            ?.let { return it }

        return lines
            .filter { looksLikePrice(it.text) }
            .maxWithOrNull(compareBy<OcrLine> { it.height }.thenBy { it.top })
            ?.text
            ?.let(::parsePriceToken)
    }

    private fun extractDesignation(lines: List<OcrLine>, price: Double?): String? {
        val desLines = lines
            .filter { line ->
                !looksLikePrice(line.text) &&
                    line.text.length >= 2 &&
                    line.text.any { it.isLetter() }
            }
            .sortedBy { it.top }

        val joined = desLines
            .take(5)
            .joinToString(" ") { cleanupOcrDesignation(it.text) }
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.length >= 4 }

        if (joined != null) return joined

        return lines
            .map { cleanupOcrDesignation(it.text) }
            .filter { !looksLikePrice(it) && it.length >= 4 }
            .joinToString(" ")
            .takeIf { it.length >= 4 }
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
