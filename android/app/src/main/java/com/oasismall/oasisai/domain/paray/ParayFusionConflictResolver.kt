package com.oasismall.oasisai.domain.paray

import org.json.JSONObject

/** Resolves overlapping knowledge without destroying local intelligence. */
object ParayFusionConflictResolver {

    data class MergeOutcome(
        val value: JSONObject,
        val conflict: Boolean,
        val resolution: String,
    )

    fun mergeLearnRecord(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        var conflict = false
        var resolution = "kept_local"

        for (side in CAPTURE_SIDES) {
            val loc = local.optJSONObject(side)
            val imp = imported.optJSONObject(side)
            when {
                loc == null && imp != null -> merged.put(side, imp)
                loc != null && imp != null -> {
                    val pick = pickHigherQualityCapture(loc, imp)
                    if (pick !== loc) {
                        merged.put(side, pick)
                        conflict = true
                        resolution = "higher_quality_capture"
                    }
                }
            }
        }

        for (sig in SIGNATURE_FIELDS) {
            val loc = local.optJSONObject(sig)
            val imp = imported.optJSONObject(sig)
            when {
                loc == null && imp != null -> merged.put(sig, imp)
                loc != null && imp != null -> {
                    val pick = pickHigherQualitySignature(loc, imp)
                    if (pick !== loc) {
                        merged.put(sig, pick)
                        conflict = true
                        resolution = "higher_quality_signature"
                    }
                }
            }
        }

        if (imported.optBoolean("frontConfirmed") && !local.optBoolean("frontConfirmed")) {
            merged.put("frontConfirmed", true)
        }
        val impFront = imported.optDouble("frontConfidence", 0.0)
        val locFront = local.optDouble("frontConfidence", 0.0)
        if (impFront > locFront) {
            merged.put("frontConfidence", impFront)
            conflict = true
            resolution = "higher_front_confidence"
        }

        val mergedRichness = learnRichness(merged)
        val localRichness = learnRichness(local)
        val importedRichness = learnRichness(imported)
        if (importedRichness > localRichness && mergedRichness <= localRichness) {
            // Imported was richer overall — ensure we did not shrink
            for (side in CAPTURE_SIDES) {
                if (merged.optJSONObject(side) == null) {
                    imported.optJSONObject(side)?.let { merged.put(side, it) }
                }
            }
            conflict = true
            resolution = "richer_imported_state"
        }

        merged.put("updatedAt", maxOf(local.optLong("updatedAt"), imported.optLong("updatedAt")))
        if (conflict && resolution == "kept_local" && mergedRichness < importedRichness) {
            resolution = "newest_timestamp"
        }
        return MergeOutcome(merged, conflict, resolution)
    }

    fun mergeVisualEntry(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val locScore = visualQualityScore(local)
        val impScore = visualQualityScore(imported)
        return when {
            impScore > locScore -> MergeOutcome(imported, conflict = true, resolution = "higher_visual_quality")
            locScore > impScore -> MergeOutcome(local, conflict = false, resolution = "kept_local")
            else -> {
                val newest = if (imported.optLong("updatedAt") >= local.optLong("updatedAt")) imported else local
                MergeOutcome(
                    newest,
                    conflict = imported.optLong("updatedAt") != local.optLong("updatedAt"),
                    resolution = "newest_timestamp",
                )
            }
        }
    }

    fun mergeBrandFamilyEntry(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        var conflict = false
        merged.put("productCount", maxOf(local.optInt("productCount"), imported.optInt("productCount")))
        merged.put("observationCount", local.optInt("observationCount") + imported.optInt("observationCount"))
        val locSig = local.optJSONObject("signature")
        val impSig = imported.optJSONObject("signature")
        if (locSig != null && impSig != null) {
            val pick = pickHigherQualitySignature(locSig, impSig)
            if (pick !== locSig) {
                merged.put("signature", pick)
                conflict = true
            }
        } else if (locSig == null && impSig != null) {
            merged.put("signature", impSig)
        }
        merged.put("updatedAt", maxOf(local.optLong("updatedAt"), imported.optLong("updatedAt")))
        return MergeOutcome(merged, conflict, if (conflict) "merged_brand_counts" else "accumulated")
    }

