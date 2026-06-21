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
import com.oasismall.oasisai.data.model.ImportChangeType
import com.oasismall.oasisai.data.repository.ImportChangeUiRow
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
        modifier = modifier
            .fillMaxWidth()
            .catalogChangeGlow(article.hasCatalogChange()),
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
                if (article.hasCatalogChange()) {
                    CatalogChangeBadge(active = true)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun StatusChip(changeStatus: String, needsTicket: Boolean) {
    val label = when {
        changeStatus == ArticleChangeStatus.PRICE_CHANGED.name -> "Price changed"
        changeStatus == ArticleChangeStatus.NEW.name -> "New"
        changeStatus == ArticleChangeStatus.REMOVED.name -> "Removed"
        changeStatus == ArticleChangeStatus.RENAMED.name -> "Renamed"
        needsTicket -> "Needs ticket"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportChangeCard(
    row: ImportChangeUiRow,
    modifier: Modifier = Modifier,
    metaLine: String? = null,
    onArticleClick: ((Long) -> Unit)? = null,
    onAddToShare: ((Long) -> Unit)? = null,
    onAddToShoot: ((Long) -> Unit)? = null,
) {
    val change = row.change
    val article = row.article
    val articleId = article?.id ?: change.articleId
    val hasImage = article?.hasAppGalleryImage() == true
    val canRoute = articleId != null && change.changeType != ImportChangeType.REMOVED.name

    Card(
        modifier = modifier
            .fillMaxWidth()
            .catalogChangeGlow(
                change.changeType == ImportChangeType.PRICE_CHANGED.name ||
                    change.changeType == ImportChangeType.NEW.name ||
                    change.changeType == ImportChangeType.RENAMED.name,
            ),
        onClick = {
            if (onArticleClick != null && articleId != null) {
                onArticleClick(articleId)
            }
        },
        enabled = onArticleClick != null && articleId != null,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImportChangeThumbnail(article)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(change.designation, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${changeTypeLabelShort(change.changeType)} — ${change.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    change.oldValue?.let { old ->
                        Text(
                            "Was: ${formatImportChangeValue(change.changeType, old)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    change.newValue?.let { new ->
                        Text(
                            "Now: ${formatImportChangeValue(change.changeType, new)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    metaLine?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (canRoute && (onAddToShare != null || onAddToShoot != null)) {
                if (hasImage && onAddToShare != null) {
                    Button(
                        onClick = { onAddToShare(articleId!!) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add to To share")
                    }
                } else if (!hasImage && onAddToShoot != null) {
                    Button(
                        onClick = { onAddToShoot(articleId!!) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add to To shoot")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportChangeThumbnail(article: ArticleWithImage?) {
    val path = article?.imagePath
    if (article != null && !path.isNullOrBlank() && File(path).exists()) {
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
            Text(
                "No image",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun changeTypeLabelShort(type: String): String = when (type) {
    ImportChangeType.NEW.name -> "New"
    ImportChangeType.PRICE_CHANGED.name -> "Price changed"
    ImportChangeType.RENAMED.name -> "Renamed"
    ImportChangeType.REMOVED.name -> "Removed"
    ImportChangeType.UNCHANGED.name -> "Unchanged"
    else -> type.replace('_', ' ')
}

private fun formatImportChangeValue(changeType: String, value: String): String =
    if (changeType == ImportChangeType.PRICE_CHANGED.name) {
        value.toDoubleOrNull()?.let { PriceFormatter.format(it) } ?: value
    } else {
        value
    }

@Composable
fun OpenCameraBatchButton(
    onClick: (articleId: Long?) -> Unit,
    articleId: Long? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedButton(onClick = { onClick(articleId) }, modifier = modifier) {
        Text("Open camera batch (free scan)")
    }
}
