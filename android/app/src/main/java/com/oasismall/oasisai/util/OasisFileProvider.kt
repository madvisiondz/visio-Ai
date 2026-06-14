package com.oasismall.oasisai.util

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * General FileProvider for PDFs and in-app files. Share-export URIs also return
 * document MIME when served through this authority (backup path).
 */
class OasisFileProvider : FileProvider() {

    override fun getType(uri: Uri): String? {
        if (isShareExportUri(uri)) {
            return OasisShareFileProvider.MIME_SHARE_DOCUMENT
        }
        return super.getType(uri)
    }

    private fun isShareExportUri(uri: Uri): Boolean {
        val encoded = uri.encodedPath?.lowercase().orEmpty()
        return encoded.contains("share-export") ||
            encoded.contains("share_export") ||
            encoded.contains("share-export%2f") ||
            encoded.contains("share_export%2f")
    }
}
