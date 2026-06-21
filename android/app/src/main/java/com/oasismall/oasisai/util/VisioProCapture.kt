package com.oasismall.oasisai.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createVisioProCaptureUri(context: Context): Pair<Uri, File>? =
    runCatching {
        val file = File(context.cacheDir, "visio_pro_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        uri to file
    }.getOrNull()
