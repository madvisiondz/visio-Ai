package com.oasismall.oasisai.ui.screens.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.CsvParseResult
import com.oasismall.oasisai.domain.CsvParser
import com.oasismall.oasisai.domain.ImportResult
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager
import com.oasismall.oasisai.data.repository.ImportChangeUiRow
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.domain.settings.ImportantRayonsConfig
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportPreview(
    val fileName: String,
    val parseResult: CsvParseResult,
    val sampleRows: List<String>,
    val scopedRowCount: Int,
    val importantRayonsFiltered: Boolean = false,
    val importantRayonsCount: Int = 0,
)

data class ImportUiState(
    val isLoading: Boolean = false,
    val lastResult: ImportResult? = null,
    val error: String? = null,
    val preview: ImportPreview? = null,
    val progress: TaskProgress? = null,
)

class ImportViewModel(
    private val repository: OasisRepository,
    private val backgroundTasks: OasisBackgroundTaskManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val imports = repository.observeImports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importantRayonsConfig = repository.importantRayonsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportantRayonsConfig())

    init {
        viewModelScope.launch {
            backgroundTasks.state.collect { task ->
                if (task.kind != com.oasismall.oasisai.domain.background.OasisBackgroundTaskKind.CSV_IMPORT) return@collect
                _uiState.update { ui ->
                    when {
                        task.running -> ui.copy(
                            isLoading = true,
                            progress = task.progress,
                            error = null,
                        )
                        task.successMessage != null -> ui.copy(
                            isLoading = false,
                            preview = null,
                            progress = null,
                            lastResult = ImportResult(success = true),
                            error = null,
                        )
                        task.errorMessage != null -> ui.copy(
                            isLoading = false,
                            progress = null,
                            error = task.errorMessage,
                        )
                        else -> ui
                    }
                }
            }
        }
    }

    fun previewFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                preview = null,
                progress = TaskProgress("Opening CSV file", 5),
            )
            val fileName = uri.lastPathSegment ?: "import.csv"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Reading CSV file", 20))
                        val parseResult = CsvParser.parseWithFallback(stream)
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Checking columns", 85))
                        val validation = CsvParser.validate(parseResult.headers)
                        if (!validation.isValid) {
                            throw IllegalArgumentException(
                                "Missing columns: ${validation.missingColumns.joinToString(", ")}",
                            )
                        }
                        if (parseResult.rows.isEmpty()) {
                            throw IllegalArgumentException("No valid article rows found in file.")
                        }
                        val rayonConfig = repository.getImportantRayonsConfig()
                        val scoped = rayonConfig.configured && rayonConfig.selectedRayons.isNotEmpty()
                        val scopedRowCount = if (scoped) {
                            parseResult.rows.count { repository.matchesImportantRayon(it.rayon, rayonConfig) }
                        } else {
                            parseResult.rows.size
                        }
                        val sampleRows = if (scoped) {
                            parseResult.rows.asSequence()
                                .filter { repository.matchesImportantRayon(it.rayon, rayonConfig) }
                                .take(10)
                                .map { "${it.designation} — ${it.barcode} — ${it.price}" }
                                .toList()
                        } else {
                            parseResult.rows.take(10).map {
                                "${it.designation} — ${it.barcode} — ${it.price}"
                            }
                        }
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Preview ready", 100))
                        ImportPreview(
                            fileName = fileName,
                            parseResult = parseResult,
                            scopedRowCount = scopedRowCount,
                            importantRayonsFiltered = scoped,
                            importantRayonsCount = rayonConfig.selectedRayons.size,
                            sampleRows = sampleRows,
                        )
                    } ?: throw IllegalArgumentException("Cannot open file")
                }
            }
            _uiState.value = result.fold(
                onSuccess = { preview ->
                    ImportUiState(isLoading = false, preview = preview, progress = null)
                },
                onFailure = { err ->
                    ImportUiState(
                        isLoading = false,
                        error = "Debug: ${err.javaClass.simpleName} while previewing CSV (${_uiState.value.progress?.label ?: "no step"}): ${err.message}",
                        progress = null,
                    )
                },
            )
        }
    }

    fun confirmImport(context: Context) {
        val preview = _uiState.value.preview ?: return
        if (backgroundTasks.isRunning()) {
            _uiState.value = _uiState.value.copy(error = "Another background task is already running.")
            return
        }
        backgroundTasks.enqueueCsvImport(preview.fileName, preview.parseResult)
        backgroundTasks.startIfPending(context)
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            progress = TaskProgress("Starting CSV import", 0),
        )
    }

    fun cancelPreview() {
        _uiState.value = _uiState.value.copy(preview = null)
    }

    fun importFromUri(context: Context, uri: Uri) {
        previewFromUri(context, uri)
    }

    fun importSample(context: Context) {
        if (backgroundTasks.isRunning()) return
        backgroundTasks.enqueue(com.oasismall.oasisai.domain.background.OasisBackgroundTaskKind.LOAD_SAMPLE_DATA)
        backgroundTasks.startIfPending(context)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, lastResult = null, preview = null)
        backgroundTasks.clearMessages()
    }

    fun observeChanges(importId: Long) = repository.observeImportChanges(importId)

    fun observeChangesEnriched(importId: Long) =
        repository.observeMeaningfulImportChangesEnrichedFiltered(importId)
            .flowOn(Dispatchers.IO)

    fun addToShareCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.SHARE, CartSourceTags.IMPORT_CHANGE)
        }
    }

    fun addToPhotoshootCart(articleId: Long) {
        viewModelScope.launch {
            repository.addToCart(articleId, CartType.PHOTOSHOOT, CartSourceTags.IMPORT_CHANGE)
        }
    }
}
