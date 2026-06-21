package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VisioProStore(context: Context) {

    private val file = File(context.filesDir, "visio_pro_memory.json")
    private val lock = Any()

    private companion object {
        const val META_KEY = "_meta"
        const val QUEUE_KEY = "printQueue"
    }

    suspend fun getMemory(slug: String): VisioProArticleMemory = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readRoot().optJSONObject(slug)?.let { parse(slug, it) }
                ?: VisioProArticleMemory(slug = slug)
        }
    }

    suspend fun getAllMemory(): Map<String, VisioProArticleMemory> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            buildMap {
                root.keys().forEach { key ->
                    if (key.startsWith("_")) return@forEach
                    put(key, parse(key, root.getJSONObject(key)))
                }
            }
        }
    }

    suspend fun getPrintQueue(): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) { readPrintQueue(readRoot()) }
    }

    suspend fun addToPrintQueue(slug: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val queue = readPrintQueue(root).toMutableList()
            if (!queue.contains(slug)) queue.add(slug)
            writePrintQueue(root, queue)
            writeRoot(root)
        }
    }

    suspend fun removeFromPrintQueue(slugs: Collection<String>) = withContext(Dispatchers.IO) {
        if (slugs.isEmpty()) return@withContext
        synchronized(lock) {
            val root = readRoot()
            val queue = readPrintQueue(root).filterNot { it in slugs }
            writePrintQueue(root, queue)
            writeRoot(root)
        }
    }

    suspend fun setManualPrice(slug: String, price: Double?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val obj = root.optJSONObject(slug) ?: JSONObject()
            if (price == null) {
                obj.remove("manualPrice")
            } else {
                obj.put("manualPrice", price)
            }
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    suspend fun setPrintModified(slug: String, modified: Boolean) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val obj = root.optJSONObject(slug) ?: JSONObject()
            if (modified) {
                obj.put("printModified", true)
            } else {
                obj.remove("printModified")
            }
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    suspend fun markSocialExport(slug: String, atMillis: Long = System.currentTimeMillis()) =
        markExport(slug, "lastSocialExportAt", atMillis)

    suspend fun markPrintExport(slug: String, atMillis: Long = System.currentTimeMillis()) =
        markExport(slug, "lastPrintExportAt", atMillis)

    private fun markExport(slug: String, field: String, atMillis: Long) {
        synchronized(lock) {
            val root = readRoot()
            val obj = root.optJSONObject(slug) ?: JSONObject()
            obj.put(field, atMillis)
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    private fun readRoot(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeRoot(root: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun readPrintQueue(root: JSONObject): List<String> {
        val meta = root.optJSONObject(META_KEY) ?: return emptyList()
        val arr = meta.optJSONArray(QUEUE_KEY) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun writePrintQueue(root: JSONObject, queue: List<String>) {
        val meta = root.optJSONObject(META_KEY) ?: JSONObject()
        meta.put(QUEUE_KEY, JSONArray(queue))
        root.put(META_KEY, meta)
    }

    private fun parse(slug: String, obj: JSONObject): VisioProArticleMemory =
        VisioProArticleMemory(
            slug = slug,
            manualPrice = if (obj.has("manualPrice") && !obj.isNull("manualPrice")) {
                obj.getDouble("manualPrice")
            } else {
                null
            },
            lastSocialExportAt = obj.optLong("lastSocialExportAt").takeIf { it > 0L },
            lastPrintExportAt = obj.optLong("lastPrintExportAt").takeIf { it > 0L },
            printModified = obj.optBoolean("printModified", false),
        )
}
