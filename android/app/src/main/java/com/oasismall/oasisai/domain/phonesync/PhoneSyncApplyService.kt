package com.oasismall.oasisai.domain.phonesync

import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import java.io.File

class PhoneSyncApplyService(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
) {
    suspend fun applyPush(
        manifest: PhoneSyncPushManifest,
        imageFiles: Map<String, File>,
    ): PhoneSyncApplyResult {
        var imagesApplied = 0
        var alternatesLinked = 0
        var skipped = 0
        val messages = mutableListOf<String>()

        for (product in manifest.products) {
            val png = imageFiles[product.imageFilename]
            if (png == null || !png.isFile) {
                skipped++
                messages.add("Missing file ${product.imageFilename} for ${product.barcode}")
                continue
            }
            val article = repository.getArticleByBarcode(product.barcode)
                ?: repository.getArticleWithImageByDesignation(product.designation)?.let {
                    repository.getArticleById(it.id)
                }
                ?: run {
                    repository.ensureUnknownArticle(product.barcode, product.designation)
                }

            runCatching {
                imageMatcher.registerCapturedImage(article, png)
                imagesApplied++
            }.onFailure { e ->
                skipped++
                messages.add("${product.barcode}: ${e.message}")
            }

            for (alt in product.alternateBarcodes) {
                if (alt == product.barcode) continue
                runCatching {
                    repository.linkAlternateBarcode(article.id, alt)
                    alternatesLinked++
                }
            }
        }

        repository.logPhoneSyncReceived(
            deviceName = manifest.deviceName,
            imagesApplied = imagesApplied,
            alternatesLinked = alternatesLinked,
            skipped = skipped,
        )

        return PhoneSyncApplyResult(
            imagesApplied = imagesApplied,
            alternatesLinked = alternatesLinked,
            skipped = skipped,
            messages = messages,
        )
    }
}
