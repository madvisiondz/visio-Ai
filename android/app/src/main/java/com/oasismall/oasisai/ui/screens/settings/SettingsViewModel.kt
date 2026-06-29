package com.oasismall.oasisai.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.DashboardStats
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskKind
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager
import com.oasismall.oasisai.domain.settings.BackupSecurityStore
import com.oasismall.oasisai.domain.visio.PhotoroomStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val isReindexing: Boolean = false,
    val isImportingCsv: Boolean = false,
    val isLoadingImages: Boolean = false,
    val isExportingPngs: Boolean = false,
    val isPurgingCatalog: Boolean = false,
    val isExportingBackup: Boolean = false,
    val isImportingBackup: Boolean = false,
    val isExportingVisioPro: Boolean = false,
    val isImportingVisioPro: Boolean = false,
    val isImportingVisioProMedia: Boolean = false,
    val isRestoringSubBarcodeFlavors: Boolean = false,
    val progress: com.oasismall.oasisai.util.TaskProgress? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

data class DatabaseOverview(
    val totalArticles: Int = 0,
    val missingImages: Int = 0,
    val pngFilesOnDevice: Int = 0,
    val withGalleryImage: Int = 0,
    val oasisModelPngs: Int = 0,
    val importantRayonsFiltered: Boolean = false,
    val importantRayonsCount: Int = 0,
)

