package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.domain.visiopro.VisioProMediaStore
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VisioProMediaImportResult(val fileCount: Int)

/** Installs `VisioPRO-media.zip` (repo script) into filesDir/visiopro_media/. */
class VisioProMediaImporter(private val context: Context) {
    suspend fun importFromUri(
        uri: Uri,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): VisioProMediaImportResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(TaskProgress("Reading VisioPRO media pack", 5))
        val zipFile = File(context.cacheDir, "import_visiopro_media.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read VisioPRO media pack")

        val extractDir = File(context.cacheDir, "import_visiopro_media_extract").apply {
            deleteRecursively()
            mkdirs()
        }
        onProgress?.invoke(TaskProgress("Extracting images", 15))
        ZipArchive.unzip(zipFile, extractDir) { progress ->
            val shifted = 15 + (progress.normalizedPercent * 70 / 100)
            onProgress?.invoke(progress.copy(percent = shifted))
        }
        zipFile.delete()

        val destRoot = VisioProMediaStore.rootDir(context)
        onProgress?.invoke(TaskProgress("Installing to device", 88))
        val visioproSrc = File(extractDir, "visiopro")
        val count = if (visioproSrc.isDirectory) {
            DeviceBackupExporter.copyDirectory(visioproSrc, File(destRoot, "visiopro"))
        } else {
            DeviceBackupExporter.copyDirectory(extractDir, destRoot)
        }
        extractDir.deleteRecursively()
        onProgress?.invoke(TaskProgress("VisioPRO media ready", 100))
        VisioProMediaImportResult(fileCount = count)
    }
}
