package com.oasismall.oasisai.domain.paray

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device visual memory for PARAY — one merged signature per article.
 */
class ParayVisualIndex(home: ParayHome) {
    private val indexFile = home.visualIndexFile
    @Volatile private var cachedSignatures: List<ProductVisualSignature>? = null

    fun learn(signature: ProductVisualSignature) {
        invalidateCache()
        val root = readRoot()
        val key = signature.articleId.toString()
        val existing = root.optJSONObject(key)
        val merged = if (existing == null) {
            signature
        } else {
            merge(existing, signature)
        }
        root.put(key, toJson(merged))
        indexFile.writeText(root.toString())
    }

    fun get(articleId: Long): ProductVisualSignature? =
        readRoot().optJSONObject(articleId.toString())?.let { fromJson(it) }

    fun allSignatures(): List<ProductVisualSignature> {
        cachedSignatures?.let { return it }
        synchronized(this) {
            cachedSignatures?.let { return it }
            val root = readRoot()
            val list = root.keys().asSequence().mapNotNull { key ->
                root.optJSONObject(key)?.let { fromJson(it) }
            }.toList()
            cachedSignatures = list
            return list
        }
    }

    fun count(): Int = readRoot().length()

    /** Single write for bulk PC import (avoids thousands of rewrites). */
    fun seedBatch(signatures: List<ProductVisualSignature>) {
        if (signatures.isEmpty()) return
        invalidateCache()
        val root = readRoot()
        for (signature in signatures) {
            val key = signature.articleId.toString()
            val existing = root.optJSONObject(key)
            val merged = if (existing == null) {
                signature
            } else {
                merge(existing, signature)
            }
            root.put(key, toJson(merged))
        }
        indexFile.writeText(root.toString())
    }

    private fun invalidateCache() {
        cachedSignatures = null
    }

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(indexFile.readText()) }.getOrElse { JSONObject() }

    private fun merge(old: JSONObject, fresh: ProductVisualSignature): ProductVisualSignature {
        val prev = fromJson(old)
        val n = prev.observationCount
        val m = fresh.observationCount
        val total = n + m
        fun blend(a: Float, b: Float) = (a * n + b * m) / total

        val colors = mergeColors(prev.dominantColors, fresh.dominantColors)
        return fresh.copy(
            shapeAspect = blend(prev.shapeAspect, fresh.shapeAspect),
            fillRatio = blend(prev.fillRatio, fresh.fillRatio),
            dominantColors = colors,
            observationCount = total,
            lastLearnedAt = fresh.lastLearnedAt,
        )
    }

    private fun mergeColors(a: List<Int>, b: List<Int>): List<Int> =
        (a + b).distinct().take(3)

    private fun toJson(s: ProductVisualSignature): JSONObject = JSONObject()
        .put("articleId", s.articleId)
        .put("barcode", s.barcode)
        .put("designation", s.designation)
        .put("shapeAspect", s.shapeAspect.toDouble())
        .put("fillRatio", s.fillRatio.toDouble())
        .put("dominantColors", JSONArray(s.dominantColors))
        .put("designationWordCount", s.designationWordCount)
        .put("designationCharCount", s.designationCharCount)
        .put("templateId", s.templateId)
        .put("labelPalette", JSONArray(s.labelPalette))
        .put("observationCount", s.observationCount)
        .put("lastLearnedAt", s.lastLearnedAt)
        .put("imageFileName", s.imageFileName)

    private fun fromJson(o: JSONObject): ProductVisualSignature {
        val colors = buildList {
            val arr = o.optJSONArray("dominantColors") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
        val palette = buildList {
            val arr = o.optJSONArray("labelPalette") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
        return ProductVisualSignature(
            articleId = o.getLong("articleId"),
            barcode = o.getString("barcode"),
            designation = o.getString("designation"),
            shapeAspect = o.getDouble("shapeAspect").toFloat(),
            fillRatio = o.getDouble("fillRatio").toFloat(),
            dominantColors = colors,
            designationWordCount = o.getInt("designationWordCount"),
            designationCharCount = o.getInt("designationCharCount"),
            templateId = o.optString("templateId", "shelf_10up"),
            labelPalette = palette.ifEmpty { VisualFeatureExtractor.shelfLabelPalette() },
            observationCount = o.optInt("observationCount", 1),
            lastLearnedAt = o.optLong("lastLearnedAt", System.currentTimeMillis()),
            imageFileName = o.optString("imageFileName", ""),
        )
    }
}
