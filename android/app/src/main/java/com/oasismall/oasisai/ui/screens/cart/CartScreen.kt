package com.oasismall.oasisai.ui.screens.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.ui.OasisViewModelFactory
import com.oasismall.oasisai.ui.components.ArticleCard
import com.oasismall.oasisai.ui.components.CartSourceLegend
import com.oasismall.oasisai.ui.components.cartSourceStyle
import com.oasismall.oasisai.ui.components.sourceBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onArticleClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedArticleIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val title = when (viewModel.cartType) {
        CartType.SHARE -> "Ready to share"
        CartType.DESIGN -> "Design"
        CartType.DESIGN_DONE -> "Design — Done"
        CartType.PHOTOSHOOT -> "Legacy queue"
    }
    val emptyHint = when (viewModel.cartType) {
        CartType.SHARE -> "Add articles that already have images in the Oasis gallery (for sharing or print later)."
        CartType.DESIGN -> "Use the Design tab for shelf label layout."
        CartType.DESIGN_DONE -> "Printed or sent articles appear in Done on the Design tab."
        CartType.PHOTOSHOOT -> "Use the AGENT tab to capture product photos."
    }

    val shareableCount = items.count { !it.imagePath.isNullOrBlank() }
    val selectedItems = items.filter { it.articleId in selectedIds && !it.imagePath.isNullOrBlank() }

    Scaffold(topBar = { TopAppBar(title = { Text("$title (${items.size})") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(emptyHint, style = MaterialTheme.typography.bodySmall)
            }
            message?.let { msg ->
                item {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (items.isNotEmpty() && viewModel.cartType == CartType.SHARE) {
                item {
                    Text(
                        "$shareableCount PNG(s) — Share all as files sends documents to Telegram (not compressed photos)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.addAllToDesign() },
                        enabled = shareableCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (shareableCount > 0) {
                                "Add all ($shareableCount) to Design"
                            } else {
                                "Add all to Design"
                            },
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.shareAllPngFiles(context) },
                        enabled = shareableCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (shareableCount > 0) {
                                "Share all as files ($shareableCount)"
                            } else {
                                "Share all as files"
                            },
                        )
                    }
                }
                item {
                    Text(
                        "Optional: send only checked items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::selectAllShareable, modifier = Modifier.weight(1f)) {
                            Text("Select all")
                        }
                        OutlinedButton(onClick = viewModel::clearSelection, modifier = Modifier.weight(1f)) {
                            Text("Clear")
                        }
                    }
                }
                if (selectedItems.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.shareSelectedPngFiles(context, selectedItems) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Share selected as files (${selectedItems.size})") }
                    }
                    item {
                        OutlinedButton(
                            onClick = { viewModel.addToDesign(selectedItems) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add selected (${selectedItems.size}) to Design") }
                    }
                }
            }
            if (items.isNotEmpty()) {
                item { CartSourceLegend() }
                item {
                    OutlinedButton(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear list")
                    }
                }
            }
            if (items.isEmpty()) {
                item { Text("List is empty.", modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(items, key = { it.preselectionId }) { item ->
                val sourceStyle = cartSourceStyle(item.note)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = sourceStyle.label,
                        color = sourceStyle.color,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ArticleCard(
                        article = item.toArticleWithImage(),
                        border = sourceBorder(item.note),
                        onClick = { onArticleClick(item.articleId) },
                        trailing = {
                            if (viewModel.cartType == CartType.SHARE) {
                                Checkbox(
                                    checked = item.articleId in selectedIds,
                                    enabled = !item.imagePath.isNullOrBlank(),
                                    onCheckedChange = { viewModel.toggleSelection(item.articleId) },
                                )
                            }
                            IconButton(onClick = { viewModel.remove(item.articleId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        },
                    )
                    Text(
                        imageTimingText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { viewModel.remove(item.articleId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove from cart") }
                }
            }
        }
    }
}

@Composable
fun CartRoute(
    cartType: CartType,
    factory: OasisViewModelFactory,
    onArticleClick: (Long) -> Unit,
) {
    val viewModel: CartViewModel = viewModel(
        key = "cart_${cartType.name}",
        factory = factory.cartViewModelFactory(cartType),
    )
    CartScreen(viewModel, onArticleClick)
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

private fun imageTimingText(item: PreselectionWithArticle): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val created = item.imageCreatedAt?.let { fmt.format(Date(it)) } ?: "unknown"
    val sent = item.imageLastSentAt?.let { fmt.format(Date(it)) } ?: "not sent"
    return "Image: $created · Last sent: $sent"
}