class SettingsViewModel(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val backgroundTasks: OasisBackgroundTaskManager,
    private val backupSecurityStore: BackupSecurityStore,
    private val visioProMediaImporter: com.oasismall.oasisai.domain.transfer.VisioProMediaImporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _pngFileCount = MutableStateFlow(0)
    private val _oasisModelCount = MutableStateFlow(0)

    val overview: StateFlow<DatabaseOverview> = combine(
        repository.observeDashboardStats(),
        repository.observeMissingImageCount(),
        repository.importantRayonsConfig,
        _pngFileCount,
        _oasisModelCount,
    ) { stats: DashboardStats?, missing: Int, rayonConfig, pngCount: Int, oasisModel: Int ->
        val total = stats?.totalArticles ?: 0
        DatabaseOverview(
            totalArticles = total,
            missingImages = missing,
            pngFilesOnDevice = pngCount,
            withGalleryImage = (total - missing).coerceAtLeast(0),
            oasisModelPngs = oasisModel,
            importantRayonsFiltered = rayonConfig.configured && rayonConfig.selectedRayons.isNotEmpty(),
            importantRayonsCount = rayonConfig.selectedRayons.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseOverview())

    private val _photoroomFolderLabel = MutableStateFlow(PhotoroomStorage.DEFAULT_DISPLAY_PATH)
    val photoroomFolderLabel: StateFlow<String> = _photoroomFolderLabel.asStateFlow()

    private val _photoroomCustomFolder = MutableStateFlow(false)
    val photoroomCustomFolder: StateFlow<Boolean> = _photoroomCustomFolder.asStateFlow()

    private val _backupEncryptionEnabled = MutableStateFlow(backupSecurityStore.isEncryptionEnabled())
    val backupEncryptionEnabled: StateFlow<Boolean> = _backupEncryptionEnabled.asStateFlow()

    init {
        refreshPngCountsLight()
        viewModelScope.launch {
            backgroundTasks.state.collect { task ->
                _uiState.update { ui ->
                    if (task.running) {
                        ui.copy(
                            isRestoringSubBarcodeFlavors = task.kind == OasisBackgroundTaskKind.SYNC_SUB_PNGS,
                            isReindexing = task.kind == OasisBackgroundTaskKind.REINDEX_IMAGES,
                            isExportingPngs = task.kind == OasisBackgroundTaskKind.EXPORT_PNG_DATABASE,
                            isPurgingCatalog = task.kind == OasisBackgroundTaskKind.PURGE_GESTIUM,
                            isExportingBackup = task.kind == OasisBackgroundTaskKind.EXPORT_FULL_BACKUP,
                            isImportingBackup = task.kind == OasisBackgroundTaskKind.IMPORT_FULL_BACKUP,
                            isExportingVisioPro = task.kind == OasisBackgroundTaskKind.EXPORT_VISIOPRO_BUNDLE,
                            isImportingVisioPro = task.kind == OasisBackgroundTaskKind.IMPORT_VISIOPRO_BUNDLE,
                            isImportingCsv = task.kind == OasisBackgroundTaskKind.CSV_IMPORT,
                            isLoadingImages = task.kind == OasisBackgroundTaskKind.LOAD_READY_PNGS,
                            progress = task.progress,
                            message = null,
                            messageIsError = false,
                        )
                    } else {
                        val finished = task.successMessage != null || task.errorMessage != null
                        if (finished) {
                            refreshPngCountsLight()
                            if (task.kind == OasisBackgroundTaskKind.REINDEX_IMAGES ||
                                task.kind == OasisBackgroundTaskKind.IMPORT_FULL_BACKUP ||
                                task.kind == OasisBackgroundTaskKind.LOAD_READY_PNGS
                            ) {
                                refreshPngCountsFull()
                            }
                        }
                        ui.copy(
                            isRestoringSubBarcodeFlavors = false,
                            isReindexing = false,
                            isExportingPngs = false,
                            isPurgingCatalog = false,
                            isExportingBackup = false,
                            isImportingBackup = false,
                            isExportingVisioPro = false,
                            isImportingVisioPro = false,
                            isImportingCsv = false,
                            isLoadingImages = false,
                            progress = null,
                            message = when {
                                task.successMessage != null -> task.successMessage
                                task.errorMessage != null -> task.errorMessage
                                else -> ui.message
                            },
                            messageIsError = task.errorMessage != null,
                        )
                    }
                }
            }
        }
    }

    fun refreshPhotoroomFolder(context: Context) {
        _photoroomFolderLabel.value = PhotoroomStorage.displayPath(context)
        _photoroomCustomFolder.value = PhotoroomStorage.isCustomFolder(context)
    }

    fun setPhotoroomFolder(context: Context, treeUri: Uri) {
        val label = PhotoroomStorage.saveFolderTree(context, treeUri)
        refreshPhotoroomFolder(context)
        showMessage("PhotoRoom folder set to $label")
    }

    fun clearPhotoroomFolder(context: Context) {
        PhotoroomStorage.clearFolderTree(context)
        refreshPhotoroomFolder(context)
        showMessage("PhotoRoom folder reset to ${PhotoroomStorage.DEFAULT_DISPLAY_PATH}")
    }

    fun refreshPngCountsLight() {
        viewModelScope.launch(Dispatchers.IO) {
            _pngFileCount.value = imageMatcher.countPngFiles()
        }
    }

    private fun refreshPngCountsFull() {
        viewModelScope.launch(Dispatchers.IO) {
            _pngFileCount.value = imageMatcher.countPngFiles()
            val indexed = imageMatcher.scanPngFilesIndexed()
            _oasisModelCount.value = indexed.count { it.isOasisReadyModel }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
        backgroundTasks.clearMessages()
    }

    fun showMessage(value: String) {
        _uiState.value = _uiState.value.copy(message = value, messageIsError = false)
    }

    private fun start(context: Context, kind: OasisBackgroundTaskKind, uri: Uri? = null) {
        if (backgroundTasks.isRunning()) {
            showMessage("Another task is already running — check the notification.")
            return
        }
        backgroundTasks.enqueue(kind, uri)
        backgroundTasks.startIfPending(context)
    }

    fun importGestiumCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            if (backgroundTasks.isRunning()) {
                showMessage("Another task is already running — check the notification.")
                return@launch
            }
            val enqueueResult = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val fileName = uri.lastPathSegment ?: "import.csv"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val parseResult = com.oasismall.oasisai.domain.CsvParser.parseWithFallback(stream)
                        val validation = com.oasismall.oasisai.domain.CsvParser.validate(parseResult.headers)
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
                    backgroundTasks.enqueueCsvImport(fileName, parseResult)
                    backgroundTasks.startIfPending(context)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        message = err.message ?: "CSV import failed",
                        messageIsError = true,
                    )
                },
            )
        }
    }

    fun exportProductImages(context: Context) =
        start(context, OasisBackgroundTaskKind.EXPORT_PNG_DATABASE)

    fun reindexProductImages(context: Context) =
        start(context, OasisBackgroundTaskKind.REINDEX_IMAGES)

    fun loadReadyPngImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || backgroundTasks.isRunning()) return
        backgroundTasks.enqueueReadyPngUris(uris)
        backgroundTasks.startIfPending(context)
    }

    fun loadReadyPngFolder(context: Context, treeUri: Uri) {
        if (backgroundTasks.isRunning()) return
        backgroundTasks.enqueueReadyPngFolder(treeUri)
        backgroundTasks.startIfPending(context)
    }

    fun purgeGestiumCatalog(context: Context) =
        start(context, OasisBackgroundTaskKind.PURGE_GESTIUM)

    fun syncSubPngs(context: Context) =
        start(context, OasisBackgroundTaskKind.SYNC_SUB_PNGS)

    fun exportFullBackup(context: Context, outputUri: Uri) =
        start(context, OasisBackgroundTaskKind.EXPORT_FULL_BACKUP, outputUri)

    fun importFullBackup(context: Context, uri: Uri) =
        start(context, OasisBackgroundTaskKind.IMPORT_FULL_BACKUP, uri)

    fun exportVisioProBundle(context: Context, outputUri: Uri) =
        start(context, OasisBackgroundTaskKind.EXPORT_VISIOPRO_BUNDLE, outputUri)

    fun importVisioProBundle(context: Context, uri: Uri) =
        start(context, OasisBackgroundTaskKind.IMPORT_VISIOPRO_BUNDLE, uri)

    fun importVisioProMedia(context: Context, uri: Uri) {
        if (_uiState.value.isImportingVisioProMedia) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingVisioProMedia = true, message = null, progress = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    visioProMediaImporter.importFromUri(uri) { progress ->
                        _uiState.update { it.copy(progress = progress) }
                    }
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isImportingVisioProMedia = false,
                        progress = null,
                        message = "VisioPRO images installed (${result.fileCount} files).",
                        messageIsError = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isImportingVisioProMedia = false,
                        progress = null,
                        message = e.message ?: "VisioPRO media install failed.",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun setBackupEncryptionEnabled(enabled: Boolean) {
        backupSecurityStore.setEncryptionEnabled(enabled)
        _backupEncryptionEnabled.value = enabled
    }

    fun setBackupPassword(password: String) {
        backupSecurityStore.setPassword(password)
    }
}
