package com.oasismall.oasisai.ui.screens.checkshoot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.oasismall.oasisai.domain.paray.ParayTicketMatch
import com.oasismall.oasisai.domain.paray.ParayTicketMatchTier
import java.io.File

data class TicketSuggestState(
    val ocrDesignation: String? = null,
    val ocrPrice: Double? = null,
    val candidates: List<ParayTicketMatch> = emptyList(),
    val debugSteps: List<TicketSnapStepLine> = emptyList(),
    val previewBitmap: android.graphics.Bitmap? = null,
)

@Composable
fun TicketSuggestionsSheet(
    state: TicketSuggestState,
    onSelect: (ParayTicketMatch) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PARAY — pick article",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        state.ocrDesignation?.let { des ->
            Text("Read: $des", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        }
        state.ocrPrice?.let { price ->
            Text(
                "Price: ${price.toInt()} DA",
                color = Color(0xFFFF6B9D),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        if (state.candidates.isEmpty()) {
            Text("No catalog match — adjust crop or tap again.", color = Color.White.copy(alpha = 0.8f))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.candidates, key = { it.article.id }) { match ->
                    TicketCandidateRow(match = match, onClick = { onSelect(match) })
                }
            }
        }
        if (state.debugSteps.isNotEmpty()) {
            TicketSnapProgressPanel(
                state = TicketSnapUiState(
                    phase = com.oasismall.oasisai.domain.paray.TicketSnapPhase.DONE,
                    message = "Process log",
                    steps = state.debugSteps,
                    ocrDesignation = state.ocrDesignation,
                    ocrPrice = state.ocrPrice,
                    previewBitmap = state.previewBitmap,
                ),
                stepsExpandedDefault = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TicketCandidateRow(
    match: ParayTicketMatch,
    onClick: () -> Unit,
) {
    val tier = ParayTicketMatchTier.fromProbability(match.fusion.probability)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            match.article.imagePath?.takeIf { File(it).exists() }?.let { path ->
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    match.article.designation,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${match.fusion.probabilityPercent}% · ${match.article.price.toInt()} DA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tier?.let {
                    Text(it.marketingLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
