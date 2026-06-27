package com.oasismall.oasisai.domain.settings

import com.oasismall.oasisai.util.writeTextAtomic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.oasismall.oasisai.util.NameNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** True when rayon filtering is off, or [rayon] is one of the user's important rayons. */
fun ImportantRayonsConfig.includesRayon(rayon: String?): Boolean {
    if (!configured || selectedRayons.isEmpty()) return true
    if (rayon.isNullOrBlank()) return false
    val normalized = NameNormalizer.normalize(rayon)
    return selectedRayons.any { NameNormalizer.normalize(it) == normalized }
}

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
            file.writeTextAtomic(root.toString(2))
            _config.value = ImportantRayonsConfig(configured = true, selectedRayons = selectedRayons)
        }
    }

    fun refreshFromDisk() {
        synchronized(lock) {
            _config.value = readConfigLocked()
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
