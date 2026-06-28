package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.layoutagent.ContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * PARAY Ticket Agent — Vision Corps pipeline.
 * Tap capture → multi-strategy visual scout → OCR corps → fuzzy catalog match.
 */
class ParayTicketReader(
    private val repository: OasisRepository,
    private val paray: ParayAgent? = null,
    private val ocrCorps: ParayTicketOcrCorps = ParayTicketOcrCorps(),
    private val fuzzy: ParayTicketFuzzyMatcher = ParayTicketFuzzyMatcher(),
) {
    suspend fun processSnap(
        bitmap: Bitmap,
        rotationDegrees: Int,
        frameQuality: Float,
        onStep: suspend (TicketSnapStep) -> Unit,
        userCropNorm: ParayTicketCropLearnStore.NormalizedCrop? = null,
    ): ParayTicketSnapResult? = withContext(Dispatchers.Default) {
        val totalStart = System.currentTimeMillis()
        val userCropLocked = userCropNorm != null
        // Own a copy — callers may still show the source bitmap in Compose.
        var working = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        onStep(
            TicketSnapStep(
                TicketSnapPhase.CAPTURED,
                if (userCropLocked) {
                    "Photo captured — OCR on your crop…"
                } else {
                    "Photo captured — PARAY Vision Corps analyzing…"
                },
                preview = ParayTicketBitmapUtils.previewCopy(working),
                frameQuality = frameQuality,
            ),
        )

        if (rotationDegrees != 0) {
            onStep(TicketSnapStep(TicketSnapPhase.ROTATE, "Orientation correction…"))
            val rotated = ParayTicketImagePrep.rotateToUpright(working, rotationDegrees)
            if (rotated !== working) {
                working.recycle()
                working = rotated
            }
        }

        if (userCropNorm != null) {
            onStep(TicketSnapStep(TicketSnapPhase.CROP_YELLOW, "Your crop applied — locked for OCR"))
            val cropped = ParayTicketCropLearnStore.cropBitmap(working, userCropNorm)
            if (cropped !== working) {
                working.recycle()
                working = cropped
            }
        }

        val processed = ParayTicketBitmapUtils.forSnapProcessing(working)
        if (processed !== working) {
            working.recycle()
            working = processed
        }

        val analysis = if (userCropLocked) {
            onStep(TicketSnapStep(TicketSnapPhase.FIND_YELLOW, "Reading all black text on your crop…"))
            ParayTicketVisionCorps.analyzeUserValidatedCrop(working, ocrCorps) { msg ->
                onStep(TicketSnapStep(TicketSnapPhase.FIND_YELLOW, msg))
            }
        } else {
            onStep(TicketSnapStep(TicketSnapPhase.FIND_YELLOW, "Visual ticket scout (5 strategies)…"))
            ParayTicketVisionCorps.analyze(working, ocrCorps) { msg ->
                onStep(TicketSnapStep(TicketSnapPhase.FIND_YELLOW, msg))
            }
        }

        if (analysis == null) {
            if (!working.isRecycled) working.recycle()
            onStep(
                TicketSnapStep(
                    TicketSnapPhase.FAILED,
                    "OCR corps could not read ticket",
                    error = if (userCropLocked) {
                        "Could not read your crop — widen box or tap again"
                    } else {
                        "Fill frame with ticket, ensure designation + price visible, tap again"
                    },
                ),
            )
            logMetrics(totalStart, 0, 0, 0, 0)
            return@withContext null
        }

        val regions = analysis.regions
        onStep(
            TicketSnapStep(
                TicketSnapPhase.CROP_YELLOW,
                if (userCropLocked) {
                    "Your crop — OCR on full ticket"
                } else {
                    "Ticket region: ${regions.strategy} (${(regions.confidence * 100).toInt()}%)"
                },
                preview = if (userCropLocked) {
                    ParayTicketBitmapUtils.previewCopy(working)
                } else {
                    ParayTicketBitmapUtils.previewCopy(regions.textCrop)
                },
            ),
        )
        if (!userCropLocked) {
            regions.productCrop?.let { product ->
                onStep(
                    TicketSnapStep(
                        TicketSnapPhase.CROP_PNG,
                        "Product strip isolated",
                        preview = ParayTicketBitmapUtils.previewCopy(product),
                    ),
                )
            }
        }

        val ocrResult = analysis.ocr.read
        onStep(
            TicketSnapStep(
                TicketSnapPhase.READ_TEXT,
                "OCR corps ${analysis.ocr.passes} passes → " +
                    "${ocrResult.ocrDesignation ?: "?"} · " +
                    "${ocrResult.ocrPrice?.toInt()?.let { "$it DA" } ?: "?"}",
                ocrDesignation = ocrResult.ocrDesignation,
                ocrPrice = ocrResult.ocrPrice,
            ),
        )

        if (analysis.recoveryUsed) {
            onStep(TicketSnapStep(TicketSnapPhase.RECOVERY, "Recovery pass contributed to read"))
        }

        regions.textCrop.recycle()

        onStep(TicketSnapStep(TicketSnapPhase.FUZZY_MATCH, "Catalog fusion (text + price + PNG)…"))

        val visualPair = coroutineScope {
            async { buildVisualHints(regions.productCrop) }.await()
        }

        if (!working.isRecycled) working.recycle()

        onStep(TicketSnapStep(TicketSnapPhase.STABILIZE, "Stabilizing match…"))
        val matchStart = System.currentTimeMillis()
        val ranked = withContext(Dispatchers.IO) {
            fuzzy.matchRanked(
                read = ocrResult,
                productCrop = regions.productCrop,
                repository = repository,
                paray = paray,
                visualHints = visualPair.first,
                productFeatures = visualPair.second,
                limit = 5,
            )
        }
        val match = ranked.firstOrNull()
        val matchMs = System.currentTimeMillis() - matchStart

        val frame = ParayTicketFrameRead(ocr = ocrResult, productCrop = regions.productCrop)
        val tier = match?.let { ParayTicketMatchTier.fromProbability(it.fusion.probability) }

        if (match != null && tier != null) {
            onStep(
                TicketSnapStep(
                    TicketSnapPhase.DONE,
                    "${tier.marketingLabel} — ${match.article.designation} (${match.fusion.probabilityPercent}%)",
                    ocrDesignation = ocrResult.ocrDesignation,
                    ocrPrice = ocrResult.ocrPrice,
                    fusion = match.fusion,
                    matchTier = tier,
                    frameQuality = frameQuality,
                ),
            )
            logMetrics(totalStart, 0, analysis.ocr.passes, matchMs, visualPair.first.size)
            ParayTicketSnapResult(frame, match, tier, frameQuality, analysis.recoveryUsed, ranked)
        } else {
            frame.recycle()
            onStep(
                TicketSnapStep(
                    TicketSnapPhase.FAILED,
                    "No catalog match",
                    ocrDesignation = ocrResult.ocrDesignation,
                    ocrPrice = ocrResult.ocrPrice,
                    error = "Read OK (${analysis.ocr.passes} OCR passes) — article not in catalog",
                    frameQuality = frameQuality,
                ),
            )
            logMetrics(totalStart, 0, analysis.ocr.passes, matchMs, visualPair.first.size)
            null
        }
    }

    suspend fun resolve(frame: ParayTicketFrameRead): ParayTicketMatch? = withContext(Dispatchers.IO) {
        try {
            fuzzy.match(frame.ocr, frame.productCrop, repository, paray)
        } finally {
            frame.recycle()
        }
    }

    suspend fun resolve(read: ParayTicketReadResult): ParayTicketMatch? =
        fuzzy.match(read, null, repository, paray)

    fun close() {
        ocrCorps.close()
    }

    private suspend fun buildVisualHints(
        productCrop: Bitmap?,
    ): Pair<Map<Long, Float>, VisualFeatureExtractor.Features?> {
        if (productCrop == null || paray == null) return emptyMap<Long, Float>() to null
        val features = extractFeatures(productCrop)
        val hints = paray.identifyFromCamera(productCrop, topK = 10)
            ?.associate { it.articleId to it.confidence }
            .orEmpty()
        return hints to features
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

    private fun logMetrics(
        totalStart: Long,
        yellowMs: Long,
        ocrPasses: Int,
        matchMs: Long,
        candidates: Int,
    ) {
        ParayTicketSnapMetrics(
            yellowMs = yellowMs,
            ocrMs = ocrPasses.toLong(),
            matchMs = matchMs,
            totalMs = System.currentTimeMillis() - totalStart,
            candidateCount = candidates,
        ).logSummary()
    }
}
