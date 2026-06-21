package com.oasismall.oasisai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.model.ArticleChangeStatus
import com.oasismall.oasisai.domain.design.DesignBatchItemUi

fun ArticleWithImage.hasCatalogChange(): Boolean =
    needsTicketUpdate ||
        changeStatus == ArticleChangeStatus.PRICE_CHANGED.name ||
        changeStatus == ArticleChangeStatus.NEW.name ||
        changeStatus == ArticleChangeStatus.RENAMED.name

fun PreselectionWithArticle.hasCatalogChange(): Boolean =
    needsTicketUpdate ||
        changeStatus == ArticleChangeStatus.PRICE_CHANGED.name ||
        changeStatus == ArticleChangeStatus.NEW.name ||
        changeStatus == ArticleChangeStatus.RENAMED.name

@Composable
fun Modifier.catalogChangeGlow(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "catalogGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "catalogGlowAlpha",
    )
    val color = MaterialTheme.colorScheme.primary
    return this
        .drawBehind {
            drawRoundRect(
                color = color.copy(alpha = pulse * 0.22f),
                cornerRadius = CornerRadius(14.dp.toPx()),
            )
        }
        .border(
            width = 2.dp,
            color = color.copy(alpha = 0.45f + pulse * 0.45f),
            shape = RoundedCornerShape(12.dp),
        )
}

@Composable
fun CatalogChangeBadge(active: Boolean) {
    if (!active) return
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        androidx.compose.material3.Text(
            "Modifié CSV",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
