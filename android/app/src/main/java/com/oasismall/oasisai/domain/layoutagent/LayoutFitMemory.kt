package com.oasismall.oasisai.domain.layoutagent

import com.oasismall.oasisai.util.writeTextAtomic

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * On-device memory of successful placements — foundation for future GPU-learned weights.
 */
data class PlacementHint(
    val imageKey: String,
    val templateId: String,
    val contentAspect: Float,
    val scale: Float,
    val updatedAt: Long,
)

class LayoutFitMemory(context: Context) {
    private val dir = File(context.filesDir, "layout_agent").also { it.mkdirs() }
    private val memoryFile = File(dir, "placement_memory.json")

    fun loadHint(imagePath: String, templateId: String): PlacementHint? {
        val key = cacheKey(imagePath, templateId)
        val root = readRoot() ?: return null
        val entry = root.optJSONObject(key) ?: return null
        return PlacementHint(
            imageKey = key,
            templateId = templateId,
            contentAspect = entry.optDouble("contentAspect").toFloat(),
            scale = entry.optDouble("scale").toFloat(),
            updatedAt = entry.optLong("updatedAt"),
        )
    }

    fun saveHint(imagePath: String, templateId: String, contentAspect: Float, scale: Float) {
        val key = cacheKey(imagePath, templateId)
        val root = readRoot() ?: JSONObject()
        root.put(
            key,
            JSONObject()
                .put("contentAspect", contentAspect.toDouble())
                .put("scale", scale.toDouble())
                .put("updatedAt", System.currentTimeMillis()),
        )
        memoryFile.writeTextAtomic(root.toString())
    }

    /** Blend learned scale with computed contain scale when content aspect is similar. */
    fun adjustScale(hint: PlacementHint?, contentAspect: Float, computedScale: Float): Float {
        if (hint == null) return computedScale
        if (abs(hint.contentAspect - contentAspect) > 0.15f) return computedScale
        return computedScale * 0.7f + hint.scale * 0.3f
    }

    private fun readRoot(): JSONObject? =
        runCatching { JSONObject(memoryFile.readText()) }.getOrNull()

    private fun cacheKey(imagePath: String, templateId: String): String =
        "${templateId}_${File(imagePath).name}"
}
