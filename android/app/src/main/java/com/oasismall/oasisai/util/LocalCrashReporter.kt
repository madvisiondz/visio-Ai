package com.oasismall.oasisai.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File-based crash reports when Firebase Crashlytics is not configured.
 * Wire Firebase in [OasisLog.ReleaseTree] when `google-services.json` is present.
 */
object LocalCrashReporter {
    private const val TAG = "Oasis/Crash"
    private const val MAX_REPORTS = 20
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, throwable, "Uncaught", "Thread ${thread.name}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, throwable: Throwable, tag: String?, message: String) {
        runCatching {
            val dir = File(context.applicationContext.filesDir, "crash_reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            val safeTag = tag?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "error"
            val file = File(dir, "${stamp}_$safeTag.txt")
            file.writeText(
                buildString {
                    appendLine("time=$stamp")
                    appendLine("tag=$tag")
                    appendLine("message=$message")
                    appendLine()
                    appendLine(Log.getStackTraceString(throwable))
                },
            )
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
            Log.e(TAG, "Crash report saved: ${file.name}", throwable)
        }.onFailure {
            Log.e(TAG, "Failed to write crash report", it)
        }
    }
}
