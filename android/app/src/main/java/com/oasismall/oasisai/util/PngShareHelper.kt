package com.oasismall.oasisai.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import java.io.File

/**
 * Shares PNG bytes as **documents** (Telegram file attach), not gallery photos.
 *
 * Telegram reads [OpenableColumns.DISPLAY_NAME] from [OasisShareFileProvider], which is
 * derived from the on-disk `.oasis` cache filename — not Intent extras. Cache files must
 * therefore use the human-readable spaced display stem, while gallery PNGs may stay compact.
 */
object PngShareHelper {

    data class NamedPng(val file: File, val displayName: String)

    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
    )

    fun targetFileName(item: PreselectionWithArticle): String {
        val source = item.imagePath?.let(::File)?.takeIf { it.exists() }
        return shareDisplayName(item, source)
    }

    fun targetFileName(designation: String, barcode: String): String {
        val spacedStem = NameNormalizer.toDisplayFileStem(designation).takeIf { it.isNotBlank() }
            ?: PngMetadata.barcodeFileStem(barcode)
        return "$spacedStem.png"
    }

    private fun shareDisplayName(item: PreselectionWithArticle, source: File?): String {
        val spacedStem = resolveSpacedStem(item, source)
        if (source != null) {
            val compactStem = PngMetadata.subVariantDesignationStem(item.designation)
            val altIndex = variantIndexFromSource(source, compactStem)
            if (altIndex != null) {
                return "$spacedStem $altIndex.png"
            }
        }
        val variant = item.variantBarcode.trim()
        val main = item.barcode.trim()
        if (variant.isNotEmpty() && variant != main) {
            return "$spacedStem ${PngMetadata.barcodeFileStem(variant)}.png"
        }
        return "$spacedStem.png"
    }

    /** Prefer spaced designation from DB; fall back to PNG metadata when DB value is compact. */
    private fun resolveSpacedStem(item: PreselectionWithArticle, source: File?): String {
        val fromDb = NameNormalizer.toDisplayFileStem(item.designation)
        if (' ' in fromDb) return fromDb

        source?.let { file ->
            runCatching { PngMetadata.readArticleDetails(file).designation }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { NameNormalizer.toDisplayFileStem(it) }
                ?.takeIf { ' ' in it }
                ?.let { return it }
        }

        val normalized = NameNormalizer.normalize(item.designation)
        if (' ' in normalized) return normalized

        return fromDb.ifBlank { PngMetadata.barcodeFileStem(item.barcode) }
    }

    private fun variantIndexFromSource(source: File, compactStem: String): Int? {
        val diskStem = source.nameWithoutExtension
        PngMetadata.parseSubVariantAltIndex(diskStem, compactStem)?.let { return it }
        if (!diskStem.startsWith(compactStem, ignoreCase = true)) return null
        return diskStem.removePrefix(compactStem).toIntOrNull()?.takeIf { it > 0 }
    }

    private fun cacheFileForDisplayName(dir: File, displayName: String, used: MutableSet<String>): File {
        var stem = displayName.removeSuffix(".png").removeSuffix(".PNG")
        var internal = "$stem${OasisShareFileProvider.SHARE_FILE_EXTENSION}"
        var n = 2
        while (!used.add(internal)) {
            stem = "${displayName.removeSuffix(".png").removeSuffix(".PNG")}_$n"
            internal = "$stem${OasisShareFileProvider.SHARE_FILE_EXTENSION}"
            n++
        }
        return File(dir, internal)
    }

    suspend fun prepareNamedFiles(
        context: Context,
        items: List<PreselectionWithArticle>,
    ): List<NamedPng> {
        val dir = File(context.cacheDir, "share-export").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val usedInternal = mutableSetOf<String>()
        return items.mapNotNull { item ->
            val source = item.imagePath?.let(::File)?.takeIf { it.exists() } ?: return@mapNotNull null
            val displayName = shareDisplayName(item, source)
            val dest = cacheFileForDisplayName(dir, displayName, usedInternal)
            source.copyTo(dest, overwrite = true)
            PngMetadata.writeArticleDetails(
                file = dest,
                barcode = item.barcode,
                designation = item.designation,
                priceNow = item.price,
                priceBefore = item.previousPrice,
                rayon = item.category,
                codeart = item.codeart,
            )
            NamedPng(dest, displayName)
        }
    }

    fun shareAsFiles(context: Context, named: List<NamedPng>, summaryText: String? = null) {
        if (named.isEmpty()) return
        val authority = OasisShareFileProvider.authority(context.packageName)
        val mime = OasisShareFileProvider.MIME_SHARE_DOCUMENT
        val pairs = named.map { entry ->
            entry to FileProvider.getUriForFile(context, authority, entry.file)
        }
        val uris = pairs.map { it.second }

        val intent = if (uris.size == 1) {
            val (entry, uri) = pairs.first()
            Intent(Intent.ACTION_SEND).apply {
                setDataAndType(uri, mime)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, entry.displayName)
                clipData = ClipData.newRawUri(entry.displayName, uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                val first = named.first()
                clipData = ClipData.newRawUri(first.displayName, uris.first()).apply {
                    named.drop(1).zip(uris.drop(1)).forEach { (entry, uri) ->
                        addItem(ClipData.Item(entry.displayName, null, uri))
                    }
                }
            }
        }.apply {
            putExtra(Intent.EXTRA_SUBJECT, "Oasis PNG files (${named.size})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        grantReadPermissionToShareTargets(context, intent, uris)
        val chooser = Intent.createChooser(intent, "Send as document file")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    private fun grantReadPermissionToShareTargets(
        context: Context,
        intent: Intent,
        uris: List<Uri>,
    ) {
        val probe = Intent(intent)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val handlers = context.packageManager.queryIntentActivities(probe, flags)
        val packages = handlers.mapNotNull { it.activityInfo?.packageName }.toMutableSet()
        packages.addAll(telegramPackages)
        for (packageName in packages) {
            for (uri in uris) {
                runCatching {
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }
    }
}
