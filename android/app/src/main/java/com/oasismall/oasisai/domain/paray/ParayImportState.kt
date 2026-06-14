package com.oasismall.oasisai.domain.paray

enum class ParayImportStatus {
    Idle,
    Running,
    Complete,
    Failed,
}

data class ParayImportProgress(
    val phase: String,
    val total: Int = 0,
    val processed: Int = 0,
    val imported: Int = 0,
    val skippedNoArticle: Int = 0,
    val skippedInvalid: Int = 0,
    val currentDesignation: String? = null,
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((processed * 100) / total).coerceIn(0, 100)
}

data class ParayNeuralSnapshot(
    val agentName: String = ParayKnowledge.AGENT_NAME,
    val agentVersion: String = ParayKnowledge.VERSION,
    val modelId: String = "",
    val embeddingDim: Int = 512,
    val modelSource: String = "",
    val modelGeneratedAt: String = "",
    val learnedBefore: Int = 0,
    val learnedNow: Int = 0,
    val fingerprintsBefore: Int = 0,
    val fingerprintsNow: Int = 0,
    val learnEvents: Int = 0,
    val gpuAvailable: Boolean = false,
    val glesVersion: String? = null,
    val lowRamDevice: Boolean = false,
    val matcherMode: String = "shape + color",
    val embeddingsReady: Boolean = false,
    val cameraReady: Boolean = false,
)

data class ParayImportUiState(
    val status: ParayImportStatus = ParayImportStatus.Idle,
    val progress: ParayImportProgress = ParayImportProgress("Waiting to start"),
    val result: ParayImportResult? = null,
    val error: String? = null,
    val neural: ParayNeuralSnapshot = ParayNeuralSnapshot(),
    val growthDelta: Int = 0,
    val runningInBackground: Boolean = false,
)
