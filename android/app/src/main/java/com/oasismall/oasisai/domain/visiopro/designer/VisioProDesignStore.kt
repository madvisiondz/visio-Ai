package com.oasismall.oasisai.domain.visiopro.designer

import com.oasismall.oasisai.util.writeTextAtomic

import android.content.Context
import com.oasismall.oasisai.domain.visiopro.VisioProTemplateAssets
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class VisioProDesignStore(
    context: Context,
    private val templateAssets: VisioProTemplateAssets,
) {
    private val dir = File(context.filesDir, "visio_pro_designs").also { it.mkdirs() }

    suspend fun load(key: VisioProPresetDesignKey): VisioProDesignerDocument? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching { parse(JSONObject(file.readText())) }.getOrNull()
    }

    suspend fun loadOrDefault(key: VisioProPresetDesignKey): VisioProDesignerDocument =
        load(key) ?: VisioProDesignerDefaults.defaultDocument(key, templateAssets)

    suspend fun save(document: VisioProDesignerDocument) = withContext(Dispatchers.IO) {
        val updated = document.copy(modifiedAt = System.currentTimeMillis())
        fileForKey(updated.presetKey).writeTextAtomic(serialize(updated).toString(2))
    }

    suspend fun reset(key: VisioProPresetDesignKey) = withContext(Dispatchers.IO) {
        fileFor(key).delete()
    }

    suspend fun loadForCategory(
        category: com.oasismall.oasisai.domain.visiopro.VisioProCategory,
        channel: com.oasismall.oasisai.domain.visiopro.VisioProChannel,
    ): VisioProDesignerDocument? {
        val key = VisioProPresetDesignKey.from(category, channel) ?: return null
        return load(key)
    }

    suspend fun isCustomized(key: VisioProPresetDesignKey): Boolean = withContext(Dispatchers.IO) {
        fileFor(key).exists()
    }

    private fun fileFor(key: VisioProPresetDesignKey) = fileForKey(key.storageKey)

    private fun fileForKey(storageKey: String) = File(dir, "$storageKey.json")

    private fun serialize(doc: VisioProDesignerDocument): JSONObject = JSONObject().apply {
        put("presetKey", doc.presetKey)
        put("canvasWidth", doc.canvasWidth)
        put("canvasHeight", doc.canvasHeight)
        put("templateId", doc.templateId)
        put("backgroundTop", doc.backgroundTop)
        put("backgroundBottom", doc.backgroundBottom)
        put("headerBand", doc.headerBand)
        put("accent", doc.accent)
        put("titleOnBand", doc.titleOnBand)
        put("bodyText", doc.bodyText)
        put("priceBackground", doc.priceBackground)
        put("priceText", doc.priceText)
        put("categoryTag", doc.categoryTag)
        put("showPrice", doc.showPrice)
        put("unitSuffix", doc.unitSuffix)
        put("headerBandHeight", doc.headerBandHeight.toDouble())
        put("photoRect", rectJson(doc.photoRect))
        put("designationRect", rectJson(doc.designationRect))
        put("priceRect", rectJson(doc.priceRect))
        doc.codeRect?.let { put("codeRect", rectJson(it)) }
        put("designationColor", doc.designationColor)
        doc.designationStrokeColor?.let { put("designationStrokeColor", it) }
        put("designationFontRatio", doc.designationFontRatio.toDouble())
        put("priceColor", doc.priceColor)
        put("priceFontRatio", doc.priceFontRatio.toDouble())
        put("priceAutoFit", doc.priceAutoFit)
        put("codeColor", doc.codeColor)
        put("codeFontRatio", doc.codeFontRatio.toDouble())
        put("sampleDesignation", doc.sampleDesignation)
        put("sampleDesignationAr", doc.sampleDesignationAr)
        put("samplePrice", doc.samplePrice)
        put("sampleCode", doc.sampleCode)
        put("modifiedAt", doc.modifiedAt)
    }

    private fun parse(root: JSONObject): VisioProDesignerDocument =
        VisioProDesignerDocument(
            presetKey = root.getString("presetKey"),
            canvasWidth = root.getInt("canvasWidth"),
            canvasHeight = root.getInt("canvasHeight"),
            templateId = root.optString("templateId").takeIf { it.isNotBlank() },
            backgroundTop = root.getInt("backgroundTop"),
            backgroundBottom = root.getInt("backgroundBottom"),
            headerBand = root.getInt("headerBand"),
            accent = root.getInt("accent"),
            titleOnBand = root.getInt("titleOnBand"),
            bodyText = root.getInt("bodyText"),
            priceBackground = root.getInt("priceBackground"),
            priceText = root.getInt("priceText"),
            categoryTag = root.getString("categoryTag"),
            showPrice = root.optBoolean("showPrice", true),
            unitSuffix = root.optString("unitSuffix").takeIf { it.isNotBlank() },
            headerBandHeight = root.optDouble("headerBandHeight", 0.12).toFloat(),
            photoRect = parseRect(root.getJSONObject("photoRect")),
            designationRect = parseRect(root.getJSONObject("designationRect")),
            priceRect = parseRect(root.getJSONObject("priceRect")),
            codeRect = root.optJSONObject("codeRect")?.let { parseRect(it) },
            designationColor = root.optInt("designationColor", Color.WHITE),
            designationStrokeColor = if (root.has("designationStrokeColor")) root.getInt("designationStrokeColor") else null,
            designationFontRatio = root.optDouble("designationFontRatio", 0.042).toFloat(),
            priceColor = root.optInt("priceColor", Color.BLACK),
            priceFontRatio = root.optDouble("priceFontRatio", 0.085).toFloat(),
            priceAutoFit = root.optBoolean("priceAutoFit", true),
            codeColor = root.optInt("codeColor", Color.WHITE),
            codeFontRatio = root.optDouble("codeFontRatio", 0.035).toFloat(),
            sampleDesignation = root.optString("sampleDesignation", "ABRICOT FRAIS"),
            sampleDesignationAr = root.optString("sampleDesignationAr", "مشمش"),
            samplePrice = root.optDouble("samplePrice", 450.0),
            sampleCode = root.optString("sampleCode", "032"),
            modifiedAt = root.optLong("modifiedAt", System.currentTimeMillis()),
        )

    private fun rectJson(rect: VisioProNormRect) = JSONObject().apply {
        put("left", rect.left.toDouble())
        put("top", rect.top.toDouble())
        put("right", rect.right.toDouble())
        put("bottom", rect.bottom.toDouble())
    }

    private fun parseRect(obj: JSONObject) = VisioProNormRect(
        left = obj.getDouble("left").toFloat(),
        top = obj.getDouble("top").toFloat(),
        right = obj.getDouble("right").toFloat(),
        bottom = obj.getDouble("bottom").toFloat(),
    )
}
