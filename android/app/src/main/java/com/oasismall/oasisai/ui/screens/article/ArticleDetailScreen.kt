package com.oasismall.oasisai.ui.screens.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oasismall.oasisai.ui.components.ImageStatusLabel
import com.oasismall.oasisai.ui.components.StatusChip
import com.oasismall.oasisai.ui.components.TicketStatusLabel
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.hasAppGalleryImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleId: Long,
    viewModel: ArticleDetailViewModel,
    onBack: () -> Unit,
    onRemoveBackground: (Long) -> Unit = {},
    onCreateAsset: (String) -> Unit = {},
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val path = a.imagePath
            if (!path.isNullOrBlank() && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = a.designation,
                    modifier = Modifier.size(160.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Image missing", color = MaterialTheme.colorScheme.error)
            }
            Text(a.designation, style = MaterialTheme.typography.headlineSmall)
            Text(
                PriceFormatter.format(a.price),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            a.previousPrice?.let { Text("Previous: ${PriceFormatter.format(it)}") }
            Text("Barcode: ${a.barcode}")
            a.reference?.let { Text("Ref: $it") }
            a.category?.let { Text("Category: $it") }
            a.brand?.let { Text("Brand: $it") }
            a.stock?.let { Text("Stock: $it") }
            a.unit?.let { Text("Unit: $it") }
            Text("Image created: ${a.imageCreatedAt?.let(::formatTimestamp) ?: "unknown"}")
            Text("Last sent: ${a.imageLastSentAt?.let(::formatTimestamp) ?: "not sent yet"}")
            TicketStatusLabel(a.needsTicketUpdate, a.changeStatus)
            ImageStatusLabel(a.imageStatus, a.imagePath)
            StatusChip(a.changeStatus, a.needsTicketUpdate)
            val canShare = a.hasAppGalleryImage()
            Button(
                onClick = { onCreateAsset(a.barcode) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create asset")
            }
            Text(
                "Take a photo → auto cutout → link PNG to this article.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onRemoveBackground(articleId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove background (offline)")
            }
            Button(
                onClick = viewModel::addToShareCart,
                enabled = canShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (canShare) "Add to To share" else "Add to To share (needs PNG)")
            }
            if (!canShare) {
                Button(
                    onClick = viewModel::addToPhotoshootCart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add to To shoot")
                }
            }
            if (a.needsTicketUpdate) {
                OutlinedButton(onClick = viewModel::markTicketVerified, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark ticket verified on shelf")
                }
            }
            a.rawData?.takeIf { it.isNotBlank() }?.let { raw ->
                Text("All CSV details", style = MaterialTheme.typography.titleMedium)
                Text(raw, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(value))
