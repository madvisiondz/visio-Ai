package com.oasismall.oasisai.domain.paray

import android.content.Context
import android.net.Uri
import com.oasismall.oasisai.OasisApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared import state — survives screen off / app background while [ParayImportForegroundService] runs.
 */
class ParayImportManager(private val app: OasisApp) {
    private val _state = MutableStateFlow(ParayImportUiState())
    val state: StateFlow<ParayImportUiState> = _state.asStateFlow()

    @Volatile
    private var pendingUri: Uri? = null

    private var learnedBefore = 0
    private var fingerprintsBefore = 0

    fun enqueue(uri: Uri) {
        pendingUri = uri
        learnedBefore = app.paray.learnedProductCount()
        fingerprintsBefore = app.paray.fingerprintCount()
        _state.value = ParayImportUiState(
            status = ParayImportStatus.Idle,
            progress = ParayImportProgress("Ready to load fingerprints"),
            neural = app.paray.buildNeuralSnapshot(
                learnedBefore = learnedBefore,
                fingerprintsBefore = fingerprintsBefore,
            ),
        )
    }

    fun startIfPending(context: Context) {
        if (_state.value.status == ParayImportStatus.Running) return
        val uri = pendingUri ?: return
        pendingUri = null
        ParayImportForegroundService.start(context.applicationContext, uri)
    }

    fun markRunning(totalHint: Int) {
        _state.update {
            it.copy(
                status = ParayImportStatus.Running,
                runningInBackground = true,
                progress = ParayImportProgress("Starting import", total = totalHint),
                error = null,
                result = null,
            )
        }
    }

    fun updateProgress(progress: ParayImportProgress) {
        _state.update {
            val base = app.paray.buildNeuralSnapshot(
                learnedBefore = learnedBefore,
                fingerprintsBefore = fingerprintsBefore,
            )
            it.copy(
                status = ParayImportStatus.Running,
                progress = progress,
                neural = base.copy(
                    modelId = it.neural.modelId.ifBlank { base.modelId },
                    embeddingDim = if (it.neural.embeddingDim != 512) it.neural.embeddingDim else base.embeddingDim,
                    modelSource = it.neural.modelSource.ifBlank { base.modelSource },
                    modelGeneratedAt = it.neural.modelGeneratedAt.ifBlank { base.modelGeneratedAt },
                ),
            )
        }
    }

    fun setModelMeta(meta: FingerprintMeta) {
        _state.update {
            it.copy(
                neural = it.neural.copy(
                    modelId = meta.model,
                    embeddingDim = meta.dim,
                    modelSource = meta.source,
                    modelGeneratedAt = meta.generatedAt,
                ),
            )
        }
    }

    fun markComplete(result: ParayImportResult) {
        val learnedNow = app.paray.learnedProductCount()
        val fingerprintsNow = app.paray.fingerprintCount()
        _state.update {
            it.copy(
                status = ParayImportStatus.Complete,
                runningInBackground = false,
                result = result,
                growthDelta = learnedNow - learnedBefore,
                progress = ParayImportProgress(
                    phase = "Import complete",
                    total = result.imported + result.skippedNoArticle + result.skippedInvalid,
                    processed = result.imported + result.skippedNoArticle + result.skippedInvalid,
                    imported = result.imported,
                    skippedNoArticle = result.skippedNoArticle,
                    skippedInvalid = result.skippedInvalid,
                    currentDesignation = null,
                ),
                neural = app.paray.buildNeuralSnapshot(
                    learnedBefore = learnedBefore,
                    fingerprintsBefore = fingerprintsBefore,
                ),
            )
        }
    }

    fun markFailed(message: String) {
        _state.update {
            it.copy(
                status = ParayImportStatus.Failed,
                runningInBackground = false,
                error = message,
                neural = app.paray.buildNeuralSnapshot(
                    learnedBefore = learnedBefore,
                    fingerprintsBefore = fingerprintsBefore,
                ),
            )
        }
    }

    fun refreshNeural() {
        _state.update {
            it.copy(neural = app.paray.buildNeuralSnapshot(
                learnedBefore = learnedBefore,
                fingerprintsBefore = fingerprintsBefore,
            ))
        }
    }
}
