package com.oasismall.oasisai.ui.screens.checkshoot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Live camera feedback while walking tickets (debug + user hint). */
data class TicketCameraFeedback(
    val stage: String,
    val ocrDesignation: String? = null,
    val ocrPrice: Double? = null,
    val hint: String,
)

/** Stable walk status for ticket tap-to-capture mode. */
enum class TicketWalkStatus {
    /** Camera live — tap screen to capture ticket. */
    READY,
    /** PARAY cropping + reading ticket. */
    PROCESSING,
    /** Article card locked — swipe unlock for next ticket. */
    PAUSED,
}

@Composable
fun AgentTicketStatusBar(
    walkStatus: TicketWalkStatus,
    showReadingIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusLine = when (walkStatus) {
        TicketWalkStatus.PAUSED -> "Card locked — swipe ← → for next ticket"
        TicketWalkStatus.PROCESSING -> "PARAY Vision Corps — scout + OCR + match…"
        TicketWalkStatus.READY -> "Tap on ticket — works with faded/yellow/magenta price tickets"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showReadingIndicator && walkStatus == TicketWalkStatus.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp),
                    strokeWidth = 2.dp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "PARAY Ticket",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Avoid flashing "Reading…" on fast empty frames. */
@Composable
fun rememberDelayedReadingFlag(walkStatus: TicketWalkStatus, delayMs: Long = 200L): Boolean {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(walkStatus) {
        if (walkStatus != TicketWalkStatus.PROCESSING) {
            show = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(delayMs)
        show = true
    }
    return show
}

@Composable
fun TicketCameraOverlay(
    feedback: TicketCameraFeedback?,
    modifier: Modifier = Modifier,
) {
    val line = feedback?.hint ?: "Point at yellow shelf ticket — tap to capture"
    val detail = when (feedback?.stage) {
        "ocr" -> feedback.ocrDesignation?.let { des ->
            buildString {
                append(des)
                feedback.ocrPrice?.let { append(" · ${it.toInt()} DA") }
            }
        }
        "no_match" -> "Searching catalog…"
        "no_yellow" -> "Move closer — fill frame with ticket"
        else -> null
    }
    Surface(
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                line,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            detail?.let {
                Text(
                    it,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (com.oasismall.oasisai.BuildConfig.DEBUG) {
                Text(
                    "Logcat: Oasis/Paray",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
