package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONArray
import org.json.JSONObject

/** Persists PARAY recognition curiosity under `paray_home/recognition/`. */
class ParayRecognitionStore(private val home: ParayHome) {
    private val eventsFile = home.recognitionEventsFile
    private val unknownFile = home.recognitionUnknownProductsFile
    private val patternsFile = home.recognitionFailurePatternsFile
    private val summaryFile = home.recognitionSummaryFile

    fun appendEvent(event: ParayRecognitionEvent) {
        eventsFile.parentFile?.mkdirs()
        eventsFile.appendText(eventToJson(event).toString() + "\n")
        trimEventsIfNeeded()
    }

    fun readUnknownProducts(): MutableMap<String, ParayUnknownProductRecord> {
        if (!unknownFile.exists()) return mutableMapOf()
        val root = runCatching { JSONObject(unknownFile.readText()) }.getOrElse { return mutableMapOf() }
        val map = mutableMapOf<String, ParayUnknownProductRecord>()
        root.keys().forEach { key ->
            root.optJSONObject(key)?.let { item ->
                map[key] = ParayUnknownProductRecord(
                    barcode = key,
                    eventCount = item.optInt("eventCount", 0),
                    correctionCount = item.optInt("correctionCount", 0),
                    lastSeenAt = item.optLong("lastSeenAt", 0L),
                    lastDesignation = item.optString("lastDesignation").takeIf { it.isNotBlank() },
                    lastArticleId = item.optLong("lastArticleId").takeIf { it > 0L },
                )
            }
        }
        return map
    }

    fun writeUnknownProducts(products: Map<String, ParayUnknownProductRecord>) {
        unknownFile.parentFile?.mkdirs()
        val root = JSONObject()
        products.forEach { (barcode, record) ->
            root.put(
                barcode,
                JSONObject()
                    .put("barcode", record.barcode)
                    .put("eventCount", record.eventCount)
                    .put("correctionCount", record.correctionCount)
                    .put("lastSeenAt", record.lastSeenAt)
                    .put("lastDesignation", record.lastDesignation)
                    .put("lastArticleId", record.lastArticleId),
            )
        }
        unknownFile.writeTextAtomic(root.toString())
    }

    fun readFailurePatterns(): MutableMap<String, ParayFailurePatternRecord> {
        if (!patternsFile.exists()) return mutableMapOf()
        val root = runCatching { JSONObject(patternsFile.readText()) }.getOrElse { return mutableMapOf() }
        val map = mutableMapOf<String, ParayFailurePatternRecord>()
        root.keys().forEach { key ->
            root.optJSONObject(key)?.let { item ->
                val type = runCatching {
                    ParayRecognitionEventType.valueOf(item.optString("eventType"))
                }.getOrDefault(ParayRecognitionEventType.RECOGNITION_FAILURE)
                map[key] = ParayFailurePatternRecord(
                    key = key,
                    eventType = type,
                    count = item.optInt("count", 0),
                    lastAt = item.optLong("lastAt", 0L),
                    sampleBarcode = item.optString("sampleBarcode").takeIf { it.isNotBlank() },
                    sampleDesignation = item.optString("sampleDesignation").takeIf { it.isNotBlank() },
                )
            }
        }
        return map
    }

    fun writeFailurePatterns(patterns: Map<String, ParayFailurePatternRecord>) {
        patternsFile.parentFile?.mkdirs()
        val root = JSONObject()
        patterns.forEach { (key, pattern) ->
            root.put(
                key,
                JSONObject()
                    .put("key", pattern.key)
                    .put("eventType", pattern.eventType.name)
                    .put("count", pattern.count)
                    .put("lastAt", pattern.lastAt)
                    .put("sampleBarcode", pattern.sampleBarcode)
                    .put("sampleDesignation", pattern.sampleDesignation),
            )
        }
        patternsFile.writeTextAtomic(root.toString())
    }

