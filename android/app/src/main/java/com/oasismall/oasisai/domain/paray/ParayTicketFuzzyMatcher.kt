package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.SearchQuery
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * PARAY fuzzy fusion for shelf tickets: designation (OCR) + price (OCR) + product PNG on ticket.
 * Weights: designation 50%, price 30%, catalog PNG match 20%.
 */
class ParayTicketFuzzyMatcher {
    suspend fun match(
        read: ParayTicketReadResult,
        productCrop: Bitmap?,
        repository: OasisRepository,
        paray: ParayAgent?,
        rayonPreference: String? = null,
        visualHints: Map<Long, Float> = emptyMap(),
        productFeatures: VisualFeatureExtractor.Features? = null,
    ): ParayTicketMatch? {
        read.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { barcode ->
            repository.resolveScannedBarcode(barcode)?.article?.let { article ->
                val fusion = scoreArticle(
                    read, productCrop, article, paray, rayonPreference,
                    barcodeBoost = 0.95f,
                    visualHints = visualHints,
                    productFeatures = productFeatures,
                )
                return ParayTicketMatch(article, read.copy(source = ParayTicketReadSource.BARCODE), fusion)
            }
        }

        val designation = read.ocrDesignation?.trim()?.takeIf { it.length >= 3 } ?: run {
            read.ocrPrice?.let { price ->
                return matchByPriceAndImage(
                    read, productCrop, price, repository, paray, rayonPreference,
                    visualHints, productFeatures,
                )
            }
            return null
        }

        val candidates = gatherCandidates(read, designation, productCrop, repository, paray, visualHints)
        if (candidates.isEmpty()) return null

        val ranked = candidates
            .distinctBy { it.id }
            .map { article ->
                val fusion = scoreArticle(
                    read, productCrop, article, paray, rayonPreference,
                    visualHints = visualHints,
                    productFeatures = productFeatures,
                )
                article to fusion
            }
            .sortedByDescending { it.second.probability }

        val best = ranked.firstOrNull() ?: return null
        if (!passesMatchGates(read, best.second, ranked.getOrNull(1)?.second)) return null
        if (best.second.probability < MIN_PROBABILITY) return null

        return ParayTicketMatch(best.first, read, best.second)
    }

    /** Top catalog candidates for user pick — designation + price + PNG fusion. */
    suspend fun matchRanked(
        read: ParayTicketReadResult,
        productCrop: Bitmap?,
        repository: OasisRepository,
        paray: ParayAgent?,
        rayonPreference: String? = null,
        visualHints: Map<Long, Float> = emptyMap(),
        productFeatures: VisualFeatureExtractor.Features? = null,
        limit: Int = 5,
    ): List<ParayTicketMatch> {
        read.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { barcode ->
            repository.resolveScannedBarcode(barcode)?.article?.let { article ->
                val fusion = scoreArticle(
                    read, productCrop, article, paray, rayonPreference,
                    barcodeBoost = 0.95f,
                    visualHints = visualHints,
                    productFeatures = productFeatures,
                )
                return listOf(ParayTicketMatch(article, read.copy(source = ParayTicketReadSource.BARCODE), fusion))
            }
        }

        val designation = read.ocrDesignation?.trim()?.takeIf { it.length >= 3 } ?: run {
            read.ocrPrice?.let { price ->
                matchByPriceAndImage(
                    read, productCrop, price, repository, paray, rayonPreference,
                    visualHints, productFeatures,
                )?.let { return listOf(it) }
            }
            return emptyList()
        }

        val candidates = gatherCandidates(read, designation, productCrop, repository, paray, visualHints)
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .distinctBy { it.id }
            .map { article ->
                val fusion = scoreArticle(
                    read, productCrop, article, paray, rayonPreference,
                    visualHints = visualHints,
                    productFeatures = productFeatures,
                )
                ParayTicketMatch(article, read, fusion)
            }
            .filter { passesMatchGates(read, it.fusion, null) || it.fusion.probability >= MIN_PROBABILITY * 0.85f }
            .sortedByDescending { it.fusion.probability }
            .take(limit)
    }

