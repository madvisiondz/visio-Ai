package com.oasismall.oasisai.domain.visiopro.designer

import android.graphics.Color
import com.oasismall.oasisai.domain.visiopro.VisioProChannel
import com.oasismall.oasisai.domain.visiopro.VisioProPresetCatalog
import com.oasismall.oasisai.domain.visiopro.VisioProTemplateAssets
import com.oasismall.oasisai.domain.visiopro.VisioProTheme

object VisioProDesignerDefaults {

    fun defaultDocument(
        key: VisioProPresetDesignKey,
        templateAssets: VisioProTemplateAssets? = null,
    ): VisioProDesignerDocument {
        val theme = VisioProPresetCatalog.themeFor(key.category, key.channel)
            ?: error("No theme for $key")
        val templateId = theme.templateId
        val layout = templateId?.let { templateAssets?.layout(it) }

        val photoRect = if (layout != null) {
            normRect(
                layout.photoLeft,
                layout.photoTop,
                layout.photoRight,
                layout.photoBottom,
                layout.width,
                layout.height,
            )
        } else {
            VisioProNormRect(0.08f, 0.16f, 0.92f, 0.72f)
        }

        val designationRect = layout?.designation?.let {
            normRect(it.left, it.top, it.right, it.bottom, layout.width, layout.height)
        } ?: VisioProNormRect(0.08f, 0.74f, 0.92f, 0.82f)

        val priceRect = layout?.price?.let {
            normRect(it.left, it.top, it.right, it.bottom, layout.width, layout.height)
        } ?: VisioProNormRect(0.48f, 0.84f, 0.92f, 0.94f)

        val codeRect = layout?.code?.let {
            normRect(it.left, it.top, it.right, it.bottom, layout.width, layout.height)
        }

        val isFvPrint = templateId == "fv_print"
        val isPrint = key.channel == VisioProChannel.PRINT

        return VisioProDesignerDocument(
            presetKey = key.storageKey,
            canvasWidth = layout?.width ?: theme.widthPx,
            canvasHeight = layout?.height ?: theme.heightPx,
            templateId = templateId,
            backgroundTop = theme.backgroundTop,
            backgroundBottom = theme.backgroundBottom,
            headerBand = theme.headerBand,
            accent = theme.accent,
            titleOnBand = theme.titleOnBand,
            bodyText = theme.bodyText,
            priceBackground = theme.priceBackground,
            priceText = theme.priceText,
            categoryTag = theme.categoryTag,
            showPrice = theme.showPrice,
            unitSuffix = theme.unitSuffix,
            headerBandHeight = if (key.channel.name == "SOCIAL") 0.12f else 0.10f,
            photoRect = photoRect,
            designationRect = designationRect,
            priceRect = priceRect,
            codeRect = codeRect,
            designationColor = when {
                isFvPrint -> Color.BLACK
                isPrint -> Color.BLACK
                else -> layout?.designation?.color ?: theme.bodyText
            },
            designationStrokeColor = when {
                isFvPrint || isPrint -> null
                else -> layout?.designation?.strokeColor
            },
            designationFontRatio = layout?.designation?.fontSizeRatio ?: 0.042f,
            priceColor = layout?.price?.color ?: theme.priceText,
            priceFontRatio = layout?.price?.fontSizeRatio ?: 0.085f,
            priceAutoFit = false,
            codeColor = when {
                isFvPrint || isPrint -> Color.BLACK
                else -> layout?.code?.color ?: Color.WHITE
            },
            codeFontRatio = layout?.code?.fontSizeRatio ?: 0.035f,
            sampleDesignation = sampleLabel(key),
            sampleDesignationAr = sampleLabelAr(key),
        )
    }

    private fun sampleLabel(key: VisioProPresetDesignKey): String = when (key.category) {
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.FRUITS -> "ABRICOT FRAIS"
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.VEGETABLES -> "TOMATE"
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.BUTCHER -> "Poulet entier"
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.FISH -> "Dorade"
    }

    private fun sampleLabelAr(key: VisioProPresetDesignKey): String = when (key.category) {
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.FRUITS -> "مشمش"
        com.oasismall.oasisai.domain.visiopro.VisioProCategory.VEGETABLES -> "طماطم"
        else -> sampleLabel(key)
    }

    private fun normRect(l: Int, t: Int, r: Int, b: Int, w: Int, h: Int): VisioProNormRect =
        VisioProNormRect(
            left = l.toFloat() / w,
            top = t.toFloat() / h,
            right = r.toFloat() / w,
            bottom = b.toFloat() / h,
        )

    fun themeFromDocument(doc: VisioProDesignerDocument): VisioProTheme =
        VisioProTheme(
            widthPx = doc.canvasWidth,
            heightPx = doc.canvasHeight,
            backgroundTop = doc.backgroundTop,
            backgroundBottom = doc.backgroundBottom,
            headerBand = doc.headerBand,
            accent = doc.accent,
            titleOnBand = doc.titleOnBand,
            bodyText = doc.bodyText,
            priceBackground = doc.priceBackground,
            priceText = doc.priceText,
            categoryTag = doc.categoryTag,
            showPrice = doc.showPrice,
            unitSuffix = doc.unitSuffix,
            templateId = doc.templateId,
        )
}
