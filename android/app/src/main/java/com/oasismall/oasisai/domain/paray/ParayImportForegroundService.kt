package com.oasismall.oasisai.domain.paray

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps PARAY fingerprint import alive when the screen is off or the app is in the background.
 */
class ParayImportForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uri = intent.data ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Starting PARAY import…", 0))
                acquireWakeLock()
                runImport(uri)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun runImport(uri: Uri) {
        val app = applicationContext as OasisApp
        val manager = app.parayImportManager
        scope.launch {
            try {
                manager.markRunning(totalHint = 0)
                val text = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: error("Could not read fingerprint file")

                val root = org.json.JSONObject(text)
                val total = root.optJSONArray("entries")?.length() ?: 0
                manager.setModelMeta(
                    FingerprintMeta(
                        version = root.optInt("version", 1),
                        agent = root.optString("agent", "PARAY"),
                        model = root.optString("model", ""),
                        dim = root.optInt("dim", 512),
                        generatedAt = root.optString("generatedAt", ""),
                        source = root.optString("source", "pc"),
                    ),
                )
                manager.markRunning(totalHint = total)

                val importer = app.paray.fingerprintImporter(app.repository)
                val result = importer.importJson(text) { progress ->
                    manager.updateProgress(progress)
                    updateNotification(progress.phase, progress.percent)
                }

                manager.markComplete(result)
                updateNotification(
                    "PARAY: ${result.imported} articles loaded",
                    100,
                    ongoing = false,
                )
            } catch (e: Exception) {
                manager.markFailed(e.message ?: "Import failed")
                updateNotification("PARAY import failed", 0, ongoing = false)
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OasisAI:ParayImport",
        ).apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun updateNotification(text: String, percent: Int, ongoing: Boolean = true) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, percent, ongoing))
    }

    private fun buildNotification(text: String, percent: Int, ongoing: Boolean = true): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.paray_import_notification_title))
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.paray_import_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.paray_import_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "paray_import"
        private const val NOTIFICATION_ID = 7101
        private const val ACTION_START = "com.oasismall.oasisai.PARAY_IMPORT_START"

        fun start(context: Context, uri: Uri) {
            val intent = Intent(context, ParayImportForegroundService::class.java).apply {
                action = ACTION_START
                setDataAndType(uri, "application/json")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startForegroundService(intent)
        }
    }
}
