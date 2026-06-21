package com.oasismall.oasisai.domain.paray

import kotlin.math.abs

/** Shared shape/color similarity for PARAY recognition and Learn front confirmation. */
object ParayVisualSimilarity {
    fun score(
        probe: VisualFeatureExtractor.Features,
        reference: VisualFeatureExtractor.Features,
        observationBoost: Float = 0f,
    ): Float {
        val aspectScore = 1f - (
            abs(probe.shapeAspect - reference.shapeAspect) /
                maxOf(probe.shapeAspect, reference.shapeAspect, 0.1f)
            ).coerceIn(0f, 1f)
        val fillScore = 1f - abs(probe.fillRatio - reference.fillRatio).coerceIn(0f, 1f)
        val colorScore = colorOverlap(probe.dominantColors, reference.dominantColors)
        return (aspectScore * 0.35f + fillScore * 0.2f + colorScore * 0.4f + observationBoost)
            .coerceIn(0f, 1f)
    }

    fun scoreCapture(probe: VisualFeatureExtractor.Features, capture: ParayViewCapture?): Float =
        capture?.let { score(probe, it.toFeatures()) } ?: 0f

    fun isDistinctEnough(
        probe: VisualFeatureExtractor.Features,
        prior: List<VisualFeatureExtractor.Features>,
        minDelta: Float = 0.12f,
    ): Boolean {
        if (prior.isEmpty()) return true
        return prior.all { score(probe, it) < (1f - minDelta) }
    }

    private fun colorOverlap(a: List<Int>, b: List<Int>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var hits = 0
        for (c in a) {
            if (b.any { nearColor(c, it) }) hits++
        }
        return hits.toFloat() / a.size
    }

    private fun nearColor(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) < 48
    }
}
