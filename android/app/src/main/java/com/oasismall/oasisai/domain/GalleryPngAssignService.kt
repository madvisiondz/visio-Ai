package com.oasismall.oasisai.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.ui.components.CartSourceTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Assign a user-picked PNG to an article (main or sub-barcode variant). */
class GalleryPngAssignService(
    private val context: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
) {
    suspend fun assignPngToArticle(
        uri: Uri,
        articleId: Long,
        subBarcode: String? = null,
        cartType: CartType? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val article = repository.getArticleById(articleId) ?: error("Article not found")
            val cache = copyUriToCache(uri, articleId, subBarcode)
            val sub = subBarcode?.trim()?.takeIf { it.isNotBlank() }
            if (sub != null) {
                val imagePath = imageMatcher.registerSubBarcodeImage(sub, article, cache)
                val err = repository.linkSubBarcodeToMainArticle(
                    articleId = articleId,
                    mainBarcode = article.barcode,
                    subBarcode = sub,
                    imagePath = imagePath,
                )
                if (err != null) error(err)
            } else {
                imageMatcher.registerCapturedImage(article, cache)
            }
            repository.removeFromCart(articleId, CartType.PHOTOSHOOT)
            when (cartType) {
                CartType.SHARE -> repository.addToCart(
                    articleId,
                    CartType.SHARE,
                    CartSourceTags.MANUAL,
                    variantBarcode = sub,
                )
                CartType.DESIGN -> repository.addToCart(
                    articleId,
                    CartType.DESIGN,
                    CartSourceTags.MANUAL,
                    variantBarcode = sub,
                )
                CartType.PHOTOSHOOT -> repository.addToCart(articleId, CartType.PHOTOSHOOT, CartSourceTags.MANUAL)
                else -> {
                    if (sub == null) {
                        repository.addToCart(articleId, CartType.SHARE, CartSourceTags.MANUAL)
                    }
                }
            }
            if (sub != null) {
                "PNG assigned to sub-barcode $sub (${article.designation})"
            } else {
                "PNG assigned to ${article.designation}"
            }
        }
    }

    private fun copyUriToCache(uri: Uri, articleId: Long, subBarcode: String?): File {
        val displayName = queryDisplayName(uri).ifBlank { "picked.png" }
        val suffix = subBarcode?.take(12) ?: "main"
        val cache = File(context.cacheDir, "gallery_png_assign/${articleId}_${suffix}_$displayName")
        cache.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read PNG file")
        return cache
    }

    private fun queryDisplayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()?.trim().orEmpty()
}
