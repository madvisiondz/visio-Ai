package com.oasismall.oasisai.domain.backgroundremoval

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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

/** U2NetP TFLite fallback when a valid .tflite is present in assets. */
internal class TfliteU2NetSegmenter(context: Context) : SaliencySegmenter {
    private val interpreter: Interpreter
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputChannels: Int
    private val outputShape: IntArray

    init {
        val assetName = MODEL_ASSETS.first { isModelPresent(context, it) }
        val model = loadModelFile(context, assetName)
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(model, options)
        val shape = interpreter.getInputTensor(0).shape()
        inputHeight = shape[1]
        inputWidth = shape[2]
        inputChannels = shape[3]
        outputShape = interpreter.getOutputTensor(0).shape()
    }

    override fun segmentMask320(source: Bitmap): FloatArray {
        val inputBuffer = preprocess(source, inputWidth, inputHeight)
        val output = Array(1) { Array(outputShape[1]) { Array(outputShape[2]) { FloatArray(outputShape[3]) } } }
        interpreter.run(inputBuffer, output)
        return flattenMask(output, outputShape)
    }

    override fun close() {
        interpreter.close()
    }

    private fun preprocess(bitmap: Bitmap, w: Int, h: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val buffer = ByteBuffer.allocateDirect(4 * w * h * inputChannels).order(ByteOrder.nativeOrder())
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            buffer.putFloat((r - MEAN[0]) / STD[0])
            buffer.putFloat((g - MEAN[1]) / STD[1])
            buffer.putFloat((b - MEAN[2]) / STD[2])
        }
        buffer.rewind()
        return buffer
    }

    private fun flattenMask(output: Array<Array<Array<FloatArray>>>, shape: IntArray): FloatArray {
        val h = if (shape.size == 4 && shape[1] == 1) shape[2] else shape[1]
        val w = if (shape.size == 4 && shape[1] == 1) shape[3] else shape[2]
        val mask = FloatArray(w * h)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = if (shape.size == 4 && shape[1] == 1) output[0][0][y][x] else output[0][y][x][0]
                mask[i++] = (1f / (1f + kotlin.math.exp(-v))).coerceIn(0f, 1f)
            }
        }
        return mask
    }

    companion object {
        private val MODEL_ASSETS = listOf("u2netp_320x320_float32.tflite", "u2netp.tflite")
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun isModelPresent(context: Context): Boolean =
            MODEL_ASSETS.any { isModelPresent(context, it) }

        private fun isModelPresent(context: Context, name: String): Boolean =
            runCatching {
                context.assets.openFd(name).use { it.declaredLength > 1_000_000L }
            }.getOrDefault(false)

        private fun loadModelFile(context: android.content.Context, assetName: String): MappedByteBuffer {
            val afd = context.assets.openFd(assetName)
            FileInputStream(afd.fileDescriptor).use { stream ->
                return stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength,
                )
            }
        }
    }
}
