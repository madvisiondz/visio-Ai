package com.oasismall.oasisai.domain.bulk

import com.oasismall.oasisai.util.writeTextAtomic

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.oasismall.oasisai.util.PngMetadata
import java.io.File
import java.io.IOException

/**
 * Public [Download/BULK] folder — barcode PNGs + [TRACKER_FILE] log for mall bulk shoots.
 * Uses MediaStore Downloads on API 29+ (DCIM is not allowed for generic files on many devices).
 */
object BulkDownloadStorage {
    const val FOLDER_NAME = "BULK"
    const val TRACKER_FILE = "bulk_done.txt"
    const val DISPLAY_PATH = "Download/BULK"
    private const val RELATIVE_PATH = "Download/BULK/"

    fun bulkDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)

    fun pngFile(barcode: String): File {
        val stem = PngMetadata.barcodeFileStem(barcode.trim())
        return File(bulkDir(), "$stem.png")
    }

    fun trackerFile(): File = File(bulkDir(), TRACKER_FILE)

    fun findPngPath(context: Context, barcode: String): String? {
        val displayName = pngDisplayName(barcode)
        queryMediaStorePath(context, displayName)?.let { return it }
        val file = pngFile(barcode)
        if (file.isFile) return file.absolutePath
        // Legacy v2.5.2 wrote to DCIM/BULK
        val legacy = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "$FOLDER_NAME/$displayName",
        )
        if (legacy.isFile) return legacy.absolutePath
        return null
    }

    fun savePng(context: Context, barcode: String, source: File): String {
        val trimmed = barcode.trim()
        val displayName = pngDisplayName(trimmed)
        deleteMediaStoreEntry(context, displayName)

        val staged = File(context.cacheDir, "bulk_staged_$displayName")
        source.copyTo(staged, overwrite = true)
        PngMetadata.writeArticleDetails(
            file = staged,
            barcode = trimmed,
            designation = trimmed,
            priceNow = null,
            priceBefore = null,
            rayon = null,
            codeart = null,
        )

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeBytesToMediaStore(context, displayName, "image/png", staged.readBytes())
                ?.let { queryMediaStorePath(context, displayName) }
        } else {
            bulkDir().mkdirs()
            val target = pngFile(trimmed)
            staged.copyTo(target, overwrite = true)
            target.absolutePath
        }
        staged.delete()
        return path ?: throw IOException("Could not save PNG to $DISPLAY_PATH")
    }

    fun appendBarcodeToTracker(context: Context, barcode: String, replaced: Boolean) {
        val trimmed = barcode.trim()
        val tag = if (replaced) "replaced" else "new"
        val lines = readTrackerLines(context)
        val updated = lines
            .filterNot { it.substringBefore('\t').trim() == trimmed }
            .toMutableList()
        updated.add("$trimmed\t$tag\t${System.currentTimeMillis()}")
        val text = updated.joinToString("\n") + "\n"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteMediaStoreEntry(context, TRACKER_FILE)
            writeBytesToMediaStore(context, TRACKER_FILE, "text/plain", text.toByteArray())
        } else {
            bulkDir().mkdirs()
            trackerFile().writeTextAtomic(text)
        }
    }

    private fun readTrackerLines(context: Context): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = queryMediaStoreUri(context, TRACKER_FILE) ?: return emptyList()
            return context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readLines().filter { it.isNotBlank() }
            } ?: emptyList()
        }
        val file = trackerFile()
        if (!file.isFile) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    private fun pngDisplayName(barcode: String): String =
        "${PngMetadata.barcodeFileStem(barcode.trim())}.png"

    private fun downloadsCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }

    private fun writeBytesToMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
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

    private fun deleteMediaStoreEntry(context: Context, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File(bulkDir(), displayName).takeIf { it.exists() }?.delete()
            return
        }
        context.contentResolver.delete(
            downloadsCollection(),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, RELATIVE_PATH),
        )
    }

    private fun queryMediaStoreUri(context: Context, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        context.contentResolver.query(
            downloadsCollection(),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, RELATIVE_PATH),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ContentUris.withAppendedId(downloadsCollection(), cursor.getLong(0))
        }
        return null
    }

    private fun queryMediaStorePath(context: Context, displayName: String): String? {
        val uri = queryMediaStoreUri(context, displayName) ?: return null
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
        return File(bulkDir(), displayName).absolutePath
    }
}
