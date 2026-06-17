package com.oasismall.oasisai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.repository.SubBarcodeInfo
import com.oasismall.oasisai.util.PriceFormatter
import com.oasismall.oasisai.util.hasAppGalleryImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ArticlePanelData(
    val articleId: Long?,
    val barcode: String,
    val designation: String,
    val price: Double? = null,
    val previousPrice: Double? = null,
    val imagePath: String? = null,
    val codeart: String? = null,
    val lastPriceChangedAt: Long? = null,
    val lastPrintedAt: Long? = null,
    val subBarcodes: List<SubBarcodeInfo> = emptyList(),
    val inGestiumCatalog: Boolean = true,
    val linkedViaAlternate: Boolean = false,
    val linkedViaBodyKey: Boolean = false,
    val needsTicketUpdate: Boolean = false,
    val changeStatus: String = "UNCHANGED",
    val imageStatus: String? = null,
    val isLocked: Boolean = false,
    val subBcMode: Boolean = false,
) {
    val hasShareablePng: Boolean
        get() = !imagePath.isNullOrBlank() && File(imagePath).exists()

    companion object {
        fun fromArticle(
            article: ArticleWithImage,
            meta: com.oasismall.oasisai.data.repository.ArticlePanelMeta? = null,
            scannedBarcode: String? = null,
            linkedViaAlternate: Boolean = false,
            linkedViaBodyKey: Boolean = false,
        ) = ArticlePanelData(
            articleId = article.id,
            barcode = scannedBarcode ?: article.barcode,
            designation = article.designation,
            price = article.price,
            previousPrice = article.previousPrice,
            imagePath = article.imagePath?.takeIf { File(it).exists() },
            codeart = meta?.codeart,
            lastPriceChangedAt = meta?.lastPriceChangedAt,
            lastPrintedAt = meta?.lastPrintedAt,
            subBarcodes = meta?.subBarcodes.orEmpty(),
            inGestiumCatalog = true,
            linkedViaAlternate = linkedViaAlternate,
            linkedViaBodyKey = linkedViaBodyKey,
            needsTicketUpdate = article.needsTicketUpdate,
            changeStatus = article.changeStatus,
            imageStatus = article.imageStatus,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleActionPanel(
    data: ArticlePanelData,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxHeight: Dp = 480.dp,
    modelReady: Boolean = true,
    showLockControls: Boolean = false,
    externalLockButton: Boolean = false,
    onLock: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onCreateAsset: (() -> Unit)? = null,
    onAddToShare: (() -> Unit)? = null,
    onAddToShoot: (() -> Unit)? = null,
    onAddToDesign: (() -> Unit)? = null,
    onToggleSubBc: (() -> Unit)? = null,
    onOpenCameraBatch: ((Long?) -> Unit)? = null,
    onAddSubBarcodeBatchShoot: (() -> Unit)? = null,
    onRemoveSubBarcode: ((String) -> Unit)? = null,
    onMarkTicketVerified: (() -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    onRemoveBackground: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val bodyModifier = if (scrollable) {
        Modifier.heightIn(max = maxHeight).verticalScroll(scroll)
    } else {
        Modifier
    }

    Column(modifier.then(bodyModifier), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Article", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (data.isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(" Locked", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        val designationLabel = when {
            data.inGestiumCatalog && data.linkedViaBodyKey ->
                "${data.designation} (same 9-digit code — in catalog)"
            data.inGestiumCatalog && data.linkedViaAlternate ->
                "${data.designation} (linked sub-barcode)"
            data.inGestiumCatalog -> data.designation
            else -> "Not in catalog — lock or link to existing article"
        }
        Text(designationLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Barcode: ${data.barcode}", style = MaterialTheme.typography.bodyMedium)
        data.codeart?.takeIf { it.isNotBlank() }?.let {
            Text("Article code: $it", style = MaterialTheme.typography.bodySmall)
        }
        data.price?.let { price ->
            Text(
                PriceFormatter.format(price),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            data.previousPrice?.let { prev ->
                Text("Previous: ${PriceFormatter.format(prev)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                formatPriceChangedLabel(data.lastPriceChangedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatLastPrintedLabel(data.lastPrintedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (data.hasShareablePng) {
            Text(
                "PNG in Oasis gallery",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            AsyncImage(
                model = File(data.imagePath!!),
                contentDescription = data.designation,
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                "No PNG — shoot photo (background removed automatically)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("No image", modifier = Modifier.padding(16.dp))
            }
        }

        if (data.inGestiumCatalog) {
            TicketStatusLabel(data.needsTicketUpdate, data.changeStatus)
            ImageStatusLabel(data.imageStatus, data.imagePath)
            StatusChip(data.changeStatus, data.needsTicketUpdate)
        }

        if (data.subBcMode) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "SUB-BC active — scan flavor/color barcodes",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (data.subBarcodes.isNotEmpty()) {
            Text(
                "Sub-barcodes (${data.subBarcodes.size}) — tap to remove",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                data.subBarcodes.forEach { sub ->
                    AssistChip(
                        onClick = { onRemoveSubBarcode?.invoke(sub.barcode) },
                        enabled = onRemoveSubBarcode != null,
                        label = { Text(sub.barcode, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }

        if (!data.isLocked && showLockControls) {
            if (!externalLockButton) {
                onLock?.let { lock ->
                    Button(onClick = lock, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null)
                        Text(if (data.inGestiumCatalog) " Lock this barcode" else " Lock — ask PARAY")
                    }
                }
            }
            onDismiss?.let { dismiss ->
                TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Dismiss — keep scanning")
                }
            }
        } else {
            if (onCreateAsset != null || onToggleSubBc != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onCreateAsset?.let { create ->
                        Button(onClick = create, enabled = modelReady, modifier = Modifier.weight(1f)) {
                            Text("Create asset")
                        }
                    }
                    onToggleSubBc?.let { toggle ->
                        if (data.subBcMode) {
                            Button(
                                onClick = toggle,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            ) { Text("SUB-BC ✓") }
                        } else {
                            OutlinedButton(onClick = toggle, modifier = Modifier.weight(1f)) {
                                Text("SUB-BC")
                            }
                        }
                    }
                }
            }
            if (!modelReady) {
                Text(
                    "Cutout model missing — ask for a full Visio Ai build",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            onAddSubBarcodeBatchShoot?.let { action ->
                Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Add sub-barcode & batch shoot")
                }
            }
            onOpenCameraBatch?.let { action ->
                OpenCameraBatchButton(onClick = action, articleId = data.articleId)
            }
            val canShare = data.hasShareablePng
            onAddToShare?.let { share ->
                if (canShare) {
                    OutlinedButton(onClick = share, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to To share")
                    }
                } else {
                    OutlinedButton(onClick = share, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("To share (no image — shoot first)")
                    }
                }
            }
            onAddToDesign?.let { design ->
                if (canShare) {
                    OutlinedButton(onClick = design, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to Design")
                    }
                }
            }
            onAddToShoot?.let { shoot ->
                if (!canShare) {
                    Button(onClick = shoot, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to To shoot")
                    }
                }
            }
            onRemoveBackground?.let { action ->
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove background (offline)")
                }
            }
            if (data.needsTicketUpdate) {
                onMarkTicketVerified?.let { verify ->
                    OutlinedButton(onClick = verify, modifier = Modifier.fillMaxWidth()) {
                        Text("Mark ticket verified on shelf")
                    }
                }
            }
            onOpenDetail?.let { detail ->
                OutlinedButton(onClick = detail, modifier = Modifier.fillMaxWidth()) {
                    Text("Open article detail")
                }
            }
            onUnlock?.let { unlock ->
                if (!externalLockButton) {
                    OutlinedButton(onClick = unlock, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Icon(Icons.Default.LockOpen, contentDescription = null)
                        Text(" Unlock — scan next")
                    }
                }
            }
        }
    }
}

fun formatPriceChangedLabel(changedAt: Long?): String {
    if (changedAt == null) return "Price: no change recorded"
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    return "Price last changed: ${fmt.format(Date(changedAt))}"
}

fun formatLastPrintedLabel(printedAt: Long?): String {
    if (printedAt == null) return "Last printed: never"
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    return "Last printed: ${fmt.format(Date(printedAt))}"
}
