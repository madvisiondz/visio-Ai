package com.oasismall.oasisai.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.ui.components.ArticleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onArticleClick: (Long) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val articles by viewModel.articles.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Article Catalog") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Search designation") },
                singleLine = true,
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = filter == CatalogFilter.ALL, onClick = { viewModel.setFilter(CatalogFilter.ALL) }, label = { Text("All") })
                FilterChip(selected = filter == CatalogFilter.NEEDS_TICKET, onClick = { viewModel.setFilter(CatalogFilter.NEEDS_TICKET) }, label = { Text("Needs ticket") })
                FilterChip(selected = filter == CatalogFilter.MISSING_IMAGE, onClick = { viewModel.setFilter(CatalogFilter.MISSING_IMAGE) }, label = { Text("Missing image") })
                FilterChip(selected = filter == CatalogFilter.NEW, onClick = { viewModel.setFilter(CatalogFilter.NEW) }, label = { Text("New") })
                FilterChip(selected = filter == CatalogFilter.PRICE_CHANGED, onClick = { viewModel.setFilter(CatalogFilter.PRICE_CHANGED) }, label = { Text("Price changed") })
            }
            if (filter == CatalogFilter.ALL && query.isBlank()) {
                Text(
                    "Type a designation to search the catalog.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onArticleClick(article.id) },
                        trailing = {
                            IconButton(onClick = { viewModel.addToPreselection(article.id) }) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = "Add to pre-selection")
                            }
                        },
                    )
                }
            }
        }
    }
}
