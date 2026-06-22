package com.oasismall.oasisai.domain.paray

import com.oasismall.oasisai.ui.navigation.OasisRoute

/** Maps NavController routes to workflow screen labels — no query params or IDs stored. */
object ParayWorkflowScreens {
    val allLabels: List<String> = listOf(
        "VisioPRO",
        "Articles",
        "To Shoot",
        "AGENT",
        "PARAY",
        "Batch txt",
        "To Share",
        "Design",
        "Settings",
        "Import",
        "Scanner",
        "Camera Batch",
        "PARAY Learn",
        "Catalog",
        "Report",
        "Print",
        "Phone Sync",
    )

    fun normalize(route: String?): String? {
        if (route.isNullOrBlank()) return null
        val base = route.substringBefore('?').substringBefore('/').trim()
        return when {
            base == OasisRoute.Home.route -> "Articles"
            base == OasisRoute.CheckShoot.route || route.startsWith("check_shoot") -> "AGENT"
            base == OasisRoute.PhotoshootCart.route -> "To Shoot"
            base == OasisRoute.Design.route -> "Design"
            base == OasisRoute.ParayMain.route -> "PARAY"
            base == OasisRoute.ParayLearnSession.route -> "PARAY Learn"
            base == OasisRoute.ParayHome.route -> "PARAY"
            base == OasisRoute.Settings.route -> "Settings"
            base.startsWith("camera_batch") -> "Camera Batch"
            base == OasisRoute.Import.route || base.startsWith("import") -> "Import"
            base == OasisRoute.Scanner.route -> "Scanner"
            base == OasisRoute.ShareCart.route -> "To Share"
            base == OasisRoute.VisioProHome.route || base.startsWith("visio_pro") -> "VisioPRO"
            base == OasisRoute.BatchTxt.route -> "Batch txt"
            base == OasisRoute.Catalog.route -> "Catalog"
            base == OasisRoute.Report.route -> "Report"
            base == OasisRoute.Print.route || base.startsWith("print") -> "Print"
            base == OasisRoute.PhoneSync.route -> "Phone Sync"
            base == OasisRoute.Images.route -> "Settings"
            base == OasisRoute.WorkHistory.route -> "Settings"
            base == OasisRoute.GalleryLink.route -> "Settings"
            else -> null
        }
    }
}
