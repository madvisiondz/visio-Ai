package com.oasismall.oasisai.domain.transfer

import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.flavors.SubBarcodeFlavorService
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore

class GestiumCatalogPurge(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val visioProCatalogConfigStore: VisioProCatalogConfigStore,
    private val subBarcodeFlavorService: SubBarcodeFlavorService,
) {
    suspend fun purge(): PurgeResult {
        val articleCount = repository.getAllArticles().size
        val archive = subBarcodeFlavorService.archiveBeforePurge()
        repository.purgeGestiumCatalog()
        visioProCatalogConfigStore.clearAll()
        imageMatcher.invalidatePngCache()
        return PurgeResult(
            articlesRemoved = articleCount,
            subBarcodeFlavorsArchived = archive.registryEntries,
        )
    }
}

data class PurgeResult(
    val articlesRemoved: Int,
    val subBarcodeFlavorsArchived: Int = 0,
)
