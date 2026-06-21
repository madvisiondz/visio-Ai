package com.oasismall.oasisai.domain

import com.oasismall.oasisai.data.db.dao.ArticleImportSnapshot

internal data class ImportCatalogMaps(
    val byBarcode: Map<String, ArticleImportSnapshot>,
    val byCodeart: Map<String, ArticleImportSnapshot>,
    val byNormalizedName: Map<String, ArticleImportSnapshot>,
)

internal fun buildImportCatalogMaps(snapshots: List<ArticleImportSnapshot>): ImportCatalogMaps {
    val byBarcode = HashMap<String, ArticleImportSnapshot>(snapshots.size)
    val byCodeart = HashMap<String, ArticleImportSnapshot>()
    val byNormalizedName = HashMap<String, ArticleImportSnapshot>()
    for (article in snapshots) {
        byBarcode[article.barcode] = article
        article.codeart?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            byCodeart.putIfAbsent(code, article)
        }
        val previous = byNormalizedName[article.normalizedName]
        if (previous == null || article.lastSeenAt >= previous.lastSeenAt) {
            byNormalizedName[article.normalizedName] = article
        }
    }
    return ImportCatalogMaps(byBarcode, byCodeart, byNormalizedName)
}
