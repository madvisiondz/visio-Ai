package com.oasismall.oasisai.util

import android.content.Context
import android.util.Log
import timber.log.Timber

/** Structured logging for Visio Ai — domain tags for filtering in Logcat / future crash SDK. */
object OasisLog {

    enum class Domain(val tag: String) {
        Import("Oasis/Import"),
        Share("Oasis/Share"),
        Paray("Oasis/Paray"),
        Design("Oasis/Design"),
        Database("Oasis/Database"),
        Backup("Oasis/Backup"),
        Agent("Oasis/Agent"),
    }

    fun d(domain: Domain, message: String) = Timber.tag(domain.tag).d(message)

    fun i(domain: Domain, message: String) = Timber.tag(domain.tag).i(message)

    fun w(domain: Domain, message: String, throwable: Throwable? = null) {
        if (throwable != null) Timber.tag(domain.tag).w(throwable, message)
        else Timber.tag(domain.tag).w(message)
    }

    fun e(domain: Domain, message: String, throwable: Throwable? = null) {
        if (throwable != null) Timber.tag(domain.tag).e(throwable, message)
        else Timber.tag(domain.tag).e(message)
    }

    /**
     * Release builds: WARN+ only. Records ERROR+ to [LocalCrashReporter].
     * Hook Firebase Crashlytics here when `google-services.json` is configured.
     */
    class ReleaseTree(private val context: Context) : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < Log.WARN) return
            if (t != null) Log.println(priority, tag ?: "Oasis", "$message\n${Log.getStackTraceString(t)}")
            else Log.println(priority, tag ?: "Oasis", message)
            if (priority >= Log.ERROR && t != null) {
                LocalCrashReporter.record(context, t, tag, message)
            }
        }
    }
}
