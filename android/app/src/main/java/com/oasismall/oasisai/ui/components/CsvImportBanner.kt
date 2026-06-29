package com.oasismall.oasisai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskKind
import com.oasismall.oasisai.domain.background.OasisBackgroundTaskState
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val AUTO_DISMISS_MS = 12_000L
private const val SWIPE_DISMISS_PX = 120f

@Composable
fun CsvImportBanner(
    taskState: OasisBackgroundTaskState,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (taskState.kind != OasisBackgroundTaskKind.CSV_IMPORT) return

    val visible = taskState.running ||
        !taskState.successMessage.isNullOrBlank() ||
        !taskState.errorMessage.isNullOrBlank()

    var dragX by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(taskState.running, taskState.successMessage, taskState.errorMessage) {
        dismissed = false
        dragX = 0f
    }

    LaunchedEffect(taskState.successMessage, taskState.errorMessage) {
        if (!taskState.running &&
            (!taskState.successMessage.isNullOrBlank() || !taskState.errorMessage.isNullOrBlank())
        ) {
            delay(AUTO_DISMISS_MS)
            dismissed = true
            onDismiss()
        }
    }

    val text = when {
        taskState.running -> {
            val pct = taskState.progress?.percent ?: 0
            val label = taskState.progress?.label ?: "Importing CSV"
            "$label · $pct%"
        }
        !taskState.errorMessage.isNullOrBlank() -> taskState.errorMessage!!
        !taskState.successMessage.isNullOrBlank() -> taskState.successMessage!!
        else -> return
    }

    val bg = when {
        !taskState.errorMessage.isNullOrBlank() -> MaterialTheme.colorScheme.errorContainer
        taskState.running -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val fg = when {
        !taskState.errorMessage.isNullOrBlank() -> MaterialTheme.colorScheme.onErrorContainer
        taskState.running -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    AnimatedVisibility(
        visible = visible && !dismissed,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier.zIndex(100f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .pointerInput(taskState) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragX) > SWIPE_DISMISS_PX) {
                                dismissed = true
                                onDismiss()
                            }
                            dragX = 0f
                        },
                        onHorizontalDrag = { _, delta -> dragX += delta },
                    )
                }
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
            if (taskState.running) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    progress = { (taskState.progress?.percent ?: 0) / 100f },
                )
            }
        }
    }
}
