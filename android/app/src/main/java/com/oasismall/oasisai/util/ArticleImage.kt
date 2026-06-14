package com.oasismall.oasisai.util

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.ImageStatus
import java.io.File

fun ArticleWithImage.hasAppGalleryImage(): Boolean {
    val path = imagePath ?: return false
    return imageStatus == ImageStatus.FOUND.name && File(path).exists()
}
