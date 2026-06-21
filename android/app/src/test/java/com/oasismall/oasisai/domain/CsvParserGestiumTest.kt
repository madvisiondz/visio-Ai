package com.oasismall.oasisai.domain

import org.junit.Assert.assertEquals
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
}
