package com.oasismall.oasisai.domain.paray

/**
 * Frame-by-frame learning session state machine for PARAY Learn V1.
 * All confidence thresholds come from [ParayLearnSettings].
 * Front confirmation validates against canonical PNG — not stored as a learned side.
 */
class ParayLearnEngine(
    private val settings: ParayLearnSettings,
) {
    fun initialPhase(record: ParayLearnRecord?): ParayLearnPhase = when {
        record?.status == ParayLearnStatus.LEARNED -> ParayLearnPhase.COMPLETE
        record?.frontConfirmed != true -> ParayLearnPhase.FRONT_CONFIRM
        record.leftCapture == null -> ParayLearnPhase.CAPTURE_LEFT
        record.rightCapture == null -> ParayLearnPhase.CAPTURE_RIGHT
        record.backCapture == null -> ParayLearnPhase.CAPTURE_BACK
        else -> ParayLearnPhase.COMPLETE
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
                updatedAt = System.currentTimeMillis(),
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

        val variantEvent = ParayPackagingVariantDetector.detectPersistentDrift(
            articleId = record.articleId,
            barcode = record.barcode,
            designation = record.designation,
            similarity = similarity,
            settings = settings,
            mismatchFrames = mismatchFrames,
        )
        val variantHint = variantEvent?.message

        if (mismatchFrames >= MISMATCH_FRAME_LIMIT && similarity < settings.frontMismatchCutoff()) {
            return ParayLearnFrameResult(
                phase = ParayLearnPhase.MISMATCH,
                instruction = "Front mismatch — please verify product",
                frontSimilarity = similarity,
                stableFrames = stableFrames,
                packagingVariantHint = variantHint,
            )
        }

        return ParayLearnFrameResult(
            phase = ParayLearnPhase.FRONT_CONFIRM,
            instruction = "Show Front Side — match official PNG (${(similarity * 100).toInt()}%)",
            frontSimilarity = similarity,
            stableFrames = stableFrames,
            progressFront = false,
            progressLeft = progress.progressLeft,
            progressRight = progress.progressRight,
            progressBack = progress.progressBack,
            packagingVariantHint = variantHint,
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
                progressFront = record.frontConfirmed,
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
            confidence = 1f - sideScore,
        )

        val now = System.currentTimeMillis()
        val updated = when (phase) {
            ParayLearnPhase.CAPTURE_LEFT -> record.copy(leftCapture = capture, updatedAt = now)
            ParayLearnPhase.CAPTURE_RIGHT -> record.copy(rightCapture = capture, updatedAt = now)
            ParayLearnPhase.CAPTURE_BACK -> record.copy(
                backCapture = capture,
                learnedAt = now,
                updatedAt = now,
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
                ParayLearnPhase.CAPTURE_RIGHT -> "Left captured ✓ — show Right side"
                ParayLearnPhase.CAPTURE_BACK -> "Right captured ✓ — show Back side"
                ParayLearnPhase.COMPLETE -> "Back captured ✓ — learning complete"
                else -> sideInstruction(nextPhase)
            },
            stableFrames = stableFrames,
            updatedRecord = updated,
            progressFront = updated.frontConfirmed,
            progressLeft = updated.leftCapture != null,
            progressRight = updated.rightCapture != null,
            progressBack = updated.backCapture != null,
        )
    }

    /** Prior views for distinctness — canonical PNG front + captured sides. */
    fun priorSideFeatures(
        record: ParayLearnRecord,
        pngFeatures: VisualFeatureExtractor.Features,
    ): List<VisualFeatureExtractor.Features> = buildList {
        add(pngFeatures)
        record.leftCapture?.toFeatures()?.let { add(it) }
        record.rightCapture?.toFeatures()?.let { add(it) }
    }

    private fun sideInstruction(phase: ParayLearnPhase): String = when (phase) {
        ParayLearnPhase.CAPTURE_LEFT -> "Show Left Side"
        ParayLearnPhase.CAPTURE_RIGHT -> "Show Right Side"
        ParayLearnPhase.CAPTURE_BACK -> "Show Back Side"
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
        const val STABLE_FRAMES_REQUIRED = 4
        const val MISMATCH_FRAME_LIMIT = 45
        const val FRAME_STABILITY_SIMILARITY = 0.92f
    }
}
