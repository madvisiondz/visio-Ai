package com.oasismall.oasisai.ui.screens.batchtxt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.visio.BatchCameraQueueStore
import com.oasismall.oasisai.ui.components.CartSourceTags
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatchTxtResult(
    val totalLines: Int = 0,
    val matchedWithPng: Int = 0,
    val matchedMissingPng: Int = 0,
    val notInCsv: Int = 0,
    val notInCsvDesignations: List<String> = emptyList(),
)

class BatchTxtViewModel(
    private val repository: OasisRepository,
    private val queueStore: BatchCameraQueueStore,
) : ViewModel() {
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _result = MutableStateFlow(BatchTxtResult())
    val result: StateFlow<BatchTxtResult> = _result.asStateFlow()

    val cameraQueue = queueStore.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setInput(value: String) {
        _input.value = value
    }

    fun processBatch() {
        val lines = _input.value
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            _message.value = "Paste at least one designation."
            return
        }
        viewModelScope.launch {
            var withPng = 0
            var noPng = 0
            val notInCsv = mutableListOf<String>()
            lines.forEach { designation ->
                val article = repository.getArticleWithImageByDesignation(designation)
                if (article == null) {
                    notInCsv += designation
                    return@forEach
                }
                val hasPng = !article.imagePath.isNullOrBlank() && File(article.imagePath).exists()
                if (hasPng) {
                    if (!repository.isInCart(article.id, CartType.SHARE)) {
                        repository.addToCart(article.id, CartType.SHARE, CartSourceTags.BATCH_TXT)
                    }
                    withPng += 1
                } else {
                    if (!repository.isInCart(article.id, CartType.PHOTOSHOOT)) {
                        repository.addToCart(article.id, CartType.PHOTOSHOOT, CartSourceTags.BATCH_TXT)
                    }
                    noPng += 1
                }
            }
            queueStore.replaceQueue(notInCsv)
            _result.value = BatchTxtResult(
                totalLines = lines.size,
                matchedWithPng = withPng,
                matchedMissingPng = noPng,
                notInCsv = notInCsv.size,
                notInCsvDesignations = notInCsv,
            )
            _message.value =
                "Batch done: $withPng → To share, $noPng → To shoot, ${notInCsv.size} → Camera batch queue."
        }
    }
}
