package com.oasismall.oasisai.ui.screens.backgroundremoval

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalOptions
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class BgRemovalStep {
    PICK,
    ADJUST,
    PREVIEW,
}

data class BgRemovalUiState(
    val step: BgRemovalStep = BgRemovalStep.PICK,
    val article: ArticleWithImage? = null,
    val sourcePath: String? = null,
    val originalBackupPath: String? = null,
    val previewOutputPath: String? = null,
    val isProcessing: Boolean = false,
    val progressLabel: String? = null,
    val error: String? = null,
    val modelReady: Boolean = false,
    val maskThreshold: Float = 0.45f,
    val edgeSmooth: Int = 2,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
)

class BackgroundRemovalViewModel(
    private val articleId: Long,
    private val appContext: Context,
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
    private val bgService: BackgroundRemovalService,
) : ViewModel() {
    private val _ui = MutableStateFlow(BgRemovalUiState(modelReady = bgService.isModelReady()))
    val ui: StateFlow<BgRemovalUiState> = _ui.asStateFlow()

    init {
        if (articleId > 0) {
            viewModelScope.launch {
                val article = repository.getArticleWithImageById(articleId)
                _ui.value = _ui.value.copy(article = article)
                article?.imagePath?.takeIf { File(it).exists() }?.let { path ->
                    _ui.value = _ui.value.copy(sourcePath = path, step = BgRemovalStep.ADJUST)
                }
            }
        }
    }

    fun setSourceFromUri(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isProcessing = true, progressLabel = "Importing photo…", error = null)
            val temp = File(bgService.workDir, "pick_${System.currentTimeMillis()}.jpg")
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not read image")
                _ui.value = _ui.value.copy(
                    sourcePath = temp.absolutePath,
                    step = BgRemovalStep.ADJUST,
                    isProcessing = false,
                    progressLabel = null,
                )
            }.onFailure { e ->
                _ui.value = _ui.value.copy(isProcessing = false, error = e.message)
            }
        }
    }

    fun setMaskThreshold(value: Float) {
        _ui.value = _ui.value.copy(maskThreshold = value)
    }

    fun setEdgeSmooth(value: Int) {
        _ui.value = _ui.value.copy(edgeSmooth = value)
    }

    fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
        _ui.value = _ui.value.copy(cropLeft = left, cropTop = top, cropRight = right, cropBottom = bottom)
    }

    fun removeBackground() {
        val path = _ui.value.sourcePath ?: return
        val file = File(path)
        if (!file.exists()) {
            _ui.value = _ui.value.copy(error = "Source image missing")
            return
        }
        val options = BackgroundRemovalOptions(
            maskThreshold = _ui.value.maskThreshold,
            edgeSmoothRadius = _ui.value.edgeSmooth,
            cropLeft = _ui.value.cropLeft,
            cropTop = _ui.value.cropTop,
            cropRight = _ui.value.cropRight,
            cropBottom = _ui.value.cropBottom,
        )
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isProcessing = true, error = null, progressLabel = "Starting…")
            val result = bgService.removeBackground(file, options) { label ->
                _ui.value = _ui.value.copy(progressLabel = label)
            }
            if (result.success && result.outputPngPath != null) {
                _ui.value = _ui.value.copy(
                    isProcessing = false,
                    step = BgRemovalStep.PREVIEW,
                    previewOutputPath = result.outputPngPath,
                    originalBackupPath = result.originalPath,
                    progressLabel = null,
                )
            } else {
                _ui.value = _ui.value.copy(
                    isProcessing = false,
                    error = result.errorMessage ?: "Background removal failed",
                    progressLabel = null,
                )
            }
        }
    }

    fun retry() {
        _ui.value = _ui.value.copy(step = BgRemovalStep.ADJUST, previewOutputPath = null, error = null)
    }

    fun acceptResult(onDone: () -> Unit) {
        val aid = articleId
        val output = _ui.value.previewOutputPath
        val original = _ui.value.originalBackupPath
        if (aid <= 0 || output == null || original == null) {
            _ui.value = _ui.value.copy(error = "Open from an article to save the cutout to the catalog.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isProcessing = true, progressLabel = "Saving to catalog…")
            runCatching {
                imageMatcher.registerBackgroundRemovedImage(aid, File(output), original)
            }.onSuccess {
                _ui.value = _ui.value.copy(isProcessing = false)
                onDone()
            }.onFailure { e ->
                _ui.value = _ui.value.copy(isProcessing = false, error = e.message)
            }
        }
    }

    override fun onCleared() {
        bgService.close()
        super.onCleared()
    }
}
