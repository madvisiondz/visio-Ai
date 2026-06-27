package com.oasismall.oasisai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.data.db.dao.ArticleWithImage
import com.oasismall.oasisai.util.hasAppGalleryImage

/** Standard article actions — one list shared across Home, Cart, AGENT, Scanner, Detail. */
enum class ArticleAction {
    ASSIGN_PNG,
    OPEN_CAMERA_BATCH,
    ADD_TO_SHARE,
    ADD_TO_SHOOT,
    ADD_TO_DESIGN,
    REMOVE_FROM_CART,
    OPEN_DETAIL,
    CREATE_ASSET,
    SUB_BC,
    MARK_TICKET,
    REMOVE_SUB_BARCODE,
}

enum class ArticleActionContext {
    HOME_LIST,
    CART_SHARE,
    CART_PHOTOSHOOT,
    CART_DESIGN,
    AGENT_UNLOCKED,
    AGENT_LOCKED,
    SCANNER,
    DETAIL,
}

data class ArticleActionHandlers(
    val onAssignPng: (() -> Unit)? = null,
    val onOpenCameraBatch: ((Long?) -> Unit)? = null,
    val onAddToShare: (() -> Unit)? = null,
    val onAddToShoot: (() -> Unit)? = null,
    val onAddToDesign: (() -> Unit)? = null,
    val onRemoveFromCart: (() -> Unit)? = null,
    val onOpenDetail: (() -> Unit)? = null,
    val onCreateAsset: (() -> Unit)? = null,
    val onToggleSubBc: (() -> Unit)? = null,
    val onMarkTicket: (() -> Unit)? = null,
)

object ArticleActionPresets {
    fun actionsFor(context: ArticleActionContext): Set<ArticleAction> = when (context) {
        ArticleActionContext.HOME_LIST -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.OPEN_CAMERA_BATCH,
            ArticleAction.ADD_TO_SHOOT,
            ArticleAction.ADD_TO_SHARE,
        )
        ArticleActionContext.CART_SHARE -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.REMOVE_FROM_CART,
        )
        ArticleActionContext.CART_PHOTOSHOOT -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.OPEN_CAMERA_BATCH,
            ArticleAction.REMOVE_FROM_CART,
        )
        ArticleActionContext.CART_DESIGN -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.REMOVE_FROM_CART,
        )
        ArticleActionContext.AGENT_UNLOCKED -> emptySet()
        ArticleActionContext.AGENT_LOCKED -> setOf(
            ArticleAction.CREATE_ASSET,
            ArticleAction.SUB_BC,
            ArticleAction.ASSIGN_PNG,
            ArticleAction.OPEN_CAMERA_BATCH,
            ArticleAction.ADD_TO_SHARE,
            ArticleAction.ADD_TO_DESIGN,
        )
        ArticleActionContext.SCANNER -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.ADD_TO_SHARE,
            ArticleAction.ADD_TO_SHOOT,
            ArticleAction.ADD_TO_DESIGN,
            ArticleAction.OPEN_DETAIL,
            ArticleAction.MARK_TICKET,
        )
        ArticleActionContext.DETAIL -> setOf(
            ArticleAction.ASSIGN_PNG,
            ArticleAction.OPEN_CAMERA_BATCH,
            ArticleAction.ADD_TO_SHARE,
            ArticleAction.ADD_TO_SHOOT,
            ArticleAction.ADD_TO_DESIGN,
            ArticleAction.MARK_TICKET,
        )
    }
}

@Composable
fun ArticleCompactActionRow(
    article: ArticleWithImage,
    context: ArticleActionContext,
    handlers: ArticleActionHandlers,
    modifier: Modifier = Modifier,
    canShare: Boolean = true,
) {
    val actions = ArticleActionPresets.actionsFor(context)
    if (actions.isEmpty()) return
    val shareEnabled = canShare && article.hasAppGalleryImage()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ArticleAction.ASSIGN_PNG in actions) {
            handlers.onAssignPng?.let { AssignPngImageButton(onClick = it) }
        }
        if (ArticleAction.ADD_TO_SHOOT in actions && !article.hasAppGalleryImage()) {
            handlers.onAddToShoot?.let { action ->
                Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Ajouter à To shoot")
                }
            }
        }
        if (ArticleAction.OPEN_CAMERA_BATCH in actions) {
            handlers.onOpenCameraBatch?.let { action ->
                OpenCameraBatchButton(onClick = action, articleId = article.id)
            }
        }
        if (ArticleAction.ADD_TO_SHARE in actions) {
            handlers.onAddToShare?.let { action ->
                Button(
                    onClick = action,
                    enabled = shareEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (shareEnabled) "Ajouter à To share" else "To share (sans image — AGENT)",
                    )
                }
            }
        }
        if (ArticleAction.ADD_TO_DESIGN in actions) {
            handlers.onAddToDesign?.let { action ->
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Ajouter à Design")
                }
            }
        }
        if (ArticleAction.REMOVE_FROM_CART in actions) {
            handlers.onRemoveFromCart?.let { action ->
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Retirer du panier")
                }
            }
        }
        if (ArticleAction.OPEN_DETAIL in actions) {
            handlers.onOpenDetail?.let { action ->
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Text("Voir fiche article")
                }
            }
        }
    }
}
