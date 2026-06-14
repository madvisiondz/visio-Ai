package com.oasismall.oasisai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.data.model.ImageStatus
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.hasAppGalleryImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleCard(
    article: ArticleWithImage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
    border: BorderStroke? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val path = article.imagePath
            if (!path.isNullOrBlank() && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = article.designation,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text("No img", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(article.designation, fontWeight = FontWeight.SemiBold)
                Text(PriceFormatter.format(article.price), color = MaterialTheme.colorScheme.primary)
                Text(article.barcode, style = MaterialTheme.typography.bodySmall)
                StatusChip(article.changeStatus, article.needsTicketUpdate)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun StatusChip(changeStatus: String, needsTicket: Boolean) {
    val label = when {
        needsTicket && changeStatus == ArticleChangeStatus.PRICE_CHANGED.name -> "Needs ticket"
        changeStatus == ArticleChangeStatus.NEW.name -> "New"
        changeStatus == ArticleChangeStatus.REMOVED.name -> "Removed"
        changeStatus == ArticleChangeStatus.RENAMED.name -> "Renamed"
        else -> null
    }
    if (label != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
            Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ImageStatusLabel(imageStatus: String?, imagePath: String?) {
    val label = when {
        imageStatus == ImageStatus.MULTIPLE_MATCHES.name -> "Image: Review (multiple matches)"
        imageStatus == ImageStatus.NEEDS_REVIEW.name -> "Image: Needs review"
        !imagePath.isNullOrBlank() && File(imagePath).exists() -> "Image: Found"
        else -> "Image: Missing"
    }
    val color = when {
        label.startsWith("Image: Missing") -> MaterialTheme.colorScheme.error
        label.contains("Review") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun TicketStatusLabel(needsTicketUpdate: Boolean, changeStatus: String) {
    val label = when {
        needsTicketUpdate -> "Ticket: Needs update"
        changeStatus == ArticleChangeStatus.NEW.name -> "Ticket: New article"
        else -> "Ticket: OK"
    }
    val color = if (needsTicketUpdate || changeStatus == ArticleChangeStatus.NEW.name) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultPanel(
    article: ArticleWithImage,
    modifier: Modifier = Modifier,
    onAddToShare: () -> Unit,
    onMarkVerified: () -> Unit,
    onOpenDetail: () -> Unit,
    onCreateAsset: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Scan result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val path = article.imagePath
            if (!path.isNullOrBlank() && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = article.designation,
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier.size(120.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text("No image", modifier = Modifier.padding(16.dp))
                }
            }
            Text(article.designation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
                PriceFormatter.format(article.price),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Barcode: ${article.barcode}", style = MaterialTheme.typography.bodySmall)
            TicketStatusLabel(article.needsTicketUpdate, article.changeStatus)
            ImageStatusLabel(article.imageStatus, article.imagePath)
            if (article.changeStatus != ArticleChangeStatus.UNCHANGED.name) {
                StatusChip(article.changeStatus, article.needsTicketUpdate)
            }
            val canShare = article.hasAppGalleryImage()
            Button(onClick = onCreateAsset, modifier = Modifier.fillMaxWidth()) {
                Text("Create asset (new photo + cutout)")
            }
            Button(
                onClick = onAddToShare,
                enabled = canShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (canShare) "Add to To share" else "No image — use AGENT")
            }
            if (article.needsTicketUpdate) {
                OutlinedButton(onClick = onMarkVerified, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark ticket verified on shelf")
                }
            }
            OutlinedButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                Text("Open article detail")
            }
        }
    }
}
