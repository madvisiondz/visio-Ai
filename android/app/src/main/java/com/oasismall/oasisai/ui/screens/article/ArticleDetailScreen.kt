package com.oasismall.oasisai.ui.screens.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oasismall.oasisai.ui.components.rememberAssignPngPicker
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.ui.components.ArticleActionPanel
import com.oasismall.oasisai.ui.components.ArticlePanelData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleId: Long,
    viewModel: ArticleDetailViewModel,
    onBack: () -> Unit,
    onRemoveBackground: (Long) -> Unit = {},
    onCreateAsset: (String) -> Unit = {},
    onOpenCameraBatch: (articleId: Long?) -> Unit = {},
    onOpenSubBarcodeBatchShoot: (articleId: Long) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, articleId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load(articleId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val article by viewModel.article.collectAsStateWithLifecycle()
    val meta by viewModel.meta.collectAsStateWithLifecycle()
    val panelMessage by viewModel.message.collectAsStateWithLifecycle()
    var assignArticleId by remember { mutableLongStateOf(0L) }
    val launchAssignPng = rememberAssignPngPicker { uri ->
        if (assignArticleId > 0L) viewModel.assignPng(uri, assignArticleId)
    }

    panelMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { androidx.compose.material3.Text("Gallery") },
            text = { androidx.compose.material3.Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { androidx.compose.material3.Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val a = article
        if (a == null) {
            Text("Loading...", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val panelData = ArticlePanelData.fromArticle(a, meta)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ArticleActionPanel(
                    data = panelData,
                    scrollable = false,
                    onCreateAsset = { onCreateAsset(a.barcode) },
                    onAddSubBarcodeBatchShoot = { onOpenSubBarcodeBatchShoot(articleId) },
                    onAssignPngImage = {
                        assignArticleId = articleId
                        launchAssignPng()
                    },
                    onOpenCameraBatch = { onOpenCameraBatch(articleId) },
                    onAddToShare = viewModel::addToShareCart,
                    onAddToShoot = viewModel::addToPhotoshootCart,
                    onAddToDesign = viewModel::addToDesignCart,
                    onRemoveBackground = { onRemoveBackground(articleId) },
                    onRemoveSubBarcode = viewModel::removeSubBarcode,
                )
            }
            a.rawData?.takeIf { it.isNotBlank() }?.let { raw ->
                item {
                    Text("All CSV details", style = MaterialTheme.typography.titleMedium)
                    Text(raw, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
