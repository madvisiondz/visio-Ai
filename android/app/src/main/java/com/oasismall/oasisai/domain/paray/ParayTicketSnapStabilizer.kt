package com.oasismall.oasisai.domain.paray

/**
 * Stabilization rules: anti-flip, tier gating, recovery decisions.
 */
object ParayTicketSnapStabilizer {
    private const val FLIP_GUARD_MS = 4_000L
    private const val FLIP_MIN_DELTA = 0.12f

    data class LockDecision(
        val allowLock: Boolean,
        val tier: ParayTicketMatchTier?,
        val message: String?,
    )

    fun decideLock(
        match: ParayTicketMatch,
        lastArticleId: Long?,
        lastProbability: Float?,
        lastLockMs: Long,
    ): LockDecision {
        val p = match.fusion.probability
        val tier = ParayTicketMatchTier.fromProbability(p) ?: return LockDecision(
            allowLock = false,
            tier = null,
            message = "Match too weak — hold again on the ticket",
        )

        val now = System.currentTimeMillis()
        if (lastArticleId != null &&
            lastArticleId != match.article.id &&
            lastProbability != null &&
            now - lastLockMs < FLIP_GUARD_MS &&
            p < ParayTicketMatchTier.HIGH.minProbability &&
            kotlin.math.abs(p - lastProbability) < FLIP_MIN_DELTA
        ) {
            return LockDecision(
                allowLock = false,
                tier = tier,
                message = "Hold steady — confirming article…",
            )
        }

        val hint = when (tier) {
            ParayTicketMatchTier.CONFIRMED -> null
            ParayTicketMatchTier.HIGH -> "Strong match — verify price on card"
            ParayTicketMatchTier.PROBABLE -> "Probable match — please verify designation"
        }
        return LockDecision(allowLock = true, tier = tier, message = hint)
    }

    fun shouldAttemptRecovery(firstPassFailed: Boolean, frameQuality: Float): Boolean =
        firstPassFailed && frameQuality >= 0.45f
}
