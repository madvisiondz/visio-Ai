package com.oasismall.oasisai.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.DashboardStats
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.ImportService
import com.oasismall.oasisai.domain.ReadyPngLoader
import com.oasismall.oasisai.domain.ReadyPngModel
import com.oasismall.oasisai.domain.visio.ProductImagesExporter
import com.oasismall.oasisai.domain.visio.PhotoroomStorage
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val isReindexing: Boolean = false,
    val isLoadingSample: Boolean = false,
    val isLoadingImages: Boolean = false,
    val isExportingPngs: Boolean = false,
    val progress: TaskProgress? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

data class DatabaseOverview(
    val totalArticles: Int = 0,
    val missingImages: Int = 0,
    val pngFilesOnDevice: Int = 0,
    val withGalleryImage: Int = 0,
    val oasisModelPngs: Int = 0,
)

class SettingsViewModel(
    private val repository: OasisRepository,
    private val importService: ImportService,
    private val imageMatcher: ImageMatcher,
    private val readyPngLoader: ReadyPngLoader,
    private val productImagesExporter: ProductImagesExporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _pngFileCount = MutableStateFlow(0)
    private val _oasisModelCount = MutableStateFlow(0)

    val overview: StateFlow<DatabaseOverview> = combine(
        repository.observeDashboardStats(),
        repository.observeMissingImageCount(),
        _pngFileCount,
        _oasisModelCount,
    ) { stats: DashboardStats?, missing: Int, pngCount: Int, oasisModel: Int ->
        val total = stats?.totalArticles ?: 0
        DatabaseOverview(
            totalArticles = total,
            missingImages = missing,
            pngFilesOnDevice = pngCount,
            withGalleryImage = (total - missing).coerceAtLeast(0),
            oasisModelPngs = oasisModel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseOverview())

    private val _photoroomFolderLabel = MutableStateFlow(PhotoroomStorage.DEFAULT_DISPLAY_PATH)
    val photoroomFolderLabel: StateFlow<String> = _photoroomFolderLabel.asStateFlow()

    private val _photoroomCustomFolder = MutableStateFlow(false)
    val photoroomCustomFolder: StateFlow<Boolean> = _photoroomCustomFolder.asStateFlow()

    init {
        refreshPngCountsLight()
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
    }

    fun showMessage(value: String) {
        _uiState.value = _uiState.value.copy(message = value, messageIsError = false)
    }

    fun loadSampleData(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingSample = true,
                message = null,
                progress = TaskProgress("Loading sample data", 0),
            )
            val result = withContext(Dispatchers.IO) {
                importService.importSample(context) { progress ->
                    _uiState.value = _uiState.value.copy(isLoadingSample = true, progress = progress)
                }
            }
            refreshPngCountsLight()
            _uiState.value = _uiState.value.copy(
                isLoadingSample = false,
                progress = null,
                message = if (result.success) {
                    "Sample data loaded (${result.summary?.newCount ?: 0} articles)."
                } else {
                    result.errorMessage ?: "Sample import failed"
                },
                messageIsError = !result.success,
            )
        }
    }

    fun exportProductImages(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExportingPngs = true,
                message = null,
                progress = TaskProgress("Exporting PNG database", 0),
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    productImagesExporter.export { progress ->
                        _uiState.value = _uiState.value.copy(isExportingPngs = true, progress = progress)
                    }
                }
            }.fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        isExportingPngs = false,
                        progress = null,
                        message = if (result.copied == 0 && result.skipped == 0) {
                            "No PNGs in product_images to export."
                        } else {
                            "Exported ${result.copied} PNG(s) to ${result.displayPath}/" +
                                if (result.skipped > 0) " (${result.skipped} already there)" else ""
                        },
                        messageIsError = false,
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isExportingPngs = false,
                        progress = null,
                        message = "${err.javaClass.simpleName}: ${err.message}",
                        messageIsError = true,
                    )
                },
            )
        }
    }

    fun reindexProductImages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isReindexing = true,
                message = null,
                progress = TaskProgress("Preparing image re-index", 0),
            )
            runReindex { progress ->
                _uiState.value = _uiState.value.copy(isReindexing = true, progress = progress)
            }.fold(
                onSuccess = { msg ->
                    refreshPngCountsFull()
                    _uiState.value = _uiState.value.copy(
                        isReindexing = false,
                        progress = null,
                        message = msg,
                        messageIsError = false,
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isReindexing = false,
                        progress = null,
                        message = "${err.javaClass.simpleName}: ${err.message}",
                        messageIsError = true,
                    )
                },
            )
        }
    }

    fun loadReadyPngImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { runReadyPngLoad { readyPngLoader.loadFromUris(context, uris, it) } }
    }

    fun loadReadyPngFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch { runReadyPngLoad { readyPngLoader.loadFromFolderTree(context, treeUri, it) } }
    }

    private suspend fun runReadyPngLoad(
        block: suspend ((TaskProgress) -> Unit) -> com.oasismall.oasisai.domain.ReadyPngLoadResult,
    ) {
        _uiState.value = _uiState.value.copy(
            isLoadingImages = true,
            message = null,
            progress = TaskProgress("Loading Oasis ready PNGs", 0),
        )
        runCatching {
            val loadResult = withContext(Dispatchers.IO) {
                block { progress ->
                    _uiState.value = _uiState.value.copy(isLoadingImages = true, progress = progress)
                }
            }
            val articleCount = withContext(Dispatchers.IO) { repository.getAllArticles().size }
            val reindexMsg = if (articleCount == 0) {
                "Import Gestium CSV first, then re-index to link images to articles."
            } else {
                reindexAfterLoad()
            }
            refreshPngCountsFull()
            buildLoadMessage(loadResult, reindexMsg)
        }.fold(
            onSuccess = { msg ->
                _uiState.value = _uiState.value.copy(
                    isLoadingImages = false,
                    progress = null,
                    message = msg,
                    messageIsError = false,
                )
            },
            onFailure = { err ->
                refreshPngCountsLight()
                _uiState.value = _uiState.value.copy(
                    isLoadingImages = false,
                    progress = null,
                    message = "${err.javaClass.simpleName}: ${err.message}",
                    messageIsError = true,
                )
            },
        )
    }

    private suspend fun reindexAfterLoad(): String = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(progress = TaskProgress("Matching PNGs to articles", 75))
        val articles = repository.getAllArticles()
        imageMatcher.syncImagesForArticles(articles) { progress ->
            val shifted = 75 + (progress.normalizedPercent * 20 / 100)
            _uiState.value = _uiState.value.copy(
                isLoadingImages = true,
                progress = progress.copy(percent = shifted),
            )
        }
        val missing = repository.countMissingImages()
        "Re-indexed ${articles.size} articles; $missing still missing images."
    }

    private fun buildLoadMessage(
        load: com.oasismall.oasisai.domain.ReadyPngLoadResult,
        reindexMsg: String,
    ): String {
        val limitNote = if (load.limitedByPicker) {
            " (max ${ReadyPngLoader.MAX_FILES_PER_PICK} per load — run again for more)"
        } else {
            ""
        }
        return buildString {
            append("Loaded ${load.copied} new PNGs")
            if (load.alreadyPresent > 0) append(", ${load.alreadyPresent} already on device")
            if (load.skipped > 0) append(", ${load.skipped} skipped")
            append(limitNote)
            append(". ")
            append(reindexMsg)
            append(" Tags: ${ReadyPngModel.SOURCE_LABEL}.")
        }
    }

    private suspend fun runReindex(onProgress: (TaskProgress) -> Unit): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            onProgress(TaskProgress("Loading articles", 5))
            val articles = repository.getAllArticles()
            if (articles.isEmpty()) {
                return@withContext "Import Gestium CSV first — no articles to match."
            }
            imageMatcher.syncImagesForArticles(articles, onProgress)
            onProgress(TaskProgress("Counting missing images", 95))
            val missing = repository.countMissingImages()
            onProgress(TaskProgress("Re-index complete", 100))
            val modelCount = imageMatcher.scanPngFilesIndexed().count { it.isOasisReadyModel }
            "Re-indexed ${articles.size} articles ($modelCount Oasis-model PNGs). $missing still missing images."
        }
    }
}
