package com.oasismall.oasisai.domain

import com.oasismall.oasisai.data.db.dao.ArticleImportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportCatalogMapsTest {

    private fun snapshot(
        id: Long,
        barcode: String,
        designation: String,
        normalizedName: String = designation.uppercase(),
        codeart: String? = null,
        lastSeenAt: Long = id,
    ) = ArticleImportSnapshot(
        id = id,
        barcode = barcode,
        designation = designation,
        normalizedName = normalizedName,
        price = 10.0,
        previousPrice = null,
        codeart = codeart,
        reference = null,
        category = null,
        rayon = null,
        famille = null,
        brand = null,
        stock = null,
        unit = null,
        lastSeenAt = lastSeenAt,
        changeStatus = "UNCHANGED",
        isActive = true,
        needsTicketUpdate = false,
    )

    @Test
    fun buildImportCatalogMaps_indexesByBarcodeAndCodeart() {
        val maps = buildImportCatalogMaps(
            listOf(
                snapshot(1, "111", "APPLE", codeart = "A01"),
                snapshot(2, "222", "BANANA", codeart = "B02"),
            ),
        )
        assertEquals("APPLE", maps.byBarcode["111"]?.designation)
        assertEquals("BANANA", maps.byCodeart["B02"]?.designation)
    }

    @Test
    fun buildImportCatalogMaps_normalizedNameKeepsMostRecentlySeen() {
        val maps = buildImportCatalogMaps(
            listOf(
                snapshot(1, "111", "OLD NAME", normalizedName = "SAME", lastSeenAt = 100L),
                snapshot(2, "222", "NEW NAME", normalizedName = "SAME", lastSeenAt = 200L),
            ),
        )
        assertEquals("NEW NAME", maps.byNormalizedName["SAME"]?.designation)
    }

    @Test
    fun buildImportCatalogMaps_missingBarcode_returnsNull() {
        val maps = buildImportCatalogMaps(emptyList())
        assertNull(maps.byBarcode["missing"])
    }
}
