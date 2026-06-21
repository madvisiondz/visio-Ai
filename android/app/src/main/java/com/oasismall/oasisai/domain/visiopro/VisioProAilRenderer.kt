package com.oasismall.oasisai.domain.visiopro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.oasismall.oasisai.domain.visiopro.designer.QuadInt
import com.oasismall.oasisai.util.PriceFormatter
import org.json.JSONObject
import kotlin.math.max

class VisioProTemplateAssets(context: Context) {

    private val appContext = context.applicationContext
    private val layoutCache = mutableMapOf<String, VisioProTemplateLayout>()
    private val overlayCache = mutableMapOf<String, Bitmap>()

    fun layout(templateId: String): VisioProTemplateLayout? {
        layoutCache[templateId]?.let { return it }
        val path = when (templateId) {
            "ail_social" -> "visiopro/ail_social/layout.json"
            "fv_print" -> "visiopro/fv_print/layout.json"
            else -> return null
        }
        val json = appContext.assets.open(path).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val text = root.optJSONObject("text") ?: JSONObject()
        val loaded = VisioProTemplateLayout(
            templateId = root.getString("templateId"),
            width = root.getInt("width"),
            height = root.getInt("height"),
            photoLeft = root.getJSONObject("photo").getInt("left"),
            photoTop = root.getJSONObject("photo").getInt("top"),
            photoRight = root.getJSONObject("photo").getInt("right"),
            photoBottom = root.getJSONObject("photo").getInt("bottom"),
            designAsset = root.getString("designAsset"),
            designation = text.optJSONObject("designation")?.let { parseSlot(it) },
            code = root.optJSONObject("code")?.let { parseSlot(it) },
            price = text.optJSONObject("price")?.let { parseSlot(it) },
        )
        layoutCache[templateId] = loaded
        return loaded
    }

    fun designOverlay(templateId: String): Bitmap? {
        overlayCache[templateId]?.let { return it }
        val layout = layout(templateId) ?: return null
        val assetPath = when (templateId) {
            "ail_social" -> "visiopro/ail_social/${layout.designAsset}"
            "fv_print" -> "visiopro/fv_print/${layout.designAsset}"
            else -> return null
        }
        return loadBitmap(assetPath)?.also { overlayCache[templateId] = it }
    }

    fun loadBitmap(assetPath: String): Bitmap? =
        runCatching {
            appContext.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    fun printProductBitmap(article: VisioProArticleDef): Bitmap? =
        article.printProductAsset?.let { loadBitmap(it) }

    private fun parseSlot(obj: JSONObject): VisioProTextSlot =
        VisioProTextSlot(
            left = obj.getInt("left"),
            top = obj.getInt("top"),
            right = obj.getInt("right"),
            bottom = obj.getInt("bottom"),
            color = Color.parseColor(obj.getString("color")),
            strokeColor = obj.optString("strokeColor").takeIf { it.isNotBlank() }?.let { Color.parseColor(it) },
            strokeWidthRatio = obj.optDouble("strokeWidthRatio").toFloat(),
            fontSizeRatio = obj.optDouble("fontSizeRatio", 0.04).toFloat(),
            align = obj.optString("align", "center"),
            autoFit = obj.optBoolean("autoFit", false),
            format = obj.optString("format").takeIf { it.isNotBlank() },
        )
}

class VisioProAilRenderer(
    private val templateAssets: VisioProTemplateAssets,
) {

    data class RenderInput(
        val preset: VisioProPreset,
        val price: Double?,
        val photoBitmap: Bitmap?,
        val design: com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument? = null,
    )

    fun render(input: RenderInput): Bitmap? {
        val templateId = input.preset.theme.templateId ?: return null
        val layout = templateAssets.layout(templateId) ?: return null
        val overlay = templateAssets.designOverlay(templateId) ?: return null
        val design = input.design
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (templateId == "fv_print") {
            canvas.drawColor(Color.WHITE)
        }

        val photo = design?.photoPixels() ?: QuadInt(layout.photoLeft, layout.photoTop, layout.photoRight, layout.photoBottom)
        val photoRect = RectF(photo.left.toFloat(), photo.top.toFloat(), photo.right.toFloat(), photo.bottom.toFloat())
        input.photoBitmap?.let { photo ->
            drawCover(canvas, photo, photoRect)
        } ?: run {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (templateId) {
                    "fv_print" -> Color.parseColor("#FFF3E0")
                    else -> Color.parseColor("#2E7D32")
                }
            }
            canvas.drawRect(photoRect, placeholderPaint)
            val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (templateId) {
                    "fv_print" -> Color.parseColor("#BF360C")
                    else -> Color.WHITE
                }
                textSize = layout.width * 0.025f
                textAlign = Paint.Align.CENTER
            }
            val hintText = when (templateId) {
                "fv_print" -> "Image catalogue"
                else -> "Photographier"
            }
            canvas.drawText(hintText, photoRect.centerX(), photoRect.centerY(), hint)
        }

