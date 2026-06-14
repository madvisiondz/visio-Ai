package com.oasismall.oasisai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Futuristic animated lens icon for the AGENT bottom-nav tab. */
@Composable
fun AgentNavIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "agent_icon")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4200, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = if (selected) 1.1f else 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val ringColors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF7C4DFF),
        Color(0xFFFF4081),
        Color(0xFF00E676),
        Color(0xFF00E5FF),
    )

    Canvas(modifier = modifier.size(46.8.dp)) {
        val c = center
        val base = size.minDimension * 0.44f * pulse

        rotate(spin, c) {
            drawArc(
                brush = Brush.sweepGradient(ringColors, c),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(c.x - base, c.y - base),
                size = Size(base * 2f, base * 2f),
                style = Stroke(width = 2.8f),
                alpha = glow * if (selected) 1f else 0.75f,
            )
        }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF311B92), Color(0xFF00BCD4)),
                start = Offset(c.x - base, c.y),
                end = Offset(c.x + base, c.y),
            ),
            topLeft = Offset(c.x - base * 0.78f, c.y - base * 0.52f),
            size = Size(base * 1.56f, base * 1.04f),
            cornerRadius = CornerRadius(base * 0.14f),
            alpha = if (selected) 1f else 0.82f,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB2EBF2), Color(0xFF536DFE), Color(0xFF0D0221)),
                center = c,
                radius = base * 0.4f,
            ),
            radius = base * 0.4f,
            center = c,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f * glow),
            radius = base * 0.11f,
            center = Offset(c.x - base * 0.12f, c.y - base * 0.12f),
        )
    }
}
