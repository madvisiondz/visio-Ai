package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import com.oasismall.oasisai.domain.transfer.ZipArchive
import com.oasismall.oasisai.util.TaskProgress
import org.json.JSONObject
import java.io.File

/**
 * Merges PARAY Knowledge Packages (PKP) into local intelligence.
 * Never overwrites blindly — only adds and enriches.
 */
class ParayKnowledgeFusionEngine(
    private val home: ParayHome,
    private val fusionStore: ParayFusionStore,
    private val validator: ParayKnowledgePackageValidator,
    private val cacheDir: File,
) {
    private val exporter = ParayKnowledgePackageExporter(home, fusionStore, cacheDir)

    fun exportPackage(onProgress: ((TaskProgress) -> Unit)? = null): Pair<File, ParayFusionExportResult> {
        val result = exporter.export(onProgress)
        val zipFile = File(cacheDir, result.fileName)
        return zipFile to result
    }

    fun stagePackage(zipFile: File, onProgress: ((TaskProgress) -> Unit)? = null): File {
        val staging = File(cacheDir, "pkp-import-${System.currentTimeMillis()}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        ZipArchive.unzip(zipFile, staging, onProgress)
        return staging
    }

    fun previewFusion(packageRoot: File): ParayFusionPreview {
        val manifest = validator.validate(packageRoot)
        val counters = FusionCounters()
        val conflicts = mutableListOf<ParayFusionConflict>()

        mergeLearnIndex(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)
        mergeVisualIndex(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)
        mergeBrandFamilyIndex(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)
        mergeKnowledgeArticles(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)
        mergeKeyedFile(
            packageRoot,
            "knowledge/knowledge_brands.json",
            home.knowledgeBrandsFile,
            dryRun = true,
            counters = counters,
            conflicts = conflicts,
            onNew = { counters.newBrands++ },
        )
        mergeWorkflowPatterns(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)
        mergeFailurePatterns(packageRoot, dryRun = true, counters = counters, conflicts = conflicts)

        return ParayFusionPreview(
            manifest = manifest,
            newArticles = counters.newArticles,
            newBrands = counters.newBrands,
            newLearnRecords = counters.newLearnRecords,
            newRecognitionPatterns = counters.newRecognitionPatterns,
            newWorkflowPatterns = counters.newWorkflowPatterns,
            conflicts = conflicts.size,
        )
    }

    fun executeFusion(
        packageRoot: File,
        onProgress: ((TaskProgress) -> Unit)? = null,
    ): ParayFusionResult {
        onProgress?.invoke(TaskProgress("Validating package", 5))
        val manifest = validator.validate(packageRoot)
        val counters = FusionCounters()
        val conflicts = mutableListOf<ParayFusionConflict>()

        onProgress?.invoke(TaskProgress("Merging learn memory", 15))
        mergeLearnIndex(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        onProgress?.invoke(TaskProgress("Merging visual signatures", 30))
        mergeVisualIndex(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        onProgress?.invoke(TaskProgress("Merging brand families", 40))
        mergeBrandFamilyIndex(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        onProgress?.invoke(TaskProgress("Merging knowledge", 50))
        mergeKnowledgeArticles(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        mergeKeyedFile(
            packageRoot,
            "knowledge/knowledge_brands.json",
            home.knowledgeBrandsFile,
            dryRun = false,
            counters = counters,
            conflicts = conflicts,
            onNew = { counters.newBrands++ },
            merge = { l, i -> ParayFusionConflictResolver.mergeCountEntry(l, i, "productCount") },
        )
        mergeKeyedFile(
            packageRoot,
            "knowledge/knowledge_categories.json",
            home.knowledgeCategoriesFile,
            dryRun = false,
            counters = counters,
            conflicts = conflicts,
            merge = { l, i -> ParayFusionConflictResolver.mergeCountEntry(l, i, "productCount") },
        )
        mergeSummaryFile(
            packageRoot,
            "knowledge/knowledge_summary.json",
            home.knowledgeSummaryFile,
            SUMMARY_ACCUMULATE_FIELDS,
        )
        onProgress?.invoke(TaskProgress("Merging workflows", 65))
        mergeWorkflowPatterns(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        mergeSummaryFile(
            packageRoot,
            "workflows/workflow_summary.json",
            home.workflowSummaryFile,
            WORKFLOW_SUMMARY_FIELDS,
        )
        onProgress?.invoke(TaskProgress("Merging recognition", 80))
        mergeFailurePatterns(packageRoot, dryRun = false, counters = counters, conflicts = conflicts)
        mergeSummaryFile(
            packageRoot,
            "recognition/recognition_summary.json",
            home.recognitionSummaryFile,
            RECOGNITION_SUMMARY_FIELDS,
        )
        onProgress?.invoke(TaskProgress("Merging observer", 90))
        mergeObserverKnowledge(packageRoot)
        mergeSummaryFile(
            packageRoot,
            "observer/observer_summary.json",
            home.observerSummaryFile,
            OBSERVER_SUMMARY_FIELDS,
        )

        fusionStore.appendConflicts(conflicts)
        val historyEntry = ParayFusionHistoryEntry(
            fusedAt = System.currentTimeMillis(),
            packageVersion = manifest.packageVersion,
            parayVersion = manifest.parayVersion,
            sourceDeviceKnowledgeId = manifest.deviceKnowledgeId,
            newArticles = counters.newArticles,
            newBrands = counters.newBrands,
            newLearnRecords = counters.newLearnRecords,
            newRecognitionPatterns = counters.newRecognitionPatterns,
            newWorkflowPatterns = counters.newWorkflowPatterns,
            conflictsResolved = conflicts.size,
        )
        fusionStore.appendHistory(historyEntry)
        val state = fusionStore.readState()
        fusionStore.writeState(
            state.copy(
                lastImportAt = System.currentTimeMillis(),
                totalImports = state.totalImports + 1,
            ),
        )
        onProgress?.invoke(TaskProgress("Fusion complete", 100))
        return ParayFusionResult(
            preview = ParayFusionPreview(
                manifest = manifest,
                newArticles = counters.newArticles,
                newBrands = counters.newBrands,
                newLearnRecords = counters.newLearnRecords,
                newRecognitionPatterns = counters.newRecognitionPatterns,
                newWorkflowPatterns = counters.newWorkflowPatterns,
                conflicts = conflicts.size,
            ),
            historyEntry = historyEntry,
        )
    }

    fun readHistory(): List<ParayFusionHistoryEntry> = fusionStore.readHistory()

    private data class FusionCounters(
        var newArticles: Int = 0,
        var newBrands: Int = 0,
        var newLearnRecords: Int = 0,
        var newRecognitionPatterns: Int = 0,
        var newWorkflowPatterns: Int = 0,
    )

    private fun mergeLearnIndex(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "memory/learn_index.json"))
        val local = readJson(home.learnIndexFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            val imp = imported.getJSONObject(key)
            val loc = local.optJSONObject(key)
            if (loc == null) {
                counters.newLearnRecords++
                if (!dryRun) merged.put(key, imp)
            } else {
                val outcome = ParayFusionConflictResolver.mergeLearnRecord(loc, imp)
                if (!dryRun) merged.put(key, outcome.value)
                if (outcome.conflict) {
                    conflicts += ParayFusionConflict(key, "learn_index", outcome.resolution)
                }
            }
        }
        if (!dryRun) home.learnIndexFile.writeTextAtomic(merged.toString())
    }

    private fun mergeVisualIndex(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "memory/visual_index.json"))
        val local = readJson(home.visualIndexFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            val imp = imported.getJSONObject(key)
            val loc = local.optJSONObject(key)
            if (loc == null) {
                if (!dryRun) merged.put(key, imp)
            } else {
                val outcome = ParayFusionConflictResolver.mergeVisualEntry(loc, imp)
                if (!dryRun) merged.put(key, outcome.value)
                if (outcome.conflict) {
                    conflicts += ParayFusionConflict(key, "visual_index", outcome.resolution)
                }
            }
        }
        if (!dryRun) home.visualIndexFile.writeTextAtomic(merged.toString())
    }

    private fun mergeBrandFamilyIndex(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "memory/brand_family_index.json"))
        val local = readJson(home.brandFamilyIndexFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            val imp = imported.getJSONObject(key)
            val loc = local.optJSONObject(key)
            if (loc == null) {
                if (!dryRun) merged.put(key, imp)
            } else {
                val outcome = ParayFusionConflictResolver.mergeBrandFamilyEntry(loc, imp)
                if (!dryRun) merged.put(key, outcome.value)
                if (outcome.conflict) {
                    conflicts += ParayFusionConflict(key, "brand_family", outcome.resolution)
                }
            }
        }
        if (!dryRun) home.brandFamilyIndexFile.writeTextAtomic(merged.toString())
    }

    private fun mergeKnowledgeArticles(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        mergeKeyedFile(
            packageRoot,
            "knowledge/knowledge_articles.json",
            home.knowledgeArticlesFile,
            dryRun = dryRun,
            counters = counters,
            conflicts = conflicts,
            onNew = { counters.newArticles++ },
            merge = { l, i -> ParayFusionConflictResolver.mergeKnowledgeArticle(l, i) },
        )
    }

    private fun mergeKeyedFile(
        packageRoot: File,
        relativePath: String,
        localFile: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
        onNew: (() -> Unit)? = null,
        merge: (JSONObject?, JSONObject) -> ParayFusionConflictResolver.MergeOutcome = { l, i ->
            ParayFusionConflictResolver.mergeCountEntry(l, i, "productCount")
        },
    ) {
        val domain = relativePath.substringAfterLast('/').removeSuffix(".json")
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, relativePath))
        val local = readJson(localFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            val imp = imported.getJSONObject(key)
            val loc = local.optJSONObject(key)
            if (loc == null) {
                onNew?.invoke()
                if (!dryRun) merged.put(key, imp)
            } else {
                val outcome = merge(loc, imp)
                if (!dryRun) merged.put(key, outcome.value)
                if (outcome.conflict) {
                    conflicts += ParayFusionConflict(key, domain, outcome.resolution)
                }
            }
        }
        if (!dryRun) localFile.writeTextAtomic(merged.toString())
    }

    private fun mergeWorkflowPatterns(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "workflows/workflow_patterns.json"))
        val local = readJson(home.workflowPatternsFile)
        val merged = JSONObject(local.toString())

        fun mergeSection(
            section: String,
            mergeFn: (JSONObject?, JSONObject) -> ParayFusionConflictResolver.MergeOutcome,
        ) {
            val impSection = imported.optJSONObject(section) ?: JSONObject()
            val locSection = local.optJSONObject(section) ?: JSONObject()
            val outSection = JSONObject(locSection.toString())
            impSection.keys().forEach { key ->
                val imp = impSection.getJSONObject(key)
                val loc = locSection.optJSONObject(key)
                if (loc == null) {
                    counters.newWorkflowPatterns++
                    if (!dryRun) outSection.put(key, imp)
                } else {
                    val outcome = mergeFn(loc, imp)
                    if (!dryRun) outSection.put(key, outcome.value)
                    if (outcome.conflict) {
                        conflicts += ParayFusionConflict(key, "workflow_$section", outcome.resolution)
                    }
                }
            }
            if (!dryRun) merged.put(section, outSection)
        }

        mergeSection("transitions") { l, i -> ParayFusionConflictResolver.mergeWorkflowTransition(l, i) }
        mergeSection("sequences") { l, i -> ParayFusionConflictResolver.mergeWorkflowTransition(l, i) }

        val impFeatures = imported.optJSONObject("features") ?: JSONObject()
        val locFeatures = local.optJSONObject("features") ?: JSONObject()
        val outFeatures = JSONObject(locFeatures.toString())
        impFeatures.keys().forEach { key ->
            val impCount = impFeatures.optInt(key, 0)
            val locCount = locFeatures.optInt(key, 0)
            if (locCount == 0 && impCount > 0) {
                counters.newWorkflowPatterns++
            }
            if (!dryRun) {
                outFeatures.put(key, locCount + impCount)
            }
        }
        if (!dryRun) merged.put("features", outFeatures)
        if (!dryRun) home.workflowPatternsFile.writeTextAtomic(merged.toString())
    }

    private fun mergeFailurePatterns(
        packageRoot: File,
        dryRun: Boolean,
        counters: FusionCounters,
        conflicts: MutableList<ParayFusionConflict>,
    ) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "recognition/failure_patterns.json"))
        val local = readJson(home.recognitionFailurePatternsFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            val imp = imported.getJSONObject(key)
            val loc = local.optJSONObject(key)
            if (loc == null) {
                counters.newRecognitionPatterns++
                if (!dryRun) merged.put(key, imp)
            } else {
                val outcome = ParayFusionConflictResolver.mergeFailurePattern(loc, imp)
                if (!dryRun) merged.put(key, outcome.value)
                if (outcome.conflict) {
                    conflicts += ParayFusionConflict(key, "failure_patterns", outcome.resolution)
                }
            }
        }
        if (!dryRun) home.recognitionFailurePatternsFile.writeTextAtomic(merged.toString())
    }

    private fun mergeSummaryFile(packageRoot: File, relativePath: String, localFile: File, fields: List<String>) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, relativePath))
        val local = readJson(localFile)
        val merged = if (local.length() == 0) imported else ParayFusionConflictResolver.mergeSummaryNumeric(local, imported, fields)
        localFile.parentFile?.mkdirs()
        localFile.writeTextAtomic(merged.toString(2))
    }

    private fun mergeObserverKnowledge(packageRoot: File) {
        val imported = readJson(ParayKnowledgePackage.fileInPackage(packageRoot, "observer/observer_knowledge.json"))
        val local = readJson(home.observerKnowledgeFile)
        val merged = JSONObject(local.toString())
        imported.keys().forEach { key ->
            if (!merged.has(key)) {
                merged.put(key, imported.get(key))
            } else {
                val loc = merged.optJSONObject(key)
                val imp = imported.optJSONObject(key)
                if (loc != null && imp != null) {
                    val outcome = ParayFusionConflictResolver.mergeCountEntry(loc, imp, "count")
                    merged.put(key, outcome.value)
                }
            }
        }
        home.observerKnowledgeFile.writeTextAtomic(merged.toString(2))
    }

    private fun readJson(file: File): JSONObject =
        runCatching { JSONObject(if (file.isFile) file.readText() else "{}") }.getOrElse { JSONObject() }

    companion object {
        private val SUMMARY_ACCUMULATE_FIELDS = listOf(
            "totalArticles", "knownArticleCount", "totalBrands", "totalCategories", "totalFamilies",
        )
        private val WORKFLOW_SUMMARY_FIELDS = listOf(
            "totalEvents", "designExportsCount", "agentUsageCount", "totalActivityCount",
        )
        private val RECOGNITION_SUMMARY_FIELDS = listOf(
            "totalFailures", "totalLowConfidence", "totalManualCorrections",
            "totalPackagingDrifts", "totalUnknownBarcodes",
        )
        private val OBSERVER_SUMMARY_FIELDS = listOf(
            "newProducts", "priceChanges", "renamedProducts", "removedProducts",
        )
    }
}
