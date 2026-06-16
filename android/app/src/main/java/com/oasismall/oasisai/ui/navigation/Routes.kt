package com.oasismall.oasisai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector

sealed class OasisRoute(val route: String) {
    data object Home : OasisRoute("home")
    data object Import : OasisRoute("import")
    data object ImportDetail : OasisRoute("import/{importId}") {
        fun create(importId: Long) = "import/$importId"
    }
    data object Catalog : OasisRoute("catalog")
    data object ArticleDetail : OasisRoute("article/{articleId}") {
        fun create(articleId: Long) = "article/$articleId"
    }
    data object Scanner : OasisRoute("scanner")
    data object PhotoshootCart : OasisRoute("photoshoot_cart")
    data object ShareCart : OasisRoute("share_cart")
    data object WorkHistory : OasisRoute("history")
    data object Report : OasisRoute("report")
    data object Settings : OasisRoute("settings")
    data object GalleryLink : OasisRoute("gallery_link")
    data object Preselection : OasisRoute("preselection")
    data object Print : OasisRoute("print")
    data object PrintHistory : OasisRoute("print_history")
    data object PrintBatchDetail : OasisRoute("print_batch/{batchId}") {
        fun create(batchId: Long) = "print_batch/$batchId"
    }
    data object Promo : OasisRoute("promo")
    data object Images : OasisRoute("images")
    data object BatchTxt : OasisRoute("batch_txt")
    data object CameraBatchShoot : OasisRoute("camera_batch_shoot?queueItemId={queueItemId}") {
        fun create(queueItemId: Long? = null): String {
            val id = queueItemId?.toString() ?: ""
            return "camera_batch_shoot?queueItemId=$id"
        }
    }
    data object CameraBatchImport : OasisRoute("camera_batch_import")
    data object CheckShoot : OasisRoute("check_shoot") {
        fun create(
            barcode: String? = null,
            startCapture: Boolean = false,
            returnArticleId: Long? = null,
        ): String {
            val encoded = if (!barcode.isNullOrBlank()) {
                java.net.URLEncoder.encode(barcode, Charsets.UTF_8.name())
            } else {
                ""
            }
            val capture = if (startCapture) "true" else "false"
            val returnId = returnArticleId?.toString() ?: ""
            return "$route?barcode=$encoded&startCapture=$capture&returnArticleId=$returnId"
        }
    }
    data object BackgroundRemoval : OasisRoute("background_removal/{articleId}") {
        fun create(articleId: Long) = "background_removal/$articleId"
    }
    data object PhoneSync : OasisRoute("phone_sync")
    data object Design : OasisRoute("design")
    data object ParayImport : OasisRoute("paray_import")
    data object ParayHome : OasisRoute("paray_home")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val useAgentIcon: Boolean = false,
)

val bottomNavItems = listOf(
    BottomNavItem(OasisRoute.Home.route, "Articles", Icons.Default.Home),
    BottomNavItem(OasisRoute.PhotoshootCart.route, "To shoot", Icons.Default.CameraAlt),
    BottomNavItem(OasisRoute.CheckShoot.route, "AGENT", Icons.Default.Home, useAgentIcon = true),
    BottomNavItem(OasisRoute.BatchTxt.route, "Batch txt", Icons.Default.ListAlt),
    BottomNavItem(OasisRoute.ShareCart.route, "To share", Icons.Default.Share),
    BottomNavItem(OasisRoute.Design.route, "Design", Icons.Default.Brush),
    BottomNavItem(OasisRoute.Settings.route, "Settings", Icons.Default.Settings),
)
