package com.oasismall.oasisai.util

import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle

/**
 * Round-trip text format for Design queue price check on PC.
 * Send → edit prices → paste back → Import checked prices.
 */
object DesignPriceMessage {
    const val HEADER = "OASIS-DESIGN-PRICES v1"

    data class PriceLine(
        val barcode: String,
        val designation: String? = null,
        val price: Double,
    )

    fun formatForShare(items: List<PreselectionWithArticle>): String = buildString {
        appendLine(HEADER)
        appendLine("# Edit the last number on each line, then send back → Design → Import checked prices")
        appendLine()
        items.forEachIndexed { index, item ->
            val price = PriceFormatter.formatNumber(item.price)
            appendLine("${index + 1}|${item.barcode}|${item.designation}|$price")
        }
    }.trimEnd()

    fun parse(raw: String): List<PriceLine> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val pipeLines = lines.mapNotNull { parsePipeLine(it) }
        if (pipeLines.isNotEmpty()) return pipeLines

        return parseLegacyBlock(lines)
    }

    private fun parsePipeLine(line: String): PriceLine? {
        if (line.startsWith("#") || line.startsWith(HEADER)) return null
        val parts = line.split("|")
        if (parts.size < 4) return null
        val barcode = parts[1].trim()
        if (barcode.isEmpty()) return null
        val price = parsePriceToken(parts.last()) ?: return null
        val designation = parts.drop(2).dropLast(1).joinToString("|").trim().ifBlank { null }
        return PriceLine(barcode = barcode, designation = designation, price = price)
    }

    private fun parseLegacyBlock(lines: List<String>): List<PriceLine> {
        val result = mutableListOf<PriceLine>()
        var pendingBarcode: String? = null
        var pendingDesignation: String? = null
        for (line in lines) {
            if (line.startsWith("#") || line.contains(HEADER, ignoreCase = true)) continue
            val numbered = Regex("""^\d+\.\s*(.+)$""").find(line)
            if (numbered != null) {
                pendingDesignation = numbered.groupValues[1].trim()
                pendingBarcode = null
                continue
            }
            val onlyBarcode = line.replace(Regex("""^\s+"""), "")
            if (onlyBarcode.all { it.isDigit() } && onlyBarcode.length >= 8) {
                pendingBarcode = onlyBarcode
                continue
            }
            val price = parsePriceToken(line) ?: continue
            val barcode = pendingBarcode ?: continue
            result.add(PriceLine(barcode, pendingDesignation, price))
            pendingBarcode = null
            pendingDesignation = null
        }
        return result
    }

    private fun parsePriceToken(token: String): Double? {
        val cleaned = token
            .replace(Regex("""(?i)\s*DA\s*$"""), "")
            .replace(" ", "")
            .replace(",", ".")
            .trim()
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value >= 0) value else null
    }
}
