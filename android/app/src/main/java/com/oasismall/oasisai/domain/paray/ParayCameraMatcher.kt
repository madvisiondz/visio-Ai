package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import kotlin.math.abs

/**
 * Offline camera matching — compares live frame features to PARAY visual index.
 * v1: shape + color similarity. v2: GPU embeddings.
 */
class ParayCameraMatcher(
    private val index: ParayVisualIndex,
    private val fingerprintStore: ParayFingerprintStore,
) {
    fun identify(bitmap: Bitmap, topK: Int = 5): List<ParayMatch> {
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }
        val probe = VisualFeatureExtractor.extract(bitmap, content)
        val records = index.allSignatures()
        if (records.isEmpty()) return emptyList()

        val embeddings = fingerprintStore.allEmbeddings().associate { it.first to it.second }

        return records
            .map { sig ->
                var score = similarity(probe.shapeAspect, probe.fillRatio, probe.dominantColors, sig)
                embeddings[sig.barcode]?.let { stored ->
                    score = (score * 0.55f + 0.45f).coerceIn(0f, 1f)
                }
                ParayMatch(
                    articleId = sig.articleId,
                    barcode = sig.barcode,
                    designation = sig.designation,
                    confidence = score,
                )
            }
            .filter { it.confidence >= 0.30f }
            .sortedByDescending { it.confidence }
            .take(topK)
    }

    private fun similarity(
        aspect: Float,
        fill: Float,
        colors: List<Int>,
        sig: ProductVisualSignature,
    ): Float {
        val aspectScore = 1f - (abs(aspect - sig.shapeAspect) / maxOf(aspect, sig.shapeAspect, 0.1f)).coerceIn(0f, 1f)
        val fillScore = 1f - abs(fill - sig.fillRatio).coerceIn(0f, 1f)
        val colorScore = colorOverlap(colors, sig.dominantColors)
        val obsBoost = (sig.observationCount.coerceAtMost(20) / 20f) * 0.05f
        return (aspectScore * 0.35f + fillScore * 0.2f + colorScore * 0.4f + obsBoost).coerceIn(0f, 1f)
    }

    private fun colorOverlap(a: List<Int>, b: List<Int>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var hits = 0
        for (c in a) {
            if (b.any { nearColor(c, it) }) hits++
        }
        return hits.toFloat() / a.size
    }

    private fun nearColor(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) < 48
    }
}
