package com.oasismall.oasisai.domain.transfer

import com.oasismall.oasisai.util.TaskProgress
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipArchive {
    fun zipDirectory(
        sourceDir: File,
        zipFile: File,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ) {
        val files = sourceDir.walkTopDown().filter { it.isFile }.toList()
        val total = files.size.coerceAtLeast(1)
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            files.forEachIndexed { index, file ->
                val entryName = file.relativeTo(sourceDir).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry(entryName))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
                val pct = ((index + 1) * 100) / total
                onProgress?.invoke(TaskProgress("Zipping $entryName", pct))
            }
        }
    }

    fun unzip(
        zipFile: File,
        destDir: File,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ) {
        destDir.mkdirs()
        val entries = mutableListOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zip.nextEntry
            }
        }
        val total = entries.size.coerceAtLeast(1)
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            var entry = zip.nextEntry
            var index = 0
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { out ->
                        zip.copyTo(out)
                    }
                }
                index++
                val pct = (index * 100) / total
                onProgress?.invoke(TaskProgress("Extracting ${entry.name}", pct))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
