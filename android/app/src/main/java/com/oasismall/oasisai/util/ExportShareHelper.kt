package com.oasismall.oasisai.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import java.io.File

object ExportShareHelper {
    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    /** Share shelf JPEG as document file (no messenger photo recompression). */
    fun shareJpegAsFile(context: Context, file: File) {
        if (!file.exists()) return
        val dir = File(context.cacheDir, "share-export").apply { mkdirs() }
        val displayName = file.name
        val internalName = displayName.replace(Regex("\\.(jpe?g)$", RegexOption.IGNORE_CASE)) {
            "${it.value}${OasisShareFileProvider.SHARE_FILE_EXTENSION}"
        }
        val shareFile = File(dir, internalName)
        file.copyTo(shareFile, overwrite = true)

        val authority = OasisShareFileProvider.authority(context.packageName)
        val uri = FileProvider.getUriForFile(context, authority, shareFile)
        val mime = OasisShareFileProvider.MIME_SHARE_DOCUMENT

        val intent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(uri, mime)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            clipData = ClipData.newRawUri(displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        grantReadToShareTargets(context, intent, listOf(uri))
        val chooser = Intent.createChooser(intent, "Send print file")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    private fun grantReadToShareTargets(context: Context, intent: Intent, uris: List<Uri>) {
        val probe = Intent(intent)
        val handlers = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        val packages = handlers.mapNotNull { it.activityInfo?.packageName }.toMutableSet()
        packages.addAll(
            listOf(
                "org.telegram.messenger",
                "org.telegram.messenger.web",
                "org.thunderdog.challegram",
            ),
        )
        for (packageName in packages) {
            for (uri in uris) {
                runCatching {
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    fun openPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }

    /** Share PNGs as document files (Telegram → send as file, not compressed photos). */
    suspend fun sharePngFiles(
        context: Context,
        items: List<PreselectionWithArticle>,
        summaryText: String? = null,
    ) {
        val named = PngShareHelper.prepareNamedFiles(context, items)
        if (named.isEmpty()) return
        PngShareHelper.shareAsFiles(context, named, summaryText)
    }

    fun sharePngFileWithText(context: Context, file: File, details: String) {
        if (!file.exists() || !file.extension.equals("png", ignoreCase = true)) return
        PngShareHelper.shareAsFiles(
            context,
            listOf(PngShareHelper.NamedPng(file, file.name)),
            details,
        )
    }

    /** Share Design queue as plain text (barcode + designation + price) for PC price check round-trip. */
    fun shareDesignCartInfo(context: Context, items: List<PreselectionWithArticle>) {
        if (items.isEmpty()) return
        val body = DesignPriceMessage.formatForShare(items)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, "Oasis Design — ${items.size} articles (price check)")
        }
        context.startActivity(Intent.createChooser(intent, "Send info"))
    }
}
