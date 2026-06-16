package com.oasismall.oasisai.domain.visio

import android.content.Context
import android.os.Environment
import com.oasismall.oasisai.util.PngMetadata
import java.io.File

/** Reads cutout PNGs saved by PhotoRoom under Pictures/Photoroom/. */
object PhotoroomStorage {
    const val DISPLAY_PATH = "Pictures/Photoroom"
    private val FOLDER_CANDIDATES = listOf("Photoroom", "PhotoRoom", "photoroom")

    fun picturesDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun resolveFolder(): File? =
        FOLDER_CANDIDATES
            .map { File(picturesDir(), it) }
            .firstOrNull { it.isDirectory }

    fun listPngFiles(): List<File> {
        val dir = resolveFolder() ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.lowercase() == "png" }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    fun findPngForBarcode(barcode: String): File? {
        val stem = PngMetadata.barcodeFileStem(barcode.trim())
        return listPngFiles().firstOrNull { file ->
            val name = file.nameWithoutExtension
            name == stem ||
                name.endsWith("_$stem") ||
                name.contains(stem)
        }
    }
}
