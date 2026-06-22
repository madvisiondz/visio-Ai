package com.oasismall.oasisai.ui.screens.parayhome

import com.oasismall.oasisai.domain.paray.ParayHome
import com.oasismall.oasisai.domain.paray.ParayKnowledgeStore
import com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary
import com.oasismall.oasisai.domain.paray.ParayManifest
import com.oasismall.oasisai.domain.paray.ParayObserverStore
import com.oasismall.oasisai.domain.paray.ParayObserverSummary
import com.oasismall.oasisai.domain.paray.ParayWorkflowStore
import com.oasismall.oasisai.domain.paray.ParayRecognitionSummary
import com.oasismall.oasisai.domain.paray.ParayRecognitionStore
import com.oasismall.oasisai.domain.paray.ParayWorkflowSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext

/** Dashboard data loaded from cached PARAY summary files only — no SQL, no live aggregation. */
data class ParayDashboardData(
    val manifest: ParayManifest,
    val observer: ParayObserverSummary,
    val knowledge: ParayKnowledgeSummary,
    val workflow: ParayWorkflowSummary,
    val recognition: ParayRecognitionSummary,
)

class ParayHomeRepository(
    private val home: ParayHome,
) {
    private val observerStore = ParayObserverStore(home)
    private val knowledgeStore = ParayKnowledgeStore(home)
    private val workflowStore = ParayWorkflowStore(home)
    private val recognitionStore = ParayRecognitionStore(home)

    suspend fun loadDashboard(): ParayDashboardData = withContext(Dispatchers.IO) {
        ParayDashboardData(
            manifest = home.readManifest(),
            observer = observerStore.readSummary(),
            knowledge = knowledgeStore.readSummary(),
            workflow = workflowStore.readSummary(),
            recognition = recognitionStore.readSummary(),
        )
    }
}
