package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.data.repository.OasisRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports PC-built paray_fingerprint_index.json into on-device PARAY memory.
 */
class ParayFingerprintImporter(
    private val repository: OasisRepository,
    private val visualIndex: ParayVisualIndex,
    private val fingerprintStore: ParayFingerprintStore,
) {
    suspend fun importJson(
        text: String,
        onProgress: (ParayImportProgress) -> Unit = {},
    ): ParayImportResult {
        onProgress(ParayImportProgress("Parsing fingerprint file"))
        val root = JSONObject(text)
        val version = root.optInt("version", 0)
        if (version < 1) {
            return ParayImportResult(0, 0, 1, 0)
        }

        val meta = FingerprintMeta(
            version = version,
            agent = root.optString("agent", "PARAY"),
            model = root.optString("model", ""),
            dim = root.optInt("dim", 512),
            generatedAt = root.optString("generatedAt", ""),
            source = root.optString("source", "pc"),
        )

        val entries = root.optJSONArray("entries") ?: JSONArray()
        val total = entries.length()
        onProgress(
            ParayImportProgress(
                phase = "Resolving barcodes in catalog",
                total = total,
            ),
        )

        val byBarcode = repository.getAllArticles()
            .filter { it.barcode.isNotBlank() }
            .associateBy { it.barcode.trim() }

        var imported = 0
        var skippedNoArticle = 0
        var skippedInvalid = 0
        val signatures = ArrayList<ProductVisualSignature>(total.coerceAtMost(4096))
        val embeddingRows = ArrayList<FingerprintEntry>(total.coerceAtMost(4096))

        for (i in 0 until total) {
            val o = entries.optJSONObject(i)
            if (o == null) {
                skippedInvalid++
                reportProgress(onProgress, total, i + 1, imported, skippedNoArticle, skippedInvalid, null)
                continue
            }
            val barcode = o.optString("barcode", "").trim()
            if (barcode.isEmpty()) {
                skippedInvalid++
                reportProgress(onProgress, total, i + 1, imported, skippedNoArticle, skippedInvalid, null)
                continue
            }
            val embeddingArr = o.optJSONArray("embedding")
            if (embeddingArr == null || embeddingArr.length() < 64) {
                skippedInvalid++
                reportProgress(onProgress, total, i + 1, imported, skippedNoArticle, skippedInvalid, null)
                continue
            }

            val article = byBarcode[barcode] ?: repository.getArticleByBarcode(barcode)
            if (article == null) {
                skippedNoArticle++
                reportProgress(onProgress, total, i + 1, imported, skippedNoArticle, skippedInvalid, null)
                continue
            }

            val designation = o.optString("designation", article.designation)
            val colors = parseColors(o.optJSONArray("dominantColors"))
            val signature = ProductVisualSignature(
                articleId = article.id,
                barcode = barcode,
                designation = designation,
                shapeAspect = o.optDouble("shapeAspect", 1.0).toFloat(),
                fillRatio = o.optDouble("fillRatio", 0.5).toFloat(),
                dominantColors = colors,
                designationWordCount = o.optInt("designationWordCount", 0),
                designationCharCount = o.optInt("designationCharCount", designation.length),
                templateId = o.optString("templateId", "shelf_10up"),
                labelPalette = VisualFeatureExtractor.shelfLabelPalette(),
                observationCount = o.optInt("observationCount", 1),
                lastLearnedAt = System.currentTimeMillis(),
                imageFileName = o.optString("imageFileName", ""),
            )
            signatures.add(signature)

            val emb = FloatArray(embeddingArr.length()) { j -> embeddingArr.getDouble(j).toFloat() }
            embeddingRows.add(
                FingerprintEntry(
                    barcode = barcode,
                    designation = designation,
                    imageFileName = signature.imageFileName,
                    embedding = emb,
                ),
            )
            imported++
            reportProgress(onProgress, total, i + 1, imported, skippedNoArticle, skippedInvalid, designation)
        }

        onProgress(
            ParayImportProgress(
                phase = "Writing PARAY neural memory",
                total = total,
                processed = total,
                imported = imported,
                skippedNoArticle = skippedNoArticle,
                skippedInvalid = skippedInvalid,
            ),
        )
        visualIndex.seedBatch(signatures)
        fingerprintStore.replaceAll(embeddingRows, meta)

        return ParayImportResult(
            imported = imported,
            skippedNoArticle = skippedNoArticle,
            skippedInvalid = skippedInvalid,
            embeddingCount = embeddingRows.size,
        )
    }

    private fun reportProgress(
        onProgress: (ParayImportProgress) -> Unit,
        total: Int,
        processed: Int,
        imported: Int,
        skippedNoArticle: Int,
        skippedInvalid: Int,
        designation: String?,
    ) {
        if (processed % 25 == 0 || processed == total) {
            onProgress(
                ParayImportProgress(
                    phase = "Loading fingerprints",
                    total = total,
                    processed = processed,
                    imported = imported,
                    skippedNoArticle = skippedNoArticle,
                    skippedInvalid = skippedInvalid,
                    currentDesignation = designation,
                ),
            )
        }
    }

    private fun parseColors(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }
}
