package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.domain.paray.ParayLearnStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParayMemoryScreen(viewModel: ParayMemoryViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Visual memory from learn_index.json",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ui.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search barcode, designation, brand") },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MemoryFilterChip("All (${ui.entries.size})", ui.filter == ParayMemoryFilter.ALL) {
                    viewModel.setFilter(ParayMemoryFilter.ALL)
                }
                MemoryFilterChip("Learned (${ui.learnedCount})", ui.filter == ParayMemoryFilter.LEARNED) {
                    viewModel.setFilter(ParayMemoryFilter.LEARNED)
                }
                MemoryFilterChip("Partial (${ui.partialCount})", ui.filter == ParayMemoryFilter.PARTIAL) {
                    viewModel.setFilter(ParayMemoryFilter.PARTIAL)
                }
                MemoryFilterChip("Pending (${ui.pendingCount})", ui.filter == ParayMemoryFilter.PENDING) {
                    viewModel.setFilter(ParayMemoryFilter.PENDING)
                }
            }
        }

        when {
            ui.loading -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            ui.error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
            }
            ui.filteredEntries.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (ui.entries.isEmpty()) "No products in PARAY memory yet."
                        else "No products match this filter or search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Refresh")
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.filteredEntries, key = { it.articleId }) { entry ->
                        ParayMemoryEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ParayMemoryEntryCard(entry: ParayMemoryEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AsyncImage(
                    model = File(entry.pngFrontPath),
                    contentDescription = entry.designation,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.designation, fontWeight = FontWeight.SemiBold)
                    Text("Barcode: ${entry.barcode}", style = MaterialTheme.typography.bodySmall)
                    entry.brand?.takeIf { it.isNotBlank() }?.let {
                        Text("Brand: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    entry.category?.takeIf { it.isNotBlank() }?.let {
                        Text("Category: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    entry.family?.takeIf { it.isNotBlank() }?.let {
                        Text("Family: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        statusLabel(entry.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(entry.status),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            MemoryFlagRow("Front confirmed", entry.frontConfirmed)
            MemoryFlagRow("Left learned", entry.leftLearned)
            MemoryFlagRow("Right learned", entry.rightLearned)
            MemoryFlagRow("Back learned", entry.backLearned)
            Text(
                "Last learning: ${formatLearningDate(entry.lastLearningDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoryFlagRow(label: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun statusColor(status: ParayLearnStatus) = when (status) {
    ParayLearnStatus.LEARNED -> MaterialTheme.colorScheme.primary
    ParayLearnStatus.PARTIALLY_LEARNED -> MaterialTheme.colorScheme.tertiary
    ParayLearnStatus.NOT_LEARNED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: ParayLearnStatus): String = when (status) {
    ParayLearnStatus.LEARNED -> "Learned"
    ParayLearnStatus.PARTIALLY_LEARNED -> "Partially Learned"
    ParayLearnStatus.NOT_LEARNED -> "Not Learned"
}

private fun formatLearningDate(ms: Long?): String {
    if (ms == null || ms <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
