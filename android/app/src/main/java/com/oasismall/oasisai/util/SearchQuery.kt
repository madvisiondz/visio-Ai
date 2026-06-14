package com.oasismall.oasisai.util

import com.oasismall.oasisai.data.db.dao.ArticleWithImage

object SearchQuery {
    data class SmartSearch(
        val raw: String,
        val normalized: String,
        val tokens: List<String>,
        /** Escaped LIKE pattern for the broadest SQL pre-filter. */
        val sqlPattern: String,
    )

    fun prepare(raw: String): SmartSearch? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = NameNormalizer.normalize(trimmed)
        val tokens = tokenize(trimmed, normalized)
        if (tokens.isEmpty()) return null
        val primary = tokens.maxByOrNull { it.length } ?: tokens.first()
        return SmartSearch(
            raw = trimmed,
            normalized = normalized,
            tokens = tokens,
            sqlPattern = escapeLikePattern(primary),
        )
    }

    /** Escape `%` and `_` for SQL LIKE with ESCAPE '\\'. */
    fun escapeLikePattern(raw: String): String =
        raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun tokenize(raw: String, normalized: String): List<String> {
        val fromNormalized = normalized
            .split(" ")
            .filter { token ->
                token.isNotEmpty() && (token.length >= 2 || token.all { it.isDigit() })
            }
            .distinct()
        if (fromNormalized.isNotEmpty()) return fromNormalized
        val fallback = NameNormalizer.normalize(raw)
        return if (fallback.isNotEmpty()) listOf(fallback) else emptyList()
    }

    fun matches(article: ArticleWithImage, search: SmartSearch): Boolean {
        val haystack = buildHaystack(article)
        return search.tokens.all { token -> haystack.contains(token) }
    }

    fun score(article: ArticleWithImage, search: SmartSearch): Int {
        val norm = article.normalizedName
        val des = NameNormalizer.normalize(article.designation)
        val barcode = article.barcode.uppercase()
        return when {
            norm == search.normalized || des == search.normalized || barcode == search.normalized -> 0
            norm.startsWith(search.normalized) || des.startsWith(search.normalized) -> 1
            search.tokens.all { norm.contains(it) || des.contains(it) } -> 2
            else -> 3
        }
    }

    private fun buildHaystack(article: ArticleWithImage): String =
        listOf(
            NameNormalizer.normalize(article.designation),
            article.normalizedName,
            article.barcode.uppercase(),
            article.brand.orEmpty().let { NameNormalizer.normalize(it) },
            article.category.orEmpty().let { NameNormalizer.normalize(it) },
        ).joinToString(" ")
}
