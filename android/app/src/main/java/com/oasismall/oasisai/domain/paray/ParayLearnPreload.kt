package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.repository.OasisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preload step before camera — PARAY already knows what product it expects.
 * Identity from Room; visual hints from PNG + optional CLIP fingerprint.
 */
class ParayLearnPreload(
    private val repository: OasisRepository,
    private val paray: ParayAgent,
) {
    suspend fun load(articleId: Long): ParayLearnSessionContext? = withContext(Dispatchers.IO) {
        val article = repository.getArticleWithImageById(articleId) ?: return@withContext null
        if (!ParayLearnEligibility.evaluate(article).eligible) return@withContext null

        val pngPath = article.imagePath!!.trim()
        val pngFeatures = paray.extractPngFeatures(pngPath) ?: return@withContext null

        val existing = paray.learnStore.get(article.id)
        val now = System.currentTimeMillis()
        val record = (existing ?: ParayLearnRecord(
            articleId = article.id,
            barcode = article.barcode,
            designation = article.designation,
            brand = article.brand?.takeIf { it.isNotBlank() },
            category = article.category?.takeIf { it.isNotBlank() },
            family = article.rayon?.takeIf { it.isNotBlank() },
            pngFrontPath = pngPath,
            createdAt = now,
            updatedAt = now,
            productSignature = pngFeatures.toSignatures("png_front_reference"),
        )).copy(
            brand = article.brand?.takeIf { it.isNotBlank() } ?: existing?.brand,
            category = article.category?.takeIf { it.isNotBlank() } ?: existing?.category,
            family = article.rayon?.takeIf { it.isNotBlank() } ?: existing?.family,
            productSignature = existing?.productSignature ?: pngFeatures.toSignatures("png_front_reference"),
        )

        val hasFingerprint = paray.hasFingerprintForBarcode(article.barcode)

        ParayLearnSessionContext(
            product = ParayLearnSessionProduct(
                articleId = article.id,
                barcode = article.barcode,
                designation = article.designation,
                brand = article.brand,
                category = article.category,
                family = article.rayon,
                pngPath = pngPath,
                learningStatus = record.status,
                hasFingerprint = hasFingerprint,
            ),
            record = record,
            pngFeatures = pngFeatures,
            hasFingerprint = hasFingerprint,
            fingerprintDim = paray.fingerprintMeta()?.dim ?: 0,
            preloadComplete = true,
        )
    }
}

private fun VisualFeatureExtractor.Features.toSignatures(source: String) = ParayVisualSignatures(
    shapeAspect = shapeAspect,
    fillRatio = fillRatio,
    dominantColors = dominantColors,
    source = source,
)
