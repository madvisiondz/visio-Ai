package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.BarcodeSuffixMatcher

data class ParaySuggestion(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val confidence: Float,
    val reason: String,
    val imagePath: String? = null,
)

/**
 * PARAY barcode intelligence — Gestium body key (9 digits after dropping last 5) and learned patterns.
 */
class ParayBarcodeAdvisor(
    private val repository: OasisRepository,
    private val barcodeMemory: ParayBarcodeMemory,
) {
    suspend fun suggestForUnknownBarcode(scannedBarcode: String, topK: Int = 8): List<ParaySuggestion> {
        val trimmed = scannedBarcode.trim()
        if (trimmed.isEmpty()) return emptyList()

        val merged = linkedMapOf<Long, ParaySuggestion>()

        fun offer(article: ArticleWithImage, confidence: Float, reason: String) {
            val existing = merged[article.id]
            if (existing == null || confidence > existing.confidence) {
                merged[article.id] = ParaySuggestion(
                    articleId = article.id,
                    barcode = article.barcode,
                    designation = article.designation,
                    confidence = confidence,
                    reason = reason,
                    imagePath = article.imagePath,
                )
            }
        }

        // Learned patterns (PARAY memory from past confirms)
        for (pattern in barcodeMemory.lookupByLastFour(trimmed)) {
            repository.getArticleWithImageById(pattern.articleId)?.let { article ->
                offer(article, 0.92f, "PARAY remembers last-4 match")
            }
        }

        // Primary Gestium rule: drop last 5 digits, compare first 9 on the left
        val scannedKey = BarcodeSuffixMatcher.gestiumBodyKey(trimmed)
        if (scannedKey != null) {
            for (article in repository.findArticlesByGestiumBodyKey(trimmed)) {
                val catalogKey = BarcodeSuffixMatcher.gestiumBodyKey(article.barcode)
                val last4Diff = BarcodeSuffixMatcher.lastFourDiffer(trimmed, article.barcode)
                val reason = when {
                    catalogKey == scannedKey && last4Diff ->
                        "Same 9-digit code — last 5 digits differ (repack)"
                    catalogKey == scannedKey ->
                        "Same 9-digit product code"
                    else -> "Body key match ($scannedKey)"
                }
                val conf = when {
                    catalogKey == scannedKey && last4Diff -> 0.91f
                    catalogKey == scannedKey -> 0.90f
                    else -> 0.82f
                }
                offer(article, conf, reason)
            }
        }

        return merged.values
            .sortedByDescending { it.confidence }
            .take(topK)
    }

    suspend fun confirmSuggestion(scannedBarcode: String, suggestion: ParaySuggestion) {
        repository.linkAlternateBarcode(suggestion.articleId, scannedBarcode)
        barcodeMemory.remember(
            scannedBarcode = scannedBarcode,
            catalogBarcode = suggestion.barcode,
            articleId = suggestion.articleId,
            designation = suggestion.designation,
        )
    }
}
