package com.oasismall.oasisai.domain.paray

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/**
 * CLIP embeddings from PC bulk fingerprint (Option A).
 * Keyed by barcode until on-phone embedder can query live frames.
 */
class ParayFingerprintStore(home: ParayHome) {
    private val storeFile = home.fingerprintIndexFile
    @Volatile private var cachedEmbeddings: List<Pair<String, FloatArray>>? = null

    fun count(): Int = readRoot().optJSONArray("entries")?.length() ?: 0

    fun getMeta(): FingerprintMeta? {
        val root = readRoot()
        if (!root.has("model")) return null
        return FingerprintMeta(
            version = root.optInt("version", 1),
            agent = root.optString("agent", "PARAY"),
            model = root.optString("model", ""),
            dim = root.optInt("dim", 512),
            generatedAt = root.optString("generatedAt", ""),
            source = root.optString("source", ""),
        )
    }

    fun getEmbedding(barcode: String): FloatArray? {
        val arr = readRoot().optJSONArray("entries") ?: return null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("barcode") == barcode) {
                return jsonToEmbedding(o.getJSONArray("embedding"))
            }
        }
        return null
    }

    fun allEmbeddings(): List<Pair<String, FloatArray>> {
        val arr = readRoot().optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(o.getString("barcode") to jsonToEmbedding(o.getJSONArray("embedding")))
            }
        }
    }

    fun replaceAll(entries: List<FingerprintEntry>, meta: FingerprintMeta) {
        val root = JSONObject()
            .put("version", meta.version)
            .put("agent", meta.agent)
            .put("model", meta.model)
            .put("dim", meta.dim)
            .put("generatedAt", meta.generatedAt)
            .put("source", meta.source)
            .put("count", entries.size)
            .put(
                "entries",
                JSONArray().apply {
                    entries.forEach { put(toJson(it)) }
                },
            )
        storeFile.writeText(root.toString())
    }

    private fun invalidateCache() {
        cachedEmbeddings = null
    }

    private fun loadEmbeddingsFromDisk(): List<Pair<String, FloatArray>> {
        val arr = readRoot().optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(o.getString("barcode") to jsonToEmbedding(o.getJSONArray("embedding")))
            }
        }
    }

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(storeFile.readText()) }.getOrElse { JSONObject() }

    private fun toJson(e: FingerprintEntry): JSONObject = JSONObject()
        .put("barcode", e.barcode)
        .put("designation", e.designation)
        .put("imageFileName", e.imageFileName)
        .put("embedding", JSONArray(e.embedding.map { it.toDouble() }))

    private fun jsonToEmbedding(arr: JSONArray): FloatArray {
        val out = FloatArray(arr.length())
        for (i in 0 until arr.length()) out[i] = arr.getDouble(i).toFloat()
        return out
    }
}

data class FingerprintMeta(
    val version: Int,
    val agent: String,
    val model: String,
    val dim: Int,
    val generatedAt: String,
    val source: String,
)

data class FingerprintEntry(
    val barcode: String,
    val designation: String,
    val imageFileName: String,
    val embedding: FloatArray,
)

data class ParayImportResult(
    val imported: Int,
    val skippedNoArticle: Int,
    val skippedInvalid: Int,
    val embeddingCount: Int,
)

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom > 1e-8f) (dot / denom).coerceIn(0f, 1f) else 0f
}
