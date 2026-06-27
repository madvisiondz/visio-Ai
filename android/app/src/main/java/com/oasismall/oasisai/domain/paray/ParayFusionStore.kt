package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persists fusion lineage under `paray_home/fusion/`. */
class ParayFusionStore(private val home: ParayHome) {
    private val historyFile = home.fusionHistoryFile
    private val stateFile = home.fusionStateFile
    private val conflictsFile = home.fusionConflictsFile

    fun readState(): ParayFusionState {
        if (!stateFile.exists()) return ParayFusionState(deviceKnowledgeId = generateDeviceKnowledgeId())
        val o = runCatching { JSONObject(stateFile.readText()) }.getOrElse {
            return ParayFusionState(deviceKnowledgeId = generateDeviceKnowledgeId())
        }
        val id = o.optString("deviceKnowledgeId").ifBlank { generateDeviceKnowledgeId() }
        return ParayFusionState(
            deviceKnowledgeId = id,
            lastExportAt = o.optLong("lastExportAt", 0L),
            lastImportAt = o.optLong("lastImportAt", 0L),
            totalImports = o.optInt("totalImports", 0),
        )
    }

    fun writeState(state: ParayFusionState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeTextAtomic(
            JSONObject()
                .put("deviceKnowledgeId", state.deviceKnowledgeId)
                .put("lastExportAt", state.lastExportAt)
                .put("lastImportAt", state.lastImportAt)
                .put("totalImports", state.totalImports)
                .toString(2),
        )
    }

    fun ensureDeviceKnowledgeId(): String {
        val current = readState()
        if (current.deviceKnowledgeId.isNotBlank()) return current.deviceKnowledgeId
        val id = generateDeviceKnowledgeId()
        writeState(current.copy(deviceKnowledgeId = id))
        return id
    }

    fun readHistory(): List<ParayFusionHistoryEntry> {
        if (!historyFile.exists()) return emptyList()
        val arr = runCatching { JSONArray(historyFile.readText()) }.getOrElse { return emptyList() }
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o ->
                    add(
                        ParayFusionHistoryEntry(
                            fusedAt = o.optLong("fusedAt"),
                            packageVersion = o.optInt("packageVersion", 1),
                            parayVersion = o.optString("parayVersion"),
                            sourceDeviceKnowledgeId = o.optString("sourceDeviceKnowledgeId"),
                            newArticles = o.optInt("newArticles", 0),
                            newBrands = o.optInt("newBrands", 0),
                            newLearnRecords = o.optInt("newLearnRecords", 0),
                            newRecognitionPatterns = o.optInt("newRecognitionPatterns", 0),
                            newWorkflowPatterns = o.optInt("newWorkflowPatterns", 0),
                            conflictsResolved = o.optInt("conflictsResolved", 0),
                        ),
                    )
                }
            }
        }
    }

    fun appendHistory(entry: ParayFusionHistoryEntry) {
        val list = readHistory().toMutableList()
        list.add(0, entry)
        val arr = JSONArray()
        list.take(MAX_HISTORY).forEach { e ->
            arr.put(
                JSONObject()
                    .put("fusedAt", e.fusedAt)
                    .put("packageVersion", e.packageVersion)
                    .put("parayVersion", e.parayVersion)
                    .put("sourceDeviceKnowledgeId", e.sourceDeviceKnowledgeId)
                    .put("newArticles", e.newArticles)
                    .put("newBrands", e.newBrands)
                    .put("newLearnRecords", e.newLearnRecords)
                    .put("newRecognitionPatterns", e.newRecognitionPatterns)
                    .put("newWorkflowPatterns", e.newWorkflowPatterns)
                    .put("conflictsResolved", e.conflictsResolved),
            )
        }
        historyFile.parentFile?.mkdirs()
        historyFile.writeTextAtomic(arr.toString(2))
    }

    fun appendConflicts(conflicts: List<ParayFusionConflict>) {
        if (conflicts.isEmpty()) return
        val existing = readConflicts().toMutableList()
        existing.addAll(0, conflicts)
        val arr = JSONArray()
        existing.take(MAX_CONFLICTS).forEach { c ->
            arr.put(
                JSONObject()
                    .put("key", c.key)
                    .put("domain", c.domain)
                    .put("resolution", c.resolution)
                    .put("resolvedAt", c.resolvedAt),
            )
        }
        conflictsFile.parentFile?.mkdirs()
        conflictsFile.writeTextAtomic(arr.toString(2))
    }

    fun readConflicts(): List<ParayFusionConflict> {
        if (!conflictsFile.exists()) return emptyList()
        val arr = runCatching { JSONArray(conflictsFile.readText()) }.getOrElse { return emptyList() }
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o ->
                    add(
                        ParayFusionConflict(
                            key = o.optString("key"),
                            domain = o.optString("domain"),
                            resolution = o.optString("resolution"),
                            resolvedAt = o.optLong("resolvedAt"),
                        ),
                    )
                }
            }
        }
    }

    private fun generateDeviceKnowledgeId(): String =
        UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        private const val MAX_HISTORY = 32
        private const val MAX_CONFLICTS = 128
    }
}
