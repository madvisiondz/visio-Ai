package com.oasismall.oasisai.domain.visiopro

data class VisioProCategoryConfig(
    val enabledIds: List<Long> = emptyList(),
    val pendingIds: List<Long> = emptyList(),
) {
    fun appendPending(newIds: List<Long>): VisioProCategoryConfig {
        if (newIds.isEmpty()) return this
        val enabledSet = enabledIds.toSet()
        val pendingSet = pendingIds.toMutableSet()
        newIds.forEach { id ->
            if (id > 0L && id !in enabledSet) pendingSet.add(id)
        }
        return copy(pendingIds = pendingSet.toList())
    }

    fun removePending(id: Long): VisioProCategoryConfig =
        copy(pendingIds = pendingIds.filter { it != id })

    fun enable(id: Long): VisioProCategoryConfig {
        if (id <= 0L) return this
        return copy(
            enabledIds = if (id in enabledIds) enabledIds else enabledIds + id,
            pendingIds = pendingIds.filter { it != id },
        )
    }

    fun disable(id: Long): VisioProCategoryConfig =
        copy(
            enabledIds = enabledIds.filter { it != id },
            pendingIds = pendingIds.filter { it != id },
        )
}
