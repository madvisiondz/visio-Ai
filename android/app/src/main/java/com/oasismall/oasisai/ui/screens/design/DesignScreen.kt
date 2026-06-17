package com.oasismall.oasisai.ui.screens.design

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.domain.design.DesignCartExpand
import com.oasismall.oasisai.util.ExportShareHelper
import com.oasismall.oasisai.util.PriceFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(viewModel: DesignViewModel) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    when (step) {
        DesignStep.HOME -> DesignHomeScreen(viewModel)
        DesignStep.READY_PRINT -> ReadyPrintScreen(viewModel)
        DesignStep.SHELF_LAYOUT -> DesignHomeScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignHomeScreen(viewModel: DesignViewModel) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val doneItems by viewModel.doneItems.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val shareable = viewModel.shareableItems()
    val labelCount = DesignCartExpand.labelCount(shareable)
    var detailItem by remember { mutableStateOf<PreselectionWithArticle?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPasteText by remember { mutableStateOf("") }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import checked prices") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the message returned from PC (barcode + designation + edited price).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = importPasteText,
                        onValueChange = { importPasteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        placeholder = { Text("OASIS-DESIGN-PRICES v1\n1|barcode|name|240") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importCheckedPrices(importPasteText)
                        showImportDialog = false
                        importPasteText = ""
                    },
                ) { Text("Apply to Design") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
        )
    }

    detailItem?.let { item ->
        DesignArticleDetailDialog(item = item, onDismiss = { detailItem = null })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Design") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val learned by viewModel.parayLearnedCount.collectAsStateWithLifecycle()
                Text(
                    "Shelf labels on phone — pick a template to generate a print-ready A4 JPEG.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${viewModel.parayName} learned $learned product look(s) — shape, colors, typography for future camera ID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            message?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.primary) }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = shareable.isNotEmpty() && !isRendering) {
                            viewModel.startShelfPrint(context)
                        },
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isRendering) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp))
                            } else {
                                Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("Shelf labels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "12 per landscape A4 (2×6) · yellow 9.2×3.5 cm · no row gaps",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (shareable.isEmpty()) "Add articles from To share (checked items)"
                            else if (isRendering) "Generating print sheet…"
                            else "$labelCount label(s) from ${shareable.size} article(s) — tap to print",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (items.isNotEmpty()) {
                item {
                    Text("To print", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Send info or print — then mark as sent/printed yourself. Pull up from Done to print again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    DesignQueueActions(
                        onSendInfo = { viewModel.sendCartInfo(context) },
                        onMarkSent = viewModel::markQueueAsSent,
                        onImportPrices = { showImportDialog = true },
                    )
                }
                items(items, key = { "print_${it.preselectionId}" }) { item ->
                    DesignQueueRow(
                        item = item,
                        onImageClick = { detailItem = item },
                        onRemove = { viewModel.remove(item.preselectionId) },
                        onPriceCommit = { text -> viewModel.updatePrice(item.articleId, text) },
                        onIncrementCopy = { viewModel.incrementCopy(item.preselectionId) },
                        onDecrementCopy = { viewModel.decrementCopy(item.preselectionId) },
                    )
                }
                item {
                    OutlinedButton(onClick = viewModel::clearDesign, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear print queue")
                    }
                }
            }
            if (doneItems.isNotEmpty()) {
                item {
                    Text(
                        "Done (${doneItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Printed or sent — newest first. Pull up to re-print, or remove from history. Keeps last 50.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(doneItems, key = { "done_${it.preselectionId}" }) { item ->
                    DesignDoneRow(
                        item = item,
                        onPullUp = { viewModel.pullUpFromDone(item.preselectionId) },
                        onRemove = { viewModel.removeFromDone(item.preselectionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignQueueActions(
    onSendInfo: () -> Unit,
    onMarkSent: () -> Unit,
    onImportPrices: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSendInfo, modifier = Modifier.weight(1f)) {
                Text("Send info")
            }
            OutlinedButton(onClick = onImportPrices, modifier = Modifier.weight(1f)) {
                Text("Import prices")
            }
        }
        OutlinedButton(onClick = onMarkSent, modifier = Modifier.fillMaxWidth()) {
            Text("Mark as sent")
        }
    }
}

@Composable
private fun DesignArticleDetailDialog(
    item: PreselectionWithArticle,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Article details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!item.imagePath.isNullOrBlank() && File(item.imagePath).exists()) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = item.designation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEEEEEE)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(item.designation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                DetailLine("Barcode", item.barcode)
                DetailLine("Price", "${PriceFormatter.formatNumber(item.price)} DA")
                item.codeart?.takeIf { it.isNotBlank() }?.let { DetailLine("Code", it) }
                item.category?.takeIf { it.isNotBlank() }?.let { DetailLine("Category", it) }
                item.previousPrice?.let { prev ->
                    if (prev > 0) DetailLine("Previous price", "${PriceFormatter.formatNumber(prev)} DA")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DesignDoneRow(
    item: PreselectionWithArticle,
    onPullUp: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.designation, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(
                    "${PriceFormatter.formatNumber(item.price)} DA · ×${item.copyCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    formatDoneTimestamp(item.addedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onPullUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                    Text("Pull up")
                }
                OutlinedButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Remove")
                }
            }
        }
    }
}

private fun formatDoneTimestamp(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))

@Composable
private fun DesignQueueRow(
    item: PreselectionWithArticle,
    onImageClick: () -> Unit,
    onRemove: () -> Unit,
    onPriceCommit: (String) -> Unit,
    onIncrementCopy: () -> Unit,
    onDecrementCopy: () -> Unit,
) {
    val copies = item.copyCount.coerceIn(1, 99)
    var priceText by remember(item.preselectionId) { mutableStateOf(PriceFormatter.formatNumber(item.price)) }
    var hadFocus by remember(item.preselectionId) { mutableStateOf(false) }
    LaunchedEffect(item.price) {
        if (!hadFocus) priceText = PriceFormatter.formatNumber(item.price)
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!item.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(item.imagePath),
                    contentDescription = item.designation,
                    modifier = Modifier
                        .height(56.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onImageClick),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier
                        .height(56.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onImageClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No PNG", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.designation, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(
                            onClick = onDecrementCopy,
                            enabled = copies > 1,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Fewer copies")
                        }
                        Text("×$copies", style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = onIncrementCopy,
                            enabled = copies < 99,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "More copies")
                        }
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Price") },
                    suffix = { Text("DA") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onPriceCommit(priceText) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (hadFocus && !state.isFocused) onPriceCommit(priceText)
                            hadFocus = state.isFocused
                        },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyPrintScreen(viewModel: DesignViewModel) {
    val jpegPath by viewModel.readyJpegPath.collectAsStateWithLifecycle()
    val pageIndex by viewModel.shelfPageIndex.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pages = viewModel.pageCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ready to print") },
                navigationIcon = {
                    IconButton(onClick = viewModel::backToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Landscape A4 · 12 shelf labels (2×6) · 300 DPI · JPEG quality 100%. Share as file to avoid recompression.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pages > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.printPage(context, pageIndex - 1) },
                        enabled = pageIndex > 0 && !isRendering,
                    ) { Text("Previous page") }
                    Text(
                        "Page ${pageIndex + 1} of $pages",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(
                        onClick = { viewModel.printPage(context, pageIndex + 1) },
                        enabled = pageIndex < pages - 1 && !isRendering,
                    ) { Text("Next page") }
                }
            }
            if (isRendering) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!jpegPath.isNullOrBlank()) {
                Card(Modifier.fillMaxWidth().weight(1f)) {
                    AsyncImage(
                        model = Uri.fromFile(File(jpegPath!!)),
                        contentDescription = "Print preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Button(
                    onClick = {
                        ExportShareHelper.shareJpegAsFile(context, File(jpegPath!!))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share as file") }
                Button(
                    onClick = { viewModel.markPageAsPrinted(pageIndex) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark as printed") }
                OutlinedButton(onClick = viewModel::backToHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Design")
                }
            }
        }
    }
}
