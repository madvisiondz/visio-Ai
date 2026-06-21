package com.oasismall.oasisai.domain.background

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.domain.CsvParseResult
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OasisBackgroundTaskState(
    val running: Boolean = false,
    val kind: OasisBackgroundTaskKind? = null,
    val progress: TaskProgress? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

/**
 * Shared long-task state — survives screen off while [OasisBackgroundTaskService] runs.
 */
class OasisBackgroundTaskManager {
    private val _state = MutableStateFlow(OasisBackgroundTaskState())
    val state: StateFlow<OasisBackgroundTaskState> = _state.asStateFlow()

    @Volatile
    private var pendingKind: OasisBackgroundTaskKind? = null

    @Volatile
    private var pendingUri: Uri? = null

    @Volatile
    private var pendingCsvFileName: String? = null

    @Volatile
    var pendingCsvParse: CsvParseResult? = null
        private set

    @Volatile
    var pendingReadyPngUris: List<Uri>? = null
        private set

    @Volatile
    var pendingReadyPngFolderUri: Uri? = null
        private set

    fun isRunning(): Boolean = _state.value.running || pendingKind != null

    fun enqueue(kind: OasisBackgroundTaskKind, uri: Uri? = null) {
        pendingKind = kind
        pendingUri = uri
        pendingCsvFileName = null
        pendingCsvParse = null
        pendingReadyPngUris = null
        pendingReadyPngFolderUri = null
        _state.value = OasisBackgroundTaskState(
            running = false,
            kind = kind,
            progress = TaskProgress(kind.displayName, 0),
            successMessage = null,
            errorMessage = null,
        )
    }

    fun enqueueCsvImport(fileName: String, parseResult: CsvParseResult) {
        pendingKind = OasisBackgroundTaskKind.CSV_IMPORT
        pendingCsvFileName = fileName
        pendingCsvParse = parseResult
        pendingUri = null
        _state.value = OasisBackgroundTaskState(
            running = false,
            kind = OasisBackgroundTaskKind.CSV_IMPORT,
            progress = TaskProgress("CSV import", 0),
        )
    }

    fun enqueueReadyPngUris(uris: List<Uri>) {
        pendingKind = OasisBackgroundTaskKind.LOAD_READY_PNGS
        pendingReadyPngUris = uris
        pendingReadyPngFolderUri = null
        pendingUri = null
        _state.value = OasisBackgroundTaskState(
            running = false,
            kind = OasisBackgroundTaskKind.LOAD_READY_PNGS,
            progress = TaskProgress("Load Oasis PNGs", 0),
        )
    }

    fun enqueueReadyPngFolder(treeUri: Uri) {
        pendingKind = OasisBackgroundTaskKind.LOAD_READY_PNGS
        pendingReadyPngFolderUri = treeUri
        pendingReadyPngUris = null
        pendingUri = null
        _state.value = OasisBackgroundTaskState(
            running = false,
            kind = OasisBackgroundTaskKind.LOAD_READY_PNGS,
            progress = TaskProgress("Load Oasis PNGs", 0),
        )
    }

    fun startIfPending(context: Context) {
        if (_state.value.running) return
        val kind = pendingKind ?: return
        pendingKind = null
        val uri = pendingUri
        pendingUri = null
        _state.update {
            it.copy(
                running = true,
                kind = kind,
                progress = TaskProgress(kind.displayName, 0),
                successMessage = null,
                errorMessage = null,
            )
        }
        OasisBackgroundTaskService.start(context.applicationContext, kind, uri)
    }

    fun takeCsvImport(): Pair<String, CsvParseResult>? {
        val name = pendingCsvFileName ?: return null
        val parse = pendingCsvParse ?: return null
        pendingCsvFileName = null
        pendingCsvParse = null
        return name to parse
    }

    fun takeReadyPngUris(): List<Uri>? {
        val uris = pendingReadyPngUris
        pendingReadyPngUris = null
        return uris
    }

    fun takeReadyPngFolder(): Uri? {
        val uri = pendingReadyPngFolderUri
        pendingReadyPngFolderUri = null
        return uri
    }

    fun markRunning(kind: OasisBackgroundTaskKind) {
        _state.update {
            it.copy(
                running = true,
                kind = kind,
                progress = TaskProgress(kind.displayName, 0),
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun updateProgress(progress: TaskProgress) {
        _state.update { it.copy(running = true, progress = progress) }
    }

    fun markSuccess(message: String) {
        _state.update {
            it.copy(
                running = false,
                progress = TaskProgress("Complete", 100),
                successMessage = message,
                errorMessage = null,
            )
        }
    }

    fun markFailed(message: String) {
        _state.update {
            it.copy(
                running = false,
                errorMessage = message,
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
