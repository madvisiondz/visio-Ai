package com.oasismall.oasisai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CsvParserGestiumTest {

    private val gestiumHeader =
        """
        "Désignation",,"Code","Référence","Famille","Stock réel","Unité de mesure (Achat)","Unité de mesure (Vente)","Prix de vente  HT","Prix de vente  TTC","Qté/Colis","Nbr. de Colis","TVA","Prix Vente (2) HT","Prix Vente (2) TTC","Marque","Catégorie","Rayon","Type","QCF","Stock facturé","Code-barres","Code Fournisseur"
        """.trimIndent()

    private val piedsDeVeauRow =
        """
        "PIEDS DE VEAU",,"13145","","033","-56","","","1 000,00","1 000,00","1","","0,00","200,00","200,00","","","Boucherie","N/D","0","0","","FR184"
        """.trimIndent()

    @Test
    fun parseGestiumRowWithoutBarcode_usesCodeartAsIdentity() {
        val csv = "$gestiumHeader\n$piedsDeVeauRow"
        val cp1252 = Charset.forName("Windows-1252")
        val result = CsvParser.parse(csv.byteInputStream(cp1252), cp1252)
        val row = result.rows.single { it.designation == "PIEDS DE VEAU" }
        assertEquals("CA:13145", row.barcode)
        assertEquals("13145", row.codeart)
        assertEquals("Boucherie", row.rayon)
        assertEquals(1000.0, row.price, 0.01)
    }

    @Test
    fun emptyBarcodeRow_isNotSkipped() {
        val csv = "$gestiumHeader\n$piedsDeVeauRow"
        val cp1252 = Charset.forName("Windows-1252")
        val result = CsvParser.parse(csv.byteInputStream(cp1252), cp1252)
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.barcodeLessRows)
        assertEquals(1, result.rows.size)
    }

    @Test
    fun parseWithFallback_prefersCharsetWithMostRows() {
        val csv = "$gestiumHeader\n$piedsDeVeauRow"
        val bytes = csv.toByteArray(Charset.forName("Windows-1252"))
        val result = CsvParser.parseWithFallback(bytes.inputStream())
        assertTrue(result.rows.any { it.designation == "PIEDS DE VEAU" })
        assertEquals("CA:13145", result.rows.first { it.designation == "PIEDS DE VEAU" }.barcode)
        assertEquals(0, result.skippedRows)
    }

    @Test
    fun garbledDesignation_withQuestionMarkRuns_isSkipped() {
        val garbledRow =
            """
            "???? ???? ? ???? ??????? ? ??????? ????????",,"75555","","4000","-1","","","290,00","290,00","1","","0,00","298,70","298,70","","","Librairie","N/D","0","0","9789961475119","0039"
            """.trimIndent()
        val goodRow =
            """
            "SKOR SUCRE CEVITAL 1KG",,"00008","1200.PALL","","4978","","","90,00","90,00","10","498","0,00","88,00","88,00","CEVITAL","","Epicerie","N/D","93660","0","6130234002366","FR714"
            """.trimIndent()
        val csv = "$gestiumHeader\n$garbledRow\n$goodRow"
        val cp1252 = Charset.forName("Windows-1252")
        val result = CsvParser.parse(csv.byteInputStream(cp1252), cp1252)
        assertEquals(1, result.garbledDesignationRows)
        assertEquals(1, result.rows.size)
        assertEquals("SKOR SUCRE CEVITAL 1KG", result.rows.single().designation)
    }

    @Test
    fun isGarbledDesignation_detectsRunsAndHighQuestionRatio() {
        assertTrue(CsvParser.isGarbledDesignation("???? ???? ? ????"))
        assertTrue(CsvParser.isGarbledDesignation("??? ??????? 5 ???????"))
        assertFalse(CsvParser.isGarbledDesignation("SKOR SUCRE CEVITAL 1KG"))
        assertFalse(CsvParser.isGarbledDesignation("WAFA FILM TRANSPARENT 200M"))
    }
}
