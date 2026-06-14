package com.oasismall.oasisai.util

/**
 * Gestium / store barcodes: 3–4 digit prefix + 9–10 digit product suffix (same suffix = same article).
 */
object BarcodeSuffixMatcher {

    fun digitsOnly(barcode: String): String = barcode.filter { it.isDigit() }

    /** Candidate suffixes after stripping a 3- or 4-digit prefix from the scanned barcode. */
    fun candidateSuffixes(barcode: String): List<String> {
        val digits = digitsOnly(barcode)
        if (digits.length < 12) return emptyList()
        val out = linkedSetOf<String>()
        if (digits.length > 3) {
            val s = digits.drop(3)
            if (s.length in 9..10) out.add(s)
        }
        if (digits.length > 4) {
            val s = digits.drop(4)
            if (s.length in 9..10) out.add(s)
        }
        return out.toList()
    }

    /** Primary suffix label for UI (prefer 10-digit after 3-digit prefix). */
    fun primarySuffixLabel(barcode: String): String {
        val digits = digitsOnly(barcode)
        if (digits.length > 3) {
            val s = digits.drop(3)
            if (s.length == 10) return s
        }
        if (digits.length > 4) {
            val s = digits.drop(4)
            if (s.length == 9) return s
        }
        return candidateSuffixes(barcode).firstOrNull() ?: digits.takeLast(10).ifBlank { digits }
    }

    fun lastFourDigits(barcode: String): String {
        val digits = digitsOnly(barcode)
        return if (digits.length >= 4) digits.takeLast(4) else digits
    }

    /**
     * Gestium repack rule: remove the last 5 digits, then compare the first 9 digits on the left.
     * Example: `61302340023666` → drop last 5 → first 9 of remainder = body key.
     */
    fun gestiumBodyKey(barcode: String): String? {
        val digits = digitsOnly(barcode)
        if (digits.length < 14) return null
        val withoutPackSuffix = digits.dropLast(5)
        if (withoutPackSuffix.length < 9) return null
        return withoutPackSuffix.take(9)
    }

    /** True when barcodes share the same body but last 5 digits differ (repack / new batch). */
    fun lastFourDiffer(a: String, b: String): Boolean {
        val da = digitsOnly(a)
        val db = digitsOnly(b)
        if (da.length < 9 || db.length < 9) return false
        if (da.takeLast(5) == db.takeLast(5)) return false
        val bodyA = da.dropLast(5)
        val bodyB = db.dropLast(5)
        return bodyA == bodyB ||
            (bodyA.length >= 8 && bodyB.length >= 8 && bodyA.takeLast(8) == bodyB.takeLast(8))
    }

    fun suffixOfCatalogBarcode(barcode: String): String {
        val digits = digitsOnly(barcode)
        if (digits.length > 3) {
            val s = digits.drop(3)
            if (s.length in 9..10) return s
        }
        if (digits.length > 4) {
            val s = digits.drop(4)
            if (s.length in 9..10) return s
        }
        return digits.takeLast(10).ifBlank { digits }
    }
}
