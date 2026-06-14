package com.oasismall.oasisai.util

import java.text.Normalizer
import java.util.Locale

object NameNormalizer {
    fun normalize(designation: String): String {
        return Normalizer.normalize(designation, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun toFileKey(designation: String): String {
        return normalize(designation).replace(" ", "_")
    }

    /**
     * Keep names human-readable for file sharing:
     * - preserve spaces between words
     * - remove only filesystem-forbidden/control characters
     * - trim trailing spaces/dots for Windows compatibility
     */
    fun toDisplayFileStem(designation: String): String {
        return designation
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
    }
}
