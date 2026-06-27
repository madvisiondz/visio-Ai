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

/** ML Kit OCR on shelf tickets — delegates parsing to [ParayTicketTextParser]. */
class ParayTicketOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readYellowBlock(bitmap: Bitmap, rotationDegrees: Int = 0): ParayTicketReadResult? =
        withContext(Dispatchers.Default) {
            val result = ParayTicketImagePrep.prepareYellowCropForOcrFast(yellowCrop = bitmap) { variant ->
                recognize(variant, rotationDegrees)?.let(ParayTicketTextParser::parse)
            }
            if (result != null) {
                OasisLog.i(
                    OasisLog.Domain.Paray,
                    "Ticket OCR: des='${result.ocrDesignation}' price=${result.ocrPrice} conf=${result.confidence}",
                )
            }
            result
        }

    fun close() {
        recognizer.close()
    }

    private suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int) =
        runCatching {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { e ->
                        OasisLog.w(OasisLog.Domain.Paray, "Ticket OCR ML Kit failed", e)
                        cont.resume(null)
                    }
            }
        }.getOrNull()
}
