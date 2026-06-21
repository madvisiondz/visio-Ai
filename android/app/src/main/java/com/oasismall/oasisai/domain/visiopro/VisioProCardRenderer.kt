package com.oasismall.oasisai.domain.visiopro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDefaults
import com.oasismall.oasisai.domain.visiopro.designer.VisioProNormRect
import com.oasismall.oasisai.util.PriceFormatter
import kotlin.math.min

object VisioProCardRenderer {

    data class RenderInput(
        val preset: VisioProPreset,
        val price: Double?,
        val productBitmap: Bitmap? = null,
        val design: com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument? = null,
    )

    fun render(input: RenderInput): Bitmap {
        val design = input.design
        val theme = if (design != null) {
            VisioProDesignerDefaults.themeFromDocument(design)
        } else {
            input.preset.theme
        }
        val preset = input.preset.copy(theme = theme)
        val bitmap = Bitmap.createBitmap(theme.widthPx, theme.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = theme.widthPx.toFloat()
        val h = theme.heightPx.toFloat()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                theme.backgroundTop,
                theme.backgroundBottom,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val bandH = (design?.headerBandHeight ?: if (preset.channel == VisioProChannel.SOCIAL) 0.12f else 0.10f) * h
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.headerBand }
        canvas.drawRect(0f, 0f, w, bandH, bandPaint)

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleOnBand
            textSize = if (input.preset.channel == VisioProChannel.SOCIAL) w * 0.028f else w * 0.032f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(theme.categoryTag, w * 0.05f, bandH * 0.62f, tagPaint)

        val mallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleOnBand
            textSize = w * 0.024f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("OASIS MALL", w * 0.05f, bandH * 0.92f, mallPaint)

        val photoNorm = design?.photoRect ?: VisioProNormRect(0.08f, (bandH / h) + 0.04f, 0.92f, if (theme.showPrice) 0.72f else 0.88f)
        val photoRect = RectF(
            photoNorm.left * w,
            photoNorm.top * h,
            photoNorm.right * w,
            photoNorm.bottom * h,
        )
        val photoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(photoRect, w * 0.04f, w * 0.04f, photoBg)

        input.productBitmap?.let { product ->
            drawCenterCrop(canvas, product, photoRect)
        } ?: run {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = min(photoRect.width(), photoRect.height()) * 0.45f
                textAlign = Paint.Align.CENTER
            }
            val emoji = categoryEmoji(input.preset.category)
            canvas.drawText(
                emoji,
                photoRect.centerX(),
                photoRect.centerY() + emojiPaint.textSize * 0.12f,
                emojiPaint,
            )
        }

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = design?.designationColor ?: theme.bodyText
            textSize = w * (design?.designationFontRatio ?: if (preset.channel == VisioProChannel.SOCIAL) 0.11f else 0.13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val nameRect = design?.designationRect
        val nameY = nameRect?.let { (it.top + it.bottom) / 2f * h + namePaint.textSize * 0.35f }
            ?: if (theme.showPrice) h * 0.80f else h * 0.92f
        val nameX = nameRect?.left?.times(w) ?: w * 0.08f
        val label = design?.sampleDesignation ?: preset.article.labelFr
        canvas.drawText(label.uppercase(), nameX, nameY, namePaint)

        if (theme.showPrice) {
            val priceLabel = input.price?.let { PriceFormatter.format(it) } ?: "— DA"
            val unit = theme.unitSuffix?.let { " $it" }.orEmpty()
            val priceNorm = design?.priceRect ?: VisioProNormRect(0.48f, 0.84f, 0.92f, 0.94f)
            val priceLeft = priceNorm.left * w
            val priceTop = priceNorm.top * h
            val priceBoxW = (priceNorm.right - priceNorm.left) * w
            val priceBoxH = (priceNorm.bottom - priceNorm.top) * h
            val priceRect = RectF(priceLeft, priceTop, priceLeft + priceBoxW, priceTop + priceBoxH)
            val priceBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.priceBackground }
            canvas.drawRoundRect(priceRect, w * 0.02f, w * 0.02f, priceBg)

            val accentStripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent }
            canvas.drawRect(priceLeft, priceTop, priceLeft + w * 0.018f, priceTop + priceBoxH, accentStripe)

            val isSocial = input.preset.channel == VisioProChannel.SOCIAL
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = design?.priceColor ?: theme.priceText
                val defaultRatio = if (isSocial) 0.102f else 0.088f
                textSize = w * (design?.priceFontRatio ?: defaultRatio)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isFakeBoldText = true
            }
            canvas.drawText(
                priceLabel,
                priceLeft + w * 0.05f,
                priceTop + priceBoxH * 0.58f,
                pricePaint,
            )

            if (unit.isNotBlank()) {
                val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.priceText
                    textSize = w * 0.035f
                }
                canvas.drawText(
                    unit.trim(),
                    priceLeft + w * 0.05f,
                    priceTop + priceBoxH * 0.88f,
                    unitPaint,
                )
            }
        }

        if (input.preset.channel == VisioProChannel.PRINT) {
            input.preset.article.barcodeSuffix?.let { code ->
                drawPrintCodeBadge(
                    canvas = canvas,
                    code = code,
                    w = w,
                    h = h,
                    theme = theme,
                    design = design,
                )
            }
        }

        return bitmap
    }

    private fun drawPrintCodeBadge(
        canvas: Canvas,
        code: String,
        w: Float,
        h: Float,
        theme: VisioProTheme,
        design: com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument?,
    ) {
        val codeNorm = design?.codeRect
        val rect = if (codeNorm != null) {
            RectF(
                codeNorm.left * w,
                codeNorm.top * h,
                codeNorm.right * w,
                codeNorm.bottom * h,
            )
        } else {
            val boxW = w * 0.16f
            val boxH = h * 0.045f
            val left = w * 0.06f
            val top = h * 0.035f
            RectF(left, top, left + boxW, top + boxH)
        }
        if (codeNorm == null) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.headerBand }
            canvas.drawRoundRect(rect, w * 0.012f, w * 0.012f, bg)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = design?.codeColor ?: android.graphics.Color.BLACK
            textSize = if (codeNorm != null) {
                w * (design?.codeFontRatio ?: 0.035f)
            } else {
                rect.height() * 0.62f
            }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val label = if (codeNorm != null) "code : $code" else code
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() - (paint.descent() + paint.ascent()) / 2f,
            paint,
        )
    }

    private fun categoryEmoji(category: VisioProCategory): String = when (category) {
        VisioProCategory.FRUITS -> "🍎"
        VisioProCategory.VEGETABLES -> "🥬"
        VisioProCategory.BUTCHER -> "🥩"
        VisioProCategory.FISH -> "🐟"
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, dest: RectF) {
        val scale = maxOf(dest.width() / source.width, dest.height() / source.height)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val left = dest.centerX() - scaledW / 2f
        val top = dest.centerY() - scaledH / 2f
        val save = canvas.save()
        canvas.clipRect(dest)
        canvas.drawBitmap(source, null, RectF(left, top, left + scaledW, top + scaledH), null)
        canvas.restoreToCount(save)
    }
}
