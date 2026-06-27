package com.oasismall.oasisai.ui.screens.checkshoot

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.domain.paray.ParayTicketMatchTier
import com.oasismall.oasisai.domain.paray.TicketSnapPhase

data class TicketSnapStepLine(
    val phase: TicketSnapPhase,
    val message: String,
    val done: Boolean,
    val failed: Boolean = false,
)

data class TicketSnapUiState(
    val phase: TicketSnapPhase,
    val message: String,
    val steps: List<TicketSnapStepLine> = emptyList(),
    val previewBitmap: android.graphics.Bitmap? = null,
    val ocrDesignation: String? = null,
    val ocrPrice: Double? = null,
    val fusionPercent: Int? = null,
    val matchTier: ParayTicketMatchTier? = null,
    val frameQuality: Float? = null,
    val error: String? = null,
) {
    val isTerminal: Boolean get() = phase == TicketSnapPhase.DONE || phase == TicketSnapPhase.FAILED
}

private val ParayYellow = Color(0xFFFFE500)
private val ParayGreen = Color(0xFF7CFC90)

/** Tap anywhere on the camera to capture the ticket — instant photo + PARAY scan. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TicketTapToSnapOverlay(
    enabled: Boolean,
    onSnap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_UP -> {
                                onSnap()
                                true
                            }
                            MotionEvent.ACTION_DOWN -> true
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                "PARAY Vision",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                color = ParayYellow,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Surface(
                        shape = CircleShape,
                        color = ParayYellow,
                        modifier = Modifier.size(52.dp),
                    ) {}
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (enabled) "Tap to capture ticket" else "Processing…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun TicketSnapProgressPanel(
    state: TicketSnapUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isTerminal) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = ParayYellow,
                    )
                }
                Text(
                    "PARAY Vision",
                    color = ParayYellow,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!state.isTerminal) {
                    Text(
                        "scanning ticket",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            state.matchTier?.let { tier ->
                Surface(
                    color = when (tier) {
                        ParayTicketMatchTier.CONFIRMED -> Color(0xFF1B5E20).copy(alpha = 0.85f)
                        ParayTicketMatchTier.HIGH -> Color(0xFF33691E).copy(alpha = 0.85f)
                        ParayTicketMatchTier.PROBABLE -> Color(0xFF455A64).copy(alpha = 0.85f)
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Star, null, tint = ParayYellow, modifier = Modifier.size(16.dp))
                        Text(tier.marketingLabel, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text(
                state.message,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.previewBitmap?.let { bmp ->
                val image = remember(bmp) { bmp.asImageBitmap() }
                val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = "Captured ticket",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            if (state.steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.steps.forEach { line ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when {
                                    line.failed -> Icons.Default.Close
                                    line.done -> Icons.Default.Check
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = when {
                                    line.failed -> Color(0xFFFF8A80)
                                    line.done -> ParayGreen
                                    line.phase == state.phase -> ParayYellow
                                    else -> Color.White.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                line.message,
                                color = if (line.done || line.phase == state.phase) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            state.ocrDesignation?.let { des ->
                Text(
                    "Designation: $des",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.ocrPrice?.let { price ->
                Text(
                    "Price: ${price.toInt()} DA",
                    color = Color(0xFFFF6B9D),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            state.fusionPercent?.let { pct ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Match: text + price + PNG → $pct%",
                        color = ParayGreen,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = ParayGreen,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
            state.error?.let { err ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                    Text(err, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
