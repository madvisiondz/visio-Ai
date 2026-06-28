package com.oasismall.oasisai.domain.paray

import android.graphics.RectF
import com.oasismall.oasisai.util.AtomicJsonWriter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Learns user-adjusted ticket crop regions (normalized 0–1) for faster shelf walks.
 * Stored under `paray_home/workflows/ticket_crop_learn.json`.
 */
class ParayTicketCropLearnStore(private val home: ParayHome) {
    data class NormalizedCrop(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun toRectF() = RectF(left, top, right, bottom)

        companion object {
            fun fromRectF(r: RectF) = NormalizedCrop(
                left = r.left.coerceIn(0f, 1f),
                top = r.top.coerceIn(0f, 1f),
                right = r.right.coerceIn(0f, 1f),
                bottom = r.bottom.coerceIn(0f, 1f),
            )

            /** Default center ticket holder on shelf photos. */
            fun centerDefault() = NormalizedCrop(0.18f, 0.14f, 0.82f, 0.86f)
        }
    }

    fun suggestDefault(): NormalizedCrop {
        val samples = loadSamples()
        if (samples.isEmpty()) return NormalizedCrop.centerDefault()
        val n = samples.size.toFloat()
        return NormalizedCrop(
            left = samples.map { it.left }.sum() / n,
            top = samples.map { it.top }.sum() / n,
            right = samples.map { it.right }.sum() / n,
            bottom = samples.map { it.bottom }.sum() / n,
        )
    }

    fun record(crop: NormalizedCrop) {
        val samples = loadSamples().toMutableList()
        samples.add(crop)
        while (samples.size > MAX_SAMPLES) samples.removeAt(0)
        saveSamples(samples)
    }

    private fun loadSamples(): List<NormalizedCrop> {
        val file = home.ticketCropLearnFile
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NormalizedCrop(
                            left = o.getDouble("l").toFloat(),
                            top = o.getDouble("t").toFloat(),
                            right = o.getDouble("r").toFloat(),
                            bottom = o.getDouble("b").toFloat(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSamples(samples: List<NormalizedCrop>) {
        val arr = JSONArray()
        samples.forEach { c ->
            arr.put(
                JSONObject()
                    .put("l", c.left.toDouble())
                    .put("t", c.top.toDouble())
                    .put("r", c.right.toDouble())
                    .put("b", c.bottom.toDouble()),
            )
        }
        AtomicJsonWriter.writeText(home.ticketCropLearnFile, arr.toString())
    }

    companion object {
        private const val MAX_SAMPLES = 24

        fun cropBitmap(source: android.graphics.Bitmap, norm: NormalizedCrop): android.graphics.Bitmap {
            val w = source.width
            val h = source.height
            val left = (norm.left * w).toInt().coerceIn(0, w - 2)
            val top = (norm.top * h).toInt().coerceIn(0, h - 2)
            val right = (norm.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (norm.bottom * h).toInt().coerceIn(top + 1, h)
            return android.graphics.Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }
    }
}
