package com.oasismall.oasisai.domain.visiopro

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class VisioProExporter(private val context: Context) {

    suspend fun exportToGallery(
        bitmap: Bitmap,
        preset: VisioProPreset,
        channel: VisioProChannel,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = when (channel) {
                VisioProChannel.SOCIAL -> "DCIM/VisioPRO/Social"
                VisioProChannel.PRINT -> "DCIM/VisioPRO/Print"
            }
            val safeName = preset.article.labelFr
                .replace(Regex("[^A-Za-z0-9À-ÿ]+"), "_")
                .trim('_')
            val fileName = "VisioPRO_${preset.category.name}_${safeName}_${System.currentTimeMillis()}.png"
            savePng(bitmap, fileName, folder)
        }
    }

    suspend fun exportA4QuadToGallery(bitmap: Bitmap, label: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = label.replace(Regex("[^A-Za-z0-9À-ÿ]+"), "_").trim('_')
                val fileName = "VisioPRO_A4x4_${safeName}_${System.currentTimeMillis()}.png"
                savePng(bitmap, fileName, "DCIM/VisioPRO/Print")
            }
        }

    private fun savePng(bitmap: Bitmap, fileName: String, folder: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("Impossible d'enregistrer dans la galerie")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writePng(bitmap, out)
        } ?: error("Impossible d'écrire l'image")
        return fileName
    }

    private fun writePng(bitmap: Bitmap, out: OutputStream) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            error("Échec compression PNG")
        }
    }
}
