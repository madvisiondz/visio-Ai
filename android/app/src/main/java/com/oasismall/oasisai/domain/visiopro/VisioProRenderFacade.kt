package com.oasismall.oasisai.domain.visiopro

import android.graphics.Bitmap

class VisioProRenderFacade(
    private val ailRenderer: VisioProAilRenderer,
    private val templateAssets: VisioProTemplateAssets,
) {
    fun render(
        preset: VisioProPreset,
        price: Double?,
        articlePhoto: Bitmap?,
        catalogPhoto: Bitmap?,
        design: com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument? = null,
        displayDesignation: String? = null,
        designationFontRatio: Float? = null,
    ): Bitmap {
        val themedPreset = design?.let {
            preset.copy(theme = com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDefaults.themeFromDocument(it))
        } ?: preset
        val templateId = themedPreset.theme.templateId
        when (templateId) {
            "ail_social" -> {
                ailRenderer.render(
                    VisioProAilRenderer.RenderInput(
                        preset = themedPreset,
                        price = price,
                        photoBitmap = articlePhoto,
                        design = design,
                        displayDesignation = displayDesignation,
                        designationFontRatio = designationFontRatio,
                    ),
                )?.let { return it }
            }
            "fv_print" -> {
                val printProduct = templateAssets.printProductBitmap(themedPreset.article)
                val photo = articlePhoto ?: printProduct ?: catalogPhoto
                ailRenderer.render(
                    VisioProAilRenderer.RenderInput(
                        preset = themedPreset,
                        price = price,
                        photoBitmap = photo,
                        design = design,
                        displayDesignation = displayDesignation,
                        designationFontRatio = designationFontRatio,
                    ),
                )?.let { return it }
            }
        }
        return VisioProCardRenderer.render(
            VisioProCardRenderer.RenderInput(
                preset = themedPreset,
                price = price,
                productBitmap = articlePhoto ?: catalogPhoto,
                design = design,
                displayDesignation = displayDesignation,
                designationFontRatio = designationFontRatio,
            ),
        )
    }
}
