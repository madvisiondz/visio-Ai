package com.oasismall.oasisai.domain.paray

/** Workflow features PARAY tracks — counts only, never user content. */
enum class ParayWorkflowFeature(val label: String) {
    BARCODE_SCAN("Barcode scan"),
    CAMERA_BATCH_SESSION("Camera batch session"),
    DESIGN_EXPORT("Design export"),
    PDF_GENERATION("PDF generation"),
    CSV_IMPORT("CSV import"),
    PNG_IMPORT("PNG import"),
    PARAY_LEARN_SESSION("PARAY Learn session"),
}

enum class ParayWorkflowConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class ParayScreenUsageEntry(
    val screen: String,
    val visits: Int = 0,
    val totalDurationMs: Long = 0L,
    val averageDurationMs: Long = 0L,
)

data class ParayWorkflowTransition(
    val from: String,
    val to: String,
    val count: Int = 0,
    val confidence: ParayWorkflowConfidence = ParayWorkflowConfidence.LOW,
)

data class ParayWorkflowSequence(
    val steps: List<String>,
    val count: Int = 0,
    val confidence: ParayWorkflowConfidence = ParayWorkflowConfidence.LOW,
) {
    val key: String get() = steps.joinToString("→")
}

data class ParayWorkflowFeatureUsage(
    val feature: ParayWorkflowFeature,
    val count: Int = 0,
)

data class ParayWorkflowGaps(
    val unusedScreens: List<String> = emptyList(),
    val unusedFeatures: List<String> = emptyList(),
    val rarelyUsedFeatures: List<String> = emptyList(),
)

data class ParayWorkflowBottleneck(
    val screen: String,
    val averageDurationMs: Long,
)

data class ParayAppMapEdge(
    val from: String,
    val to: String,
    val weight: Int,
)

/** Cached workflow KPIs — read by future PARAY Home, never built on screen open. */
data class ParayWorkflowSummary(
    val topScreens: List<ParayScreenUsageEntry> = emptyList(),
    val topFeatures: List<ParayWorkflowFeatureUsage> = emptyList(),
    val topTransitions: List<ParayWorkflowTransition> = emptyList(),
    val topSequences: List<ParayWorkflowSequence> = emptyList(),
    val gaps: ParayWorkflowGaps = ParayWorkflowGaps(),
    val bottleneck: ParayWorkflowBottleneck? = null,
    val appMap: List<ParayAppMapEdge> = emptyList(),
    val agentUsageCount: Int = 0,
    val designExportsCount: Int = 0,
    val averageDailyActivity: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ParayWorkflowEvent(
    val type: String,
    val at: Long = System.currentTimeMillis(),
    val screen: String? = null,
    val from: String? = null,
    val to: String? = null,
    val feature: String? = null,
    val durationMs: Long? = null,
)
