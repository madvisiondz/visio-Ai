package com.oasismall.oasisai.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.ui.components.ArticleCard
import com.oasismall.oasisai.ui.components.OpenCameraBatchButton
import com.oasismall.oasisai.util.hasAppGalleryImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onArticleClick: (Long) -> Unit,
    onOpenCameraBatch: (articleId: Long?) -> Unit = {},
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedRayon by viewModel.selectedRayon.collectAsStateWithLifecycle()
    val rayons by viewModel.rayons.collectAsStateWithLifecycle()
    val result by viewModel.searchResult.collectAsStateWithLifecycle()
    val shareCount by viewModel.shareCartCount.collectAsStateWithLifecycle()
    val shootCount by viewModel.photoshootCartCount.collectAsStateWithLifecycle()

    val showResults = query.isNotBlank() || selectedRayon != null

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(
                        if (selectedRayon != null) {
                            "Rechercher dans $selectedRayon"
                        } else {
                            "Désignation, code-barres ou code"
                        },
                    )
                },
                placeholder = { Text("Ex. banane, coca, 250…") },
                singleLine = true,
            )

            Text(
                "Rayon",
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedRayon == null,
                    onClick = { viewModel.setSelectedRayon(null) },
                    label = { Text("Tous les rayons") },
                )
                rayons.forEach { rayon ->
                    FilterChip(
                        selected = selectedRayon == rayon,
                        onClick = { viewModel.setSelectedRayon(rayon) },
                        label = { Text(rayon) },
                    )
                }
            }

            if (!showResults) {
                Text(
                    "Choisissez un rayon pour parcourir les prix, ou recherchez dans tout le catalogue.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "To share : $shareCount · To shoot : $shootCount",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (selectedRayon != null && query.isBlank()) {
                Text(
                    "${result.total} article(s) · $selectedRayon — saisissez un nom pour affiner",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showResults) {
                    item {
                        Text(
                            "${result.total} résultat(s)",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    if (result.withImage.isNotEmpty()) {
                        item { SectionHeader("Dans la galerie (${result.withImage.size})") }
                        items(result.withImage, key = { "ok_${it.id}_${it.barcode}" }) { article ->
                            HomeArticleRow(
                                article = article,
                                canShare = true,
                                onArticleClick = onArticleClick,
                                onShare = { viewModel.addToShareCart(article.id, article.barcode) },
                                onOpenCameraBatch = onOpenCameraBatch,
                            )
                        }
                    }
                    if (result.withoutImage.isNotEmpty()) {
                        item { SectionHeader("Sans photo (${result.withoutImage.size})") }
                        items(result.withoutImage, key = { "miss_${it.id}_${it.barcode}" }) { article ->
                            HomeArticleRow(
                                article = article,
                                canShare = false,
                                onArticleClick = onArticleClick,
                                onShare = { viewModel.addToShareCart(article.id, article.barcode) },
                                onShoot = { viewModel.addToPhotoshootCart(article.id, article.barcode) },
                                onOpenCameraBatch = onOpenCameraBatch,
                            )
                        }
                    }
                    if (result.total == 0) {
                        item {
                            Text(
                                if (query.isNotBlank()) {
                                    "Aucun article pour « $query »" +
                                        (selectedRayon?.let { " dans $it" } ?: "")
                                } else {
                                    "Aucun article dans ce rayon."
                                },
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeArticleRow(
    article: ArticleWithImage,
    canShare: Boolean,
    onArticleClick: (Long) -> Unit,
    onShare: () -> Unit,
    onOpenCameraBatch: (Long?) -> Unit,
    onShoot: (() -> Unit)? = null,
) {
    val shareEnabled = canShare && article.hasAppGalleryImage()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ArticleCard(article = article, onClick = { onArticleClick(article.id) })
        if (onShoot != null && !article.hasAppGalleryImage()) {
            Button(onClick = onShoot, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter à To shoot")
            }
        }
        OpenCameraBatchButton(onClick = onOpenCameraBatch, articleId = article.id)
        Button(
            onClick = onShare,
            enabled = shareEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (shareEnabled) "Ajouter à To share" else "To share (sans image — AGENT)",
            )
        }
    }
}
