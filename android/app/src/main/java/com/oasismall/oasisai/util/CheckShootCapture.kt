package com.oasismall.oasisai.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** In-app camera target (not DCIM) for Scan & shoot → Create asset flow. */
fun createCheckShootCaptureUri(context: Context): Pair<Uri, File>? {
    return runCatching {
        val file = File(context.cacheDir, "check_shoot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        uri to file
    }.getOrNull()
}
