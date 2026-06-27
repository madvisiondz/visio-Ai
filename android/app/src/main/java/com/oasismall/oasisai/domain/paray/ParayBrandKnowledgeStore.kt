package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Aggregates brand / family visual relationships from learned products.
 * Architecture for future recognition — updated when products reach LEARNED.
 */
class ParayBrandKnowledgeStore(private val home: ParayHome) {
    private val file = home.brandFamilyIndexFile

    fun upsertFromRecord(record: ParayLearnRecord) {
        if (record.status != ParayLearnStatus.LEARNED) return
        val brand = record.brand?.trim().orEmpty()
        val family = record.family?.trim().orEmpty()
        if (brand.isBlank() && family.isBlank()) return

        val root = readRoot()
        val key = "${brand}|${family}"
        val existing = root.optJSONObject(key)
        val count = existing?.optInt("productCount", 0)?.plus(1) ?: 1
        root.put(
            key,
            JSONObject()
                .put("brand", brand)
                .put("family", family)
                .put("productCount", count)
                .put("brandSignature", signaturesToJson(record.brandSignature))
                .put("familySignature", signaturesToJson(record.familySignature))
                .put("updatedAt", System.currentTimeMillis()),
        )
        file.writeTextAtomic(root.toString(2))
    }

    fun allEntries(): List<ParayBrandFamilyEntry> =
        readRoot().keys().asSequence().mapNotNull { key ->
            readRoot().optJSONObject(key)?.let { entryFromJson(it) }
        }.toList()

    fun getEntry(brand: String, family: String): ParayBrandFamilyEntry? {
        val key = "${brand.trim()}|${family.trim()}"
        if (key == "|") return null
        return readRoot().optJSONObject(key)?.let { entryFromJson(it) }
    }

    private fun entryFromJson(o: JSONObject): ParayBrandFamilyEntry = ParayBrandFamilyEntry(
        brand = o.optString("brand"),
        family = o.optString("family"),
        productCount = o.optInt("productCount", 0),
        brandSignature = signaturesFromJson(o.optJSONObject("brandSignature")),
        familySignature = signaturesFromJson(o.optJSONObject("familySignature")),
    )

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    private fun signaturesToJson(s: ParayVisualSignatures?): JSONObject? {
        if (s == null) return null
        return JSONObject()
            .put("shapeAspect", s.shapeAspect.toDouble())
            .put("fillRatio", s.fillRatio.toDouble())
            .put("dominantColors", JSONArray(s.dominantColors))
            .put("source", s.source)
            .put("capturedAt", s.capturedAt)
    }

    private fun signaturesFromJson(o: JSONObject?): ParayVisualSignatures? {
        if (o == null) return null
        val colors = buildList {
            val arr = o.optJSONArray("dominantColors") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
        return ParayVisualSignatures(
            shapeAspect = o.optDouble("shapeAspect", 0.0).toFloat(),
            fillRatio = o.optDouble("fillRatio", 0.0).toFloat(),
            dominantColors = colors,
            source = o.optString("source"),
            capturedAt = o.optLong("capturedAt", System.currentTimeMillis()),
        )
    }
}
