package com.oasismall.oasisai.domain.visiopro

data class VisioProTextSlot(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val color: Int,
    val strokeColor: Int? = null,
    val strokeWidthRatio: Float = 0f,
    val fontSizeRatio: Float,
    val align: String = "center",
    /** When true, scale type to fill the slot (up for short prices, down for long ones). */
    val autoFit: Boolean = false,
    /** Optional pattern for dynamic text, e.g. `code : {value}`. */
    val format: String? = null,
)

data class VisioProTemplateLayout(
    val templateId: String,
    val width: Int,
    val height: Int,
    val photoLeft: Int,
    val photoTop: Int,
    val photoRight: Int,
    val photoBottom: Int,
    /** `cover` fills and crops; `contain` scales to fit without cropping. */
    val photoFit: String = "cover",
    val designAsset: String,
    val designation: VisioProTextSlot?,
    val code: VisioProTextSlot?,
    val price: VisioProTextSlot?,
)
