package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Persists PARAY Learn thresholds on device.
 *
 * File: `paray_home/memory/learn_settings.json`
 */
class ParayLearnSettingsStore(private val home: ParayHome) {
    private val file = home.learnSettingsFile
    private val lock = Any()
    private val _settings = MutableStateFlow(readLocked())
    val settings: StateFlow<ParayLearnSettings> = _settings.asStateFlow()

    fun current(): ParayLearnSettings = _settings.value

    suspend fun get(): ParayLearnSettings = withContext(Dispatchers.IO) {
        synchronized(lock) { readLocked() }
    }

    suspend fun save(settings: ParayLearnSettings) = withContext(Dispatchers.IO) {
        val validated = settings.validated()
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeTextAtomic(toJson(validated).toString(2))
            _settings.value = validated
        }
    }

    fun refreshFromDisk() {
        synchronized(lock) {
            _settings.value = readLocked()
        }
    }

    private fun readLocked(): ParayLearnSettings {
        if (!file.exists()) {
            val defaults = ParayLearnSettings.factoryDefaults()
            file.parentFile?.mkdirs()
            file.writeTextAtomic(toJson(defaults).toString(2))
            return defaults
        }
        val root = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        return ParayLearnSettings(
            frontConfirmationThreshold = root.optDouble(
                KEY_FRONT,
                ParayLearnSettings.factoryDefaults().frontConfirmationThreshold.toDouble(),
            ).toFloat(),
            sideCaptureThreshold = root.optDouble(
                KEY_SIDE,
                ParayLearnSettings.factoryDefaults().sideCaptureThreshold.toDouble(),
            ).toFloat(),
            backCaptureThreshold = root.optDouble(
                KEY_BACK,
                ParayLearnSettings.factoryDefaults().backCaptureThreshold.toDouble(),
            ).toFloat(),
        ).validated()
    }

    private fun toJson(s: ParayLearnSettings): JSONObject = JSONObject()
        .put(KEY_VERSION, 1)
        .put(KEY_FRONT, s.frontConfirmationThreshold.toDouble())
        .put(KEY_SIDE, s.sideCaptureThreshold.toDouble())
        .put(KEY_BACK, s.backCaptureThreshold.toDouble())

    companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_FRONT = "frontConfirmationThreshold"
        private const val KEY_SIDE = "sideCaptureThreshold"
        private const val KEY_BACK = "backCaptureThreshold"
    }
}
