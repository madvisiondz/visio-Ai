package com.oasismall.oasisai.domain.phonesync

import android.content.Context

object PhoneSyncConfig {
    fun loadPin(context: Context): String {
        val prefs = context.getSharedPreferences(PhoneSyncProtocol.PREFS, Context.MODE_PRIVATE)
        return prefs.getString("pin", "2468")?.trim().orEmpty().ifBlank { "2468" }
    }

    fun savePin(context: Context, pin: String) {
        context.getSharedPreferences(PhoneSyncProtocol.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("pin", pin.trim())
            .apply()
    }

    fun loadMasterHost(context: Context): String =
        context.getSharedPreferences(PhoneSyncProtocol.PREFS, Context.MODE_PRIVATE)
            .getString("master_host", "")
            ?.trim()
            .orEmpty()

    fun saveMasterHost(context: Context, host: String) {
        context.getSharedPreferences(PhoneSyncProtocol.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("master_host", host.trim())
            .apply()
    }
}
