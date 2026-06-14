package com.oasismall.oasisai.ui.screens.checkshoot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.oasismall.oasisai.domain.paray.ParayMatch
import com.oasismall.oasisai.domain.paray.ParaySuggestion
import java.io.File

data class ParaySuggestState(
    val scannedBarcode: String,
    val suggestions: List<ParaySuggestion> = emptyList(),
    val visualMatches: List<ParayMatch> = emptyList(),
    val designationQuery: String = "",
    val designationResults: List<ArticleWithImage> = emptyList(),
    val designationSearching: Boolean = false,
    val loading: Boolean = false,
    val teachingVisual: Boolean = false,
    val teachCapturePath: String? = null,
)

@Composable
fun ParaySuggestionSheet(
    state: ParaySuggestState,
    onSelectSuggestion: (ParaySuggestion) -> Unit,
    onSelectVisualMatch: (ParayMatch) -> Unit,
    onTeachParayLook: () -> Unit,
    onManualSearch: () -> Unit,
    onDesignationQueryChange: (String) -> Unit,
    onSearchDesignation: () -> Unit,
    onSelectDesignationMatch: (ArticleWithImage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
            elevation = CardDefaults.cardElevation(16.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("PARAY suggestions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Scanned: ${state.scannedBarcode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Pick a match to lock the barcode — then Add to To share / Design appear below. PARAY compares the first 9 digits (last 5 ignored).",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text(if (state.teachingVisual) "PARAY looking at product…" else "PARAY thinking…")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.suggestions.isNotEmpty()) {
                        item {
                            Text("Barcode matches", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.suggestions, key = { "b-${it.articleId}" }) { suggestion ->
                            SuggestionCard(
                                designation = suggestion.designation,
                                barcode = suggestion.barcode,
                                reason = suggestion.reason,
                                confidence = suggestion.confidence,
                                imagePath = suggestion.imagePath,
                                onClick = { onSelectSuggestion(suggestion) },
                            )
                        }
                    }
                    if (state.visualMatches.isNotEmpty()) {
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Looks like (camera)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.visualMatches, key = { "v-${it.articleId}" }) { match ->
                            SuggestionCard(
                                designation = match.designation,
                                barcode = match.barcode,
                                reason = "PARAY visual match",
                                confidence = match.confidence,
                                imagePath = null,
                                onClick = { onSelectVisualMatch(match) },
                            )
                        }
                    }
                    if (state.designationResults.isNotEmpty()) {
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Designation search", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.designationResults, key = { "d-${it.id}" }) { article ->
                            SuggestionCard(
                                designation = article.designation,
                                barcode = article.barcode,
                                reason = "Catalog designation match",
                                confidence = 0.88f,
                                imagePath = article.imagePath,
                                onClick = { onSelectDesignationMatch(article) },
                            )
                        }
                    }
                    if (!state.loading && state.suggestions.isEmpty() && state.visualMatches.isEmpty() &&
                        state.designationResults.isEmpty()
                    ) {
                        item {
                            Text(
                                "No automatic match yet. Search by designation, let PARAY look at the pack, or search digits.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.designationQuery,
                    onValueChange = onDesignationQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search by designation") },
                    placeholder = { Text("e.g. lait ifri 1l") },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = onSearchDesignation,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && !state.designationSearching,
                ) {
                    if (state.designationSearching) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Search catalog")
                }
                Button(onClick = onTeachParayLook, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) {
                    Text("Let PARAY look at product")
                }
                OutlinedButton(onClick = onManualSearch, modifier = Modifier.fillMaxWidth()) {
                    Text("Manual digit search")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    designation: String,
    barcode: String,
    reason: String,
    confidence: Float,
    imagePath: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(designation, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(barcode, style = MaterialTheme.typography.bodySmall)
            Text(reason, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${(confidence * 100).toInt()}% confidence", style = MaterialTheme.typography.labelSmall)
            imagePath?.takeIf { File(it).exists() }?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}
