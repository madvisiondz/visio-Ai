package com.oasismall.oasisai.ui.screens.preselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.ui.components.ArticleCard
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.dao.ArticleWithImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreselectionScreen(
    viewModel: PreselectionViewModel,
    onArticleClick: (Long) -> Unit,
    onGoToPrint: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pre-selection (${items.size})") }) },
        bottomBar = {
            Button(
                onClick = onGoToPrint,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = items.isNotEmpty(),
            ) { Text("Choose template & print") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                Text("Add articles from Catalog or Scanner first.", modifier = Modifier.padding(16.dp))
            }
            Button(onClick = viewModel::clear, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear all") }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.preselectionId }) { item ->
                    ArticleCard(
                        article = item.toArticleWithImage(),
                        onClick = { onArticleClick(item.articleId) },
                        trailing = {
                            IconButton(onClick = { viewModel.remove(item.articleId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun PreselectionWithArticle.toArticleWithImage() = ArticleWithImage(
    id = articleId,
    barcode = barcode,
    designation = designation,
    normalizedName = designation,
    price = price,
    previousPrice = previousPrice,
    reference = null,
    category = category,
    brand = null,
    stock = null,
    unit = null,
    rawData = null,
    changeStatus = "UNCHANGED",
    isActive = true,
    needsTicketUpdate = false,
    imagePath = imagePath,
    imageStatus = null,
    imageCreatedAt = imageCreatedAt,
    imageLastSentAt = imageLastSentAt,
)
