package com.oasismall.oasisai.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oasismall.oasisai.ui.OasisViewModelFactory
import com.oasismall.oasisai.ui.components.ArticleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageManagerScreen(
    viewModel: ImageManagerViewModel,
    onArticleClick: (Long) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val totalMissing by viewModel.totalMissing.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Image Manager") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Articles missing a product image. Prepare PNGs outside the app, then use Settings > Link images from gallery.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (totalMissing > articles.size) {
                        "Showing ${articles.size} of $totalMissing — search to narrow down"
                    } else {
                        "$totalMissing articles missing images"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search designation") },
                    singleLine = true,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (articles.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No missing images — great!" else "No matches for this search.",
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onArticleClick(article.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ImageManagerRoute(
    factory: OasisViewModelFactory,
    onArticleClick: (Long) -> Unit,
) {
    val viewModel: ImageManagerViewModel = viewModel(factory = factory)
    ImageManagerScreen(viewModel, onArticleClick)
}
