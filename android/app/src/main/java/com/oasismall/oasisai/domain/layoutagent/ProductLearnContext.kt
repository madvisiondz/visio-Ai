package com.oasismall.oasisai.domain.layoutagent

import android.graphics.Bitmap

data class ProductLearnContext(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val imagePath: String,
)

data class ProductObservation(
    val bitmap: Bitmap,
    val contentBounds: ContentBounds,
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val imagePath: String,
)
