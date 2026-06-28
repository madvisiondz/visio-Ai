package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.oasismall.oasisai.util.OasisLog
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * OCR corps — multi-pass ML Kit with preprocessing variants + consensus voting.
 * Works on cropped text band OR full frame when crop is uncertain.
 */
class ParayTicketOcrCorps {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class Result(
        val read: ParayTicketReadResult,
        val passes: Int,
        val winningVariant: String,
        val corpsConfidence: Float,
    )

    suspend fun read(
        textCrop: Bitmap?,
        fullFrame: Bitmap,
        includeRecovery: Boolean = true,
        userValidatedCrop: Boolean = false,
    ): Result? = withContext(Dispatchers.Default) {
        if (userValidatedCrop) {
            val crop = textCrop ?: fullFrame
            return@withContext readUserValidatedCrop(crop, includeRecovery)
        }
        textCrop?.let { crop ->
            val cropResults = runVariants(crop, includeRecovery, "crop", yellowBandOnly = true)
            if (cropResults.isNotEmpty()) {
                consensus(cropResults, preferCompactDesignation = true)?.let { return@withContext it }
            }
        }
        val fullResults = runVariants(fullFrame, includeRecovery = false, label = "full", yellowBandOnly = false)
        if (fullResults.isEmpty()) {
            OasisLog.d(OasisLog.Domain.Paray, "OCR corps: zero passes succeeded")
            return@withContext null
        }
        consensus(fullResults, preferCompactDesignation = false)
    }

    private suspend fun readUserValidatedCrop(crop: Bitmap, includeRecovery: Boolean): Result? {
        val passes = ArrayList<PassResult>()
        passes.addAll(runVariants(crop, includeRecovery, "user-crop", yellowBandOnly = false))

        val h = crop.height
        if (h >= 72) {
            val priceBandHeight = (h * 0.40f).toInt().coerceIn(24, h - 24)
            val desBandHeight = h - priceBandHeight
            val desBand = Bitmap.createBitmap(crop, 0, 0, crop.width, desBandHeight)
            val priceBand = Bitmap.createBitmap(crop, 0, desBandHeight, crop.width, priceBandHeight)
            try {
                passes.addAll(runVariants(desBand, includeRecovery, "des-band", yellowBandOnly = false))
                passes.addAll(runPriceBandVariants(priceBand))
            } finally {
                desBand.recycle()
                priceBand.recycle()
            }
        }

        return mergeSplitConsensus(passes) ?: consensus(passes, preferCompactDesignation = false)
    }

    private suspend fun runPriceBandVariants(source: Bitmap): List<PassResult> {
        val variants = ParayTicketPreprocessCorps.priceBandVariants(source)
        val out = ArrayList<PassResult>()
        try {
            for (prepared in variants) {
                val text = recognize(prepared.bitmap) ?: continue
                val parsed = ParayTicketTextParser.parse(
                    text,
                    imageWidth = source.width,
                    yellowBandOnly = false,
                    maxDesignationRows = 1,
                ) ?: continue
                out.add(PassResult(parsed, "price-band/${prepared.variant.label}"))
            }
        } finally {
            ParayTicketPreprocessCorps.recycleAll(variants)
        }
        return out
    }

    private fun mergeSplitConsensus(passes: List<PassResult>): Result? {
        if (passes.isEmpty()) return null
        val desPasses = passes.filter {
            it.variant.startsWith("des-band/") || it.variant.startsWith("user-crop/")
        }
        val pricePasses = passes.filter {
            it.variant.startsWith("price-band/") || it.variant.startsWith("user-crop/")
        }
        val desResult = consensus(desPasses, preferCompactDesignation = false)
        val priceResult = consensus(
            pricePasses.filter { it.read.ocrPrice != null || looksLikePriceLine(it.read) },
            preferCompactDesignation = false,
        )

        val designation = desResult?.read?.ocrDesignation ?: passes.firstNotNullOfOrNull { it.read.ocrDesignation }
        val price = priceResult?.read?.ocrPrice
            ?: passes.mapNotNull { it.read.ocrPrice }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        if (designation == null && price == null) return null

        val trimmedDes = designation?.let { ParayTicketTextParser.trimShelfDesignation(it) }
        val passCount = passes.size
        val agreement = listOfNotNull(
            designation?.let { d -> desPasses.count { normalizeDes(it.read.ocrDesignation.orEmpty()) == normalizeDes(d) } },
            price?.let { p -> pricePasses.count { it.read.ocrPrice == p } },
        ).sum().toFloat() / (passCount * 2).coerceAtLeast(1)

        val confidence = when {
            trimmedDes != null && price != null -> 0.90f + agreement * 0.08f
            trimmedDes != null -> 0.72f + agreement * 0.15f
            else -> 0.55f + agreement * 0.20f
        }.coerceIn(0f, 0.98f)

        val winningVariant = (desResult ?: priceResult)?.winningVariant ?: passes.first().variant
        OasisLog.i(
            OasisLog.Domain.Paray,
            "OCR corps split: $passCount passes → des='$trimmedDes' price=$price conf=$confidence",
        )
        return Result(
            read = ParayTicketReadResult(
                source = ParayTicketReadSource.OCR_DESIGNATION,
                ocrDesignation = trimmedDes,
                ocrPrice = price,
                confidence = confidence,
            ),
            passes = passCount,
            winningVariant = winningVariant,
            corpsConfidence = confidence,
        )
    }

