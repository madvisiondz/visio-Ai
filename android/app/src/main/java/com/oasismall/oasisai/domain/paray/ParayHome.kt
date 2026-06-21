package com.oasismall.oasisai.domain.paray

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * PARAY's own living space on the device — separate from Oasis UI, linked via [officeLink].
 *
 * ```
 * files/paray_home/
 * ├── manifest.json          # who PARAY is
 * ├── office_link.json       # Oasis AI workplace connection
 * ├── memory/                # visual + neural + barcode memory
 * ├── sessions/              # active work sessions (scan lock, etc.)
 * └── logs/                  # learn events
 * ```
 */
class ParayHome(private val context: Context) {
    val root: File = File(context.filesDir, ROOT_DIR).also { it.mkdirs() }
    val memoryDir: File = File(root, "memory").also { it.mkdirs() }
    val sessionsDir: File = File(root, "sessions").also { it.mkdirs() }
    val logsDir: File = File(root, "logs").also { it.mkdirs() }

    val manifestFile: File = File(root, "manifest.json")
    val officeLinkFile: File = File(root, "office_link.json")

    val visualIndexFile: File = File(memoryDir, "visual_index.json")
    val fingerprintIndexFile: File = File(memoryDir, "fingerprint_index.json")
    val barcodePatternsFile: File = File(memoryDir, "barcode_patterns.json")
    val learnIndexFile: File = File(memoryDir, "learn_index.json")
    val learnSettingsFile: File = File(memoryDir, "learn_settings.json")
    val learnViewsDir: File = File(memoryDir, "learn_views").also { it.mkdirs() }
    val checkShootSessionFile: File = File(sessionsDir, "check_shoot_session.json")
    val learnEventsFile: File = File(logsDir, "learn_events.jsonl")

    init {
        ensureHome()
    }

    fun ensureHome() {
        migrateLegacyParayFolder()
        if (!manifestFile.exists()) {
            writeManifest(ParayManifest())
        }
        touchOfficeLink()
    }

    fun readManifest(): ParayManifest {
        ensureHome()
        val o = runCatching { JSONObject(manifestFile.readText()) }.getOrElse { JSONObject() }
        return ParayManifest(
            agentName = o.optString("agentName", ParayKnowledge.AGENT_NAME),
            version = o.optString("version", ParayKnowledge.VERSION),
            homePath = root.absolutePath,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            motto = o.optString("motto", ParayKnowledge.motto),
        )
    }

    fun touchOfficeLink(lastWorkplace: String? = null) {
        val existing = readOfficeLink()
        val updated = existing.copy(
            oasisApp = OASIS_OFFICE_ID,
            lastVisitAt = System.currentTimeMillis(),
            lastWorkplace = lastWorkplace ?: existing.lastWorkplace,
        )
        officeLinkFile.writeText(
            JSONObject()
                .put("oasisApp", updated.oasisApp)
                .put("lastVisitAt", updated.lastVisitAt)
                .put("lastWorkplace", updated.lastWorkplace)
                .put("status", updated.status)
                .toString(),
        )
    }

    fun readOfficeLink(): ParayOfficeLink {
        if (!officeLinkFile.exists()) {
            return ParayOfficeLink()
        }
        val o = runCatching { JSONObject(officeLinkFile.readText()) }.getOrElse { JSONObject() }
        return ParayOfficeLink(
            oasisApp = o.optString("oasisApp", OASIS_OFFICE_ID),
            lastVisitAt = o.optLong("lastVisitAt", 0L),
            lastWorkplace = o.optString("lastWorkplace", ""),
            status = o.optString("status", "linked"),
        )
    }

    fun folderSummary(): List<ParayFolderEntry> = listOf(
        ParayFolderEntry("memory", memoryDir, fileCount(memoryDir)),
        ParayFolderEntry("sessions", sessionsDir, fileCount(sessionsDir)),
        ParayFolderEntry("logs", logsDir, fileCount(logsDir)),
    )

    private fun writeManifest(m: ParayManifest) {
        manifestFile.writeText(
            JSONObject()
                .put("agentName", m.agentName)
                .put("version", m.version)
                .put("homePath", m.homePath)
                .put("createdAt", m.createdAt)
                .put("motto", m.motto)
                .toString(),
        )
    }

    private fun migrateLegacyParayFolder() {
        val legacy = File(context.filesDir, LEGACY_DIR)
        if (!legacy.isDirectory) return

        val moves = listOf(
            legacy.resolve("visual_index.json") to visualIndexFile,
            legacy.resolve("fingerprint_index.json") to fingerprintIndexFile,
            legacy.resolve("barcode_patterns.json") to barcodePatternsFile,
            legacy.resolve("check_shoot_session.json") to checkShootSessionFile,
            legacy.resolve("learn_events.jsonl") to learnEventsFile,
        )
        for ((from, to) in moves) {
            if (from.exists() && !to.exists()) {
                from.copyTo(to, overwrite = false)
                from.delete()
            }
        }
    }

    private fun fileCount(dir: File): Int =
        dir.listFiles()?.count { it.isFile } ?: 0

    companion object {
        const val ROOT_DIR = "paray_home"
        const val LEGACY_DIR = "paray"
        const val OASIS_OFFICE_ID = "com.oasismall.oasisai"
    }
}

data class ParayManifest(
    val agentName: String = ParayKnowledge.AGENT_NAME,
    val version: String = ParayKnowledge.VERSION,
    val homePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val motto: String = ParayKnowledge.motto,
)

data class ParayOfficeLink(
    val oasisApp: String = ParayHome.OASIS_OFFICE_ID,
    val lastVisitAt: Long = 0L,
    val lastWorkplace: String = "",
    val status: String = "linked",
)

data class ParayFolderEntry(
    val name: String,
    val path: File,
    val fileCount: Int,
)