    fun mergeKnowledgeArticle(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        var conflict = false
        val locAt = local.optLong("lastModifiedAt")
        val impAt = imported.optLong("lastModifiedAt")
        if (impAt > locAt) {
            imported.optString("designation").takeIf { it.isNotBlank() }?.let {
                if (it != local.optString("designation")) {
                    merged.put("designation", it)
                    conflict = true
                }
            }
            merged.put("lastModifiedAt", impAt)
        }
        mergeStringField(merged, local, imported, "brand")?.let { conflict = true }
        mergeStringField(merged, local, imported, "category")?.let { conflict = true }
        mergeStringField(merged, local, imported, "family")?.let { conflict = true }
        return MergeOutcome(merged, conflict, if (conflict) "newest_article_fields" else "kept_local")
    }

    fun mergeCountEntry(local: JSONObject?, imported: JSONObject, countField: String): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        merged.put(countField, maxOf(local.optInt(countField), imported.optInt(countField)))
        merged.put("updatedAt", maxOf(local.optLong("updatedAt"), imported.optLong("updatedAt")))
        return MergeOutcome(merged, conflict = false, resolution = "max_count")
    }

    fun mergeFailurePattern(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        merged.put("count", local.optInt("count") + imported.optInt("count"))
        merged.put("lastAt", maxOf(local.optLong("lastAt"), imported.optLong("lastAt")))
        if (imported.optString("sampleDesignation").isNotBlank()) {
            merged.put("sampleDesignation", imported.optString("sampleDesignation"))
        }
        return MergeOutcome(merged, conflict = false, resolution = "accumulated")
    }

    fun mergeWorkflowTransition(local: JSONObject?, imported: JSONObject): MergeOutcome {
        if (local == null) {
            return MergeOutcome(imported, conflict = false, resolution = "added")
        }
        val merged = JSONObject(local.toString())
        merged.put("count", local.optInt("count") + imported.optInt("count"))
        merged.put("lastAt", maxOf(local.optLong("lastAt"), imported.optLong("lastAt")))
        return MergeOutcome(merged, conflict = false, resolution = "accumulated")
    }

    fun mergeSummaryNumeric(local: JSONObject, imported: JSONObject, fields: List<String>): JSONObject {
        val merged = JSONObject(local.toString())
        fields.forEach { field ->
            if (imported.has(field)) {
                merged.put(field, local.optLong(field) + imported.optLong(field))
            }
        }
        merged.put("generatedAt", maxOf(local.optLong("generatedAt"), imported.optLong("generatedAt")))
        return merged
    }

    fun learnRichness(record: JSONObject): Int {
        var count = 0
        CAPTURE_SIDES.forEach { side ->
            if (record.optJSONObject(side) != null) count++
        }
        if (record.optBoolean("frontConfirmed", false)) count++
        return count
    }

    private fun visualQualityScore(entry: JSONObject): Int {
        val obs = entry.optInt("observationCount", 0)
        val conf = (entry.optDouble("confidence", 0.0) * 100).toInt()
        return obs * 10 + conf
    }

    private fun pickHigherQualityCapture(a: JSONObject, b: JSONObject): JSONObject =
        if (a.optDouble("confidence", 0.0) >= b.optDouble("confidence", 0.0)) a else b

    private fun pickHigherQualitySignature(a: JSONObject, b: JSONObject): JSONObject {
        val aAt = a.optLong("capturedAt", 0L)
        val bAt = b.optLong("capturedAt", 0L)
        return if (bAt > aAt) b else a
    }

    private fun mergeStringField(
        merged: JSONObject,
        local: JSONObject,
        imported: JSONObject,
        field: String,
    ): Boolean? {
        val loc = local.optString(field).takeIf { it.isNotBlank() }
        val imp = imported.optString(field).takeIf { it.isNotBlank() }
        return when {
            loc == null && imp != null -> {
                merged.put(field, imp)
                true
            }
            loc != null && imp != null && loc != imp -> {
                if (imported.optLong("lastModifiedAt") >= local.optLong("lastModifiedAt")) {
                    merged.put(field, imp)
                    true
                } else null
            }
            else -> null
        }
    }

    private val CAPTURE_SIDES = listOf("leftCapture", "rightCapture", "backCapture")
    private val SIGNATURE_FIELDS = listOf("productSignature", "brandSignature", "familySignature")
}
