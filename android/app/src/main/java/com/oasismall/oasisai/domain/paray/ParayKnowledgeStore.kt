package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONArray
import org.json.JSONObject

/** Persists PARAY catalog knowledge under `paray_home/knowledge/`. */
class ParayKnowledgeStore(private val home: ParayHome) {
    private val stateFile = home.knowledgeStateFile
    private val articlesFile = home.knowledgeArticlesFile
    private val brandsFile = home.knowledgeBrandsFile
    private val categoriesFile = home.knowledgeCategoriesFile
    private val summaryFile = home.knowledgeSummaryFile

    fun readState(): ParayKnowledgeState {
        if (!stateFile.exists()) return ParayKnowledgeState()
        val o = runCatching { JSONObject(stateFile.readText()) }.getOrElse { return ParayKnowledgeState() }
        val history = buildList {
            val arr = o.optJSONArray("importHistory") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayImportKnowledgeRecord(
                            importId = item.optLong("importId"),
                            newCount = item.optInt("newCount", 0),
                            priceChangedCount = item.optInt("priceChangedCount", 0),
                            designationChangedCount = item.optInt("designationChangedCount", 0),
                            removedCount = item.optInt("removedCount", 0),
                            observedAt = item.optLong("observedAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }
        return ParayKnowledgeState(
            lastProcessedImportId = o.optLong("lastProcessedImportId", 0L),
            lastSummaryRefresh = o.optLong("lastSummaryRefresh", 0L),
            articleRecordCount = o.optInt("articleRecordCount", 0),
            importHistory = history,
        )
    }

    fun writeState(state: ParayKnowledgeState) {
        stateFile.parentFile?.mkdirs()
        val history = JSONArray()
        state.importHistory.takeLast(MAX_IMPORT_HISTORY).forEach { record ->
            history.put(
                JSONObject()
                    .put("importId", record.importId)
                    .put("newCount", record.newCount)
                    .put("priceChangedCount", record.priceChangedCount)
                    .put("designationChangedCount", record.designationChangedCount)
                    .put("removedCount", record.removedCount)
                    .put("observedAt", record.observedAt),
            )
        }
        stateFile.writeTextAtomic(
            JSONObject()
                .put("lastProcessedImportId", state.lastProcessedImportId)
                .put("lastSummaryRefresh", state.lastSummaryRefresh)
                .put("articleRecordCount", state.articleRecordCount)
                .put("importHistory", history)
                .toString(2),
        )
    }

    fun readArticles(): MutableMap<Long, ParayArticleKnowledge> {
        if (!articlesFile.exists()) return mutableMapOf()
        val root = runCatching { JSONObject(articlesFile.readText()) }.getOrElse { return mutableMapOf() }
        val map = mutableMapOf<Long, ParayArticleKnowledge>()
        root.keys().forEach { key ->
            root.optJSONObject(key)?.let { map[key.toLongOrNull() ?: return@let] = articleFromJson(it) }
        }
        return map
    }

    fun writeArticles(articles: Map<Long, ParayArticleKnowledge>) {
        articlesFile.parentFile?.mkdirs()
        val root = JSONObject()
        articles.forEach { (id, article) -> root.put(id.toString(), articleToJson(article)) }
        articlesFile.writeTextAtomic(root.toString())
    }

    fun readBrandSummaries(): MutableMap<String, ParayBrandKnowledgeSummary> =
        readGroupSummaries(brandsFile) { name, o ->
            ParayBrandKnowledgeSummary(
                brand = name,
                productCount = o.optInt("productCount", 0),
                pngCount = o.optInt("pngCount", 0),
                learnedCount = o.optInt("learnedCount", 0),
                missingPngCount = o.optInt("missingPngCount", 0),
            )
        }

    fun writeBrandSummaries(brands: Map<String, ParayBrandKnowledgeSummary>) {
        writeGroupSummaries(brandsFile, brands) { summary ->
            JSONObject()
                .put("brand", summary.brand)
                .put("productCount", summary.productCount)
                .put("pngCount", summary.pngCount)
                .put("learnedCount", summary.learnedCount)
                .put("missingPngCount", summary.missingPngCount)
        }
    }

    fun readCategorySummaries(): MutableMap<String, ParayCategoryKnowledgeSummary> =
        readGroupSummaries(categoriesFile) { name, o ->
            ParayCategoryKnowledgeSummary(
                category = name,
                productCount = o.optInt("productCount", 0),
                pngCount = o.optInt("pngCount", 0),
                learnedCount = o.optInt("learnedCount", 0),
            )
        }

    fun writeCategorySummaries(categories: Map<String, ParayCategoryKnowledgeSummary>) {
        writeGroupSummaries(categoriesFile, categories) { summary ->
            JSONObject()
                .put("category", summary.category)
                .put("productCount", summary.productCount)
                .put("pngCount", summary.pngCount)
                .put("learnedCount", summary.learnedCount)
        }
    }

    fun readSummary(): ParayKnowledgeSummary {
        if (!summaryFile.exists()) return ParayKnowledgeSummary()
        val o = runCatching { JSONObject(summaryFile.readText()) }.getOrElse { return ParayKnowledgeSummary() }
        val gapsO = o.optJSONObject("gaps")
        return ParayKnowledgeSummary(
            totalArticles = o.optInt("totalArticles", 0),
            totalBrands = o.optInt("totalBrands", 0),
            totalCategories = o.optInt("totalCategories", 0),
            totalFamilies = o.optInt("totalFamilies", 0),
            knownArticleCount = o.optInt("knownArticleCount", 0),
            pngCoveragePercent = o.optDouble("pngCoveragePercent", 0.0).toFloat(),
            learnCoveragePercent = o.optDouble("learnCoveragePercent", 0.0).toFloat(),
            articlesMissingPng = o.optInt("articlesMissingPng", 0),
            articlesMissingLearn = o.optInt("articlesMissingLearn", 0),
            recentPriceChanges = o.optInt("recentPriceChanges", 0),
            recentImportId = o.optLong("recentImportId", 0L),
            gaps = ParayKnowledgeGaps(
                missingPng = gapsO?.optInt("missingPng", 0) ?: 0,
                missingLearn = gapsO?.optInt("missingLearn", 0) ?: 0,
                missingBrand = gapsO?.optInt("missingBrand", 0) ?: 0,
                missingCategory = gapsO?.optInt("missingCategory", 0) ?: 0,
                missingFamily = gapsO?.optInt("missingFamily", 0) ?: 0,
            ),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    fun writeSummary(summary: ParayKnowledgeSummary) {
        summaryFile.parentFile?.mkdirs()
        summaryFile.writeTextAtomic(
            JSONObject()
                .put("totalArticles", summary.totalArticles)
                .put("totalBrands", summary.totalBrands)
                .put("totalCategories", summary.totalCategories)
                .put("totalFamilies", summary.totalFamilies)
                .put("knownArticleCount", summary.knownArticleCount)
                .put("pngCoveragePercent", summary.pngCoveragePercent.toDouble())
                .put("learnCoveragePercent", summary.learnCoveragePercent.toDouble())
                .put("articlesMissingPng", summary.articlesMissingPng)
                .put("articlesMissingLearn", summary.articlesMissingLearn)
                .put("recentPriceChanges", summary.recentPriceChanges)
                .put("recentImportId", summary.recentImportId)
                .put(
                    "gaps",
                    JSONObject()
                        .put("missingPng", summary.gaps.missingPng)
                        .put("missingLearn", summary.gaps.missingLearn)
                        .put("missingBrand", summary.gaps.missingBrand)
                        .put("missingCategory", summary.gaps.missingCategory)
                        .put("missingFamily", summary.gaps.missingFamily),
                )
                .put("updatedAt", summary.updatedAt)
                .toString(2),
        )
    }

    private fun articleToJson(article: ParayArticleKnowledge): JSONObject {
        val timeline = JSONArray()
        article.timeline.takeLast(MAX_TIMELINE_EVENTS).forEach { event ->
            timeline.put(
                JSONObject()
                    .put("event", event.event)
                    .put("at", event.at)
                    .put("detail", event.detail),
            )
        }
        return JSONObject()
            .put("articleId", article.articleId)
            .put("barcode", article.barcode)
            .put("designation", article.designation)
            .put("brand", article.brand)
            .put("category", article.category)
            .put("family", article.family)
            .put("hasPng", article.hasPng)
            .put("learnStatus", article.learnStatus.name)
            .put("firstSeenAt", article.firstSeenAt)
            .put("lastModifiedAt", article.lastModifiedAt)
            .put("lastPriceChangeAt", article.lastPriceChangeAt)
            .put("removed", article.removed)
            .put("timeline", timeline)
    }

    private fun articleFromJson(o: JSONObject): ParayArticleKnowledge {
        val timeline = buildList {
            val arr = o.optJSONArray("timeline") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item ->
                    add(
                        ParayArticleTimelineEvent(
                            event = item.optString("event"),
                            at = item.optLong("at"),
                            detail = item.optString("detail").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
        val status = runCatching {
            ParayLearnStatus.valueOf(o.optString("learnStatus", ParayLearnStatus.NOT_LEARNED.name))
        }.getOrDefault(ParayLearnStatus.NOT_LEARNED)
        return ParayArticleKnowledge(
            articleId = o.getLong("articleId"),
            barcode = o.optString("barcode"),
            designation = o.optString("designation"),
            brand = o.optString("brand").takeIf { it.isNotBlank() },
            category = o.optString("category").takeIf { it.isNotBlank() },
            family = o.optString("family").takeIf { it.isNotBlank() },
            hasPng = o.optBoolean("hasPng", false),
            learnStatus = status,
            firstSeenAt = o.optLong("firstSeenAt", System.currentTimeMillis()),
            lastModifiedAt = o.optLong("lastModifiedAt", System.currentTimeMillis()),
            lastPriceChangeAt = o.optLong("lastPriceChangeAt").takeIf { it > 0L },
            removed = o.optBoolean("removed", false),
            timeline = timeline,
        )
    }

    private inline fun <T> readGroupSummaries(
        file: java.io.File,
        parse: (String, JSONObject) -> T,
    ): MutableMap<String, T> {
        if (!file.exists()) return mutableMapOf()
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { return mutableMapOf() }
        val map = mutableMapOf<String, T>()
        root.keys().forEach { key ->
            root.optJSONObject(key)?.let { map[key] = parse(key, it) }
        }
        return map
    }

    private inline fun <T> writeGroupSummaries(
        file: java.io.File,
        items: Map<String, T>,
        toJson: (T) -> JSONObject,
    ) {
        file.parentFile?.mkdirs()
        val root = JSONObject()
        items.forEach { (key, value) -> root.put(key, toJson(value)) }
        file.writeTextAtomic(root.toString())
    }

    companion object {
        private const val MAX_TIMELINE_EVENTS = 24
        private const val MAX_IMPORT_HISTORY = 32
    }
}
