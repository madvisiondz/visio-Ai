package com.oasismall.oasisai.ui.screens.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.CsvParseResult
import com.oasismall.oasisai.domain.CsvParser
import com.oasismall.oasisai.domain.ImportResult
import com.oasismall.oasisai.domain.ImportService
import com.oasismall.oasisai.data.repository.ImportChangeUiRow
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.ui.components.CartSourceTags
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportPreview(
    val fileName: String,
    val parseResult: CsvParseResult,
    val sampleRows: List<String>,
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
    private val importService: ImportService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val imports = repository.observeImports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Parsing CSV file", 35))
                        val parseResult = CsvParser.parseWithFallback(stream)
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Validating CSV rows", 75))
                        val validation = CsvParser.validate(parseResult.headers)
                        if (!validation.isValid) {
                            throw IllegalArgumentException(
                                "Missing columns: ${validation.missingColumns.joinToString(", ")}",
                            )
                        }
                        if (parseResult.rows.isEmpty()) {
                            throw IllegalArgumentException("No valid article rows found in file.")
                        }
                        _uiState.value = _uiState.value.copy(progress = TaskProgress("Preview ready", 100))
                        ImportPreview(
                            fileName = fileName,
                            parseResult = parseResult,
                            sampleRows = parseResult.rows.take(10).map {
                                "${it.designation} — ${it.barcode} — ${it.price}"
                            },
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

    fun confirmImport() {
        val preview = _uiState.value.preview ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = TaskProgress("Starting import", 0),
            )
            val result = withContext(Dispatchers.IO) {
                importService.importFromParseResult(preview.parseResult, preview.fileName) { progress ->
                    _uiState.value = _uiState.value.copy(isLoading = true, progress = progress)
                }
            }
            _uiState.value = ImportUiState(
                isLoading = false,
                lastResult = result,
                error = result.errorMessage?.let {
                    "Debug: Import failed (${_uiState.value.progress?.label ?: "no step"}): $it"
                },
                progress = null,
            )
        }
    }

    fun cancelPreview() {
        _uiState.value = _uiState.value.copy(preview = null)
    }

    fun importFromUri(context: Context, uri: Uri) {
        previewFromUri(context, uri)
    }

    fun importSample(context: Context) {
        viewModelScope.launch {
            _uiState.value = ImportUiState(
                isLoading = true,
                progress = TaskProgress("Loading sample data", 0),
            )
            val result = withContext(Dispatchers.IO) {
                context.assets.open("sample_articles.csv").use { stream ->
                    importService.importFromStream(stream, "sample_articles.csv") { progress ->
                        _uiState.value = _uiState.value.copy(isLoading = true, progress = progress)
                    }
                }
            }
            _uiState.value = ImportUiState(
                isLoading = false,
                lastResult = result,
                error = result.errorMessage?.let {
                    "Debug: Sample import failed (${_uiState.value.progress?.label ?: "no step"}): $it"
                },
                progress = null,
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, lastResult = null, preview = null)
    }

    fun observeChanges(importId: Long) = repository.observeImportChanges(importId)

    /** Meaningful changes only (excludes ~26k UNCHANGED rows per large import). */
    fun observeChangesEnriched(importId: Long) =
        repository.observeMeaningfulImportChanges(importId)
            .mapLatest { repository.enrichImportChanges(it) }
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
