package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import org.junit.Assert.assertEquals
import org.junit.Test

class VisioProPrintCodeTest {

    private fun article(
        barcode: String,
        codeart: String? = null,
        designation: String = "TEST",
    ) = ArticleWithImage(
        id = 1L,
        barcode = barcode,
        codeart = codeart,
        designation = designation,
        normalizedName = designation,
        price = 100.0,
        previousPrice = null,
        reference = null,
        category = null,
        brand = null,
        stock = null,
        unit = null,
        changeStatus = "UNCHANGED",
        isActive = true,
        needsTicketUpdate = false,
        rawData = null,
        imagePath = null,
        imageStatus = null,
        imageCreatedAt = null,
        imageLastSentAt = null,
    )

    @Test
    fun retailBarcode_usesLastThreeDigits() {
        assertEquals("032", VisioProPrintCode.resolve(article("2500032", "12514")))
    }

    @Test
    fun syntheticBarcode_usesGestiumCode() {
        assertEquals("13145", VisioProPrintCode.resolve(article("CA:13145", "13145", "PIEDS DE VEAU")))
    }

    @Test
    fun eanBarcode_usesLastThreeDigits() {
        assertEquals("755", VisioProPrintCode.resolve(article("7893000558755", "86075")))
    }
}
