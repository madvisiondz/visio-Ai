package com.oasismall.oasisai.domain.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.oasismall.oasisai.MainActivity
import com.oasismall.oasisai.OasisApp
import com.oasismall.oasisai.R
import com.oasismall.oasisai.util.TaskProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps long Visio Ai tasks alive when the screen is off or the app is in the background. */
class OasisBackgroundTaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kindName = intent?.getStringExtra(EXTRA_KIND) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val kind = runCatching { OasisBackgroundTaskKind.valueOf(kindName) }.getOrNull() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val uri = intent.getParcelableExtra(EXTRA_URI, Uri::class.java)

        startForeground(NOTIFICATION_ID, buildNotification(kind.displayName, 0))
        acquireWakeLock()
        runTask(kind, uri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun runTask(kind: OasisBackgroundTaskKind, uri: Uri?) {
        val app = applicationContext as OasisApp
        val manager = app.backgroundTaskManager
        scope.launch {
            try {
                manager.markRunning(kind)
                when (kind) {
                    OasisBackgroundTaskKind.SYNC_SUB_PNGS -> {
                        val result = app.subBarcodeFlavorService.syncSubPngsFromMetadata { progress ->
                            report(manager, progress)
                        }
                        manager.markSuccess(
                            when {
                                result.subPngs == 0 -> "No sub-barcode PNGs found."
                                result.linked > 0 ->
                                    "Synced ${result.linked} sub-PNG(s) from ${result.subPngs} file(s)." +
                                        (if (result.renamed > 0) " Renamed ${result.renamed}." else "")
                                else -> "No new links (${result.skippedConflict} conflict(s))."
                            },
                        )
                    }
                    OasisBackgroundTaskKind.REINDEX_IMAGES -> {
                        val articles = app.repository.getAllArticles()
                        if (articles.isEmpty()) {
                            manager.markSuccess("Import Gestium CSV first — no articles to match.")
                        } else {
                            app.imageMatcher.syncImagesForArticles(articles) { report(manager, it) }
                            val missing = app.repository.countMissingImages()
                            val modelCount = app.imageMatcher.scanPngFilesIndexed()
                                .count { it.isOasisReadyModel }
                            manager.markSuccess(
                                "Re-indexed ${articles.size} articles ($modelCount Oasis-model PNGs). $missing still missing images.",
                            )
                            app.parayObserver.onTrigger(
                                com.oasismall.oasisai.domain.paray.ParayObserverTrigger.PNG_REINDEX_COMPLETED,
                            )
                        }
                    }
                    OasisBackgroundTaskKind.EXPORT_PNG_DATABASE -> {
                        val result = app.productImagesExporter.export { report(manager, it) }
                        manager.markSuccess(
                            "Exported ${result.copied} PNG(s) (incl. sub-barcode) to ${result.displayPath}/",
                        )
                    }
                    OasisBackgroundTaskKind.EXPORT_FULL_BACKUP -> {
                        val outputUri = uri ?: error("No save location chosen")
                        val result = app.deviceBackupExporter.exportFullBackup(outputUri) { report(manager, it) }
                        val encNote = if (result.encrypted) " (AES-256 encrypted)" else ""
                        manager.markSuccess(
                            "Backup saved as ${result.zipFileName}$encNote — ${result.articleCount} articles, ${result.fileCount} files.",
                        )
                    }
                    OasisBackgroundTaskKind.IMPORT_FULL_BACKUP -> {
                        val inputUri = uri ?: error("No backup file chosen")
                        val result = app.deviceBackupImporter.importFromUri(inputUri) { report(manager, it) }
                        manager.markSuccess(
                            "Restored ${result.articleCount} articles and ${result.filesRestored} files from backup.",
                        )
                    }
                    OasisBackgroundTaskKind.EXPORT_VISIOPRO_BUNDLE -> {
                        val outputUri = uri ?: error("No save location chosen")
                        val result = app.visioProBundleExporter.export(outputUri) { report(manager, it) }
                        manager.markSuccess(
                            "VisioPRO saved as ${result.zipFileName} (${result.categoryCount} sections, ${result.imageCount} images).",
                        )
                    }
                    OasisBackgroundTaskKind.IMPORT_VISIOPRO_BUNDLE -> {
                        val inputUri = uri ?: error("No VisioPRO bundle chosen")
                        val result = app.visioProBundleImporter.importFromUri(inputUri) { report(manager, it) }
                        manager.markSuccess(
                            "VisioPRO restored — ${result.categoryCount} sections, ${result.imageCount} files.",
                        )
                    }
                    OasisBackgroundTaskKind.PURGE_GESTIUM -> {
                        val result = app.gestiumCatalogPurge.purge()
                        val flavorNote = if (result.subBarcodeFlavorsArchived > 0) {
                            " ${result.subBarcodeFlavorsArchived} flavor(s) archived."
                        } else ""
                        manager.markSuccess("Purged ${result.articlesRemoved} articles.$flavorNote PNG files kept.")
                    }
                    OasisBackgroundTaskKind.LOAD_SAMPLE_DATA -> {
                        app.importService.importSample(applicationContext) { report(manager, it) }
                        manager.markSuccess("Sample data loaded.")
                    }
                    OasisBackgroundTaskKind.CSV_IMPORT -> {
                        val pending = manager.takeCsvImport() ?: error("CSV import payload missing")
                        val (fileName, parseResult) = pending
                        val result = app.importService.importFromParseResult(parseResult, fileName) { report(manager, it) }
                        if (result.success) {
                            manager.markSuccess(
                                "Import complete — ${result.summary?.newCount ?: 0} new, " +
                                    "${result.summary?.priceChangedCount ?: 0} price changes.",
                            )
                        } else {
                            error(result.errorMessage ?: "Import failed")
                        }
                    }
                    OasisBackgroundTaskKind.LOAD_READY_PNGS -> {
                        val uris = manager.takeReadyPngUris()
                        val folder = manager.takeReadyPngFolder()
                        val loadResult = when {
                            uris != null -> app.readyPngLoader.loadFromUris(applicationContext, uris) { report(manager, it) }
                            folder != null -> app.readyPngLoader.loadFromFolderTree(applicationContext, folder) { report(manager, it) }
                            else -> error("No PNG source selected")
                        }
                        val articles = app.repository.getAllArticles()
                        val reindexNote = if (articles.isNotEmpty()) {
                            app.imageMatcher.syncImagesForArticles(articles) { report(manager, it) }
                            val missing = app.repository.countMissingImages()
                            " Re-indexed ${articles.size} articles; $missing still missing."
                        } else {
                            " Import CSV then re-index."
                        }
                        manager.markSuccess(
                            "Loaded ${loadResult.copied} PNG(s).$reindexNote",
                        )
                        app.parayObserver.onTrigger(
                            com.oasismall.oasisai.domain.paray.ParayObserverTrigger.PNG_LOAD_COMPLETED,
                        )
                        app.parayWorkflowTracker.recordFeature(
                            com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.PNG_IMPORT,
                        )
                    }
                }
                finishNotification(manager.state.value.successMessage ?: "Complete", 100, ongoing = false)
            } catch (e: Exception) {
                manager.markFailed("${e.javaClass.simpleName}: ${e.message}")
                finishNotification("Failed: ${e.message}", 0, ongoing = false)
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun report(manager: OasisBackgroundTaskManager, progress: TaskProgress) {
        manager.updateProgress(progress)
        updateNotification(progress.label, progress.normalizedPercent)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OasisAI:BackgroundTask").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun finishNotification(text: String, percent: Int, ongoing: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, percent, ongoing))
    }

    private fun updateNotification(text: String, percent: Int) {
        finishNotification(text, percent, ongoing = true)
    }

    private fun buildNotification(text: String, percent: Int, ongoing: Boolean = true): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.background_task_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_task_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.background_task_channel_desc)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "oasis_background_tasks"
        private const val NOTIFICATION_ID = 7102
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_URI = "uri"

        fun start(context: Context, kind: OasisBackgroundTaskKind, uri: Uri? = null) {
            val intent = Intent(context, OasisBackgroundTaskService::class.java).apply {
                putExtra(EXTRA_KIND, kind.name)
                if (uri != null) {
                    putExtra(EXTRA_URI, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
            context.startForegroundService(intent)
        }
    }
}
