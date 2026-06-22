package com.oasismall.oasisai.domain.paray

/** Cached recognition KPIs for PARAY Home / Statistics — never built on screen open. */
data class ParayRecognitionSummary(
    val totalFailures: Int = 0,
    val totalLowConfidence: Int = 0,
    val totalManualCorrections: Int = 0,
    val totalPackagingDrifts: Int = 0,
    val totalUnknownBarcodes: Int = 0,
    val mostProblematicProducts: List<ParayRecognitionProductRank> = emptyList(),
    val mostCorrectedProducts: List<ParayRecognitionProductRank> = emptyList(),
    val mostFrequentFailures: List<ParayRecognitionFailureRank> = emptyList(),
    val mostCommonPackagingDrifts: List<ParayRecognitionProductRank> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val topBlindSpot: ParayRecognitionProductRank?
        get() = mostProblematicProducts.firstOrNull()

    val totalEvents: Int
        get() = totalFailures + totalLowConfidence + totalManualCorrections +
            totalPackagingDrifts + totalUnknownBarcodes
}
