package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.util.writeTextAtomic

import com.oasismall.oasisai.BuildConfig
import com.oasismall.oasisai.domain.transfer.ZipArchive
import com.oasismall.oasisai.util.TaskProgress
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParayKnowledgePackageValidator {

    fun validate(packageRoot: File): ParayKnowledgePackageManifest {
        val manifestFile = File(packageRoot, ParayKnowledgePackage.MANIFEST_FILE)
        if (!manifestFile.isFile) error("Invalid package — missing package_manifest.json")

        val manifestJson = runCatching { JSONObject(manifestFile.readText()) }
            .getOrElse { error("Invalid package — manifest not readable") }

        ParayKnowledgePackage.REQUIRED_MANIFEST_FIELDS.forEach { field ->
            if (!manifestJson.has(field)) error("Invalid package — manifest missing $field")
        }

        val version = manifestJson.getInt("packageVersion")
        if (version != ParayKnowledgePackage.PACKAGE_VERSION) {
            error("Unsupported package version $version")
        }
        if (manifestJson.optString("exportType") != ParayKnowledgePackage.EXPORT_TYPE) {
            error("Invalid package — not a knowledge fusion export")
        }

        ParayKnowledgePackage.EXPORT_FILES.forEach { (relative, _) ->
            val file = ParayKnowledgePackage.fileInPackage(packageRoot, relative)
            if (!file.isFile) error("Invalid package — missing $relative")
            runCatching { JSONObject(file.readText()) }
                .getOrElse { error("Invalid package — $relative is not readable JSON") }
        }

        return ParayKnowledgePackageManifest(
            packageVersion = version,
            parayVersion = manifestJson.optString("parayVersion"),
            createdAt = manifestJson.optLong("createdAt"),
            knowledgeCount = manifestJson.optInt("knowledgeCount"),
            deviceKnowledgeId = manifestJson.optString("deviceKnowledgeId"),
            exportType = manifestJson.optString("exportType"),
        )
    }
}

class ParayKnowledgePackageExporter(
    private val home: ParayHome,
    private val fusionStore: ParayFusionStore,
    private val cacheDir: File,
) {
    fun export(onProgress: ((TaskProgress) -> Unit)? = null): ParayFusionExportResult {
        val stamp = SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
        val fileName = "PARAY_${stamp}${ParayKnowledgePackage.EXTENSION}"
        val workDir = File(cacheDir, "pkp-export-$stamp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        onProgress?.invoke(TaskProgress("Collecting knowledge", 10))
        var knowledgeCount = 0
        ParayKnowledgePackage.EXPORT_FILES.forEach { (relative, resolver) ->
            val source = resolver(home)
            val dest = ParayKnowledgePackage.fileInPackage(workDir, relative)
            dest.parentFile?.mkdirs()
            if (source.isFile) {
                source.copyTo(dest, overwrite = true)
                knowledgeCount += countJsonKeys(dest)
            } else {
                dest.writeTextAtomic("{}")
            }
        }

        val deviceId = fusionStore.ensureDeviceKnowledgeId()
        val manifest = ParayKnowledgePackageManifest(
            packageVersion = ParayKnowledgePackage.PACKAGE_VERSION,
            parayVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
            knowledgeCount = knowledgeCount,
            deviceKnowledgeId = deviceId,
            exportType = ParayKnowledgePackage.EXPORT_TYPE,
        )
        File(workDir, ParayKnowledgePackage.MANIFEST_FILE).writeTextAtomic(manifestToJson(manifest).toString(2))

        onProgress?.invoke(TaskProgress("Building PKP package", 60))
        val zipFile = File(cacheDir, fileName)
        if (zipFile.exists()) zipFile.delete()
        ZipArchive.zipDirectory(workDir, zipFile) { progress ->
            val pct = 60 + (progress.percent * 35 / 100)
            onProgress?.invoke(TaskProgress(progress.label, pct))
        }
        workDir.deleteRecursively()

        fusionStore.writeState(
            fusionStore.readState().copy(
                deviceKnowledgeId = deviceId,
                lastExportAt = System.currentTimeMillis(),
            ),
        )
        onProgress?.invoke(TaskProgress("Export complete", 100))
        return ParayFusionExportResult(fileName = fileName, knowledgeCount = knowledgeCount)
    }

    private fun countJsonKeys(file: File): Int {
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { return 0 }
        return root.length()
    }

    fun manifestToJson(manifest: ParayKnowledgePackageManifest): JSONObject =
        JSONObject()
            .put("packageVersion", manifest.packageVersion)
            .put("parayVersion", manifest.parayVersion)
            .put("createdAt", manifest.createdAt)
            .put("knowledgeCount", manifest.knowledgeCount)
            .put("deviceKnowledgeId", manifest.deviceKnowledgeId)
            .put("exportType", manifest.exportType)
}
