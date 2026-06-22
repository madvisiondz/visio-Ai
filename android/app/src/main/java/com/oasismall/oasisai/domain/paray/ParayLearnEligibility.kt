package com.oasismall.oasisai.domain.paray

import android.graphics.BitmapFactory
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import java.io.File

/** Trusted-product eligibility for PARAY Learn queue. */
object ParayLearnEligibility {
    data class Result(
        val eligible: Boolean,
        val reason: String? = null,
    )

    fun evaluate(article: ArticleWithImage): Result {
        if (!article.isActive) return Result(false, "Inactive article")
        if (article.barcode.isBlank()) return Result(false, "Missing barcode")
        val path = article.imagePath?.trim().orEmpty()
        if (path.isBlank()) return Result(false, "Missing PNG path")
        if (article.imageStatus != null && article.imageStatus != "FOUND") {
            return Result(false, "PNG not linked (status=${article.imageStatus})")
        }
        val file = File(path)
        if (!file.isFile) return Result(false, "PNG file missing on disk")
        if (file.length() <= 0L) return Result(false, "Corrupted PNG (empty file)")
        if (!isDecodablePng(path)) return Result(false, "Corrupted PNG (decode failed)")
        return Result(true)
    }

    fun filterReady(articles: List<ArticleWithImage>): List<ArticleWithImage> =
        articles.filter { evaluate(it).eligible }

    /** Lightweight bounds-only decode — verifies PNG is readable without loading pixels. */
    private fun isDecodablePng(path: String): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}
