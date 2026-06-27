package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONArray
import org.json.JSONObject

/** Persists PARAY workflow knowledge under `paray_home/workflows/`. */
class ParayWorkflowStore(private val home: ParayHome) {
    private val eventsFile = home.workflowEventsFile
    private val patternsFile = home.workflowPatternsFile
    private val summaryFile = home.workflowSummaryFile
    private val screenUsageFile = home.workflowScreenUsageFile

    fun appendEvent(event: ParayWorkflowEvent) {
        eventsFile.parentFile?.mkdirs()
        val line = JSONObject()
            .put("type", event.type)
            .put("at", event.at)
            .put("screen", event.screen)
            .put("from", event.from)
            .put("to", event.to)
            .put("feature", event.feature)
            .put("durationMs", event.durationMs)
            .toString()
        eventsFile.appendText(line + "\n")
        trimEventsIfNeeded()
    }

    fun readScreenUsage(): MutableMap<String, ParayScreenUsageEntry> {
        if (!screenUsageFile.exists()) return mutableMapOf()
        val root = runCatching { JSONObject(screenUsageFile.readText()) }.getOrElse { return mutableMapOf() }
        val screens = root.optJSONObject("screens") ?: return mutableMapOf()
        val map = mutableMapOf<String, ParayScreenUsageEntry>()
        screens.keys().forEach { key ->
            screens.optJSONObject(key)?.let { item ->
                map[key] = ParayScreenUsageEntry(
                    screen = key,
                    visits = item.optInt("visits", 0),
                    totalDurationMs = item.optLong("totalDurationMs", 0L),
                    averageDurationMs = item.optLong("averageDurationMs", 0L),
                )
            }
        }
        return map
    }

    fun writeScreenUsage(screens: Map<String, ParayScreenUsageEntry>) {
        screenUsageFile.parentFile?.mkdirs()
        val root = JSONObject()
        screens.forEach { (key, entry) ->
            root.put(
                key,
                JSONObject()
                    .put("visits", entry.visits)
                    .put("totalDurationMs", entry.totalDurationMs)
                    .put("averageDurationMs", entry.averageDurationMs),
            )
        }
        screenUsageFile.writeTextAtomic(
            JSONObject()
                .put("screens", root)
                .put("updatedAt", System.currentTimeMillis())
                .toString(2),
        )
    }

    data class PatternState(
        val transitions: MutableMap<String, ParayWorkflowTransition> = mutableMapOf(),
        val sequences: MutableMap<String, ParayWorkflowSequence> = mutableMapOf(),
        val features: MutableMap<ParayWorkflowFeature, Int> = mutableMapOf(),
        var activityTrackingStartedAt: Long = 0L,
        var totalActivityCount: Int = 0,
    )

    fun readPatterns(): PatternState {
        if (!patternsFile.exists()) return PatternState()
        val root = runCatching { JSONObject(patternsFile.readText()) }.getOrElse { return PatternState() }
        val transitions = mutableMapOf<String, ParayWorkflowTransition>()
        root.optJSONObject("transitions")?.let { obj ->
            obj.keys().forEach { key ->
                obj.optJSONObject(key)?.let { item ->
                    transitions[key] = ParayWorkflowTransition(
                        from = item.optString("from"),
                        to = item.optString("to"),
                        count = item.optInt("count", 0),
                        confidence = confidenceFromString(item.optString("confidence")),
                    )
                }
            }
        }
        val sequences = mutableMapOf<String, ParayWorkflowSequence>()
        root.optJSONObject("sequences")?.let { obj ->
            obj.keys().forEach { key ->
                obj.optJSONObject(key)?.let { item ->
                    val steps = buildList {
                        val arr = item.optJSONArray("steps") ?: JSONArray()
                        for (i in 0 until arr.length()) add(arr.optString(i))
                    }
                    sequences[key] = ParayWorkflowSequence(
                        steps = steps,
                        count = item.optInt("count", 0),
                        confidence = confidenceFromString(item.optString("confidence")),
                    )
                }
            }
        }
        val features = mutableMapOf<ParayWorkflowFeature, Int>()
        root.optJSONObject("features")?.let { obj ->
            obj.keys().forEach { key ->
                runCatching { ParayWorkflowFeature.valueOf(key) }.getOrNull()?.let { feature ->
                    features[feature] = obj.optInt(key, 0)
                }
            }
        }
        return PatternState(
            transitions = transitions,
            sequences = sequences,
            features = features,
            activityTrackingStartedAt = root.optLong("activityTrackingStartedAt", 0L),
            totalActivityCount = root.optInt("totalActivityCount", 0),
        )
    }

    fun writePatterns(state: PatternState) {
        patternsFile.parentFile?.mkdirs()
        val transitions = JSONObject()
        state.transitions.forEach { (key, transition) ->
            transitions.put(
                key,
                JSONObject()
                    .put("from", transition.from)
                    .put("to", transition.to)
                    .put("count", transition.count)
                    .put("confidence", transition.confidence.name.lowercase()),
            )
        }
        val sequences = JSONObject()
        state.sequences.forEach { (key, sequence) ->
            sequences.put(
                key,
                JSONObject()
                    .put("steps", JSONArray(sequence.steps))
                    .put("count", sequence.count)
                    .put("confidence", sequence.confidence.name.lowercase()),
            )
        }
        val features = JSONObject()
        state.features.forEach { (feature, count) ->
            features.put(feature.name, count)
        }
        patternsFile.writeTextAtomic(
            JSONObject()
                .put("transitions", transitions)
                .put("sequences", sequences)
                .put("features", features)
                .put("activityTrackingStartedAt", state.activityTrackingStartedAt)
                .put("totalActivityCount", state.totalActivityCount)
                .put("updatedAt", System.currentTimeMillis())
                .toString(2),
        )
    }

