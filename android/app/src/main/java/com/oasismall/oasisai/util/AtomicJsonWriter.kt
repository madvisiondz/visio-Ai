package com.oasismall.oasisai.util

import java.io.File
import java.io.IOException

/** Writes JSON atomically: temp file → rename, so partial writes never corrupt on-disk state. */
object AtomicJsonWriter {
    fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp.${System.nanoTime()}")
        try {
            tmp.writeText(text, Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot replace ${file.path}")
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}

fun File.writeTextAtomic(text: String) = AtomicJsonWriter.writeText(this, text)
