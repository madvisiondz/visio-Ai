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
 * Telegram (LaunchActivity) routes to photo compression when ContentResolver.getType(uri)
 * is image-type. Default FileProvider returns image/png for .png files.
 *
 * Fix: dedicated [OasisShareFileProvider] (always application/octet-stream) and
 * on-disk `.oasis` extension with DISPLAY_NAME = real `NAME.png`.
 */
object PngShareHelper {

    data class NamedPng(val file: File, val displayName: String)

    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
    )

    fun targetFileName(item: PreselectionWithArticle): String =
        targetFileName(item.designation, item.barcode)

    fun targetFileName(designation: String, barcode: String): String {
        val displayStem = NameNormalizer.toDisplayFileStem(designation).takeIf { it.isNotBlank() }
        val stem = displayStem ?: PngMetadata.barcodeFileStem(barcode)
        return "$stem.png"
    }

    private fun internalShareFileName(displayName: String): String {
        val stem = displayName.removeSuffix(".png").removeSuffix(".PNG")
        return "$stem${OasisShareFileProvider.SHARE_FILE_EXTENSION}"
    }

    suspend fun prepareNamedFiles(
        context: Context,
        items: List<PreselectionWithArticle>,
    ): List<NamedPng> {
        val dir = File(context.cacheDir, "share-export").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        return items.mapNotNull { item ->
            val source = item.imagePath?.let(::File)?.takeIf { it.exists() } ?: return@mapNotNull null
            val displayName = targetFileName(item)
            val dest = File(dir, internalShareFileName(displayName))
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
                        addItem(ClipData.Item(uri))
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
