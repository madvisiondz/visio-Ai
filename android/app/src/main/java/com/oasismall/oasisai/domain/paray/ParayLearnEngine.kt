package com.oasismall.oasisai.domain.paray

/**
 * Frame-by-frame learning session state machine for PARAY Learn V1.
 * All confidence thresholds come from [ParayLearnSettings] — nothing hardcoded here.
 */
class ParayLearnEngine(
    private val settings: ParayLearnSettings,
) {
    fun initialPhase(record: ParayLearnRecord?): ParayLearnPhase = when {
        record?.status == ParayLearnStatus.LEARNED -> ParayLearnPhase.COMPLETE
        record?.frontConfirmed == true && record.leftCapture == null -> ParayLearnPhase.CAPTURE_LEFT
        record?.frontConfirmed == true && record.rightCapture == null -> ParayLearnPhase.CAPTURE_RIGHT
        record?.frontConfirmed == true && record.backCapture == null -> ParayLearnPhase.CAPTURE_BACK
        else -> ParayLearnPhase.FRONT_CONFIRM
    }

    fun processFrontConfirm(
        record: ParayLearnRecord,
        pngFeatures: VisualFeatureExtractor.Features,
        frameFeatures: VisualFeatureExtractor.Features,
        stableFrames: Int,
        mismatchFrames: Int,
    ): ParayLearnFrameResult {
        val similarity = ParayVisualSimilarity.score(frameFeatures, pngFeatures)
        val progress = progressFlags(record)

        if (similarity >= settings.frontConfirmationThreshold &&
            stableFrames >= STABLE_FRAMES_REQUIRED
        ) {
            val confirmed = record.copy(
                frontConfirmed = true,
                frontConfidence = similarity,
                frontCapture = ParayViewCapture(
                    shapeAspect = frameFeatures.shapeAspect,
                    fillRatio = frameFeatures.fillRatio,
                    dominantColors = frameFeatures.dominantColors,
                    confidence = similarity,
                ),
            )
            return ParayLearnFrameResult(
                phase = ParayLearnPhase.CAPTURE_LEFT,
                instruction = "Front confirmed — show Left side",
                frontSimilarity = similarity,
                stableFrames = stableFrames,
                updatedRecord = confirmed,
                progressFront = true,
                progressLeft = progress.progressLeft,
                progressRight = progress.progressRight,
                progressBack = progress.progressBack,
            )
        }

        if (mismatchFrames >= MISMATCH_FRAME_LIMIT && similarity < settings.frontMismatchCutoff()) {
            return ParayLearnFrameResult(
                phase = ParayLearnPhase.MISMATCH,
                instruction = "Front mismatch — please verify product",
                frontSimilarity = similarity,
                stableFrames = stableFrames,
            )
        }

        return ParayLearnFrameResult(
            phase = ParayLearnPhase.FRONT_CONFIRM,
            instruction = "Show product front — match PNG reference (${(similarity * 100).toInt()}%)",
            frontSimilarity = similarity,
            stableFrames = stableFrames,
            progressFront = false,
            progressLeft = progress.progressLeft,
            progressRight = progress.progressRight,
            progressBack = progress.progressBack,
        )
    }

    fun processSideCapture(
        record: ParayLearnRecord,
        phase: ParayLearnPhase,
        pngFeatures: VisualFeatureExtractor.Features,
        frameFeatures: VisualFeatureExtractor.Features,
        priorFeatures: List<VisualFeatureExtractor.Features>,
        stableFrames: Int,
    ): ParayLearnFrameResult {
        val captureThreshold = when (phase) {
            ParayLearnPhase.CAPTURE_BACK -> settings.backCaptureThreshold
            else -> settings.sideCaptureThreshold
        }
        val maxPriorSimilarity = when (phase) {
            ParayLearnPhase.CAPTURE_BACK -> settings.maxPriorSimilarityForBack()
            else -> settings.maxPriorSimilarityForSide()
        }

        val distinctFromPrior = ParayVisualSimilarity.isDistinctEnough(
            probe = frameFeatures,
            prior = priorFeatures,
            minDelta = captureThreshold,
        )
        val notFrontDuplicate = ParayVisualSimilarity.score(frameFeatures, pngFeatures) < maxPriorSimilarity
        val distinct = distinctFromPrior && notFrontDuplicate

        if (stableFrames < STABLE_FRAMES_REQUIRED || !distinct) {
            return ParayLearnFrameResult(
                phase = phase,
                instruction = sideInstruction(phase),
                stableFrames = stableFrames,
                progressFront = true,
                progressLeft = record.leftCapture != null,
                progressRight = record.rightCapture != null,
                progressBack = record.backCapture != null,
            )
        }

        val sideScore = priorFeatures.maxOfOrNull { ParayVisualSimilarity.score(frameFeatures, it) } ?: 0f
        val capture = ParayViewCapture(
            shapeAspect = frameFeatures.shapeAspect,
            fillRatio = frameFeatures.fillRatio,
            dominantColors = frameFeatures.dominantColors,
            confidence = sideScore,
        )

        val updated = when (phase) {
            ParayLearnPhase.CAPTURE_LEFT -> record.copy(leftCapture = capture)
            ParayLearnPhase.CAPTURE_RIGHT -> record.copy(rightCapture = capture)
            ParayLearnPhase.CAPTURE_BACK -> record.copy(
                backCapture = capture,
                learnedAt = System.currentTimeMillis(),
            )
            else -> record
        }

        val nextPhase = when (phase) {
            ParayLearnPhase.CAPTURE_LEFT -> ParayLearnPhase.CAPTURE_RIGHT
            ParayLearnPhase.CAPTURE_RIGHT -> ParayLearnPhase.CAPTURE_BACK
            ParayLearnPhase.CAPTURE_BACK -> ParayLearnPhase.COMPLETE
            else -> phase
        }

        return ParayLearnFrameResult(
            phase = nextPhase,
            instruction = when (nextPhase) {
                ParayLearnPhase.CAPTURE_RIGHT -> "Left captured — show Right side"
                ParayLearnPhase.CAPTURE_BACK -> "Right captured — show Back side"
                ParayLearnPhase.COMPLETE -> "Learning complete"
                else -> sideInstruction(nextPhase)
            },
            stableFrames = stableFrames,
            updatedRecord = updated,
            progressFront = true,
            progressLeft = updated.leftCapture != null,
            progressRight = updated.rightCapture != null,
            progressBack = updated.backCapture != null,
        )
    }

    fun priorSideFeatures(record: ParayLearnRecord): List<VisualFeatureExtractor.Features> =
        buildList {
            record.frontCapture?.toFeatures()?.let { add(it) }
            record.leftCapture?.toFeatures()?.let { add(it) }
            record.rightCapture?.toFeatures()?.let { add(it) }
        }

    private fun sideInstruction(phase: ParayLearnPhase): String = when (phase) {
        ParayLearnPhase.CAPTURE_LEFT -> "Show Left side"
        ParayLearnPhase.CAPTURE_RIGHT -> "Show Right side"
        ParayLearnPhase.CAPTURE_BACK -> "Show Back side"
        else -> "Hold product steady"
    }

    private data class ProgressFlags(
        val progressLeft: Boolean,
        val progressRight: Boolean,
        val progressBack: Boolean,
    )

    private fun progressFlags(record: ParayLearnRecord) = ProgressFlags(
        progressLeft = record.leftCapture != null,
        progressRight = record.rightCapture != null,
        progressBack = record.backCapture != null,
    )

    companion object {
        /** Operational — not confidence thresholds (see [ParayLearnSettings]). */
        const val STABLE_FRAMES_REQUIRED = 4
        const val MISMATCH_FRAME_LIMIT = 45
        const val FRAME_STABILITY_SIMILARITY = 0.92f
    }
}
