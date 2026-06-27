package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.util.writeTextAtomic

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
                obj.remove("manualPriceOverridden")
                obj.remove("csvPriceWhenOverridden")
                obj.remove("manualPriceChangedAt")
            } else {
                obj.put("manualPrice", price)
            }
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    suspend fun setManualPriceOverride(slug: String, price: Double, csvBaseline: Double) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val root = readRoot()
                val obj = root.optJSONObject(slug) ?: JSONObject()
                obj.put("manualPrice", price)
                obj.put("manualPriceOverridden", true)
                obj.put("csvPriceWhenOverridden", csvBaseline)
                obj.put("manualPriceChangedAt", System.currentTimeMillis())
                root.put(slug, obj)
                writeRoot(root)
            }
        }

    suspend fun clearManualPriceOverride(slug: String) = setManualPrice(slug, null)

    suspend fun setManualDesignation(slug: String, designation: String?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val obj = root.optJSONObject(slug) ?: JSONObject()
            val trimmed = designation?.trim().orEmpty()
            if (trimmed.isBlank()) {
                obj.remove("manualDesignation")
            } else {
                obj.put("manualDesignation", trimmed)
            }
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    suspend fun setManualDesignationFontRatio(slug: String, ratio: Float?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val root = readRoot()
            val obj = root.optJSONObject(slug) ?: JSONObject()
            if (ratio == null || ratio <= 0f) {
                obj.remove("manualDesignationFontRatio")
            } else {
                obj.put("manualDesignationFontRatio", ratio.toDouble())
            }
            root.put(slug, obj)
            writeRoot(root)
        }
    }

    suspend fun getPresetDesignationFontRatio(slug: String, presetId: String): Float? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val obj = readRoot().optJSONObject(slug) ?: return@withContext null
                obj.optJSONObject("presetFontRatios")
                    ?.optDouble(presetId)
                    ?.toFloat()
                    ?.takeIf { it > 0f }
            }
        }

    suspend fun setPresetDesignationFontRatio(slug: String, presetId: String, ratio: Float?) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val root = readRoot()
                val obj = root.optJSONObject(slug) ?: JSONObject()
                val ratios = obj.optJSONObject("presetFontRatios") ?: JSONObject()
                if (ratio == null || ratio <= 0f) {
                    ratios.remove(presetId)
                } else {
                    ratios.put(presetId, ratio.toDouble())
                }
                if (ratios.length() == 0) {
                    obj.remove("presetFontRatios")
                } else {
                    obj.put("presetFontRatios", ratios)
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
        file.writeTextAtomic(root.toString(2))
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
            manualPriceOverridden = obj.optBoolean("manualPriceOverridden", false),
            csvPriceWhenOverridden = if (obj.has("csvPriceWhenOverridden") && !obj.isNull("csvPriceWhenOverridden")) {
                obj.getDouble("csvPriceWhenOverridden")
            } else {
                null
            },
            manualPriceChangedAt = obj.optLong("manualPriceChangedAt").takeIf { it > 0L },
            manualDesignation = obj.optString("manualDesignation").takeIf { it.isNotBlank() },
            manualDesignationFontRatio = if (obj.has("manualDesignationFontRatio") && !obj.isNull("manualDesignationFontRatio")) {
                obj.getDouble("manualDesignationFontRatio").toFloat()
            } else {
                null
            },
            lastSocialExportAt = obj.optLong("lastSocialExportAt").takeIf { it > 0L },
            lastPrintExportAt = obj.optLong("lastPrintExportAt").takeIf { it > 0L },
            printModified = obj.optBoolean("printModified", false),
        )
}
