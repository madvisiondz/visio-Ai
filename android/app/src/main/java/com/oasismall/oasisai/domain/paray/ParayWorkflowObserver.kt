package com.oasismall.oasisai.domain.paray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Builds workflow knowledge from navigation and feature events.
 * Workflow patterns only — never user text, images, or personal content.
 */
class ParayWorkflowObserver(
    home: ParayHome,
    private val store: ParayWorkflowStore = ParayWorkflowStore(home),
) {
    private val mutex = Mutex()

    suspend fun recordScreenVisit(
        screen: String,
        durationMs: Long,
        previous: String?,
        previousPrevious: String?,
        destination: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val screens = store.readScreenUsage()
            val patterns = store.readPatterns()
            bumpActivity(patterns, now)

            if (durationMs >= 0L) {
                val existing = screens[screen]
                val visits = (existing?.visits ?: 0) + 1
                val total = (existing?.totalDurationMs ?: 0L) + durationMs
                screens[screen] = ParayScreenUsageEntry(
                    screen = screen,
                    visits = visits,
                    totalDurationMs = total,
                    averageDurationMs = if (visits > 0) total / visits else 0L,
                )
                store.appendEvent(
                    ParayWorkflowEvent(
                        type = "screen_visit",
                        at = now,
                        screen = screen,
                        durationMs = durationMs,
                    ),
                )
            }

            if (previous != null) {
                val key = transitionKey(previous, screen)
                val existing = patterns.transitions[key]
                val count = (existing?.count ?: 0) + 1
                patterns.transitions[key] = ParayWorkflowTransition(
                    from = previous,
                    to = screen,
                    count = count,
                    confidence = confidenceFor(count),
                )
                store.appendEvent(
                    ParayWorkflowEvent(
                        type = "transition",
                        at = now,
                        from = previous,
                        to = screen,
                    ),
                )
            }

            if (previousPrevious != null && previous != null) {
                val steps = listOf(previousPrevious, previous, destination)
                val key = steps.joinToString("→")
                val existing = patterns.sequences[key]
                val count = (existing?.count ?: 0) + 1
                patterns.sequences[key] = ParayWorkflowSequence(
                    steps = steps,
                    count = count,
                    confidence = confidenceFor(count),
                )
                store.appendEvent(
                    ParayWorkflowEvent(
                        type = "sequence",
                        at = now,
                        from = previousPrevious,
                        to = screen,
                    ),
                )
            }

            store.writeScreenUsage(screens)
            store.writePatterns(patterns)
            store.writeSummary(buildSummary(screens, patterns))
        }
    }

    suspend fun recordFeature(feature: ParayWorkflowFeature) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val patterns = store.readPatterns()
            bumpActivity(patterns, now)
            patterns.features[feature] = (patterns.features[feature] ?: 0) + 1
            store.appendEvent(
                ParayWorkflowEvent(
                    type = "feature",
                    at = System.currentTimeMillis(),
                    feature = feature.name,
                ),
            )
            store.writePatterns(patterns)
            store.writeSummary(buildSummary(store.readScreenUsage(), patterns))
        }
    }

    fun readSummary(): ParayWorkflowSummary = store.readSummary()

    private fun buildSummary(
        screens: Map<String, ParayScreenUsageEntry>,
        patterns: ParayWorkflowStore.PatternState,
    ): ParayWorkflowSummary {
        val topScreens = screens.values
            .sortedWith(compareByDescending<ParayScreenUsageEntry> { it.visits }.thenBy { it.screen })
            .take(12)
        val topFeatures = ParayWorkflowFeature.entries.map { feature ->
            ParayWorkflowFeatureUsage(feature, patterns.features[feature] ?: 0)
        }.sortedByDescending { it.count }
        val topTransitions = patterns.transitions.values
            .sortedByDescending { it.count }
            .take(12)
        val topSequences = patterns.sequences.values
            .sortedByDescending { it.count }
            .take(8)
        val usedScreens = screens.filter { it.value.visits > 0 }.keys
        val unusedScreens = ParayWorkflowScreens.allLabels.filter { it !in usedScreens }
        val featureCounts = topFeatures.associate { it.feature to it.count }
        val unusedFeatures = ParayWorkflowFeature.entries
            .filter { (featureCounts[it] ?: 0) == 0 }
            .map { it.label }
        val rarelyUsedFeatures = topFeatures
            .filter { it.count in 1..RARE_FEATURE_MAX }
            .map { it.feature.label }
        val bottleneck = screens.values
            .filter { it.visits >= MIN_BOTTLENECK_VISITS && it.averageDurationMs > 0L }
            .maxByOrNull { it.averageDurationMs }
            ?.let { ParayWorkflowBottleneck(it.screen, it.averageDurationMs) }
        val appMap = patterns.transitions.values
            .sortedByDescending { it.count }
            .take(20)
            .map { ParayAppMapEdge(from = it.from, to = it.to, weight = it.count) }
        val agentUsageCount = screens["AGENT"]?.visits ?: 0
        val designExportsCount = patterns.features[ParayWorkflowFeature.DESIGN_EXPORT] ?: 0
        val averageDailyActivity = averageDaily(patterns)

        return ParayWorkflowSummary(
            topScreens = topScreens,
            topFeatures = topFeatures,
            topTransitions = topTransitions,
            topSequences = topSequences,
            gaps = ParayWorkflowGaps(
                unusedScreens = unusedScreens,
                unusedFeatures = unusedFeatures,
                rarelyUsedFeatures = rarelyUsedFeatures,
            ),
            bottleneck = bottleneck,
            appMap = appMap,
            agentUsageCount = agentUsageCount,
            designExportsCount = designExportsCount,
            averageDailyActivity = averageDailyActivity,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun bumpActivity(patterns: ParayWorkflowStore.PatternState, now: Long) {
        if (patterns.activityTrackingStartedAt <= 0L) {
            patterns.activityTrackingStartedAt = now
        }
        patterns.totalActivityCount += 1
    }

    private fun averageDaily(patterns: ParayWorkflowStore.PatternState): Float {
        if (patterns.totalActivityCount <= 0) return 0f
        val started = patterns.activityTrackingStartedAt.takeIf { it > 0L } ?: return 0f
        val days = ((System.currentTimeMillis() - started) / 86_400_000L).toInt() + 1
        return patterns.totalActivityCount.toFloat() / days.coerceAtLeast(1)
    }

    private fun transitionKey(from: String, to: String): String = "$from→$to"

    private fun confidenceFor(count: Int): ParayWorkflowConfidence = when {
        count >= HIGH_CONFIDENCE_MIN -> ParayWorkflowConfidence.HIGH
        count >= MEDIUM_CONFIDENCE_MIN -> ParayWorkflowConfidence.MEDIUM
        else -> ParayWorkflowConfidence.LOW
    }

    companion object {
        private const val HIGH_CONFIDENCE_MIN = 50
        private const val MEDIUM_CONFIDENCE_MIN = 10
        private const val RARE_FEATURE_MAX = 5
        private const val MIN_BOTTLENECK_VISITS = 3
    }
}
