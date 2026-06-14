package com.oasismall.oasisai.util

import java.text.NumberFormat
import java.util.Locale

object PriceFormatter {
    private val format = NumberFormat.getNumberInstance(Locale.FRENCH)

    fun format(price: Double): String = "${formatNumber(price)} DA"

    /** Shelf labels: whole-number prices without thousands grouping (e.g. 1200 not 1 200). */
    fun formatNumber(price: Double): String {
        if (price % 1.0 == 0.0) return price.toLong().toString()
        return format.format(price)
    }
}
