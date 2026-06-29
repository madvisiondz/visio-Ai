package com.oasismall.oasisai.domain.backgroundremoval

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

internal interface SaliencySegmenter : AutoCloseable {
    fun segmentMask320(source: Bitmap): FloatArray
}

/** U2NetP via ONNX Runtime (bundled rembg u2netp.onnx, 320×320). */
internal class OnnxU2NetSegmenter(context: Context) : SaliencySegmenter {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).readBytes()
        session = env.createSession(
            modelBytes,
            OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) },
        )
        inputName = session.inputNames.first()
    }

    override fun segmentMask320(source: Bitmap): FloatArray {
        val w = INPUT_SIZE
        val h = INPUT_SIZE
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val chw = FloatArray(3 * w * h)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()

        var maxPixel = 1e-6f
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            maxPixel = maxOf(maxPixel, r.toFloat(), g.toFloat(), b.toFloat())
        }

        var offset = 0
        for (c in 0 until 3) {
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / maxPixel
                val g = ((pixel shr 8) and 0xFF) / maxPixel
                val b = (pixel and 0xFF) / maxPixel
                val v = when (c) {
                    0 -> r
                    1 -> g
                    else -> b
                }
                chw[offset++] = (v - MEAN[c]) / STD[c]
            }
        }

        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, h.toLong(), w.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { outputs ->
                val tensor = outputs.get(0) as OnnxTensor
                val shape = tensor.info.shape
                val outH = shape[shape.size - 2].toInt()
                val outW = shape[shape.size - 1].toInt()
                val buffer = tensor.floatBuffer
                val count = outH * outW
                val raw = FloatArray(count)
                for (i in 0 until count) {
                    raw[i] = buffer.get(i)
                }
                var min = Float.MAX_VALUE
                var max = -Float.MAX_VALUE
                for (v in raw) {
                    if (v < min) min = v
                    if (v > max) max = v
                }
                val range = (max - min).coerceAtLeast(1e-6f)
                val mask = FloatArray(count)
                for (i in 0 until count) {
                    mask[i] = ((raw[i] - min) / range).coerceIn(0f, 1f)
                }
                return mask
            }
        }
    }

    override fun close() {
        session.close()
        env.close()
    }

    companion object {
        const val MODEL_ASSET = "u2netp.onnx"
        private const val INPUT_SIZE = 320
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun isModelPresent(context: Context): Boolean =
            runCatching {
                context.assets.openFd(MODEL_ASSET).use { it.declaredLength > 1_000_000L }
            }.getOrDefault(false)

    }
}
