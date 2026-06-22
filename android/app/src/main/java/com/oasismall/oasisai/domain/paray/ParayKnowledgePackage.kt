package com.oasismall.oasisai.domain.paray

import java.io.File

/** PARAY Knowledge Package (PKP) — offline shared intelligence format. */
object ParayKnowledgePackage {
    const val PACKAGE_VERSION = 1
    const val EXPORT_TYPE = "knowledge_fusion"
    const val MANIFEST_FILE = "package_manifest.json"
    const val EXTENSION = ".pkp.zip"

    val REQUIRED_MANIFEST_FIELDS = listOf(
        "packageVersion",
        "parayVersion",
        "createdAt",
        "deviceKnowledgeId",
        "exportType",
    )

    /** Knowledge files included in every PKP export. */
    val EXPORT_FILES: List<Pair<String, (ParayHome) -> File>> = listOf(
        "memory/learn_index.json" to { it.learnIndexFile },
        "memory/visual_index.json" to { it.visualIndexFile },
        "memory/brand_family_index.json" to { it.brandFamilyIndexFile },
        "knowledge/knowledge_articles.json" to { it.knowledgeArticlesFile },
        "knowledge/knowledge_brands.json" to { it.knowledgeBrandsFile },
        "knowledge/knowledge_categories.json" to { it.knowledgeCategoriesFile },
        "knowledge/knowledge_summary.json" to { it.knowledgeSummaryFile },
        "workflows/workflow_summary.json" to { it.workflowSummaryFile },
        "workflows/workflow_patterns.json" to { it.workflowPatternsFile },
        "recognition/recognition_summary.json" to { it.recognitionSummaryFile },
        "recognition/failure_patterns.json" to { it.recognitionFailurePatternsFile },
        "observer/observer_knowledge.json" to { it.observerKnowledgeFile },
        "observer/observer_summary.json" to { it.observerSummaryFile },
    )

    fun fileInPackage(packageRoot: File, relativePath: String): File =
        File(packageRoot, relativePath.replace('/', File.separatorChar))
}
