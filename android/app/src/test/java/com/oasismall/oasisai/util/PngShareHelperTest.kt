package com.oasismall.oasisai.util

import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PngShareHelperTest {

    private fun item(
        designation: String,
        barcode: String = "1234567890123",
        variantBarcode: String = "",
        imagePath: String? = null,
    ) = PreselectionWithArticle(
        preselectionId = 1L,
        articleId = 10L,
        sortOrder = 0,
        addedAt = 0L,
        note = null,
        intendedTemplateType = null,
        variantBarcode = variantBarcode,
        designation = designation,
        barcode = barcode,
        price = 9.99,
        previousPrice = null,
        codeart = null,
        category = null,
        imagePath = imagePath,
        imageCreatedAt = null,
        imageLastSentAt = null,
    )

    @Test
    fun targetFileName_mainArticle_usesSpacedDesignation() {
        val name = PngShareHelper.targetFileName(
            item("TACO DELICE TORTILLAS BLE N 900GR"),
        )
        assertEquals("TACO DELICE TORTILLAS BLE N 900GR.png", name)
    }

    @Test
    fun targetFileName_subVariant_usesSpacedDesignationAndIndex() {
        val dir = Files.createTempDirectory("png-share-test").toFile()
        val gallery = File(dir, "MAASDAMGRUYERELEFRIANDCESAR3.png")
        gallery.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))

        val name = PngShareHelper.targetFileName(
            item(
                designation = "MAASDAM GRUYERE LE FRIAND CESAR",
                imagePath = gallery.absolutePath,
            ),
        )
        assertEquals("MAASDAM GRUYERE LE FRIAND CESAR 3.png", name)
        gallery.delete()
        dir.delete()
    }

    @Test
    fun targetFileName_subVariants_produceDistinctNames() {
        val dir = Files.createTempDirectory("png-share-distinct").toFile()
        val d1 = File(dir, "CHEESEPLATE1.png").apply { writeBytes(byteArrayOf(0x89.toByte())) }
        val d2 = File(dir, "CHEESEPLATE2.png").apply { writeBytes(byteArrayOf(0x89.toByte())) }

        val n1 = PngShareHelper.targetFileName(
            item("CHEESE PLATE", imagePath = d1.absolutePath),
        )
        val n2 = PngShareHelper.targetFileName(
            item("CHEESE PLATE", imagePath = d2.absolutePath),
        )
        assertNotEquals(n1, n2)
        assertEquals("CHEESE PLATE 1.png", n1)
        assertEquals("CHEESE PLATE 2.png", n2)
        dir.deleteRecursively()
    }
}
