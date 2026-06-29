package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import java.io.File

/** On-device VisioPRO template images (product PNGs, overlays) — not shipped in the lean APK. */
object VisioProMediaStore {
    private const val ROOT = "visiopro_media"

    fun rootDir(context: Context): File =
        File(context.filesDir, ROOT).also { it.mkdirs() }

    fun mediaFile(context: Context, assetPath: String): File =
        File(rootDir(context), assetPath.replace('\\', '/'))

    fun isInstalled(context: Context): Boolean {
        val products = mediaFile(context, "visiopro/fv_print/products")
        return products.isDirectory && (products.list()?.isNotEmpty() == true)
    }
}
