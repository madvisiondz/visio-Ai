package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists ordered catalog article ids per VisioPRO card (offline JSON). */
class VisioProCatalogConfigStore(context: Context) {

    private val file = File(context.filesDir, "visio_pro_catalog_config.json")
    private val lock = Any()

    suspend fun getCategoryConfig(category: VisioProCategory): VisioProCategoryConfig = withContext(Dispatchers.IO) {
        synchronized(lock) { readCategoryConfigLocked(category) }
    }

    /** Legacy — enabled ids only. */
    suspend fun getOrderedArticleIds(category: VisioProCategory): List<Long>? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val config = readCategoryConfigLocked(category)
            config.enabledIds.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun setCategoryConfig(category: VisioProCategory, config: VisioProCategoryConfig) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val root = readRoot()
                root.put(category.name, encodeCategoryConfig(config))
                writeRoot(root)
            }
        }

    suspend fun setOrderedArticleIds(category: VisioProCategory, ids: List<Long>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val current = readCategoryConfigFromValue(root.opt(category.name))
            val updated = current.copy(
                enabledIds = ids.distinct(),
                pendingIds = current.pendingIds.filter { it !in ids },
            )
            root.put(category.name, encodeCategoryConfig(updated))
            writeRoot(root)
        }
    }

    suspend fun appendPendingIds(category: VisioProCategory, ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        synchronized(lock) {
            val root = readRoot()
            val current = readCategoryConfigFromValue(root.opt(category.name))
            val updated = current.appendPending(ids)
            if (updated.pendingIds == current.pendingIds) return@synchronized
            root.put(category.name, encodeCategoryConfig(updated))
            writeRoot(root)
        }
    }

    suspend fun clearCategory(category: VisioProCategory) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            root.remove(category.name)
            writeRoot(root)
        }
    }

    private fun readCategoryConfigLocked(category: VisioProCategory): VisioProCategoryConfig =
        readCategoryConfigFromValue(readRoot().opt(category.name))

    private fun readCategoryConfigFromValue(value: Any?): VisioProCategoryConfig = when (value) {
        is JSONArray -> VisioProCategoryConfig(enabledIds = parseIdArray(value))
        is JSONObject -> VisioProCategoryConfig(
            enabledIds = parseIdArray(value.optJSONArray("enabled")),
            pendingIds = parseIdArray(value.optJSONArray("pending")),
        )
        else -> VisioProCategoryConfig()
    }

    private fun encodeCategoryConfig(config: VisioProCategoryConfig): JSONObject =
        JSONObject()
            .put("enabled", JSONArray(config.enabledIds))
            .put("pending", JSONArray(config.pendingIds))

    private fun readRoot(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeRoot(root: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun parseIdArray(arr: JSONArray?): List<Long> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            val value = arr.optLong(i, -1L)
            if (value > 0L) add(value)
        }
    }
}
