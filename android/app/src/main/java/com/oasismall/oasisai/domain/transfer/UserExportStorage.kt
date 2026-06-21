package com.oasismall.oasisai.domain.transfer

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Write export ZIPs to a user-picked URI (SAF) and keep app cache lean. */
object UserExportStorage {
    fun copyFileToUri(context: Context, source: File, destUri: Uri) {
        if (!source.isFile) error("Export file missing: ${source.name}")
        val out = context.contentResolver.openOutputStream(destUri, "wt")
            ?: throw IOException("Impossible d'écrire à l'emplacement choisi")
        BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(out).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun cleanupStaleExportCache(cacheDir: File) {
        cacheDir.listFiles()?.forEach { entry ->
            when {
                entry.isDirectory && (
                    entry.name.startsWith("device-backup-") ||
                        entry.name.startsWith("VisioPRO_export_")
                    ) -> entry.deleteRecursively()
                entry.isFile && entry.extension.equals("zip", ignoreCase = true) &&
                    (
                        entry.name.startsWith("VisioAi_backup_") ||
                            entry.name.startsWith("VisioPRO_export_")
                        ) -> entry.delete()
            }
        }
    }
}
