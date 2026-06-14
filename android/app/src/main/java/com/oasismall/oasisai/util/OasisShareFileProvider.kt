package com.oasismall.oasisai.util

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider

/**
 * Dedicated provider for outbound shares. Telegram calls [getType] on each URI;
 * default FileProvider returns `image/png` for `.png`, which forces photo compression.
 *
 * Files are stored as `.oasis` on disk; [OpenableColumns.DISPLAY_NAME] is the real `.png` name.
 */
class OasisShareFileProvider : FileProvider() {

    override fun getType(uri: Uri): String = MIME_SHARE_DOCUMENT

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val base = super.query(uri, projection, selection, selectionArgs, sortOrder)
        val cols = projection ?: base.columnNames
        val out = MatrixCursor(cols)
        val displayIdx = base.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = base.getColumnIndex(OpenableColumns.SIZE)
        while (base.moveToNext()) {
            val row = arrayOfNulls<Any?>(cols.size)
            for (i in cols.indices) {
                row[i] = when (cols[i]) {
                    OpenableColumns.DISPLAY_NAME -> {
                        val raw = if (displayIdx >= 0) base.getString(displayIdx) else null
                        toShareDisplayName(raw ?: uri.lastPathSegment.orEmpty())
                    }
                    OpenableColumns.SIZE -> if (sizeIdx >= 0) base.getLong(sizeIdx) else null
                    else -> {
                        val idx = base.getColumnIndex(cols[i])
                        if (idx >= 0) base.getString(idx) else null
                    }
                }
            }
            out.addRow(row)
        }
        base.close()
        return out
    }

    companion object {
        const val AUTHORITY_SUFFIX = "shareprovider"
        const val MIME_SHARE_DOCUMENT = "application/octet-stream"
        const val SHARE_FILE_EXTENSION = ".oasis"

        fun authority(packageName: String): String = "$packageName.$AUTHORITY_SUFFIX"

        fun toShareDisplayName(fileName: String): String {
            val name = fileName.substringAfterLast('/')
            if (!name.endsWith(SHARE_FILE_EXTENSION, ignoreCase = true)) return name
            val stem = name.dropLast(SHARE_FILE_EXTENSION.length)
            return when {
                stem.endsWith(".jpg", ignoreCase = true) -> stem
                stem.endsWith(".jpeg", ignoreCase = true) -> stem
                stem.endsWith(".png", ignoreCase = true) -> stem
                else -> stem + ".png"
            }
        }

        /** @deprecated Use [toShareDisplayName] */
        fun toPngDisplayName(fileName: String): String = toShareDisplayName(fileName)
    }
}
