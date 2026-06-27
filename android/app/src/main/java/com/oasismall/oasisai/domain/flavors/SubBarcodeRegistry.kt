package com.oasismall.oasisai.domain.flavors

import com.oasismall.oasisai.util.writeTextAtomic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Offline map of flavor sub-barcodes → parent article — survives Gestium catalog purge. */
data class SubBarcodeRegistryEntry(
    val subBarcode: String,
    val parentBarcode: String,
    val parentDesignation: String? = null,
    val imageRelativePath: String? = null,
    val archivedAt: Long = System.currentTimeMillis(),
)

class SubBarcodeRegistry(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    suspend fun load(): List<SubBarcodeRegistryEntry> = withContext(Dispatchers.IO) {
        synchronized(lock) { readLocked() }
    }

    suspend fun save(entries: List<SubBarcodeRegistryEntry>) = withContext(Dispatchers.IO) {
        synchronized(lock) { writeLocked(entries) }
    }

    suspend fun upsert(entry: SubBarcodeRegistryEntry) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val merged = readLocked()
                .filterNot { it.subBarcode == entry.subBarcode }
                .plus(entry)
            writeLocked(merged)
        }
    }

    suspend fun remove(subBarcode: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeLocked(readLocked().filterNot { it.subBarcode == subBarcode })
        }
    }

    suspend fun count(): Int = load().size

    private fun readLocked(): List<SubBarcodeRegistryEntry> {
        if (!file.exists()) return emptyList()
        val root = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        val arr = root.optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sub = o.optString("subBarcode").trim()
                val parent = o.optString("parentBarcode").trim()
                if (sub.isEmpty() || parent.isEmpty()) continue
                add(
                    SubBarcodeRegistryEntry(
                        subBarcode = sub,
                        parentBarcode = parent,
                        parentDesignation = o.optString("parentDesignation").takeIf { it.isNotBlank() },
                        imageRelativePath = o.optString("imageRelativePath").takeIf { it.isNotBlank() },
                        archivedAt = o.optLong("archivedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun writeLocked(entries: List<SubBarcodeRegistryEntry>) {
        val arr = JSONArray()
        entries.distinctBy { it.subBarcode }.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("subBarcode", e.subBarcode)
                    put("parentBarcode", e.parentBarcode)
                    e.parentDesignation?.let { put("parentDesignation", it) }
                    e.imageRelativePath?.let { put("imageRelativePath", it) }
                    put("archivedAt", e.archivedAt)
                },
            )
        }
        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("entries", arr)
        }
        file.parentFile?.mkdirs()
        file.writeTextAtomic(root.toString(2))
    }

    companion object {
        const val FILE_NAME = "sub_barcode_registry.json"
        const val FORMAT_VERSION = 1
    }
}
