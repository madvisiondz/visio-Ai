package com.oasismall.oasisai.domain.paray

/**
 * Tunable confidence thresholds for PARAY Learn sessions.
 *
 * Values are **not** hardcoded in [ParayLearnEngine] — loaded from
 * [ParayLearnSettingsStore] (`paray_home/memory/learn_settings.json`).
 *
 * | Field | Meaning (0..1) |
 * |-------|----------------|
 * | [frontConfirmationThreshold] | Min PNG↔camera similarity to confirm front |
 * | [sideCaptureThreshold] | Min view change vs prior captures for left/right auto-capture |
 * | [backCaptureThreshold] | Min view change vs prior captures for back auto-capture |
 */
data class ParayLearnSettings(
    val frontConfirmationThreshold: Float,
    val sideCaptureThreshold: Float,
    val backCaptureThreshold: Float,
) {
    fun validated(): ParayLearnSettings = copy(
        frontConfirmationThreshold = frontConfirmationThreshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
        sideCaptureThreshold = sideCaptureThreshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
        backCaptureThreshold = backCaptureThreshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
    )

    /** Front mismatch when similarity stays below this fraction of the confirmation threshold. */
    fun frontMismatchCutoff(): Float = frontConfirmationThreshold * FRONT_MISMATCH_RATIO

    /** Max allowed similarity to a prior view when capturing a side (1 − threshold). */
    fun maxPriorSimilarityForSide(): Float = 1f - sideCaptureThreshold

    fun maxPriorSimilarityForBack(): Float = 1f - backCaptureThreshold

    companion object {
        private const val MIN_THRESHOLD = 0.01f
        private const val MAX_THRESHOLD = 0.99f
        private const val FRONT_MISMATCH_RATIO = 0.45f

        /** Initial values written when no settings file exists — tune via settings UI later. */
        fun factoryDefaults(): ParayLearnSettings = ParayLearnSettings(
            frontConfirmationThreshold = 0.62f,
            sideCaptureThreshold = 0.12f,
            backCaptureThreshold = 0.12f,
        )
    }
}
