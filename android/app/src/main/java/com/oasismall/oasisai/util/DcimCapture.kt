package com.oasismall.oasisai.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

fun createDcimCaptureUri(context: Context): Uri? {
    val resolver = context.contentResolver
    val fileName = "OASIS_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_DCIM}/OasisAI",
            )
        }
    }
    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}
