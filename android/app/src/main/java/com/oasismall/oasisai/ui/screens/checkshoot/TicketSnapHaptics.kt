package com.oasismall.oasisai.ui.screens.checkshoot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.oasismall.oasisai.domain.paray.ParayTicketMatchTier
import com.oasismall.oasisai.util.OasisLog

/** Haptic feedback for PARAY ticket snap — never crash if vibrate unavailable. */
object TicketSnapHaptics {
    fun tapShutter(context: Context) = oneShot(context, 32, 80)
    fun holdComplete(context: Context) = tapShutter(context)
    fun processing(context: Context) = oneShot(context, 12, 60)
    fun match(context: Context, tier: ParayTicketMatchTier) = when (tier) {
        ParayTicketMatchTier.CONFIRMED -> oneShot(context, 45, 160)
        ParayTicketMatchTier.HIGH -> oneShot(context, 35, 120)
        ParayTicketMatchTier.PROBABLE -> oneShot(context, 22, 90)
    }
    fun failed(context: Context) = safeVibrate(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 60, 40, 60),
                    intArrayOf(0, 20, 0, 20),
                    -1,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    private fun oneShot(context: Context, amplitude: Int, durationMs: Long) = safeVibrate(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private inline fun safeVibrate(context: Context, block: (Vibrator) -> Unit) {
        if (!hasVibratePermission(context)) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            block(vibrator)
        } catch (e: SecurityException) {
            OasisLog.w(OasisLog.Domain.Paray, "Vibrate denied — ${e.message}")
        }
    }

    private fun hasVibratePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.VIBRATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService()
        }
}
