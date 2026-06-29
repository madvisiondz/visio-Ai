package com.oasismall.oasisai.domain.backgroundremoval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Offline product background removal (U2NetP TFLite). No cloud APIs.
 */
class BackgroundRemovalService(private val context: Context) {

    private val segmenterMutex = Mutex()
    private var segmenter: SaliencySegmenter? = null

    val originalsDir: File
        get() = File(context.filesDir, "image_originals").also { it.mkdirs() }

    val workDir: File
        get() = File(context.filesDir, "bg_removal_work").also { it.mkdirs() }

    fun isModelReady(): Boolean = OnnxU2NetSegmenter.isModelPresent(context)

    suspend fun removeBackground(
        inputImage: File,
        options: BackgroundRemovalOptions = BackgroundRemovalOptions(),
        onProgress: (String) -> Unit = {},
    ): BackgroundRemovalResult = withContext(Dispatchers.Default) {
        if (!isModelReady()) {
            return@withContext BackgroundRemovalResult(
                originalPath = inputImage.absolutePath,
                success = false,
                errorMessage = "U2NetP cutout model missing from app assets. Run scripts/download-u2netp-tflite.ps1 and rebuild the APK.",
            )
        }
        try {
            onProgress("Loading image…")
            val originalBackup = backupOriginal(inputImage)
            val bitmap = decodeSafely(inputImage, options.maxInputDimension)
                ?: return@withContext fail(originalBackup, "Could not decode image.")

            val cropped = if (options.cropLeft > 0f || options.cropTop > 0f ||
                options.cropRight < 1f || options.cropBottom < 1f
            ) {
                onProgress("Cropping…")
                MaskPostProcessor.cropBitmap(bitmap, options.cropLeft, options.cropTop, options.cropRight, options.cropBottom)
            } else {
                bitmap
            }

            onProgress("Running on-device model…")
            val mask320 = segmenterMutex.withLock {
                val engine = segmenter ?: createSegmenter(context).also { segmenter = it }
                engine.segmentMask320(cropped)
            }

            onProgress("Applying mask…")
            val transparent = MaskPostProcessor.applyMask(
                source = cropped,
                mask320 = mask320,
                maskW = 320,
                maskH = 320,
                threshold = options.maskThreshold,
                edgeSmoothRadius = options.edgeSmoothRadius,
            )

            onProgress("Saving PNG…")
            val outputFile = File(workDir, "cutout_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use { out ->
                transparent.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            transparent.recycle()

            BackgroundRemovalResult(
                originalPath = originalBackup.absolutePath,
                outputPngPath = outputFile.absolutePath,
                success = true,
            )
        } catch (e: Exception) {
            BackgroundRemovalResult(
                originalPath = inputImage.absolutePath,
                success = false,
                errorMessage = "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    suspend fun removeBackground(
        inputUri: Uri,
        options: BackgroundRemovalOptions = BackgroundRemovalOptions(),
        onProgress: (String) -> Unit = {},
    ): BackgroundRemovalResult = withContext(Dispatchers.IO) {
        val temp = File(workDir, "import_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext BackgroundRemovalResult(
            originalPath = "",
            success = false,
            errorMessage = "Could not read image URI.",
        )
        removeBackground(temp, options, onProgress)
    }

    fun close() {
        segmenter?.close()
        segmenter = null
    }

    private fun backupOriginal(input: File): File {
        val originals = originalsDir
        if (input.parentFile?.canonicalPath == originals.canonicalPath) return input
        val ext = input.extension.ifBlank { "jpg" }
        val backup = File(originals, "orig_${System.currentTimeMillis()}.$ext")
        input.copyTo(backup, overwrite = true)
        return backup
    }

    private fun decodeSafely(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun createSegmenter(context: Context): SaliencySegmenter {
        if (!OnnxU2NetSegmenter.isModelPresent(context)) {
            error("No segmentation model in assets")
        }
        return OnnxU2NetSegmenter(context)
    }

    private fun fail(original: File, message: String) = BackgroundRemovalResult(
        originalPath = original.absolutePath,
        success = false,
        errorMessage = message,
    )
}