    private fun passesMatchGates(
        read: ParayTicketReadResult,
        best: ParayTicketFusionBreakdown,
        second: ParayTicketFusionBreakdown?,
    ): Boolean {
        val des = read.ocrDesignation?.trim().orEmpty()
        if (des.isNotEmpty() && best.designationScore < MIN_DESIGNATION_SCORE) return false
        if (read.ocrPrice != null && best.priceScore < MIN_PRICE_SCORE) return false
        if (des.split(" ").size > 6 && best.probability < MIN_NOISY_DESIGNATION_PROBABILITY) return false
        if (second != null && best.probability - second.probability < MIN_WIN_MARGIN) return false
        return true
    }

    private suspend fun gatherCandidates(
        read: ParayTicketReadResult,
        designation: String,
        productCrop: Bitmap?,
        repository: OasisRepository,
        paray: ParayAgent?,
        visualHints: Map<Long, Float>,
    ): List<ArticleWithImage> = coroutineScope {
        val found = linkedMapOf<Long, ArticleWithImage>()

        val hintLoads = visualHints.keys.map { id ->
            async { repository.getArticleWithImageById(id) }
        }
        hintLoads.awaitAll().filterNotNull().forEach { found[it.id] = it }

        val exactDeferred = async { repository.getArticleWithImageByDesignation(designation) }
        val searchDeferred = async { repository.searchArticlesForPicker(designation, limit = 30) }
        val priceDeferred = async {
            read.ocrPrice?.let { price ->
                repository.searchArticlesNearPrice(price, max(3.0, price * 0.03), limit = 25)
            }.orEmpty()
        }

        exactDeferred.await()?.let { found[it.id] = it }
        searchDeferred.await().forEach { found[it.id] = it }
        priceDeferred.await().forEach { found[it.id] = it }

        val tokens = NameNormalizer.normalize(designation).split(" ").filter { it.length >= 3 }
        if (tokens.size >= 2) {
            async { repository.searchArticlesForPicker(tokens.take(3).joinToString(" "), limit = 20) }
                .await()
                .forEach { found[it.id] = it }
            tokens.take(2).map { token ->
                async { repository.searchArticlesForPicker(token, limit = 12) }
            }.awaitAll().flatten().forEach { found[it.id] = it }
        }

        if (visualHints.isEmpty()) {
            productCrop?.let { crop ->
                paray?.identifyFromCamera(crop, topK = 8)?.forEach { match ->
                    repository.getArticleWithImageById(match.articleId)?.let { found[it.id] = it }
                }
            }
        }

        found.values.take(MAX_CANDIDATES)
    }

    private fun scoreArticle(
        read: ParayTicketReadResult,
        productCrop: Bitmap?,
        article: ArticleWithImage,
        paray: ParayAgent?,
        rayonPreference: String?,
        barcodeBoost: Float? = null,
        visualHints: Map<Long, Float> = emptyMap(),
        productFeatures: VisualFeatureExtractor.Features? = null,
    ): ParayTicketFusionBreakdown {
        val desScore = designationScore(read.ocrDesignation.orEmpty(), article)
        val priceScore = priceScore(read.ocrPrice, article.price)
        val imgScore = imageScore(productCrop, article, visualHints, productFeatures)

        var probability = desScore * WEIGHT_DESIGNATION +
            priceScore * WEIGHT_PRICE +
            imgScore * WEIGHT_IMAGE

        barcodeBoost?.let { probability = max(probability, it) }
        probability = probability.coerceIn(0f, 1f)

        return ParayTicketFusionBreakdown(
            designationScore = desScore,
            priceScore = priceScore,
            imageScore = imgScore,
            rayonBoost = 0f,
            probability = probability,
        )
    }

    private fun designationScore(ocrDesignation: String, article: ArticleWithImage): Float {
        if (ocrDesignation.isBlank()) return 0.22f
        val search = SearchQuery.prepare(ocrDesignation) ?: return 0.4f
        val rankScore = when (SearchQuery.score(article, search)) {
            0 -> 1f
            1 -> 0.9f
            2 -> 0.75f
            else -> 0.45f
        }
        val ocrNorm = NameNormalizer.normalize(ocrDesignation)
        val artNorm = NameNormalizer.normalize(article.designation)
        if (ocrNorm == artNorm) return 1f
        if (artNorm.contains(ocrNorm) || ocrNorm.contains(artNorm)) return max(rankScore, 0.92f)

        val ocrTokens = ocrNorm.split(" ").filter { it.length >= 2 }
        val artTokens = artNorm.split(" ").toSet()
        if (ocrTokens.isEmpty()) return rankScore
        val hits = ocrTokens.count { token ->
            artTokens.any { art -> tokenMatches(token, art) }
        }
        val tokenScore = hits.toFloat() / ocrTokens.size.toFloat()
        return max(rankScore, tokenScore * 0.98f).coerceIn(0f, 1f)
    }

