package com.oasismall.oasisai.ui.components.paray

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.domain.paray.ParayActivityState

private val LedViolet = Color(0xFF9D6FD4)
private val LedTeal = Color(0xFF1A7A7A)
private val LedWarn = Color(0xFFE8A838)

/**
 * Lightweight neural-activity LED — Compose animations only, no timers in ViewModel.
 */
@Composable
fun ParayActivityLed(
    state: ParayActivityState,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val transition = rememberInfiniteTransition(label = "paray_led")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDuration(state), easing = FastOutSlowInEasing),
            repeatMode = when (state) {
                ParayActivityState.PROCESSING -> RepeatMode.Reverse
                ParayActivityState.DISCOVERY -> RepeatMode.Restart
                else -> RepeatMode.Reverse
            },
        ),
        label = "paray_led_phase",
    )

    val (alpha, scale, color) = ledVisuals(state, phase)

    Box(
        modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

private fun animationDuration(state: ParayActivityState): Int = when (state) {
    ParayActivityState.IDLE -> 2_800
    ParayActivityState.OBSERVING -> 1_800
    ParayActivityState.PROCESSING -> 280
    ParayActivityState.DISCOVERY -> 520
    ParayActivityState.LEARNING -> 1_400
    ParayActivityState.WARNING -> 420
}

private fun ledVisuals(state: ParayActivityState, phase: Float): Triple<Float, Float, Color> = when (state) {
    ParayActivityState.IDLE -> Triple(
        0.22f + phase * 0.18f,
        0.92f + phase * 0.08f,
        LedViolet.copy(alpha = 0.55f),
    )
    ParayActivityState.OBSERVING -> Triple(
        0.45f + phase * 0.35f,
        0.95f + phase * 0.1f,
        LedTeal,
    )
    ParayActivityState.PROCESSING -> Triple(
        0.35f + phase * 0.65f,
        0.85f + phase * 0.2f,
        LedViolet,
    )
    ParayActivityState.DISCOVERY -> {
        val double = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
        Triple(0.5f + double * 0.5f, 1f + double * 0.15f, LedTeal)
    }
    ParayActivityState.LEARNING -> Triple(
        0.55f + phase * 0.4f,
        1f,
        LedViolet,
    )
    ParayActivityState.WARNING -> Triple(
        0.4f + phase * 0.6f,
        1f + phase * 0.2f,
        LedWarn,
    )
}
