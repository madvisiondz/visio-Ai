package com.oasismall.oasisai.domain.paray

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** File-based visual knowledge store — separate from identity (Room articles). */
class ParayLearnStore(private val home: ParayHome) {
    private val indexFile = home.learnIndexFile

    fun get(articleId: Long): ParayLearnRecord? =
        readRoot().optJSONObject(articleId.toString())?.let { fromJson(it) }

    fun put(record: ParayLearnRecord) {
        val root = readRoot()
        root.put(record.articleId.toString(), toJson(record))
        indexFile.writeText(root.toString())
    }

    fun allRecords(): List<ParayLearnRecord> {
        val root = readRoot()
        return root.keys().asSequence().mapNotNull { key ->
            root.optJSONObject(key)?.let { fromJson(it) }
        }.toList()
    }

    fun countByStatus(status: ParayLearnStatus): Int =
        allRecords().count { it.status == status }

    fun learnedCount(): Int = countByStatus(ParayLearnStatus.LEARNED)

    fun partiallyLearnedCount(): Int = countByStatus(ParayLearnStatus.PARTIALLY_LEARNED)

    fun viewsDir(articleId: Long): File =
        File(home.learnViewsDir, articleId.toString()).also { it.mkdirs() }

    fun saveViewImage(articleId: Long, side: ParayViewSide, bytes: ByteArray): File {
        val file = File(viewsDir(articleId), "${side.name.lowercase()}.jpg")
        file.writeBytes(bytes)
        return file
    }

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(indexFile.readText()) }.getOrElse { JSONObject() }

    private fun captureToJson(c: ParayViewCapture?): JSONObject? {
        if (c == null) return null
        return JSONObject()
            .put("shapeAspect", c.shapeAspect.toDouble())
            .put("fillRatio", c.fillRatio.toDouble())
            .put("dominantColors", JSONArray(c.dominantColors))
            .put("confidence", c.confidence.toDouble())
            .put("imagePath", c.imagePath)
            .put("capturedAt", c.capturedAt)
    }

    private fun captureFromJson(o: JSONObject?): ParayViewCapture? {
        if (o == null) return null
        val colors = buildList {
            val arr = o.optJSONArray("dominantColors") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
        return ParayViewCapture(
            shapeAspect = o.getDouble("shapeAspect").toFloat(),
            fillRatio = o.getDouble("fillRatio").toFloat(),
            dominantColors = colors,
            confidence = o.optDouble("confidence", 0.0).toFloat(),
            imagePath = o.optString("imagePath").takeIf { it.isNotBlank() },
            capturedAt = o.optLong("capturedAt", System.currentTimeMillis()),
        )
    }

    private fun toJson(r: ParayLearnRecord): JSONObject = JSONObject()
        .put("articleId", r.articleId)
        .put("barcode", r.barcode)
        .put("designation", r.designation)
        .put("pngFrontPath", r.pngFrontPath)
        .put("frontConfirmed", r.frontConfirmed)
        .put("frontConfidence", r.frontConfidence.toDouble())
        .put("frontCapture", captureToJson(r.frontCapture))
        .put("leftCapture", captureToJson(r.leftCapture))
        .put("rightCapture", captureToJson(r.rightCapture))
        .put("backCapture", captureToJson(r.backCapture))
        .put("learnedAt", r.learnedAt)
        .put("version", r.version)

    private fun fromJson(o: JSONObject): ParayLearnRecord = ParayLearnRecord(
        articleId = o.getLong("articleId"),
        barcode = o.getString("barcode"),
        designation = o.getString("designation"),
        pngFrontPath = o.getString("pngFrontPath"),
        frontConfirmed = o.optBoolean("frontConfirmed", false),
        frontConfidence = o.optDouble("frontConfidence", 0.0).toFloat(),
        frontCapture = captureFromJson(o.optJSONObject("frontCapture")),
        leftCapture = captureFromJson(o.optJSONObject("leftCapture")),
        rightCapture = captureFromJson(o.optJSONObject("rightCapture")),
        backCapture = captureFromJson(o.optJSONObject("backCapture")),
        learnedAt = o.optLong("learnedAt").takeIf { it > 0L },
        version = o.optInt("version", 1),
    )
}
