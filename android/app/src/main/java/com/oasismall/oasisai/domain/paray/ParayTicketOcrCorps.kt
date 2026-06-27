package com.oasismall.oasisai.domain.paray

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.oasismall.oasisai.util.OasisLog
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    ): Result? = withContext(Dispatchers.Default) {
        val jobs = buildList {
            textCrop?.let { crop ->
                add(async { runVariants(crop, includeRecovery, "crop") })
            }
            add(async { runVariants(fullFrame, includeRecovery = false, label = "full") })
        }
        val allResults = jobs.awaitAll().flatten()
        if (allResults.isEmpty()) {
            OasisLog.d(OasisLog.Domain.Paray, "OCR corps: zero passes succeeded")
            return@withContext null
        }
        val merged = consensus(allResults) ?: return@withContext null
        OasisLog.i(
            OasisLog.Domain.Paray,
            "OCR corps: ${merged.passes} passes → des='${merged.read.ocrDesignation}' " +
                "price=${merged.read.ocrPrice} conf=${merged.corpsConfidence}",
        )
        merged
    }

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
    ): List<PassResult> {
        val variants = ParayTicketPreprocessCorps.allVariants(source, includeRecovery)
        val out = ArrayList<PassResult>()
        try {
            for (prepared in variants) {
                val text = recognize(prepared.bitmap) ?: continue
                val parsed = ParayTicketTextParser.parse(text) ?: continue
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

    private fun consensus(passes: List<PassResult>): Result? {
        if (passes.isEmpty()) return null

        val desVotes = passes.mapNotNull { it.read.ocrDesignation?.let { d -> d to it.read.confidence } }
        val priceVotes = passes.mapNotNull { it.read.ocrPrice?.let { p -> p to it.read.confidence } }

        val designation = desVotes
            .groupBy { normalizeDes(it.first) }
            .maxByOrNull { (_, group) -> group.size * 10 + group.maxOf { it.second } }
            ?.value
            ?.maxByOrNull { it.second }
            ?.first

        val price = priceVotes
            .groupBy { it.first }
            .maxByOrNull { (_, group) -> group.size * 10 + group.maxOf { it.second } }
            ?.key

        if (designation == null && price == null) return null

        val bestPass = passes.maxByOrNull { it.read.confidence }!!
        val agreement = when {
            designation != null && price != null -> {
                val desAgree = desVotes.count { normalizeDes(it.first) == normalizeDes(designation!!) }
                val priceAgree = priceVotes.count { it.first == price }
                (desAgree + priceAgree).toFloat() / (passes.size * 2).coerceAtLeast(1)
            }
            designation != null -> desVotes.count { normalizeDes(it.first) == normalizeDes(designation!!) }.toFloat() / passes.size
            else -> priceVotes.count { it.first == price }.toFloat() / passes.size
        }

        val confidence = when {
            designation != null && price != null -> 0.90f + agreement * 0.08f
            designation != null -> 0.72f + agreement * 0.15f
            else -> 0.55f + agreement * 0.20f
        }.coerceIn(0f, 0.98f)

        return Result(
            read = ParayTicketReadResult(
                source = ParayTicketReadSource.OCR_DESIGNATION,
                ocrDesignation = designation,
                ocrPrice = price,
                confidence = confidence,
            ),
            passes = passes.size,
            winningVariant = bestPass.variant,
            corpsConfidence = confidence,
        )
    }

    private fun normalizeDes(raw: String): String =
        ParayTicketTextParser.cleanupOcrDesignation(raw).uppercase()
}