        canvas.drawBitmap(overlay, 0f, 0f, null)

        val article = input.preset.article
        val designation = if (design != null && templateId == "ail_social") {
            design.sampleDesignationAr.ifBlank { article.labelAr ?: article.labelFr }
        } else {
            article.labelAr ?: article.labelFr
        }
        val designationSlot = design?.designationSlot() ?: layout.designation
        designationSlot?.let { drawText(canvas, designation, it, layout.width, bold = true) }

        if (templateId == "fv_print") {
            val codeSuffix = design?.sampleCode ?: article.barcodeSuffix
            codeSuffix?.let { suffix ->
                val slot = design?.codeSlot() ?: layout.code
                slot?.let {
                    val label = it.format?.replace("{value}", suffix) ?: suffix
                    drawText(canvas, label, it, layout.width, bold = true)
                }
            }
        }

        val priceText = input.price?.let { PriceFormatter.formatNumber(it) } ?: "—"
        val priceSlot = design?.priceSlot() ?: layout.price
        priceSlot?.let { drawText(canvas, priceText, it, layout.width, bold = true) }

        return bitmap
    }

    private fun drawCover(canvas: Canvas, source: Bitmap, dest: RectF) {
        val scale = max(dest.width() / source.width, dest.height() / source.height)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val left = dest.centerX() - scaledW / 2f
        val top = dest.centerY() - scaledH / 2f
        val save = canvas.save()
        canvas.clipRect(dest)
        canvas.drawBitmap(source, null, RectF(left, top, left + scaledW, top + scaledH), null)
        canvas.restoreToCount(save)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        slot: VisioProTextSlot,
        docWidth: Int,
        bold: Boolean = false,
    ) {
        val rect = RectF(slot.left.toFloat(), slot.top.toFloat(), slot.right.toFloat(), slot.bottom.toFloat())
        val paint = createTextPaint(slot, docWidth, bold)
        val targetSize = docWidth * slot.fontSizeRatio
        if (slot.autoFit) {
            fitTextSize(paint, text, rect, targetSize)
        } else {
            paint.textSize = targetSize
        }
        drawTextInRect(canvas, text, rect, slot, docWidth, paint)
    }

    private fun createTextPaint(slot: VisioProTextSlot, docWidth: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slot.color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = when (slot.align) {
                "left" -> Paint.Align.LEFT
                "right" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            if (bold) {
                isFakeBoldText = true
            }
        }

    private fun fitTextSize(paint: Paint, text: String, rect: RectF, targetFontSize: Float) {
        val padX = rect.width() * 0.08f
        val padY = rect.height() * 0.06f
        val maxW = rect.width() - padX * 2f
        val maxH = rect.height() - padY * 2f

        var fontSize = targetFontSize.coerceIn(12f, maxH * 0.98f)
        paint.textSize = fontSize

        var textW = paint.measureText(text)
        var textH = paint.descent() - paint.ascent()

        while ((textW > maxW || textH > maxH) && fontSize > 12f) {
            fontSize *= 0.92f
            paint.textSize = fontSize
            textW = paint.measureText(text)
            textH = paint.descent() - paint.ascent()
        }
    }

    private fun drawTextInRect(
        canvas: Canvas,
        text: String,
        rect: RectF,
        slot: VisioProTextSlot,
        docWidth: Int,
        paint: Paint,
    ) {
        val x = when (paint.textAlign) {
            Paint.Align.LEFT -> rect.left + rect.width() * 0.08f
            Paint.Align.RIGHT -> rect.right - rect.width() * 0.08f
            else -> rect.centerX()
        }
        val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        if (slot.strokeColor != null && slot.strokeWidthRatio > 0f) {
            val stroke = Paint(paint).apply {
                color = slot.strokeColor
                style = Paint.Style.STROKE
                strokeWidth = docWidth * slot.strokeWidthRatio
            }
            canvas.drawText(text, x, y, stroke)
        }
        paint.style = Paint.Style.FILL
        canvas.drawText(text, x, y, paint)
    }
}