    private fun priceScore(ocrPrice: Double?, catalogPrice: Double): Float {
        if (ocrPrice == null || ocrPrice <= 0) return 0.22f
        val diff = abs(ocrPrice - catalogPrice)
        return when {
            diff < 0.5 -> 1f
            diff <= catalogPrice * 0.02 -> 0.96f
            diff <= catalogPrice * 0.05 -> 0.85f
            diff <= catalogPrice * 0.10 -> 0.6f
            else -> 0.2f
        }
    }

    private fun imageScore(
        productCrop: Bitmap?,
        article: ArticleWithImage,
        visualHints: Map<Long, Float>,
        productFeatures: VisualFeatureExtractor.Features?,
    ): Float {
        visualHints[article.id]?.let { return it.coerceIn(0f, 1f) }

        if (productCrop == null) return 0.5f
        val probe = productFeatures ?: extractFeatures(productCrop) ?: return 0.5f
        var best = 0.35f

        val path = article.imagePath?.takeIf { it.isNotBlank() && File(it).exists() }
        if (path != null) {
            runCatching {
                decodeSampled(path, maxSide = 160)?.let { catalogBmp ->
                    try {
                        extractFeatures(catalogBmp)?.let { ref ->
                            best = max(best, ParayVisualSimilarity.score(probe, ref))
                        }
                    } finally {
                        if (!catalogBmp.isRecycled) catalogBmp.recycle()
                    }
                }
            }
        }

        return best.coerceIn(0f, 1f)
    }

    private fun decodeSampled(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun extractFeatures(bitmap: Bitmap): VisualFeatureExtractor.Features? {
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }
        return VisualFeatureExtractor.extract(bitmap, content)
    }

    private fun tokenMatches(ocr: String, art: String): Boolean {
        if (ocr == art || art.contains(ocr) || ocr.contains(art)) return true
        if (ocr.length >= 4 && art.length >= 4) {
            val dist = levenshtein(ocr, art)
            val maxLen = max(ocr.length, art.length)
            return dist <= 1 || dist.toFloat() / maxLen <= 0.22f
        }
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return prev[b.length]
    }

    private fun ArticleWithImage.rayonMatches(filter: String): Boolean {
        val a = rayon?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = NameNormalizer.normalize(filter)
        return NameNormalizer.normalize(a) == target || a.equals(filter, ignoreCase = true)
    }

    private suspend fun matchByPriceAndImage(
        read: ParayTicketReadResult,
        productCrop: Bitmap?,
        price: Double,
        repository: OasisRepository,
        paray: ParayAgent?,
        rayonPreference: String?,
        visualHints: Map<Long, Float>,
        productFeatures: VisualFeatureExtractor.Features?,
    ): ParayTicketMatch? {
        val tolerance = max(3.0, price * 0.03)
        val candidates = repository.searchArticlesNearPrice(price, tolerance, limit = 30)
        if (candidates.isEmpty()) return null
        val ranked = candidates.map { article ->
            val fusion = scoreArticle(
                read, productCrop, article, paray, rayonPreference,
                visualHints = visualHints,
                productFeatures = productFeatures,
            )
            article to fusion
        }.sortedByDescending { it.second.probability }
        val best = ranked.firstOrNull() ?: return null
        if (!passesMatchGates(read, best.second, ranked.getOrNull(1)?.second)) return null
        if (best.second.probability < MIN_PROBABILITY) return null
        return ParayTicketMatch(best.first, read, best.second)
    }

    companion object {
        private const val WEIGHT_DESIGNATION = 0.50f
        private const val WEIGHT_PRICE = 0.30f
        private const val WEIGHT_IMAGE = 0.20f
        private const val MAX_CANDIDATES = 55
        const val MIN_PROBABILITY = 0.52f
        private const val MIN_DESIGNATION_SCORE = 0.58f
        private const val MIN_PRICE_SCORE = 0.72f
        private const val MIN_WIN_MARGIN = 0.10f
        private const val MIN_NOISY_DESIGNATION_PROBABILITY = 0.76f
    }
}
