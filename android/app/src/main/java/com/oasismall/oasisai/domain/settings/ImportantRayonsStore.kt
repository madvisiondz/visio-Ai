package com.oasismall.oasisai.domain.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ImportantRayonsConfig(
    val configured: Boolean = false,
    val selectedRayons: Set<String> = emptySet(),
)

/** User-selected Gestium rayons used for stats, Articles filters, and CSV change report. */
class ImportantRayonsStore(context: Context) {

    private val file = File(context.filesDir, "important_rayons.json")
    private val lock = Any()
    private val _config = MutableStateFlow(readConfigLocked())
    val config: StateFlow<ImportantRayonsConfig> = _config.asStateFlow()

    suspend fun getConfig(): ImportantRayonsConfig = withContext(Dispatchers.IO) {
        synchronized(lock) { readConfigLocked() }
    }

    suspend fun save(selectedRayons: Set<String>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = JSONObject()
            root.put("configured", true)
            root.put("rayons", JSONArray(selectedRayons.sorted()))
            file.parentFile?.mkdirs()
            file.writeText(root.toString(2))
            _config.value = ImportantRayonsConfig(configured = true, selectedRayons = selectedRayons)
        }
    }

    private fun readConfigLocked(): ImportantRayonsConfig {
        if (!file.exists()) return ImportantRayonsConfig()
        val root = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        val configured = root.optBoolean("configured", false)
        val rayons = parseRayonArray(root.optJSONArray("rayons"))
        return ImportantRayonsConfig(configured = configured, selectedRayons = rayons)
    }

    private fun parseRayonArray(arr: JSONArray?): Set<String> = buildSet {
        if (arr == null) return@buildSet
        for (i in 0 until arr.length()) {
            arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}
