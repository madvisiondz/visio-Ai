package com.oasismall.oasisai.domain.visiopro

import com.oasismall.oasisai.data.db.dao.ArticleWithImage

/** Resolves the short code printed on VisioPRO fv_print labels (`code : XX`). */
object VisioProPrintCode {

    fun resolve(article: ArticleWithImage): String? {
        val barcode = article.barcode.trim()
        if (barcode.startsWith("CA:", ignoreCase = true)) {
            val codeart = article.codeart?.trim()?.takeIf { it.isNotBlank() }
            val fromSynthetic = barcode.substringAfter(':').trim().takeIf { it.isNotBlank() }
            return codeart ?: fromSynthetic
        }
        val digits = barcode.filter { it.isDigit() }
        if (digits.length >= 3) {
            return digits.takeLast(3)
        }
        val codeartDigits = article.codeart?.filter { it.isDigit() }.orEmpty()
        return when {
            codeartDigits.length >= 3 -> codeartDigits.takeLast(3)
            codeartDigits.isNotBlank() -> codeartDigits
            else -> null
        }
    }
}
