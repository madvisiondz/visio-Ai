package com.oasismall.oasisai.domain.paray

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Bridge from AGENT / Scanner / Learn hooks to [ParayRecognitionObserver] — observation only. */
class ParayRecognitionTracker(
    private val observer: ParayRecognitionObserver,
    private val scope: CoroutineScope,
) {
    fun recordUnknownBarcode(barcode: String, source: String = SOURCE_SCANNER) {
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.UNKNOWN_BARCODE,
                    barcode = barcode,
                    source = source,
                    metadata = mapOf("reason" to "not_in_catalog"),
                ),
            )
        }
    }

    fun recordRecognitionFailure(barcode: String?, source: String, detail: String) {
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.RECOGNITION_FAILURE,
                    barcode = barcode,
                    source = source,
                    metadata = mapOf("detail" to detail),
                ),
            )
        }
    }

    fun recordLowConfidenceMatch(
        barcode: String?,
        articleId: Long?,
        designation: String?,
        confidence: Float,
        source: String = SOURCE_CAMERA_MATCHER,
    ) {
        if (confidence >= ParayRecognitionObserver.LOW_CONFIDENCE_THRESHOLD) return
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.LOW_CONFIDENCE_MATCH,
                    barcode = barcode,
                    articleId = articleId,
                    confidence = confidence,
                    source = source,
                    metadata = buildMap {
                        designation?.let { put("designation", it) }
                        put(
                            "threshold",
                            ParayRecognitionObserver.LOW_CONFIDENCE_THRESHOLD.toString(),
                        )
                    },
                ),
            )
        }
    }

    fun recordManualCorrection(
        scannedBarcode: String,
        selectedArticleId: Long,
        selectedDesignation: String,
        offeredArticleId: Long?,
        source: String,
    ) {
        val isCorrection = offeredArticleId != null && offeredArticleId != selectedArticleId
        val isManualPick = offeredArticleId == null
        if (!isCorrection && !isManualPick) return
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.MANUAL_CORRECTION,
                    barcode = scannedBarcode,
                    articleId = selectedArticleId,
                    source = source,
                    metadata = mapOf(
                        "designation" to selectedDesignation,
                        "offeredArticleId" to (offeredArticleId?.toString() ?: ""),
                    ),
                ),
            )
        }
    }

    fun recordPackagingDrift(event: ParayPackagingVariantEvent) {
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.PACKAGING_DRIFT,
                    barcode = event.barcode,
                    articleId = event.articleId,
                    confidence = event.similarity,
                    source = SOURCE_PARAY_LEARN,
                    metadata = mapOf(
                        "designation" to event.designation,
                        "message" to event.message,
                    ),
                ),
            )
        }
    }

    fun observeVisualIdentification(
        scannedBarcode: String?,
        matches: List<ParayMatch>,
    ) {
        if (matches.isEmpty()) {
            recordRecognitionFailure(
                scannedBarcode,
                SOURCE_AGENT,
                "AGENT could not identify product from camera frame",
            )
        }
    }

    fun onCameraMatcherResults(matches: List<ParayMatch>) {
        matches.forEach { match ->
            recordLowConfidenceMatch(
                match.barcode,
                match.articleId,
                match.designation,
                match.confidence,
                SOURCE_CAMERA_MATCHER,
            )
        }
    }

    fun recordTicketScan(
        barcode: String,
        articleId: Long?,
        designation: String?,
        assessment: com.oasismall.oasisai.domain.paray.ParayTicketAssessment,
    ) {
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.TICKET_VERIFY_SCAN,
                    barcode = barcode,
                    articleId = articleId,
                    source = SOURCE_AGENT,
                    metadata = mapOf(
                        "designation" to (designation ?: ""),
                        "status" to assessment.status.name,
                        "message" to assessment.message,
                        "rayon" to (assessment.rayon ?: ""),
                    ),
                ),
            )
        }
    }

    fun recordTicketVerified(
        barcode: String,
        articleId: Long?,
        designation: String?,
        assessment: com.oasismall.oasisai.domain.paray.ParayTicketAssessment,
    ) {
        scope.launch {
            observer.record(
                ParayRecognitionEvent(
                    type = ParayRecognitionEventType.TICKET_VERIFIED,
                    barcode = barcode,
                    articleId = articleId,
                    source = SOURCE_AGENT,
                    metadata = mapOf(
                        "designation" to (designation ?: ""),
                        "status" to assessment.status.name,
                        "rayon" to (assessment.rayon ?: ""),
                    ),
                ),
            )
        }
    }

    companion object {
        const val SOURCE_AGENT = "agent"
        const val SOURCE_SCANNER = "scanner"
        const val SOURCE_CAMERA_MATCHER = "camera_matcher"
        const val SOURCE_PARAY_LEARN = "paray_learn"
    }
}
