package com.oasismall.oasisai.ui.screens.paraylearn

import com.oasismall.oasisai.domain.paray.ParayLearnRecord
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import com.oasismall.oasisai.domain.paray.ParayLearnStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only view of PARAY visual memory from `learn_index.json` — no Room queries. */
enum class ParayMemoryFilter {
    ALL,
    LEARNED,
    PARTIAL,
    PENDING,
}

data class ParayMemoryEntry(
    val articleId: Long,
    val barcode: String,
    val designation: String,
    val brand: String?,
    val category: String?,
    val family: String?,
    val pngFrontPath: String,
    val status: ParayLearnStatus,
    val frontConfirmed: Boolean,
    val leftLearned: Boolean,
    val rightLearned: Boolean,
    val backLearned: Boolean,
    val lastLearningDate: Long?,
)

class ParayMemoryRepository(
    private val learnStore: ParayLearnStore,
) {
    suspend fun loadAll(): List<ParayMemoryEntry> = withContext(Dispatchers.IO) {
        learnStore.allRecords()
            .map { it.toMemoryEntry() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.designation })
    }
}

private fun ParayLearnRecord.toMemoryEntry(): ParayMemoryEntry = ParayMemoryEntry(
    articleId = articleId,
    barcode = barcode,
    designation = designation,
    brand = brand,
    category = category,
    family = family,
    pngFrontPath = pngFrontPath,
    status = status,
    frontConfirmed = frontConfirmed,
    leftLearned = leftCapture != null,
    rightLearned = rightCapture != null,
    backLearned = backCapture != null,
    lastLearningDate = learnedAt ?: updatedAt.takeIf { it > 0L },
)