    fun readSummary(): ParayRecognitionSummary {
        if (!summaryFile.exists()) return ParayRecognitionSummary()
        val o = runCatching { JSONObject(summaryFile.readText()) }.getOrElse { return ParayRecognitionSummary() }
        return ParayRecognitionSummary(
            totalFailures = o.optInt("totalFailures", o.optInt("recognitionFailureCount", 0)),
            totalLowConfidence = o.optInt("totalLowConfidence", o.optInt("lowConfidenceCount", 0)),
            totalManualCorrections = o.optInt("totalManualCorrections", o.optInt("manualCorrectionCount", 0)),
            totalPackagingDrifts = o.optInt("totalPackagingDrifts", o.optInt("packagingDriftCount", 0)),
            totalUnknownBarcodes = o.optInt("totalUnknownBarcodes", o.optInt("unknownBarcodeCount", 0)),
            mostProblematicProducts = readProductRanks(o.optJSONArray("mostProblematicProducts")),
            mostFrequentFailures = readFailureRanks(o.optJSONArray("mostFrequentFailures")),
            mostCommonPackagingDrifts = readProductRanks(o.optJSONArray("mostCommonPackagingDrifts")),
            mostCorrectedProducts = readProductRanks(o.optJSONArray("mostCorrectedProducts")),
            generatedAt = o.optLong("generatedAt", o.optLong("updatedAt", System.currentTimeMillis())),
        )
    }

    fun writeSummary(summary: ParayRecognitionSummary) {
        summaryFile.parentFile?.mkdirs()
        summaryFile.writeTextAtomic(
            JSONObject()
                .put("totalFailures", summary.totalFailures)
                .put("totalLowConfidence", summary.totalLowConfidence)
                .put("totalManualCorrections", summary.totalManualCorrections)
                .put("totalPackagingDrifts", summary.totalPackagingDrifts)
                .put("totalUnknownBarcodes", summary.totalUnknownBarcodes)
                .put("mostProblematicProducts", productRanksToJson(summary.mostProblematicProducts))
                .put("mostFrequentFailures", failureRanksToJson(summary.mostFrequentFailures))
                .put("mostCommonPackagingDrifts", productRanksToJson(summary.mostCommonPackagingDrifts))
                .put("mostCorrectedProducts", productRanksToJson(summary.mostCorrectedProducts))
                .put("generatedAt", summary.generatedAt)
                .toString(2),
        )
    }

    private fun eventToJson(event: ParayRecognitionEvent): JSONObject {
        val metadata = JSONObject()
        event.metadata.forEach { (k, v) -> metadata.put(k, v) }
        return JSONObject()
            .put("type", event.type.name)
            .put("timestamp", event.timestamp)
            .put("articleId", event.articleId)
            .put("barcode", event.barcode)
            .put("confidence", event.confidence?.toDouble())
            .put("source", event.source)
            .put("metadata", metadata)
    }

    private fun readProductRanks(arr: JSONArray?): List<ParayRecognitionProductRank> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayRecognitionProductRank(
                            barcode = item.optString("barcode"),
                            designation = item.optString("designation").takeIf { it.isNotBlank() },
                            count = item.optInt("count", 0),
                        ),
                    )
                }
            }
        }
    }

    private fun readFailureRanks(arr: JSONArray?): List<ParayRecognitionFailureRank> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayRecognitionFailureRank(
                            eventType = item.optString("eventType"),
                            label = item.optString("label"),
                            count = item.optInt("count", 0),
                        ),
                    )
                }
            }
        }
    }

    private fun productRanksToJson(ranks: List<ParayRecognitionProductRank>): JSONArray {
        val arr = JSONArray()
        ranks.forEach { rank ->
            arr.put(
                JSONObject()
                    .put("barcode", rank.barcode)
                    .put("designation", rank.designation)
                    .put("count", rank.count),
            )
        }
        return arr
    }

    private fun failureRanksToJson(ranks: List<ParayRecognitionFailureRank>): JSONArray {
        val arr = JSONArray()
        ranks.forEach { rank ->
            arr.put(
                JSONObject()
                    .put("eventType", rank.eventType)
                    .put("label", rank.label)
                    .put("count", rank.count),
            )
        }
        return arr
    }

    private fun trimEventsIfNeeded() {
        if (!eventsFile.exists()) return
        val lines = eventsFile.readLines()
        if (lines.size <= MAX_EVENTS) return
        eventsFile.writeTextAtomic(lines.takeLast(MAX_EVENTS).joinToString("\n", postfix = "\n"))
    }

    companion object {
        private const val MAX_EVENTS = 2000
    }
}
