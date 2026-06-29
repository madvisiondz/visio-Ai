package com.oasismall.oasisai.ui.screens.checkshoot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.util.BarcodeSuffixMatcher
import com.oasismall.oasisai.util.PriceFormatter

@Composable
fun CheckShootSuffixPicker(
    state: SuffixMatchState,
    onEditableBarcodeChange: (String) -> Unit,
    onTrimPrefix: (Int) -> Unit,
    onSearch: () -> Unit,
    onSelectArticle: (ArticleWithImage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Link barcode to catalog article",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Scanned: ${state.scannedBarcode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Trim digits below (remove store prefix) then search. Tap the correct article.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = state.editableBarcode,
                    onValueChange = onEditableBarcodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barcode digits to match") },
                    placeholder = { Text("Edit — delete prefix digits…") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onTrimPrefix(3) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Drop 3")
                    }
                    OutlinedButton(
                        onClick = { onTrimPrefix(4) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Drop 4")
                    }
                    TextButton(
                        onClick = { onEditableBarcodeChange(BarcodeSuffixMatcher.digitsOnly(state.scannedBarcode)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Reset")
                    }
                }
                Button(
                    onClick = onSearch,
                    enabled = state.editableBarcode.length >= 4 && !state.searching,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (state.searching) "Searching…" else "Search catalog")
                }
                HorizontalDivider()
                Text(
                    when {
                        state.searching -> "Searching…"
                        state.candidates.isEmpty() -> "No matches — trim more digits and search again"
                        else -> "${state.candidates.size} possible articles"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.candidates, key = { it.id }) { article ->
                        SuffixCandidateRow(
                            article = article,
                            queryDigits = state.editableBarcode,
                            onClick = { onSelectArticle(article) },
                        )
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SuffixCandidateRow(
    article: ArticleWithImage,
    queryDigits: String,
    onClick: () -> Unit,
) {
    val catalogSuffix = BarcodeSuffixMatcher.suffixOfCatalogBarcode(article.barcode)
    val endsMatch = queryDigits.length >= 4 &&
        article.barcode.endsWith(queryDigits)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                article.designation,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                PriceFormatter.format(article.price),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Barcode: ${article.barcode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (endsMatch || catalogSuffix == queryDigits) {
                Text(
                    "Suffix match",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                )
            }
        }
    }
}
