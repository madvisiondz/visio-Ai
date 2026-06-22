package com.oasismall.oasisai.ui.screens.paraylearn

import com.oasismall.oasisai.domain.paray.ParayKnowledgeStore
import com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import com.oasismall.oasisai.domain.paray.ParayLearnStore
import com.oasismall.oasisai.domain.paray.ParayRecognitionStore
import com.oasismall.oasisai.domain.paray.ParayRecognitionSummary
import com.oasismall.oasisai.domain.paray.ParayWorkflowStore
import com.oasismall.oasisai.domain.paray.ParayWorkflowSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParayLearningKpis(
    val readyCount: Int,
    val learnedCount: Int,
    val partiallyLearnedCount: Int,
    val pendingCount: Int,
    val coveragePercent: Float,
)

data class ParayKnowledgeKpis(
    val knownBrands: Int,
    val knownCategories: Int,
    val knownFamilies: Int,
)

data class ParayWorkflowKpis(
    val mostUsedScreen: String?,
    val mostUsedFeature: String?,
    val agentUsageCount: Int,
    val designExportsCount: Int,
)

data class ParayLearningTrendEntry(
    val designation: String,
    val barcode: String,
    val learnedAt: Long,
    val status: ParayLearnStatus,
)

data class ParayRecognitionKpis(
    val failures: Int,
    val unknownProducts: Int,
    val packagingDrifts: Int,
    val manualCorrections: Int,
    val mostProblematicLabel: String?,
)

data class ParayStatisticsUiData(
    val learning: ParayLearningKpis,
    val knowledge: ParayKnowledgeKpis,
    val workflow: ParayWorkflowKpis,
    val recognition: ParayRecognitionKpis,
    val learningTrend: List<ParayLearningTrendEntry>,
    val knowledgeUpdatedAt: Long,
    val workflowUpdatedAt: Long,
    val recognitionGeneratedAt: Long,
)

/** Instant KPI dashboard from cached JSON files — no Room, no live aggregation. */
class ParayStatisticsRepository(
    private val learnStore: ParayLearnStore,
    private val knowledgeStore: ParayKnowledgeStore,
    private val workflowStore: ParayWorkflowStore,
    private val recognitionStore: ParayRecognitionStore,
) {
    suspend fun load(): ParayStatisticsUiData = withContext(Dispatchers.IO) {
        val knowledge = knowledgeStore.readSummary()
        val workflow = workflowStore.readSummary()
        val recognition = recognitionStore.readSummary()
        val records = learnStore.allRecords()

        val learnedCount = records.count { it.status == ParayLearnStatus.LEARNED }
        val partialCount = records.count { it.status == ParayLearnStatus.PARTIALLY_LEARNED }
        val readyCount = readyPoolFromSummary(knowledge)
        val pendingCount = (readyCount - learnedCount).coerceAtLeast(0)

        ParayStatisticsUiData(
            learning = ParayLearningKpis(
                readyCount = readyCount,
                learnedCount = learnedCount,
                partiallyLearnedCount = partialCount,
                pendingCount = pendingCount,
                coveragePercent = knowledge.learnCoveragePercent,
            ),
            knowledge = ParayKnowledgeKpis(
                knownBrands = knowledge.totalBrands,
                knownCategories = knowledge.totalCategories,
                knownFamilies = knowledge.totalFamilies,
            ),
            workflow = ParayWorkflowKpis(
                mostUsedScreen = workflow.topScreens.firstOrNull()?.screen?.takeIf { it.isNotBlank() },
                mostUsedFeature = workflow.topFeatures.firstOrNull()?.feature?.label,
                agentUsageCount = workflow.agentUsageCount,
                designExportsCount = workflow.designExportsCount,
            ),
            recognition = recognitionKpis(recognition),
            learningTrend = learningTrend(records),
            knowledgeUpdatedAt = knowledge.updatedAt,
            workflowUpdatedAt = workflow.updatedAt,
            recognitionGeneratedAt = recognition.generatedAt,
        )
    }

    private fun recognitionKpis(summary: ParayRecognitionSummary): ParayRecognitionKpis {
        val top = summary.topBlindSpot
        return ParayRecognitionKpis(
            failures = summary.totalFailures,
            unknownProducts = summary.totalUnknownBarcodes,
            packagingDrifts = summary.totalPackagingDrifts,
            manualCorrections = summary.totalManualCorrections,
            mostProblematicLabel = top?.let { it.designation ?: it.barcode },
        )
    }

    /** PNG-linked catalog pool cached in knowledge summary (not a Room scan). */
    private fun readyPoolFromSummary(summary: ParayKnowledgeSummary): Int =
        (summary.totalArticles - summary.articlesMissingPng).coerceAtLeast(0)

    private fun learningTrend(records: List<com.oasismall.oasisai.domain.paray.ParayLearnRecord>): List<ParayLearningTrendEntry> =
        records
            .mapNotNull { record ->
                val at = record.learnedAt ?: record.updatedAt.takeIf { record.status != ParayLearnStatus.NOT_LEARNED }
                if (at == null || at <= 0L) return@mapNotNull null
                ParayLearningTrendEntry(
                    designation = record.designation,
                    barcode = record.barcode,
                    learnedAt = at,
                    status = record.status,
                )
            }
            .sortedByDescending { it.learnedAt }
            .take(TREND_LIMIT)

    companion object {
        private const val TREND_LIMIT = 20
    }
}
