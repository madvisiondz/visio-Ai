package com.oasismall.oasisai.domain.paray

/** PARAY Knowledge Package manifest — PKP v1. */
data class ParayKnowledgePackageManifest(
    val packageVersion: Int = ParayKnowledgePackage.PACKAGE_VERSION,
    val parayVersion: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val knowledgeCount: Int = 0,
    val deviceKnowledgeId: String = "",
    val exportType: String = "knowledge_fusion",
)

data class ParayFusionState(
    val deviceKnowledgeId: String = "",
    val lastExportAt: Long = 0L,
    val lastImportAt: Long = 0L,
    val totalImports: Int = 0,
)

data class ParayFusionHistoryEntry(
    val fusedAt: Long = System.currentTimeMillis(),
    val packageVersion: Int = 1,
    val parayVersion: String = "",
    val sourceDeviceKnowledgeId: String = "",
    val newArticles: Int = 0,
    val newBrands: Int = 0,
    val newLearnRecords: Int = 0,
    val newRecognitionPatterns: Int = 0,
    val newWorkflowPatterns: Int = 0,
    val conflictsResolved: Int = 0,
)

data class ParayFusionConflict(
    val key: String,
    val domain: String,
    val resolution: String,
    val resolvedAt: Long = System.currentTimeMillis(),
)

/** Pre-merge summary shown before user confirms fusion. */
data class ParayFusionPreview(
    val manifest: ParayKnowledgePackageManifest,
    val newArticles: Int = 0,
    val newBrands: Int = 0,
    val newLearnRecords: Int = 0,
    val newRecognitionPatterns: Int = 0,
    val newWorkflowPatterns: Int = 0,
    val conflicts: Int = 0,
)

data class ParayFusionResult(
    val preview: ParayFusionPreview,
    val historyEntry: ParayFusionHistoryEntry,
)

data class ParayFusionExportResult(
    val fileName: String,
    val knowledgeCount: Int,
)
