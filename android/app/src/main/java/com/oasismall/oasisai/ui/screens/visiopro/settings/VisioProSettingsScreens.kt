package com.oasismall.oasisai.ui.screens.visiopro.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.visiopro.VisioProCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisioProSettingsScreen(
    viewModel: VisioProSettingsViewModel,
    onBack: () -> Unit,
    onOpenCategory: (VisioProCategory) -> Unit,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VisioPRO — listes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Text(
                    "À chaque import CSV, les listes se rechargent depuis Gestium. Cochez les articles à afficher sur chaque carte VisioPRO.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rows) { row ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCategory(row.category) }
                        .padding(horizontal = 4.dp),
                    headlineContent = {
                        Text(
                            "${row.category.emoji}  ${row.category.labelFr}",
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    supportingContent = {
                        val pendingHint = if (row.pendingCount > 0) {
                            " · ${row.pendingCount} en attente"
                        } else {
                            ""
                        }
                        Text(
                            "${row.enabledCount} actifs / ${row.poolCount} rayon$pendingHint · ${viewModel.poolHint(row.category)}",
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Storefront, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Default.Reorder, contentDescription = null)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisioProListEditorScreen(
    viewModel: VisioProListEditorViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (ui.showOrderSheet) {
        VisioProOrderSheet(
            articles = ui.orderedSelectedArticles,
            onDone = { ids -> viewModel.applyOrder(ids) },
            onDismiss = { viewModel.dismissOrderSheet() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${ui.category.emoji} ${ui.category.labelFr}")
                        Text(
                            viewModel.poolHint(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.openOrderSheet() },
                    enabled = ui.selectedIds.isNotEmpty() && !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Reorder, contentDescription = null)
                    Text(
                        "Ordre d'affichage (${ui.orderedSelectedArticles.size})",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = ui.selectedIds.isNotEmpty() && !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enregistrer la sélection")
                }
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    enabled = !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text("Restaurer la liste par défaut", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        if (ui.isLoading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    androidx.compose.material3.OutlinedTextField(
                        value = ui.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        label = { Text("Rechercher dans le rayon") },
                        singleLine = true,
                    )
                }
                item {
                    val pendingCount = ui.pendingIds.size
                    Text(
                        "${ui.selectedIds.size} sélectionné(s) sur ${ui.poolArticles.size} dans le rayon" +
                            if (pendingCount > 0) " · $pendingCount nouveau(x) CSV" else "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ui.filteredPool, key = { it.id }) { article ->
                    val checked = article.id in ui.selectedIds
                    val isPending = article.id in ui.pendingIds && !checked
                    CheckboxListItem(
                        checked = checked,
                        onCheckedChange = { enabled -> viewModel.toggleArticle(article.id, enabled) },
                        headline = article.designation,
                        supporting = buildString {
                            append("${article.barcode} · ${com.oasismall.oasisai.util.PriceFormatter.format(article.price)}")
                            if (isPending) append(" · Nouveau CSV")
                        },
                        highlight = isPending,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisioProOrderSheet(
    articles: List<com.oasismall.oasisai.data.db.dao.ArticleWithImage>,
    onDone: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val orderState = remember(articles) {
        mutableStateListOf<Long>().apply { addAll(articles.map { it.id }) }
    }
    val articleById = remember(articles) { articles.associateBy { it.id } }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }

    fun moveEntry(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in orderState.indices || toIndex !in orderState.indices || fromIndex == toIndex) return
        val id = orderState.removeAt(fromIndex)
        orderState.add(toIndex, id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Ordre d'affichage",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Glissez ⋮⋮ ou utilisez ↑ ↓. Puis Terminé.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(orderState, key = { _, id -> id }) { index, articleId ->
                    val article = articleById[articleId] ?: return@itemsIndexed
                    val isDragging = draggingId == articleId
                    ListItem(
                        modifier = Modifier.then(
                            if (isDragging) {
                                Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                            } else {
                                Modifier
                            },
                        ),
                        headlineContent = { Text(article.designation, maxLines = 2) },
                        supportingContent = { Text(article.barcode) },
                        leadingContent = {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Glisser pour réorganiser",
                                modifier = Modifier
                                    .size(40.dp)
                                    .pointerInput(articleId) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingId = articleId
                                                dragStartIndex = orderState.indexOf(articleId)
                                                dragAccumulatedY = 0f
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                dragStartIndex = -1
                                                dragAccumulatedY = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragStartIndex = -1
                                                dragAccumulatedY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggingId != articleId || dragStartIndex < 0) return@detectDragGestures
                                                dragAccumulatedY += dragAmount.y
                                                val targetIndex = (dragStartIndex + (dragAccumulatedY / itemHeightPx).toInt())
                                                    .coerceIn(0, orderState.lastIndex)
                                                val currentIndex = orderState.indexOf(articleId)
                                                if (currentIndex >= 0 && targetIndex != currentIndex) {
                                                    moveEntry(currentIndex, targetIndex)
                                                }
                                            },
                                        )
                                    },
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { moveEntry(index, index - 1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                                }
                                IconButton(
                                    onClick = { moveEntry(index, index + 1) },
                                    enabled = index < orderState.lastIndex,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                                }
                            }
                        },
                    )
                }
            }
            Button(
                onClick = { onDone(orderState.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Terminé")
            }
        }
    }
}

@Composable
private fun CheckboxListItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headline: String,
    supporting: String,
    highlight: Boolean = false,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                } else {
                    Modifier
                },
            ),
        headlineContent = { Text(headline) },
        supportingContent = {
            Text(
                supporting,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
