package com.oasismall.oasisai.domain.visio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Public [Download/VisioAi/…] folders for exports and camera-batch shoots.
 */
object VisioDownloadStorage {
    const val ROOT_FOLDER = "VisioAi"
    const val DISPLAY_ROOT = "Download/VisioAi"
    private const val RELATIVE_ROOT = "Download/VisioAi/"

    fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun batchFolderName(date: String = todayDate()): String = "Batch_images$date"

    fun productExportFolderName(date: String = todayDate()): String = "Product_images$date"

    fun relativePath(folderName: String): String = "$RELATIVE_ROOT$folderName/"

    fun displayPath(folderName: String): String = "$DISPLAY_ROOT/$folderName"

    fun legacyDir(folderName: String): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$ROOT_FOLDER${File.separator}$folderName",
        )

    fun saveBytes(
        context: Context,
        folderName: String,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        deleteMediaStoreEntry(context, folderName, displayName)
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeBytesToMediaStore(context, folderName, displayName, mimeType, bytes)
                ?.let { queryMediaStorePath(context, folderName, displayName) }
        } else {
            val dir = legacyDir(folderName)
            dir.mkdirs()
            val target = File(dir, displayName)
            target.writeBytes(bytes)
            target.absolutePath
        }
        return path ?: throw IOException("Could not save to ${displayPath(folderName)}/$displayName")
    }

    fun copyFileToFolder(
        context: Context,
        folderName: String,
        displayName: String,
        mimeType: String,
        source: File,
    ): String = saveBytes(context, folderName, displayName, mimeType, source.readBytes())

    fun listFilesInFolder(context: Context, folderName: String, extension: String): List<VisioDownloadFile> {
        val ext = extension.lowercase().removePrefix(".")
        val out = mutableListOf<VisioDownloadFile>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                downloadsCollection(),
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath(folderName)),
                "${MediaStore.MediaColumns.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (!name.lowercase().endsWith(".$ext")) continue
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    out += VisioDownloadFile(name, path ?: legacyDir(folderName).resolve(name).absolutePath)
                }
            }
        }
        legacyDir(folderName).listFiles()?.forEach { file ->
            if (file.isFile && file.extension.lowercase() == ext) {
                if (out.none { it.displayName == file.name }) {
                    out += VisioDownloadFile(file.name, file.absolutePath)
                }
            }
        }
        return out.sortedBy { it.displayName.lowercase() }
    }

    private fun downloadsCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }

    private fun writeBytesToMediaStore(
        context: Context,
        folderName: String,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folderName))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(downloadsCollection(), values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        return uri
    }

    private fun deleteMediaStoreEntry(context: Context, folderName: String, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            legacyDir(folderName).resolve(displayName).takeIf { it.exists() }?.delete()
            return
        }
        context.contentResolver.delete(
            downloadsCollection(),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, relativePath(folderName)),
        )
    }

    private fun queryMediaStorePath(context: Context, folderName: String, displayName: String): String? {
        val uri = queryMediaStoreUri(context, folderName, displayName) ?: return null
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) return path
                    }
                }
            }
        return legacyDir(folderName).resolve(displayName).absolutePath
    }

    private fun queryMediaStoreUri(context: Context, folderName: String, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        context.contentResolver.query(
            downloadsCollection(),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, relativePath(folderName)),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ContentUris.withAppendedId(downloadsCollection(), cursor.getLong(0))
        }
        return null
    }
}

data class VisioDownloadFile(
    val displayName: String,
    val absolutePath: String,
)
