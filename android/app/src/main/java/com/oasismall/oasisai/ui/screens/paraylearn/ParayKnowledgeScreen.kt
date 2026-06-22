package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayKnowledgeSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParayKnowledgeScreen(viewModel: ParayKnowledgeViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    when {
        ui.loading -> {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
        ui.error != null -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            }
        }
        ui.data != null -> {
            KnowledgeContent(data = ui.data!!, onRefresh = viewModel::refresh)
        }
    }
}

@Composable
private fun KnowledgeContent(data: ParayKnowledgeUiData, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Catalog knowledge from cached JSON",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            KnowledgeSummaryCard(data.summary)
        }
        item {
            KnowledgeSectionCard(
                title = "Brands",
                rows = data.brands.map { it.name to it.productCount },
            )
        }
        item {
            KnowledgeSectionCard(
                title = "Categories",
                rows = data.categories.map { it.name to it.productCount },
            )
        }
        item {
            KnowledgeSectionCard(
                title = "Families",
                rows = data.families.map { it.name to it.productCount },
            )
        }
        item {
            Text("Recently learned products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (data.recentlyLearned.isEmpty()) {
            item {
                Text(
                    "No learned products in knowledge cache yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(data.recentlyLearned, key = { it.articleId }) { product ->
                RecentlyLearnedCard(product)
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun KnowledgeSummaryCard(summary: ParayKnowledgeSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Global summary", fontWeight = FontWeight.Bold)
            SummaryRow("Known articles", summary.knownArticleCount)
            SummaryRow("Brands", summary.totalBrands)
            SummaryRow("Categories", summary.totalCategories)
            SummaryRow("Families", summary.totalFamilies)
            Text(
                "Updated: ${formatKnowledgeDate(summary.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KnowledgeSectionCard(title: String, rows: List<Pair<String, Int>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (rows.isEmpty()) {
                Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                rows.take(MAX_SECTION_ROWS).forEachIndexed { index, (name, count) ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    GroupCountRow(name, count)
                }
                if (rows.size > MAX_SECTION_ROWS) {
                    Text(
                        "+ ${rows.size - MAX_SECTION_ROWS} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyLearnedCard(product: ParayRecentlyLearnedProduct) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.designation, fontWeight = FontWeight.SemiBold)
            Text("Barcode: ${product.barcode}", style = MaterialTheme.typography.bodySmall)
            product.brand?.takeIf { it.isNotBlank() }?.let {
                Text("Brand: $it", style = MaterialTheme.typography.bodySmall)
            }
            product.category?.takeIf { it.isNotBlank() }?.let {
                Text("Category: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Learned: ${formatKnowledgeDate(product.learnedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GroupCountRow(name: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$count products", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatKnowledgeDate(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}

private const val MAX_SECTION_ROWS = 12
