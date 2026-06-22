package com.oasismall.oasisai.ui.screens.parayhome

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayFusionHistoryEntry
import com.oasismall.oasisai.domain.paray.ParayFusionPreview
import com.oasismall.oasisai.domain.paray.ParayKnowledgeFusionEngine
import com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary
import com.oasismall.oasisai.domain.paray.ParayManifest
import com.oasismall.oasisai.domain.paray.ParayObserverSummary
import com.oasismall.oasisai.domain.paray.ParayRecognitionSummary
import com.oasismall.oasisai.domain.paray.ParayWorkflowSummary
import com.oasismall.oasisai.domain.transfer.UserExportStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParayHomeUiState(
    val loading: Boolean = true,
    val manifest: ParayManifest? = null,
    val observer: ParayObserverSummary = ParayObserverSummary(),
    val knowledge: ParayKnowledgeSummary = ParayKnowledgeSummary(),
    val workflow: ParayWorkflowSummary = ParayWorkflowSummary(),
    val recognition: ParayRecognitionSummary = ParayRecognitionSummary(),
)

data class ParayFusionUiState(
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val merging: Boolean = false,
    val progressMessage: String? = null,
    val preview: ParayFusionPreview? = null,
    val lastMessage: String? = null,
    val history: List<ParayFusionHistoryEntry> = emptyList(),
    val showHistory: Boolean = false,
)

class ParayHomeViewModel(
    private val repository: ParayHomeRepository,
    private val paray: ParayAgent,
    private val fusionEngine: ParayKnowledgeFusionEngine,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParayHomeUiState())
    val ui: StateFlow<ParayHomeUiState> = _ui.asStateFlow()

    private val _fusion = MutableStateFlow(ParayFusionUiState())
    val fusion: StateFlow<ParayFusionUiState> = _fusion.asStateFlow()

    private var stagingDir: File? = null

    init {
        reload()
        loadFusionHistory()
    }

    fun reload() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = _ui.value.manifest == null)
            val dashboard = withContext(Dispatchers.IO) { repository.loadDashboard() }
            _ui.value = ParayHomeUiState(
                loading = false,
                manifest = dashboard.manifest,
                observer = dashboard.observer,
                knowledge = dashboard.knowledge,
                workflow = dashboard.workflow,
                recognition = dashboard.recognition,
            )
        }
    }

    fun loadFusionHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { fusionEngine.readHistory() }
            _fusion.value = _fusion.value.copy(history = history)
        }
    }

    fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
        return "PARAY_${stamp}.pkp.zip"
    }

    fun exportKnowledge(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            _fusion.value = _fusion.value.copy(exporting = true, lastMessage = null, progressMessage = "Collecting knowledge…")
            try {
                val (zipFile, result) = withContext(Dispatchers.IO) {
                    fusionEngine.exportPackage { progress ->
                        _fusion.value = _fusion.value.copy(progressMessage = progress.label)
                    }
                }
                withContext(Dispatchers.IO) {
                    UserExportStorage.copyFileToUri(context, zipFile, outputUri)
                }
                _fusion.value = _fusion.value.copy(
                    exporting = false,
                    progressMessage = null,
                    lastMessage = "Exported ${result.knowledgeCount} knowledge records",
                )
            } catch (e: Exception) {
                _fusion.value = _fusion.value.copy(
                    exporting = false,
                    progressMessage = null,
                    lastMessage = e.message ?: "Export failed",
                )
            }
        }
    }

    fun importKnowledge(context: Context, uri: Uri) {
        viewModelScope.launch {
            stagingDir?.deleteRecursively()
            stagingDir = null
            _fusion.value = _fusion.value.copy(
                importing = true,
                preview = null,
                lastMessage = null,
                progressMessage = "Reading package…",
            )
            try {
                val zipFile = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                val staged = withContext(Dispatchers.IO) {
                    fusionEngine.stagePackage(zipFile) { progress ->
                        _fusion.value = _fusion.value.copy(progressMessage = progress.label)
                    }
                }
                stagingDir = staged
                val preview = withContext(Dispatchers.IO) { fusionEngine.previewFusion(staged) }
                _fusion.value = _fusion.value.copy(
                    importing = false,
                    preview = preview,
                    progressMessage = null,
                )
            } catch (e: Exception) {
                stagingDir?.deleteRecursively()
                stagingDir = null
                _fusion.value = _fusion.value.copy(
                    importing = false,
                    progressMessage = null,
                    lastMessage = e.message ?: "Invalid knowledge package",
                )
            }
        }
    }

    fun confirmMerge() {
        val dir = stagingDir ?: return
        viewModelScope.launch {
            _fusion.value = _fusion.value.copy(merging = true, progressMessage = "Merging knowledge…")
            try {
                val result = withContext(Dispatchers.IO) {
                    fusionEngine.executeFusion(dir) { progress ->
                        _fusion.value = _fusion.value.copy(progressMessage = progress.label)
                    }
                }
                dir.deleteRecursively()
                stagingDir = null
                reload()
                loadFusionHistory()
                _fusion.value = _fusion.value.copy(
                    merging = false,
                    preview = null,
                    progressMessage = null,
                    lastMessage = buildMergeMessage(result.preview),
                )
            } catch (e: Exception) {
                _fusion.value = _fusion.value.copy(
                    merging = false,
                    progressMessage = null,
                    lastMessage = e.message ?: "Fusion failed",
                )
            }
        }
    }

    fun cancelMerge() {
        stagingDir?.deleteRecursively()
        stagingDir = null
        _fusion.value = _fusion.value.copy(preview = null)
    }

    fun toggleFusionHistory() {
        _fusion.value = _fusion.value.copy(showHistory = !_fusion.value.showHistory)
    }

    fun clearFusionMessage() {
        _fusion.value = _fusion.value.copy(lastMessage = null)
    }

    fun recordOfficeVisit(workplace: String) {
        paray.goToOffice(workplace)
    }

    private fun buildMergeMessage(preview: ParayFusionPreview): String {
        val parts = buildList {
            if (preview.newArticles > 0) add("${preview.newArticles} articles")
            if (preview.newLearnRecords > 0) add("${preview.newLearnRecords} learn records")
            if (preview.newBrands > 0) add("${preview.newBrands} brands")
            if (preview.newWorkflowPatterns > 0) add("${preview.newWorkflowPatterns} workflow patterns")
            if (preview.newRecognitionPatterns > 0) add("${preview.newRecognitionPatterns} recognition patterns")
        }
        return if (parts.isEmpty()) {
            "Fusion complete — no new records (local knowledge already covered this package)"
        } else {
            "Fusion complete — added ${parts.joinToString(", ")}"
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File {
        val dest = File(context.cacheDir, "pkp-import-${System.currentTimeMillis()}.zip")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot read knowledge package")
        input.use { stream ->
            dest.outputStream().use { out -> stream.copyTo(out) }
        }
        return dest
    }
}
