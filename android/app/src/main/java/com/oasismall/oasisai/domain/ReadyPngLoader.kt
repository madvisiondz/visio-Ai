package com.oasismall.oasisai.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.oasismall.oasisai.util.TaskProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

data class ReadyPngLoadResult(
    val copied: Int,
    val skipped: Int,
    val alreadyPresent: Int,
    val oasisModelCount: Int = 0,
    val incompleteTags: Int = 0,
    val limitedByPicker: Boolean = false,
)

class ReadyPngLoader(
    private val imageMatcher: ImageMatcher,
) {
    companion object {
        const val MAX_FILES_PER_PICK = 500
        const val MIN_FREE_BYTES = 512L * 1024L * 1024L // 512 MB headroom
    }

    suspend fun loadFromUris(
        context: Context,
        uris: List<Uri>,
        onProgress: (TaskProgress) -> Unit,
    ): ReadyPngLoadResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ReadyPngLoadResult(0, 0, 0)
        val limitedByPicker = uris.size > MAX_FILES_PER_PICK
        val limited = if (limitedByPicker) {
            onProgress(TaskProgress("Limiting to $MAX_FILES_PER_PICK of ${uris.size} files", 2))
            uris.take(MAX_FILES_PER_PICK)
        } else {
            uris
        }
        copyPngs(context, limited, onProgress).copy(limitedByPicker = limitedByPicker)
    }

    suspend fun loadFromFolderTree(
        context: Context,
        treeUri: Uri,
        onProgress: (TaskProgress) -> Unit,
    ): ReadyPngLoadResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ReadyPngLoadResult(0, 0, 0)
        val uris = mutableListOf<Uri>()
        val names = mutableListOf<String?>()
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)
        var scanned = 0
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val node = pending.removeFirst()
            if (node.isDirectory) {
                node.listFiles()?.forEach { pending.add(it) }
                continue
            }
            if (!node.isFile || node.name?.endsWith(".png", ignoreCase = true) != true) continue
            uris.add(node.uri)
            names.add(node.name)
            scanned++
            if (scanned % 100 == 0) {
                yield()
                onProgress(TaskProgress("Found $scanned PNGs in folder", (scanned % 50).coerceAtMost(40)))
            }
        }
        if (uris.isEmpty()) return@withContext ReadyPngLoadResult(0, 0, 0)
        val totalBatches = (uris.size + MAX_FILES_PER_PICK - 1) / MAX_FILES_PER_PICK
        var copied = 0
        var skipped = 0
        var alreadyPresent = 0
        uris.chunked(MAX_FILES_PER_PICK).forEachIndexed { batchIndex, chunk ->
            coroutineContext.ensureActive()
            val batchNames = names.drop(batchIndex * MAX_FILES_PER_PICK).take(chunk.size)
            val batchStart = 40 + (batchIndex * 30 / totalBatches)
            onProgress(
                TaskProgress(
                    "Processing folder batch ${batchIndex + 1}/$totalBatches (${chunk.size} PNGs)",
                    batchStart,
                ),
            )
            val batch = copyPngsWithNames(context, chunk, batchNames, onProgress)
            copied += batch.copied
            skipped += batch.skipped
            alreadyPresent += batch.alreadyPresent
        }
        ReadyPngLoadResult(copied = copied, skipped = skipped, alreadyPresent = alreadyPresent)
    }

    private suspend fun copyPngs(
        context: Context,
        uris: List<Uri>,
        onProgress: (TaskProgress) -> Unit,
    ): ReadyPngLoadResult {
        val names = uris.map { null as String? }
        return copyPngsWithNames(context, uris, names, onProgress)
    }

    private suspend fun copyPngsWithNames(
        context: Context,
        uris: List<Uri>,
        names: List<String?>,
        onProgress: (TaskProgress) -> Unit,
    ): ReadyPngLoadResult {
        val targetDir = imageMatcher.getImagesDirectory()
        ensureFreeSpace(targetDir, onProgress)
        var copied = 0
        var skipped = 0
        var alreadyPresent = 0
        val total = uris.size.coerceAtLeast(1)
        uris.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            if (index % 10 == 0) yield()
            onProgress(TaskProgress("Copying PNG ${index + 1}/$total", 5 + (index * 60 / total)))
            when (copyOne(context.contentResolver, uri, targetDir, names.getOrNull(index))) {
                CopyOutcome.COPIED -> copied++
                CopyOutcome.ALREADY_PRESENT -> alreadyPresent++
                CopyOutcome.SKIPPED -> skipped++
            }
        }
        onProgress(TaskProgress("Copy done — matching articles next", 68))
        return ReadyPngLoadResult(
            copied = copied,
            skipped = skipped,
            alreadyPresent = alreadyPresent,
        )
    }

    private fun ensureFreeSpace(targetDir: File, onProgress: (TaskProgress) -> Unit) {
        val stat = StatFs(targetDir.absolutePath)
        val free = stat.availableBytes
        if (free < MIN_FREE_BYTES) {
            onProgress(
                TaskProgress(
                    "Low storage (${free / (1024 * 1024)} MB free) — copy may fail",
                    3,
                ),
            )
        }
    }

    private enum class CopyOutcome { COPIED, SKIPPED, ALREADY_PRESENT }

    private fun copyOne(
        resolver: ContentResolver,
        uri: Uri,
        targetDir: File,
        preferredName: String? = null,
    ): CopyOutcome {
        val rawName = preferredName ?: displayName(resolver, uri) ?: return CopyOutcome.SKIPPED
        val fileName = sanitizeFileName(rawName)
        if (!fileName.endsWith(".png", ignoreCase = true)) return CopyOutcome.SKIPPED
        val target = File(targetDir, fileName)
        if (target.exists() && target.length() > 0L) {
            return CopyOutcome.ALREADY_PRESENT
        }
        return try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                CopyOutcome.COPIED
            } ?: CopyOutcome.SKIPPED
        } catch (_: Exception) {
            target.delete()
            CopyOutcome.SKIPPED
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("""[<>:"|?*]"""), "_").trim()
        return cleaned.ifBlank { "image_${System.currentTimeMillis()}.png" }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }
}
