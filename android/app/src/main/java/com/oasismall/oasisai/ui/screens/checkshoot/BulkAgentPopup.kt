package com.oasismall.oasisai.ui.screens.checkshoot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

data class BulkScanState(
    val barcode: String,
    val imagePath: String?,
    val hasImage: Boolean,
)

@Composable
fun BulkAgentPopup(
    state: BulkScanState,
    modelReady: Boolean,
    onTakeOrReplace: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bulk capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Barcode: ${state.barcode}", style = MaterialTheme.typography.bodyLarge)
            if (state.hasImage && !state.imagePath.isNullOrBlank()) {
                Text(
                    "PNG already saved for this barcode.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AsyncImage(
                    model = File(state.imagePath),
                    contentDescription = "Existing PNG",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTakeOrReplace,
                        enabled = modelReady,
                        modifier = Modifier.weight(1f),
                    ) { Text("Replace photo") }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text("Skip — next")
                    }
                }
            } else {
                Text(
                    "No PNG yet — take photo (auto cutout, saved to Download/BULK).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onTakeOrReplace,
                    enabled = modelReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Take photo") }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip — keep scanning")
                }
            }
        }
    }
}
