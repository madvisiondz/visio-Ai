package com.oasismall.oasisai.domain.visio

import com.oasismall.oasisai.data.db.dao.BatchCameraQueueDao
import com.oasismall.oasisai.data.db.entity.BatchCameraQueueEntity
import kotlinx.coroutines.flow.Flow

class BatchCameraQueueStore(
    private val dao: BatchCameraQueueDao,
) {
    fun observePending(): Flow<List<BatchCameraQueueEntity>> = dao.observePending()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun replaceQueue(designations: List<String>) {
        dao.clearAll()
        if (designations.isEmpty()) return
        dao.insertAll(
            designations.mapIndexed { index, designation ->
                BatchCameraQueueEntity(
                    designation = designation.trim(),
                    sortOrder = index,
                )
            },
        )
    }

    suspend fun getPending(): List<BatchCameraQueueEntity> = dao.getPending()

    suspend fun getById(id: Long): BatchCameraQueueEntity? = dao.getById(id)

    suspend fun markDone(id: Long) = dao.markDone(id)

    suspend fun nextPending(): BatchCameraQueueEntity? = dao.getPending().firstOrNull()
}
