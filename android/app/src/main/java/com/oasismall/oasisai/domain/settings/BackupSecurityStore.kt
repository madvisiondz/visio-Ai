package com.oasismall.oasisai.domain.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Optional backup encryption password stored in EncryptedSharedPreferences. */
class BackupSecurityStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isEncryptionEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEncryptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clearPassword()
    }

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() }

    fun setPassword(password: String?) {
        prefs.edit().putString(KEY_PASSWORD, password?.takeIf { it.isNotEmpty() }).apply()
    }

    fun clearPassword() = prefs.edit().remove(KEY_PASSWORD).apply()

    companion object {
        private const val PREFS_NAME = "backup_security"
        private const val KEY_ENABLED = "encrypt_enabled"
        private const val KEY_PASSWORD = "encrypt_password"
    }
}
