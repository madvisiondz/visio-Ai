package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds

/**
 * Offline camera matching — compares live frame to PARAY visual index + learned multi-view records.
 */
class ParayCameraMatcher(
    private val index: ParayVisualIndex,
    private val fingerprintStore: ParayFingerprintStore,
    private val learnStore: ParayLearnStore,
    private val brandKnowledge: BrandKnowledgeProvider = BrandKnowledgeProvider.None,
    private val onMatchResults: ((List<ParayMatch>) -> Unit)? = null,
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
        val learned = learnStore.recordsByArticleId()
        val embeddings = fingerprintStore.allEmbeddings().associate { it.first to it.second }

        if (records.isEmpty() && learned.isEmpty()) return emptyList()

        val articleIds = (records.map { it.articleId } + learned.keys).distinct()

        val results = articleIds
            .mapNotNull { articleId ->
                val sig = records.firstOrNull { it.articleId == articleId }
                val learn = learned[articleId]
                var score = sig?.let { similarity(probe, it) } ?: 0f
                learn?.let { score = maxOf(score, learnedViewScore(probe, it)) }
                embeddings[sig?.barcode ?: learn?.barcode]?.let {
                    score = (score * 0.55f + 0.45f).coerceIn(0f, 1f)
                }
                score = (score + brandKnowledgeReadBoost(articleId)).coerceIn(0f, 1f)
                val barcode = sig?.barcode ?: learn?.barcode ?: return@mapNotNull null
                val designation = sig?.designation ?: learn?.designation ?: return@mapNotNull null
                ParayMatch(
                    articleId = articleId,
                    barcode = barcode,
                    designation = designation,
                    confidence = score,
                )
            }
            .filter { it.confidence >= 0.30f }
            .sortedByDescending { it.confidence }
            .take(topK)
        onMatchResults?.invoke(results)
        return results
    }

    /** V1 read-side hook — loads brand/family knowledge; scoring boost is 0f until V2. */
    private fun brandKnowledgeReadBoost(articleId: Long): Float {
        brandKnowledge.getBrandKnowledge(articleId)
        return 0f
    }

    private fun learnedViewScore(probe: VisualFeatureExtractor.Features, record: ParayLearnRecord): Float {
        val views = buildList {
            record.productSignature?.let {
                add(
                    VisualFeatureExtractor.Features(
                        it.shapeAspect,
                        it.fillRatio,
                        it.dominantColors,
                    ),
                )
            }
            record.leftCapture?.toFeatures()?.let { add(it) }
            record.rightCapture?.toFeatures()?.let { add(it) }
            record.backCapture?.toFeatures()?.let { add(it) }
        }
        if (views.isEmpty()) return 0f
        val best = views.maxOf { ParayVisualSimilarity.score(probe, it) }
        val boost = when (record.status) {
            ParayLearnStatus.LEARNED -> 0.12f
            ParayLearnStatus.PARTIALLY_LEARNED -> 0.05f
            else -> 0f
        }
        return (best + boost).coerceIn(0f, 1f)
    }

    private fun similarity(
        probe: VisualFeatureExtractor.Features,
        sig: ProductVisualSignature,
    ): Float {
        val obsBoost = (sig.observationCount.coerceAtMost(20) / 20f) * 0.05f
        return ParayVisualSimilarity.score(
            probe,
            VisualFeatureExtractor.Features(sig.shapeAspect, sig.fillRatio, sig.dominantColors),
            obsBoost,
        )
    }
}