    private fun looksLikePriceLine(read: ParayTicketReadResult): Boolean =
        read.ocrPrice != null ||
            read.ocrDesignation?.let { ParayTicketTextParser.looksLikePrice(it) } == true

    fun close() {
        recognizer.close()
    }

    private data class PassResult(
        val read: ParayTicketReadResult,
        val variant: String,
    )

    private suspend fun runVariants(
        source: Bitmap,
        includeRecovery: Boolean,
        label: String,
        yellowBandOnly: Boolean,
    ): List<PassResult> {
        val variants = ParayTicketPreprocessCorps.allVariants(source, includeRecovery)
        val out = ArrayList<PassResult>()
        try {
            for (prepared in variants) {
                val text = recognize(prepared.bitmap) ?: continue
                val parsed = ParayTicketTextParser.parse(
                    text,
                    imageWidth = source.width,
                    yellowBandOnly = yellowBandOnly,
                    maxDesignationRows = if (label == "user-crop") 4 else 2,
                ) ?: continue
                out.add(PassResult(parsed, "$label/${prepared.variant.label}"))
            }
        } finally {
            ParayTicketPreprocessCorps.recycleAll(variants)
        }
        return out
    }

    private suspend fun recognize(bitmap: Bitmap) =
        runCatching {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }.getOrNull()

    private fun consensus(passes: List<PassResult>, preferCompactDesignation: Boolean): Result? {
        if (passes.isEmpty()) return null

        val desVotes = passes.mapNotNull { it.read.ocrDesignation?.let { d -> d to it.read.confidence } }
        val priceVotes = passes.mapNotNull { it.read.ocrPrice?.let { p -> p to it.read.confidence } }

        val designation = desVotes
            .groupBy { normalizeDes(it.first) }
            .maxByOrNull { (_, group) ->
                val wordPenalty = if (preferCompactDesignation) {
                    group.minOfOrNull { it.first.split(" ").size } ?: 99
                } else {
                    0
                }
                group.size * 10 + group.maxOf { it.second } - wordPenalty
            }
            ?.value
            ?.let { group ->
                if (preferCompactDesignation) {
                    group.minByOrNull { it.first.split(" ").size } ?: group.maxByOrNull { it.second }!!
                } else {
                    group.maxByOrNull { it.second }!!
                }
            }
            ?.first

        val price = priceVotes
            .groupBy { it.first }
            .maxByOrNull { (_, group) -> group.size * 10 + group.maxOf { it.second } }
            ?.key

        if (designation == null && price == null) return null

        val trimmedDes = designation?.let { ParayTicketTextParser.trimShelfDesignation(it) }

        val bestPass = passes.maxByOrNull { it.read.confidence }!!
        val agreement = when {
            designation != null && price != null -> {
                val desAgree = desVotes.count { normalizeDes(it.first) == normalizeDes(designation) }
                val priceAgree = priceVotes.count { it.first == price }
                (desAgree + priceAgree).toFloat() / (passes.size * 2).coerceAtLeast(1)
            }
            designation != null -> desVotes.count { normalizeDes(it.first) == normalizeDes(designation) }.toFloat() / passes.size
            else -> priceVotes.count { it.first == price }.toFloat() / passes.size
        }

        val confidence = when {
            trimmedDes != null && price != null -> 0.90f + agreement * 0.08f
            trimmedDes != null -> 0.72f + agreement * 0.15f
            else -> 0.55f + agreement * 0.20f
        }.coerceIn(0f, 0.98f)

        val merged = Result(
            read = ParayTicketReadResult(
                source = ParayTicketReadSource.OCR_DESIGNATION,
                ocrDesignation = trimmedDes,
                ocrPrice = price,
                confidence = confidence,
            ),
            passes = passes.size,
            winningVariant = bestPass.variant,
            corpsConfidence = confidence,
        )
        OasisLog.i(
            OasisLog.Domain.Paray,
            "OCR corps: ${merged.passes} passes → des='${merged.read.ocrDesignation}' " +
                "price=${merged.read.ocrPrice} conf=${merged.corpsConfidence}",
        )
        return merged
    }

    private fun normalizeDes(raw: String): String =
        ParayTicketTextParser.cleanupOcrDesignation(raw).uppercase()
}