    fun writeSummary(summary: ParayWorkflowSummary) {
        summaryFile.parentFile?.mkdirs()
        val topScreens = JSONArray()
        summary.topScreens.forEach { entry ->
            topScreens.put(
                JSONObject()
                    .put("screen", entry.screen)
                    .put("visits", entry.visits)
                    .put("averageDurationMs", entry.averageDurationMs),
            )
        }
        val topFeatures = JSONArray()
        summary.topFeatures.forEach { usage ->
            topFeatures.put(
                JSONObject()
                    .put("feature", usage.feature.name)
                    .put("label", usage.feature.label)
                    .put("count", usage.count),
            )
        }
        val topTransitions = JSONArray()
        summary.topTransitions.take(12).forEach { transition ->
            topTransitions.put(
                JSONObject()
                    .put("from", transition.from)
                    .put("to", transition.to)
                    .put("count", transition.count)
                    .put("confidence", transition.confidence.name.lowercase()),
            )
        }
        val topSequences = JSONArray()
        summary.topSequences.take(8).forEach { sequence ->
            topSequences.put(
                JSONObject()
                    .put("steps", JSONArray(sequence.steps))
                    .put("count", sequence.count)
                    .put("confidence", sequence.confidence.name.lowercase()),
            )
        }
        val gaps = summary.gaps
        val appMap = JSONArray()
        summary.appMap.take(20).forEach { edge ->
            appMap.put(
                JSONObject()
                    .put("from", edge.from)
                    .put("to", edge.to)
                    .put("weight", edge.weight),
            )
        }
        val bottleneck = summary.bottleneck?.let {
            JSONObject()
                .put("screen", it.screen)
                .put("averageDurationMs", it.averageDurationMs)
        }
        summaryFile.writeTextAtomic(
            JSONObject()
                .put("topScreens", topScreens)
                .put("topFeatures", topFeatures)
                .put("topTransitions", topTransitions)
                .put("topSequences", topSequences)
                .put(
                    "gaps",
                    JSONObject()
                        .put("unusedScreens", JSONArray(gaps.unusedScreens))
                        .put("unusedFeatures", JSONArray(gaps.unusedFeatures))
                        .put("rarelyUsedFeatures", JSONArray(gaps.rarelyUsedFeatures)),
                )
                .put("bottleneck", bottleneck)
                .put("appMap", appMap)
                .put("agentUsageCount", summary.agentUsageCount)
                .put("designExportsCount", summary.designExportsCount)
                .put("averageDailyActivity", summary.averageDailyActivity.toDouble())
                .put("updatedAt", summary.updatedAt)
                .toString(2),
        )
    }

    fun readSummary(): ParayWorkflowSummary {
        if (!summaryFile.exists()) return ParayWorkflowSummary()
        val o = runCatching { JSONObject(summaryFile.readText()) }.getOrElse { return ParayWorkflowSummary() }
        val gapsO = o.optJSONObject("gaps")
        val bottleneckO = o.optJSONObject("bottleneck")
        val topScreens = buildList {
            val arr = o.optJSONArray("topScreens") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayScreenUsageEntry(
                            screen = item.optString("screen"),
                            visits = item.optInt("visits", 0),
                            averageDurationMs = item.optLong("averageDurationMs", 0L),
                        ),
                    )
                }
            }
        }
        val topFeatures = buildList {
            val arr = o.optJSONArray("topFeatures") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    val featureName = item.optString("feature")
                    runCatching { ParayWorkflowFeature.valueOf(featureName) }.getOrNull()?.let { feature ->
                        add(ParayWorkflowFeatureUsage(feature, item.optInt("count", 0)))
                    }
                }
            }
        }
        val appMap = buildList {
            val arr = o.optJSONArray("appMap") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayAppMapEdge(
                            from = item.optString("from"),
                            to = item.optString("to"),
                            weight = item.optInt("weight", 0),
                        ),
                    )
                }
            }
        }
        return ParayWorkflowSummary(
            topScreens = topScreens,
            topFeatures = topFeatures,
            gaps = ParayWorkflowGaps(
                unusedScreens = jsonStringList(gapsO?.optJSONArray("unusedScreens")),
                unusedFeatures = jsonStringList(gapsO?.optJSONArray("unusedFeatures")),
                rarelyUsedFeatures = jsonStringList(gapsO?.optJSONArray("rarelyUsedFeatures")),
            ),
            bottleneck = bottleneckO?.let {
                ParayWorkflowBottleneck(
                    screen = it.optString("screen"),
                    averageDurationMs = it.optLong("averageDurationMs", 0L),
                )
            },
            appMap = appMap,
            agentUsageCount = o.optInt("agentUsageCount", 0),
            designExportsCount = o.optInt("designExportsCount", 0),
            averageDailyActivity = o.optDouble("averageDailyActivity", 0.0).toFloat(),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun jsonStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun trimEventsIfNeeded() {
        if (!eventsFile.exists()) return
        val lines = eventsFile.readLines()
        if (lines.size <= MAX_EVENT_LINES) return
        eventsFile.writeTextAtomic(lines.takeLast(MAX_EVENT_LINES).joinToString("\n") + "\n")
    }

    private fun confidenceFromString(value: String): ParayWorkflowConfidence =
        runCatching { ParayWorkflowConfidence.valueOf(value.uppercase()) }
            .getOrDefault(ParayWorkflowConfidence.LOW)

    companion object {
        private const val MAX_EVENT_LINES = 400
    }
}
