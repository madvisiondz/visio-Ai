package com.oasismall.oasisai.domain.phonesync

import com.oasismall.oasisai.data.repository.OasisRepository
import java.time.Instant

class PhoneSyncCatalogService(
    private val repository: OasisRepository,
) {
    suspend fun buildCatalog(deviceName: String): PhoneSyncCatalog {
        val rows = repository.getPhoneSyncCatalogRows()
        val alternates = repository.getPhoneSyncAlternatePairs()
        val entries = rows.map { row ->
            PhoneSyncCatalogEntry(
                barcode = row.barcode,
                codeart = row.codeart,
                designation = row.designation,
                hasImage = row.hasImage,
            )
        }
        val imageCount = entries.count { it.hasImage }
        val textLines = buildString {
            appendLine("barcode\tcodeart\tdesignation\thas_image")
            entries.forEach { e ->
                append(e.barcode)
                append('\t')
                append(e.codeart.orEmpty())
                append('\t')
                append(e.designation.replace('\t', ' '))
                append('\t')
                append(if (e.hasImage) "1" else "0")
                appendLine()
            }
        }
        return PhoneSyncCatalog(
            exportedAt = Instant.now().toString(),
            deviceName = deviceName,
            articleCount = entries.size,
            imageCount = imageCount,
            catalogText = textLines,
            entries = entries,
            alternateBarcodes = alternates.map {
                PhoneSyncAlternateEntry(barcode = it.alternateBarcode, primaryBarcode = it.primaryBarcode)
            },
        )
    }
}
