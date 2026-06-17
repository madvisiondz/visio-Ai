package com.oasismall.oasisai.domain.visio

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.oasismall.oasisai.util.NameNormalizer
import com.oasismall.oasisai.util.PngMetadata
import java.io.File

/** Reads cutout PNGs from a user-selected folder or default Pictures/Photoroom/. */
object PhotoroomStorage {
    const val DEFAULT_DISPLAY_PATH = "Pictures/Photoroom"
    private const val PREFS = "visio_photoroom"
    private const val KEY_FOLDER_URI = "folder_tree_uri"
    private const val KEY_FOLDER_LABEL = "folder_label"
    private val FOLDER_CANDIDATES = listOf("Photoroom", "PhotoRoom", "photoroom")

    @Volatile
    private var cachedIndex: PhotoroomIndex? = null

    data class PngRef(
        val name: String,
        val pathLabel: String,
        private val legacyFile: File?,
        val documentUri: Uri?,
    ) {
        fun previewSource(): Any = legacyFile ?: documentUri ?: name

        fun legacyFileOrNull(): File? = legacyFile

        fun asLocalFile(context: Context): File {
            legacyFile?.let { return it }
            val uri = documentUri ?: error("No PNG source for $name")
            val cache = File(context.cacheDir, "photoroom_import/${name}")
            if (cache.exists() && cache.length() > 0L) return cache
            cache.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read $name from PhotoRoom folder")
            return cache
        }
    }

    private data class PhotoroomIndex(
        val files: List<PngRef>,
        private val byBarcodeStem: Map<String, PngRef>,
        private val byDesignationStem: Map<String, PngRef>,
    ) {
        fun find(barcode: String, designation: String? = null): PngRef? {
            val trimmed = barcode.trim()
            if (trimmed.isEmpty()) return null
            val stem = PngMetadata.barcodeFileStem(trimmed)
            byBarcodeStem[stem]?.let { return it }
            files.firstOrNull { ref ->
                val name = ref.name.substringBeforeLast('.').lowercase()
                name == stem.lowercase() ||
                    name.endsWith("_$stem") ||
                    name.endsWith(stem) ||
                    name.contains(stem)
            }?.let { return it }
            designation?.trim()?.takeIf { it.isNotBlank() }?.let { des ->
                val dStem = NameNormalizer.toDisplayFileStem(des).lowercase()
                if (dStem.isNotBlank()) {
                    byDesignationStem[dStem]?.let { return it }
                    files.firstOrNull { ref ->
                        ref.name.substringBeforeLast('.').lowercase().contains(dStem)
                    }?.let { return it }
                }
            }
            return files.asSequence()
                .filter { ref ->
                    val name = ref.name.lowercase()
                    name.contains(stem.takeLast(8).lowercase())
                }
                .firstOrNull { ref ->
                    val file = ref.legacyFileOrNull() ?: return@firstOrNull false
                    PngMetadata.readArticleDetails(file).barcode?.trim() == trimmed
                }
        }
    }

    fun invalidateCache() {
        cachedIndex = null
    }

    fun saveFolderTree(context: Context, treeUri: Uri): String {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        val label = doc?.name?.trim().orEmpty().ifBlank { treeUri.lastPathSegment.orEmpty() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, treeUri.toString())
            .putString(KEY_FOLDER_LABEL, label)
            .apply()
        invalidateCache()
        return label.ifBlank { "Selected folder" }
    }

    fun clearFolderTree(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_LABEL)
            .apply()
        invalidateCache()
    }

    fun folderTreeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
            ?.let(Uri::parse)

    fun displayPath(context: Context): String {
        val custom = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_LABEL, null)
            ?.trim()
            .orEmpty()
        return custom.ifBlank { DEFAULT_DISPLAY_PATH }
    }

    fun isCustomFolder(context: Context): Boolean = folderTreeUri(context) != null

    fun picturesDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun resolveLegacyFolder(): File? =
        FOLDER_CANDIDATES
            .map { File(picturesDir(), it) }
            .firstOrNull { it.isDirectory }

    fun listPngFiles(context: Context): List<PngRef> = index(context).files

    fun findPngForBarcode(context: Context, barcode: String, designation: String? = null): PngRef? =
        index(context).find(barcode, designation)

    private fun index(context: Context): PhotoroomIndex {
        cachedIndex?.let { return it }
        val files = loadAllPngRefs(context)
        val byBarcode = mutableMapOf<String, PngRef>()
        val byDesignation = mutableMapOf<String, PngRef>()
        files.forEach { ref ->
            val stem = ref.name.substringBeforeLast('.')
            PngMetadata.extractBarcodeFromFilename(stem)?.let { bc ->
                byBarcode.putIfAbsent(PngMetadata.barcodeFileStem(bc), ref)
            }
            val file = ref.legacyFileOrNull()
            if (file != null && file.exists()) {
                val details = PngMetadata.readArticleDetails(file)
                details.barcode?.let { bc ->
                    byBarcode.putIfAbsent(PngMetadata.barcodeFileStem(bc), ref)
                }
                details.designation?.let { des ->
                    val key = NameNormalizer.toDisplayFileStem(des).lowercase()
                    if (key.isNotBlank()) byDesignation.putIfAbsent(key, ref)
                }
            }
        }
        return PhotoroomIndex(files, byBarcode, byDesignation).also { cachedIndex = it }
    }

    private fun loadAllPngRefs(context: Context): List<PngRef> {
        folderTreeUri(context)?.let { uri ->
            return listPngFromTree(context, uri)
        }
        val dir = resolveLegacyFolder() ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.lowercase() == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                PngRef(
                    name = file.name,
                    pathLabel = "$DEFAULT_DISPLAY_PATH/${file.name}",
                    legacyFile = file,
                    documentUri = null,
                )
            }
            .orEmpty()
    }

    private fun listPngFromTree(context: Context, treeUri: Uri): List<PngRef> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val label = displayPath(context)
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)
        val out = mutableListOf<PngRef>()
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.isDirectory) {
                node.listFiles()?.forEach { pending.add(it) }
            } else if (node.isFile && node.name?.lowercase()?.endsWith(".png") == true) {
                val name = node.name ?: continue
                out += PngRef(
                    name = name,
                    pathLabel = "$label/$name",
                    legacyFile = null,
                    documentUri = node.uri,
                )
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }
}
