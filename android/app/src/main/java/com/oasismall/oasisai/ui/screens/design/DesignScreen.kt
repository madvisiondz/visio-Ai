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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.domain.design.DesignBatchItemUi
import com.oasismall.oasisai.ui.components.CatalogChangeBadge
import com.oasismall.oasisai.ui.components.catalogChangeGlow
import com.oasismall.oasisai.ui.components.hasCatalogChange
import com.oasismall.oasisai.domain.design.DesignCartExpand
import com.oasismall.oasisai.domain.design.shelfDisplayPrice
import com.oasismall.oasisai.domain.design.shelfOriginalPrice
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
        DesignStep.BATCH_DETAIL -> DesignBatchDetailScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignHomeScreen(viewModel: DesignViewModel) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val doneItems by viewModel.doneItems.collectAsStateWithLifecycle()
    val printHistory by viewModel.printHistory.collectAsStateWithLifecycle()
    val homeTab by viewModel.homeTab.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val shareable = viewModel.shareableItems()
    val labelCount = DesignCartExpand.labelCount(shareable)
    val learned by viewModel.parayLearnedCount.collectAsStateWithLifecycle()
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
                TabRow(selectedTabIndex = homeTab.ordinal) {
                    Tab(
                        selected = homeTab == DesignHomeTab.QUEUE,
                        onClick = { viewModel.setHomeTab(DesignHomeTab.QUEUE) },
                        text = { Text("À imprimer") },
                    )
                    Tab(
                        selected = homeTab == DesignHomeTab.HISTORY,
                        onClick = { viewModel.setHomeTab(DesignHomeTab.HISTORY) },
                        text = { Text("Historique (${printHistory.size})") },
                    )
                }
            }
            if (homeTab == DesignHomeTab.QUEUE) {
            item {
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
                            "12 per landscape A4 (2×6) · standard or promo tickets (prix-barrée)",
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
                        onPromoToggle = { enabled -> viewModel.setPromoTicket(item.preselectionId, enabled) },
                        onPromoPricesCommit = { promo, original ->
                            viewModel.updatePromoPrices(item.preselectionId, promo, original)
                        },
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
            } else {
                item {
                    Text(
                        "Fichiers générés à chaque impression — date et heure dans le dossier d'export.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                message?.let { msg ->
                    item { Text(msg, color = MaterialTheme.colorScheme.primary) }
                }
                if (printHistory.isEmpty()) {
                    item {
                        Text(
                            "Aucune impression enregistrée. Imprimez depuis « À imprimer ».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(printHistory, key = { "hist_${it.id}" }) { batch ->
                        DesignHistoryBatchRow(
                            batch = batch,
                            onClick = { viewModel.openBatchDetail(batch.id) },
                        )
                    }
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
private fun DesignHistoryBatchRow(
    batch: PrintBatchEntity,
    onClick: () -> Unit,
) {
    val exportFile = File(batch.exportPath)
    val folderLabel = exportFile.parentFile?.name ?: "exports"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatDoneTimestamp(batch.createdAt),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${batch.itemCount} article(s) · page ${batch.pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "$folderLabel/${exportFile.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                batch.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignBatchDetailScreen(viewModel: DesignViewModel) {
    val context = LocalContext.current
    val detail by viewModel.batchDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val batch = detail.batch
    val activeCount = detail.items.count { !it.excludedFromReprint }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(batch?.let { formatDoneTimestamp(it.createdAt) } ?: "Impression") },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeBatchDetail) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            batch?.let { b ->
                item {
                    Text(
                        "${b.itemCount} article(s) · ${File(b.exportPath).name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        File(b.exportPath).parentFile?.absolutePath ?: b.exportPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.primary) }
            }
            item {
                Text(
                    "$activeCount / ${detail.items.size} sélectionné(s) pour réimpression",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Articles modifiés par import CSV sont surlignés. Décochez pour exclure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(detail.items, key = { it.batchItemId }) { item ->
                DesignBatchItemRow(
                    item = item,
                    onToggleExclude = { viewModel.toggleExcludeBatchItem(item.batchItemId) },
                    onSendToDesign = { viewModel.sendBatchItemToDesign(item.batchItemId) },
                    onSendToShare = { viewModel.sendBatchItemToShare(item.batchItemId) },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.reprintFromBatchDetail(context) },
                        enabled = activeCount > 0 && !isRendering,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(20.dp).padding(end = 8.dp),
                            )
                        }
                        Text("Réimprimer la sélection")
                    }
                    OutlinedButton(
                        onClick = viewModel::loadBatchToDesignQueue,
                        enabled = activeCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Charger dans Design") }
                    OutlinedButton(
                        onClick = { viewModel.shareBatchExportFile(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Partager le fichier JPEG") }
                }
            }
        }
    }
}

@Composable
private fun DesignBatchItemRow(
    item: DesignBatchItemUi,
    onToggleExclude: () -> Unit,
    onSendToDesign: () -> Unit,
    onSendToShare: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .catalogChangeGlow(item.hasCatalogChange),
        colors = CardDefaults.cardColors(
            containerColor = if (item.excludedFromReprint) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.imagePath.isNullOrBlank() && File(item.imagePath).exists()) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = item.designation,
                        modifier = Modifier
                            .height(56.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.designation, fontWeight = FontWeight.Medium, maxLines = 2)
                    Text(
                        if (item.isPromoTicket) {
                            val orig = item.promoOriginalPrice
                            if (orig != null) {
                                "${PriceFormatter.formatNumber(item.promoPrice ?: item.price)} DA promo · ${PriceFormatter.formatNumber(orig)} barré · ×${item.copyCount}"
                            } else {
                                "${PriceFormatter.formatNumber(item.promoPrice ?: item.price)} DA promo · ×${item.copyCount}"
                            }
                        } else {
                            "${PriceFormatter.formatNumber(item.price)} DA · ×${item.copyCount}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (item.hasCatalogChange) {
                        CatalogChangeBadge(active = true)
                        if (kotlin.math.abs(item.price - item.priceAtPrint) > 0.009) {
                            Text(
                                "Imprimé à ${PriceFormatter.formatNumber(item.priceAtPrint)} DA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !item.excludedFromReprint,
                    onClick = onToggleExclude,
                    label = { Text(if (item.excludedFromReprint) "Exclu" else "Inclus") },
                )
                OutlinedButton(onClick = onSendToDesign) { Text("Design") }
                OutlinedButton(onClick = onSendToShare) { Text("To share") }
            }
        }
    }
}

@Composable
private fun DesignDoneRow(
    item: PreselectionWithArticle,
    onPullUp: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .catalogChangeGlow(item.hasCatalogChange()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.designation, fontWeight = FontWeight.Medium, maxLines = 2)
                    CatalogChangeBadge(active = item.hasCatalogChange())
                }
                Text(
                    if (item.isPromoTicket) {
                        val promo = item.promoPrice ?: item.shelfDisplayPrice()
                        val orig = item.promoOriginalPrice ?: item.shelfOriginalPrice()
                        if (orig != null) {
                            "${PriceFormatter.formatNumber(promo)} DA promo · ${PriceFormatter.formatNumber(orig)} barré · ×${item.copyCount.coerceAtLeast(1)}"
                        } else {
                            "${PriceFormatter.formatNumber(promo)} DA promo · ×${item.copyCount.coerceAtLeast(1)}"
                        }
                    } else {
                        "${PriceFormatter.formatNumber(item.price)} DA · ×${item.copyCount.coerceAtLeast(1)}"
                    },
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
    onPromoToggle: (Boolean) -> Unit,
    onPromoPricesCommit: (promo: String, original: String) -> Unit,
    onIncrementCopy: () -> Unit,
    onDecrementCopy: () -> Unit,
) {
    val copies = item.copyCount.coerceIn(1, 99)
    var priceText by remember(item.preselectionId, item.isPromoTicket) {
        mutableStateOf(PriceFormatter.formatNumber(item.price))
    }
    var promoText by remember(item.preselectionId, item.promoPrice, item.isPromoTicket) {
        mutableStateOf(PriceFormatter.formatNumber(item.promoPrice ?: item.shelfDisplayPrice()))
    }
    var originalText by remember(item.preselectionId, item.promoOriginalPrice, item.isPromoTicket) {
        mutableStateOf(
            PriceFormatter.formatNumber(
                item.promoOriginalPrice ?: item.shelfOriginalPrice() ?: item.price,
            ),
        )
    }
    var hadPriceFocus by remember(item.preselectionId) { mutableStateOf(false) }
    var hadPromoFocus by remember(item.preselectionId) { mutableStateOf(false) }
    LaunchedEffect(item.price, item.isPromoTicket) {
        if (!hadPriceFocus && !item.isPromoTicket) priceText = PriceFormatter.formatNumber(item.price)
    }
    LaunchedEffect(item.promoPrice, item.promoOriginalPrice, item.isPromoTicket) {
        if (!hadPromoFocus && item.isPromoTicket) {
            promoText = PriceFormatter.formatNumber(item.promoPrice ?: item.shelfDisplayPrice())
            originalText = PriceFormatter.formatNumber(
                item.promoOriginalPrice ?: item.shelfOriginalPrice() ?: item.price,
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .catalogChangeGlow(item.hasCatalogChange()),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
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
                        CatalogChangeBadge(active = item.hasCatalogChange())
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !item.isPromoTicket,
                            onClick = { if (item.isPromoTicket) onPromoToggle(false) },
                            label = { Text("Standard") },
                        )
                        FilterChip(
                            selected = item.isPromoTicket,
                            onClick = { if (!item.isPromoTicket) onPromoToggle(true) },
                            label = { Text("Promo") },
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            if (item.isPromoTicket) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = promoText,
                        onValueChange = { promoText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Promo price") },
                        suffix = { Text("DA") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (hadPromoFocus && !state.isFocused) {
                                    onPromoPricesCommit(promoText, originalText)
                                }
                                hadPromoFocus = state.isFocused
                            },
                    )
                    OutlinedTextField(
                        value = originalText,
                        onValueChange = { originalText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Original") },
                        suffix = { Text("DA") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { onPromoPricesCommit(promoText, originalText) },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (hadPromoFocus && !state.isFocused) {
                                    onPromoPricesCommit(promoText, originalText)
                                }
                                hadPromoFocus = state.isFocused
                            },
                    )
                }
                Text(
                    "Promo ticket: same red as standard; original prix-barrée sits beside promo price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
                            if (hadPriceFocus && !state.isFocused) onPriceCommit(priceText)
                            hadPriceFocus = state.isFocused
                        },
                )
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
