package com.oasismall.oasisai.ui.screens.checkshoot

import android.graphics.Bitmap
import com.oasismall.oasisai.domain.paray.ParayTicketBitmapUtils
import com.oasismall.oasisai.domain.paray.ParayTicketFrameQuality
import com.oasismall.oasisai.domain.paray.ParayTicketImagePrep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ring buffer of upright camera frames — tap uses the freshest frame (WYSIWYG).
 */
class TicketCameraBuffer {
    private val lock = Any()
    private val ring = ArrayDeque<Frame>()

    private val _liveQuality = MutableStateFlow(0f)
    val liveQuality: StateFlow<Float> = _liveQuality.asStateFlow()

    private val _liveQualityLabel = MutableStateFlow(ParayTicketFrameQuality.labelFor(0f))
    val liveQualityLabel: StateFlow<String> = _liveQualityLabel.asStateFlow()

    data class Frame(
        val bitmap: Bitmap,
        val rotationDegrees: Int,
        val capturedAtMs: Long,
        val qualityScore: Float,
        val qualityReport: ParayTicketFrameQuality.Report,
    )

    val hasFreshFrame: Boolean
        get() = synchronized(lock) {
            freshFrames().isNotEmpty()
        }

    fun offer(source: Bitmap, rotationDegrees: Int) {
        val now = System.currentTimeMillis()
        val upright = ParayTicketImagePrep.rotateToUpright(source, rotationDegrees)
        if (upright !== source) source.recycle()
        val scaled = ParayTicketBitmapUtils.forCameraBuffer(upright)
        if (scaled !== upright) upright.recycle()
        val report = ParayTicketFrameQuality.evaluate(scaled)
        synchronized(lock) {
        if (ring.isNotEmpty() && now - ring.last().capturedAtMs < MIN_OFFER_INTERVAL_MS) {
            scaled.recycle()
            return
        }
            ring.addLast(Frame(scaled, rotationDegrees = 0, now, report.composite, report))
            while (ring.size > RING_CAPACITY) {
                ring.removeFirst().bitmap.recycle()
            }
            _liveQuality.value = report.composite
            _liveQualityLabel.value = report.label
        }
    }

    /** Freshest frame — matches what the user just saw when tapping. */
    fun takeLatestSnap(): Frame? = synchronized(lock) {
        val latest = freshFrames().maxByOrNull { it.capturedAtMs } ?: return null
        copyFrame(latest)
    }

    fun takeBestSnap(): Frame? = takeLatestSnap()

    fun takeAlternateSnap(excludeCapturedAtMs: Long): Frame? = synchronized(lock) {
        val alt = freshFrames()
            .filter { it.capturedAtMs != excludeCapturedAtMs }
            .maxByOrNull { it.qualityScore }
            ?: return null
        copyFrame(alt)
    }

    fun clear() {
        synchronized(lock) {
            ring.forEach { it.bitmap.recycle() }
            ring.clear()
            _liveQuality.value = 0f
            _liveQualityLabel.value = ParayTicketFrameQuality.labelFor(0f)
        }
    }

    private fun freshFrames(): List<Frame> {
        val now = System.currentTimeMillis()
        return ring.filter { now - it.capturedAtMs <= MAX_FRAME_AGE_MS }
    }

    private fun copyFrame(frame: Frame): Frame {
        val copy = frame.bitmap.copy(frame.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        return frame.copy(bitmap = copy)
    }

    companion object {
        private const val RING_CAPACITY = 4
        private const val MAX_FRAME_AGE_MS = 1_500L
        private const val MIN_OFFER_INTERVAL_MS = 80L
    }
}
