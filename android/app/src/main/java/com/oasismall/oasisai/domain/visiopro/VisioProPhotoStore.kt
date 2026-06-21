package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VisioProPhotoStore(context: Context) {

    private val dir = File(context.filesDir, "visio_pro_photos").also { it.mkdirs() }

    fun photoFile(slug: String, channel: VisioProChannel): File {
        val channelDir = File(dir, channel.name.lowercase()).also { it.mkdirs() }
        return File(channelDir, "$slug.jpg")
    }

    /** Pre-v2.14 single-file photos — treated as social only. */
    private fun legacyPhotoFile(slug: String): File = File(dir, "$slug.jpg")

    fun hasPhoto(slug: String, channel: VisioProChannel): Boolean {
        val channelFile = photoFile(slug, channel)
        if (channelFile.exists() && channelFile.length() > 0L) return true
        return channel == VisioProChannel.SOCIAL &&
            legacyPhotoFile(slug).let { it.exists() && it.length() > 0L }
    }

    fun photoModifiedAt(slug: String, channel: VisioProChannel): Long? {
        val channelFile = photoFile(slug, channel)
        if (channelFile.exists() && channelFile.length() > 0L) return channelFile.lastModified()
        if (channel == VisioProChannel.SOCIAL) {
            return legacyPhotoFile(slug).takeIf { it.exists() && it.length() > 0L }?.lastModified()
        }
        return null
    }

    suspend fun loadBitmap(slug: String, channel: VisioProChannel): Bitmap? = withContext(Dispatchers.IO) {
        val file = resolvePhotoFile(slug, channel) ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    suspend fun saveFromFile(slug: String, channel: VisioProChannel, source: File) = withContext(Dispatchers.IO) {
        val dest = photoFile(slug, channel)
        source.copyTo(dest, overwrite = true)
        dest.lastModified()
    }

    private fun resolvePhotoFile(slug: String, channel: VisioProChannel): File? {
        val channelFile = photoFile(slug, channel)
        if (channelFile.exists() && channelFile.length() > 0L) return channelFile
        if (channel == VisioProChannel.SOCIAL) {
            val legacy = legacyPhotoFile(slug)
            if (legacy.exists() && legacy.length() > 0L) return legacy
        }
        return null
    }
}
