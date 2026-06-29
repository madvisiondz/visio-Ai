package com.oasismall.oasisai.ui.screens.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.CsvParser
import com.oasismall.oasisai.domain.ImportResult
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager
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

data class ImportUiState(
    val isLoading: Boolean = false,
    val lastResult: ImportResult? = null,
    val error: String? = null,
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

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = TaskProgress("Opening CSV file", 5),
            )
            val enqueueResult = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = uri.lastPathSegment ?: "import.csv"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val parseResult = CsvParser.parseWithFallback(stream)
                        val validation = CsvParser.validate(parseResult.headers)
                        if (!validation.isValid) {
                            throw IllegalArgumentException(
                                "Missing columns: ${validation.missingColumns.joinToString(", ")}",
                            )
                        }
                        if (parseResult.rows.isEmpty()) {
                            throw IllegalArgumentException("No valid article rows found in file.")
                        }
                        fileName to parseResult
                    } ?: throw IllegalArgumentException("Cannot open file")
                }
            }
            enqueueResult.fold(
                onSuccess = { (fileName, parseResult) ->
                    if (backgroundTasks.isRunning()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Another background task is already running.",
                            progress = null,
                        )
                        return@launch
                    }
                    backgroundTasks.enqueueCsvImport(fileName, parseResult)
                    backgroundTasks.startIfPending(context)
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        error = null,
                        progress = TaskProgress("Starting CSV import", 0),
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = err.message ?: "CSV import failed",
                        progress = null,
                    )
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, lastResult = null)
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
