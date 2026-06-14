package com.oasismall.oasisai.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.data.db.dao.WorkflowHistoryItem
import com.oasismall.oasisai.data.repository.OasisRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class WorkHistoryViewModel(repository: OasisRepository) : ViewModel() {
    val items = repository.observeWorkflowHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkHistoryScreen(
    viewModel: WorkHistoryViewModel,
    onArticleClick: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("History (${items.size})") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Starts empty on this version and records new app actions: searched/scanned, added to carts, linked images, and sent files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (items.isEmpty()) {
                item { Text("History is empty.", modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(items, key = { it.id }) { item ->
                WorkHistoryCard(
                    item = item,
                    onClick = { item.articleId?.let(onArticleClick) },
                )
            }
        }
    }
}

@Composable
private fun WorkHistoryCard(
    item: WorkflowHistoryItem,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(historyLabel(item.eventType), style = MaterialTheme.typography.titleSmall)
            item.designationSnapshot?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            item.barcodeSnapshot?.let { Text("Barcode: $it", style = MaterialTheme.typography.bodySmall) }
            item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(formatTimestamp(item.createdAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(value))

private fun historyLabel(type: String): String = when (type) {
    "SEARCHED" -> "Searched"
    "SCANNED" -> "Barcode scanned"
    "ADDED_TO_PHOTOSHOOT" -> "Added to To shoot"
    "ADDED_TO_SHARE" -> "Added to To share"
    "IMAGE_LINKED" -> "Image linked / shot"
    "SENT" -> "Sent as file"
    else -> type.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
}
