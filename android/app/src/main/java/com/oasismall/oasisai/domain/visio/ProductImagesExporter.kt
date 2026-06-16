package com.oasismall.oasisai.domain.visio

import android.content.Context
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.util.TaskProgress
import java.io.File

data class ProductImagesExportResult(
    val folderName: String,
    val copied: Int,
    val skipped: Int,
    val displayPath: String,
)

class ProductImagesExporter(
    private val context: Context,
    private val imageMatcher: ImageMatcher,
) {
    suspend fun export(onProgress: (TaskProgress) -> Unit): ProductImagesExportResult {
        val folderName = VisioDownloadStorage.productExportFolderName()
        val sources = imageMatcher.getImagesDirectory().listFiles { f -> f.isFile && f.extension.lowercase() == "png" }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        if (sources.isEmpty()) {
            return ProductImagesExportResult(
                folderName = folderName,
                copied = 0,
                skipped = 0,
                displayPath = VisioDownloadStorage.displayPath(folderName),
            )
        }
        var copied = 0
        var skipped = 0
        sources.forEachIndexed { index, source ->
            val pct = ((index + 1) * 100) / sources.size
            onProgress(TaskProgress("Exporting ${source.name}", pct))
            val targetName = source.name
            val existing = VisioDownloadStorage.listFilesInFolder(context, folderName, "png")
                .any { it.displayName == targetName }
            if (existing) {
                skipped++
            } else {
                VisioDownloadStorage.copyFileToFolder(
                    context = context,
                    folderName = folderName,
                    displayName = targetName,
                    mimeType = "image/png",
                    source = source,
                )
                copied++
            }
        }
        onProgress(TaskProgress("Export complete", 100))
        return ProductImagesExportResult(
            folderName = folderName,
            copied = copied,
            skipped = skipped,
            displayPath = VisioDownloadStorage.displayPath(folderName),
        )
    }
}
