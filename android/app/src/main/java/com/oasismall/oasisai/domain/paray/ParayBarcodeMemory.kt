package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONObject
import java.io.File

/**
 * PARAY remembers barcode patterns — same article, different packaging barcode (often last 4 digits).
 */
class ParayBarcodeMemory(home: ParayHome) {
    private val file = home.barcodePatternsFile

    fun remember(scannedBarcode: String, catalogBarcode: String, articleId: Long, designation: String) {
        val root = readRoot()
        val key = patternKey(scannedBarcode, catalogBarcode)
        root.put(
            key,
            JSONObject()
                .put("scanned", scannedBarcode)
                .put("catalogBarcode", catalogBarcode)
                .put("articleId", articleId)
                .put("designation", designation)
                .put("lastFourScanned", lastFour(scannedBarcode))
                .put("lastFourCatalog", lastFour(catalogBarcode))
                .put("learnedAt", System.currentTimeMillis()),
        )
        file.writeTextAtomic(root.toString())
    }

    fun lookupByLastFour(scannedBarcode: String): List<LearnedBarcodePattern> {
        val last4 = lastFour(scannedBarcode)
        if (last4.length < 4) return emptyList()
        return readRoot().keys().asSequence().mapNotNull { key ->
            readRoot().optJSONObject(key)?.let { o ->
                if (o.optString("lastFourScanned") == last4 || o.optString("lastFourCatalog") == last4) {
                    LearnedBarcodePattern(
                        articleId = o.getLong("articleId"),
                        catalogBarcode = o.getString("catalogBarcode"),
                        designation = o.getString("designation"),
                        scannedBarcode = o.optString("scanned", ""),
                    )
                } else null
            }
        }.distinctBy { it.articleId }.toList()
    }

    fun count(): Int = readRoot().length()

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    private fun patternKey(scanned: String, catalog: String): String =
        "${scanned.trim()}->${catalog.trim()}"

    private fun lastFour(barcode: String): String {
        val digits = barcode.filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else digits
    }
}

data class LearnedBarcodePattern(
    val articleId: Long,
    val catalogBarcode: String,
    val designation: String,
    val scannedBarcode: String,
)
