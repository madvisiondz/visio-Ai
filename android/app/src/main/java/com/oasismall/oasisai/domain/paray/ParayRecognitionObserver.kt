package com.oasismall.oasisai.domain.paray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Observes AGENT recognition weaknesses — log-only, no prompts or automatic actions.
 * Mirrors [ParayWorkflowObserver] / [ParayKnowledgeObserver] architecture.
 */
class ParayRecognitionObserver(
    home: ParayHome,
    private val store: ParayRecognitionStore = ParayRecognitionStore(home),
    private val activityMonitor: ParayActivityMonitor? = null,
) {
    private val mutex = Mutex()

    suspend fun record(event: ParayRecognitionEvent) = withContext(Dispatchers.IO) {
        activityMonitor?.pulse(ParayActivityState.OBSERVING)
        mutex.withLock {
            activityMonitor?.pulse(ParayActivityState.PROCESSING)
            store.appendEvent(event)
            val unknown = store.readUnknownProducts()
            val patterns = store.readFailurePatterns()
            val applyResult = applyEvent(event, unknown, patterns)
            store.writeUnknownProducts(unknown)
            store.writeFailurePatterns(patterns)
            store.writeSummary(buildSummary(unknown, patterns))
            publishActivity(event, applyResult)
        }
    }

    fun readSummary(): ParayRecognitionSummary = store.readSummary()

    private fun publishActivity(event: ParayRecognitionEvent, result: ParayRecognitionApplyResult) {
        when (event.type) {
            ParayRecognitionEventType.PACKAGING_DRIFT ->
                activityMonitor?.pulse(ParayActivityState.LEARNING)
            else -> Unit
        }
        if (result.newDiscovery) {
            activityMonitor?.pulse(ParayActivityState.DISCOVERY)
        }
        if (result.blindSpotWarning) {
            activityMonitor?.pulse(ParayActivityState.WARNING)
        }
    }

    private fun applyEvent(
        event: ParayRecognitionEvent,
        unknown: MutableMap<String, ParayUnknownProductRecord>,
        patterns: MutableMap<String, ParayFailurePatternRecord>,
    ): ParayRecognitionApplyResult {
        val typeKey = event.type.name
        val isNewTypePattern = patterns[typeKey] == null
        bumpPattern(patterns, typeKey, event)

        var newDiscovery = isNewTypePattern
        var blindSpotWarning = false

        event.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
            val productKey = "${event.type.name}|$barcode"
            val isNewProductPattern = patterns[productKey] == null
            if (isNewProductPattern && event.type != ParayRecognitionEventType.MANUAL_CORRECTION) {
                newDiscovery = true
            }

            val existing = unknown[barcode]
            val nextEventCount = (existing?.eventCount ?: 0) + 1
            unknown[barcode] = (existing ?: ParayUnknownProductRecord(barcode = barcode)).copy(
                eventCount = nextEventCount,
                lastSeenAt = event.timestamp,
                lastDesignation = event.designation ?: existing?.lastDesignation,
                lastArticleId = event.articleId ?: existing?.lastArticleId,
                correctionCount = when (event.type) {
                    ParayRecognitionEventType.MANUAL_CORRECTION ->
                        (existing?.correctionCount ?: 0) + 1
                    else -> existing?.correctionCount ?: 0
                },
            )

            if (event.type != ParayRecognitionEventType.MANUAL_CORRECTION) {
                bumpPattern(patterns, productKey, event)
            }

            blindSpotWarning = shouldWarnBlindSpot(event, nextEventCount)
        }

        return ParayRecognitionApplyResult(
            newDiscovery = newDiscovery,
            blindSpotWarning = blindSpotWarning,
        )
    }

    private fun shouldWarnBlindSpot(event: ParayRecognitionEvent, eventCount: Int): Boolean =
        when (event.type) {
            ParayRecognitionEventType.UNKNOWN_BARCODE,
            ParayRecognitionEventType.RECOGNITION_FAILURE,
            -> eventCount >= BLIND_SPOT_THRESHOLD && eventCount % BLIND_SPOT_THRESHOLD == 0
            else -> false
        }

    private fun bumpPattern(
        patterns: MutableMap<String, ParayFailurePatternRecord>,
        key: String,
        event: ParayRecognitionEvent,
    ) {
        val existing = patterns[key]
        patterns[key] = ParayFailurePatternRecord(
            key = key,
            eventType = event.type,
            count = (existing?.count ?: 0) + 1,
            lastAt = event.timestamp,
            sampleBarcode = event.barcode ?: existing?.sampleBarcode,
            sampleDesignation = event.designation ?: existing?.sampleDesignation,
        )
    }

    private fun buildSummary(
        unknown: Map<String, ParayUnknownProductRecord>,
        patterns: Map<String, ParayFailurePatternRecord>,
    ): ParayRecognitionSummary {
        val typeCounts = patterns.values
            .filter { !it.key.contains('|') }
            .groupBy { it.eventType }
            .mapValues { (_, rows) -> rows.sumOf { it.count } }

        val problematic = unknown.values
            .sortedWith(compareByDescending<ParayUnknownProductRecord> { it.eventCount }.thenBy { it.barcode })
            .take(TOP_N)
            .map { toRank(it.barcode, it.lastDesignation, it.eventCount) }

        val packagingDrifts = patterns.values
            .filter { it.eventType == ParayRecognitionEventType.PACKAGING_DRIFT && it.key.contains('|') }
            .sortedByDescending { it.count }
            .take(TOP_N)
            .map { toRank(it.sampleBarcode.orEmpty(), it.sampleDesignation, it.count) }
            .filter { it.barcode.isNotBlank() }

        val corrected = unknown.values
            .filter { it.correctionCount > 0 }
            .sortedWith(compareByDescending<ParayUnknownProductRecord> { it.correctionCount }.thenBy { it.barcode })
            .take(TOP_N)
            .map { toRank(it.barcode, it.lastDesignation, it.correctionCount) }

        val failures = ParayRecognitionEventType.entries.mapNotNull { type ->
            val count = typeCounts[type] ?: 0
            if (count <= 0) null
            else ParayRecognitionFailureRank(
                eventType = type.name,
                label = failureLabel(type),
                count = count,
            )
        }.sortedByDescending { it.count }

        return ParayRecognitionSummary(
            totalFailures = typeCounts[ParayRecognitionEventType.RECOGNITION_FAILURE] ?: 0,
            totalLowConfidence = typeCounts[ParayRecognitionEventType.LOW_CONFIDENCE_MATCH] ?: 0,
            totalManualCorrections = typeCounts[ParayRecognitionEventType.MANUAL_CORRECTION] ?: 0,
            totalPackagingDrifts = typeCounts[ParayRecognitionEventType.PACKAGING_DRIFT] ?: 0,
            totalUnknownBarcodes = typeCounts[ParayRecognitionEventType.UNKNOWN_BARCODE] ?: 0,
            mostProblematicProducts = problematic,
            mostFrequentFailures = failures,
            mostCommonPackagingDrifts = packagingDrifts,
            mostCorrectedProducts = corrected,
            generatedAt = System.currentTimeMillis(),
        )
    }

    private fun toRank(barcode: String, designation: String?, count: Int) =
        ParayRecognitionProductRank(barcode = barcode, designation = designation, count = count)

    private fun failureLabel(type: ParayRecognitionEventType): String = when (type) {
        ParayRecognitionEventType.RECOGNITION_FAILURE -> "Recognition failure"
        ParayRecognitionEventType.LOW_CONFIDENCE_MATCH -> "Low confidence match"
        ParayRecognitionEventType.MANUAL_CORRECTION -> "Manual correction"
        ParayRecognitionEventType.PACKAGING_DRIFT -> "Packaging drift"
        ParayRecognitionEventType.UNKNOWN_BARCODE -> "Unknown barcode"
        ParayRecognitionEventType.TICKET_VERIFY_SCAN -> "Shelf ticket scan"
        ParayRecognitionEventType.TICKET_VERIFIED -> "Shelf ticket verified"
    }

    companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.55f
        private const val TOP_N = 10
        private const val BLIND_SPOT_THRESHOLD = 3
    }
}
