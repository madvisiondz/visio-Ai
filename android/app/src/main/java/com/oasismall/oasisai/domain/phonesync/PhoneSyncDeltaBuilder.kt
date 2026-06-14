package com.oasismall.oasisai.domain.phonesync

import com.oasismall.oasisai.data.repository.OasisRepository
import java.io.File

data class PhoneSyncOutboundItem(
    val barcode: String,
    val codeart: String?,
    val designation: String,
    val price: Double,
    val imageFile: File,
    val alternateBarcodes: List<String>,
)

class PhoneSyncDeltaBuilder(
    private val repository: OasisRepository,
) {
    /**
     * Compare slave local PNG work against master catalog; return items master still needs.
     */
    suspend fun buildOutbound(masterCatalog: PhoneSyncCatalog): List<PhoneSyncOutboundItem> {
        val masterImageBarcodes = masterCatalog.entries
            .filter { it.hasImage }
            .map { it.barcode }
            .toSet()
        val masterImageCodearts = masterCatalog.entries
            .filter { it.hasImage && !it.codeart.isNullOrBlank() }
            .mapNotNull { it.codeart }
            .toSet()
        val masterAlternateBarcodes = masterCatalog.alternateBarcodes
            .map { it.barcode }
            .toSet()

        val localSources = repository.getPhoneSyncPushSources()
        val seen = linkedSetOf<String>()
        val outbound = mutableListOf<PhoneSyncOutboundItem>()

        for (row in localSources) {
            val file = File(row.imagePath)
            if (!file.isFile) continue
            val key = row.barcode
            if (!seen.add(key)) continue

            val masterHasImage = masterImageBarcodes.contains(row.barcode) ||
                (!row.codeart.isNullOrBlank() && masterImageCodearts.contains(row.codeart))
            if (masterHasImage) continue

            val alternates = repository.getAlternateBarcodesForArticle(row.articleId)
                .filter { it !in masterAlternateBarcodes }

            outbound.add(
                PhoneSyncOutboundItem(
                    barcode = row.barcode,
                    codeart = row.codeart,
                    designation = row.designation,
                    price = row.price,
                    imageFile = file,
                    alternateBarcodes = alternates,
                ),
            )
        }
        return outbound
    }
}
