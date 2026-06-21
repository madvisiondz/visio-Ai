package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VisioProPhotoStore(context: Context) {

    private val dir = File(context.filesDir, "visio_pro_photos").also { it.mkdirs() }

    fun photoFile(slug: String): File = File(dir, "$slug.jpg")

    fun hasPhoto(slug: String): Boolean = photoFile(slug).let { it.exists() && it.length() > 0L }

    fun photoModifiedAt(slug: String): Long? =
        photoFile(slug).takeIf { hasPhoto(slug) }?.lastModified()

    suspend fun loadBitmap(slug: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = photoFile(slug)
        if (!file.exists()) return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    suspend fun saveFromFile(slug: String, source: File) = withContext(Dispatchers.IO) {
        val dest = photoFile(slug)
        source.copyTo(dest, overwrite = true)
        dest.lastModified()
    }
}
