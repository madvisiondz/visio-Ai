package com.oasismall.oasisai.domain.visiopro.designer

import com.oasismall.oasisai.domain.visiopro.VisioProTextSlot

/** Normalized rectangle (0–1) on the preset canvas. */
data class VisioProNormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left < right && top < bottom) { "Invalid norm rect" }
    }

    fun translate(dx: Float, dy: Float, minSize: Float = 0.05f): VisioProNormRect {
        val w = right - left
        val h = bottom - top
        var l = (left + dx).coerceIn(0f, 1f - w)
        var t = (top + dy).coerceIn(0f, 1f - h)
        return copy(left = l, top = t, right = l + w, bottom = t + h)
    }

    fun toPixelRect(canvasWidth: Int, canvasHeight: Int): VisioProTextSlot =
        VisioProTextSlot(
            left = (left * canvasWidth).toInt(),
            top = (top * canvasHeight).toInt(),
            right = (right * canvasWidth).toInt(),
            bottom = (bottom * canvasHeight).toInt(),
            color = 0,
            fontSizeRatio = 0.04f,
        )
}

/** Full canvas design for one preset family — persisted offline. */
data class VisioProDesignerDocument(
    val presetKey: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val templateId: String?,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val headerBand: Int,
    val accent: Int,
    val titleOnBand: Int,
    val bodyText: Int,
    val priceBackground: Int,
    val priceText: Int,
    val categoryTag: String,
    val showPrice: Boolean,
    val unitSuffix: String?,
    val headerBandHeight: Float = 0.12f,
    val photoRect: VisioProNormRect,
    val designationRect: VisioProNormRect,
    val priceRect: VisioProNormRect,
    val codeRect: VisioProNormRect? = null,
    val designationColor: Int,
    val designationStrokeColor: Int? = null,
    val designationFontRatio: Float = 0.042f,
    val priceColor: Int,
    val priceFontRatio: Float = 0.085f,
    val priceAutoFit: Boolean = true,
    val codeColor: Int = 0xFFFFFFFF.toInt(),
    val codeFontRatio: Float = 0.035f,
    val sampleDesignation: String = "ABRICOT FRAIS",
    val sampleDesignationAr: String = "مشمش",
    val samplePrice: Double = 450.0,
    val sampleCode: String = "032",
    val modifiedAt: Long = System.currentTimeMillis(),
) {
    fun rectFor(layer: VisioProDesignLayerKind): VisioProNormRect? = when (layer) {
        VisioProDesignLayerKind.PHOTO -> photoRect
        VisioProDesignLayerKind.DESIGNATION -> designationRect
        VisioProDesignLayerKind.PRICE -> priceRect
        VisioProDesignLayerKind.CODE -> codeRect
        VisioProDesignLayerKind.HEADER,
        VisioProDesignLayerKind.BACKGROUND,
        -> null
    }

    fun withRect(layer: VisioProDesignLayerKind, rect: VisioProNormRect): VisioProDesignerDocument = when (layer) {
        VisioProDesignLayerKind.PHOTO -> copy(photoRect = rect)
        VisioProDesignLayerKind.DESIGNATION -> copy(designationRect = rect)
        VisioProDesignLayerKind.PRICE -> copy(priceRect = rect)
        VisioProDesignLayerKind.CODE -> copy(codeRect = rect)
        else -> this
    }

    fun designationSlot(): VisioProTextSlot {
        val base = designationRect.toPixelRect(canvasWidth, canvasHeight)
        return base.copy(
            color = designationColor,
            strokeColor = designationStrokeColor,
            strokeWidthRatio = if (designationStrokeColor != null) 0.004f else 0f,
            fontSizeRatio = designationFontRatio,
            align = "center",
        )
    }

    fun priceSlot(): VisioProTextSlot {
        val base = priceRect.toPixelRect(canvasWidth, canvasHeight)
        return base.copy(
            color = priceColor,
            fontSizeRatio = priceFontRatio,
            align = "center",
            autoFit = priceAutoFit,
        )
    }

    fun codeSlot(): VisioProTextSlot? {
        val rect = codeRect ?: return null
        val base = rect.toPixelRect(canvasWidth, canvasHeight)
        return base.copy(
            color = codeColor,
            fontSizeRatio = codeFontRatio,
            align = "center",
            format = "code : {value}",
        )
    }

    fun photoPixels(): QuadInt = QuadInt(
        (photoRect.left * canvasWidth).toInt(),
        (photoRect.top * canvasHeight).toInt(),
        (photoRect.right * canvasWidth).toInt(),
        (photoRect.bottom * canvasHeight).toInt(),
    )
}

data class QuadInt(val left: Int, val top: Int, val right: Int, val bottom: Int)
