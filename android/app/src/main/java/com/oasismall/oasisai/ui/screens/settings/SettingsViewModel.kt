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

data class SettingsUiState(
    val isReindexing: Boolean = false,
    val isLoadingSample: Boolean = false,
    val isLoadingImages: Boolean = false,
    val isExportingPngs: Boolean = false,
    val isPurgingCatalog: Boolean = false,
    val isExportingBackup: Boolean = false,
    val isImportingBackup: Boolean = false,
    val isExportingVisioPro: Boolean = false,
    val isImportingVisioPro: Boolean = false,
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
                            isLoadingSample = task.kind == OasisBackgroundTaskKind.LOAD_SAMPLE_DATA,
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
                            isLoadingSample = false,
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

    fun loadSampleData(context: Context) =
        start(context, OasisBackgroundTaskKind.LOAD_SAMPLE_DATA)

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

    fun setBackupEncryptionEnabled(enabled: Boolean) {
        backupSecurityStore.setEncryptionEnabled(enabled)
        _backupEncryptionEnabled.value = enabled
    }

    fun setBackupPassword(password: String) {
        backupSecurityStore.setPassword(password)
    }
}
