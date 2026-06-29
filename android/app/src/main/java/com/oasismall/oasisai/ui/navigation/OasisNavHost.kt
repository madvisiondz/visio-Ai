package com.oasismall.oasisai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.settings.ImportantRayonsConfig
import com.oasismall.oasisai.ui.components.LocalImportantRayonsConfig
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.ui.OasisViewModelFactory
import com.oasismall.oasisai.ui.components.CsvImportBanner
import com.oasismall.oasisai.ui.screens.article.ArticleDetailScreen
import com.oasismall.oasisai.ui.screens.article.ArticleDetailViewModel
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchImportScreen
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchImportViewModel
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchShootScreen
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchShootViewModel
import com.oasismall.oasisai.ui.screens.batchtxt.BatchTxtScreen
import com.oasismall.oasisai.ui.screens.batchtxt.BatchTxtViewModel
import com.oasismall.oasisai.ui.screens.backgroundremoval.BackgroundRemovalScreen
import com.oasismall.oasisai.ui.screens.backgroundremoval.BackgroundRemovalViewModel
import com.oasismall.oasisai.ui.screens.design.DesignScreen
import com.oasismall.oasisai.ui.screens.design.DesignViewModel
import com.oasismall.oasisai.ui.screens.checkshoot.CheckShootScreen
import com.oasismall.oasisai.ui.screens.checkshoot.CheckShootViewModel
import com.oasismall.oasisai.ui.screens.catalog.CatalogScreen
import com.oasismall.oasisai.ui.screens.catalog.CatalogViewModel
import com.oasismall.oasisai.ui.screens.history.PrintBatchDetailScreen
import com.oasismall.oasisai.ui.screens.history.PrintHistoryScreen
import com.oasismall.oasisai.ui.screens.history.PrintHistoryViewModel
import com.oasismall.oasisai.ui.screens.history.WorkHistoryScreen
import com.oasismall.oasisai.ui.screens.history.WorkHistoryViewModel
import com.oasismall.oasisai.ui.screens.report.ReportScreen
import com.oasismall.oasisai.ui.screens.report.ReportViewModel
import com.oasismall.oasisai.ui.screens.cart.CartRoute
import com.oasismall.oasisai.ui.screens.home.HomeScreen
import com.oasismall.oasisai.ui.screens.home.HomeViewModel
import com.oasismall.oasisai.ui.screens.settings.SettingsScreen
import com.oasismall.oasisai.ui.screens.settings.SettingsViewModel
import com.oasismall.oasisai.ui.screens.importing.ImportDetailScreen
import com.oasismall.oasisai.ui.screens.importing.ImportScreen
import com.oasismall.oasisai.ui.screens.importing.ImportViewModel
import com.oasismall.oasisai.ui.screens.preselection.PreselectionScreen
import com.oasismall.oasisai.ui.screens.preselection.PreselectionViewModel
import com.oasismall.oasisai.ui.screens.print.PrintScreen
import com.oasismall.oasisai.ui.screens.print.PrintViewModel
import com.oasismall.oasisai.ui.screens.promo.PromoScreen
import com.oasismall.oasisai.ui.screens.promo.PromoViewModel
import com.oasismall.oasisai.ui.screens.phonesync.PhoneSyncScreen
import com.oasismall.oasisai.ui.screens.phonesync.PhoneSyncViewModel
import com.oasismall.oasisai.ui.screens.scanner.ScannerScreen
import com.oasismall.oasisai.ui.screens.scanner.ScannerViewModel
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.ui.screens.visiopro.VisioProCategoryScreen
import com.oasismall.oasisai.ui.screens.visiopro.VisioProHomeScreen
import com.oasismall.oasisai.ui.screens.visiopro.VisioProHomeViewModel
import com.oasismall.oasisai.ui.screens.visiopro.VisioProViewModel
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerCanvasScreen
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerHubScreen
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerHubViewModel
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerViewModel
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProListEditorScreen
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProListEditorViewModel
import com.oasismall.oasisai.ui.screens.settings.ImportantRayonsScreen
import com.oasismall.oasisai.ui.screens.settings.ImportantRayonsViewModel
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProSettingsScreen
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProSettingsViewModel
import com.oasismall.oasisai.ui.screens.images.ImageManagerRoute

@Composable
fun OasisNavHost(factory: OasisViewModelFactory) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = showMainBottomBar(currentRoute)
    val rayonsConfig by factory.repository.importantRayonsConfig
        .collectAsStateWithLifecycle(initialValue = ImportantRayonsConfig())
    val backgroundTasks by factory.backgroundTaskManager.state
        .collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalImportantRayonsConfig provides rayonsConfig,
    ) {
        Box {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        SwipeableBottomNavBar(
                            items = bottomNavItems,
                            currentRoute = currentRoute,
                            navController = navController,
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = OasisRoute.Home.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(OasisRoute.Home.route) {
                        val vm: HomeViewModel = viewModel(factory = factory)
                        HomeScreen(
                            viewModel = vm,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                            onOpenCameraBatch = { articleId ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(null, articleId))
                            },
                        )
                    }
                    composable(OasisRoute.Settings.route) {
                        val vm: SettingsViewModel = viewModel(factory = factory)
                        SettingsScreen(
                            viewModel = vm,
                            onNavigateImportHistory = { navController.navigate(OasisRoute.Import.route) },
                            onNavigateImageManager = { navController.navigate(OasisRoute.Images.route) },
                            onNavigateScanner = { navController.navigate(OasisRoute.Scanner.route) },
                            onNavigateBackgroundRemoval = {
                                navController.navigate(OasisRoute.BackgroundRemoval.create(0L))
                            },
                            onNavigatePhoneSync = { navController.navigate(OasisRoute.PhoneSync.route) },
                            onNavigateHistory = { navController.navigate(OasisRoute.WorkHistory.route) },
                            onNavigateReport = { navController.navigate(OasisRoute.Report.route) },
                            onNavigateVisioProSettings = { navController.navigate(OasisRoute.VisioProSettings.route) },
                            onNavigateImportantRayons = { navController.navigate(OasisRoute.ImportantRayons.route) },
                        )
                    }
                    composable(OasisRoute.ImportantRayons.route) {
                        val vm: ImportantRayonsViewModel = viewModel(factory = factory)
                        ImportantRayonsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(OasisRoute.VisioProSettings.route) {
                        val vm: VisioProSettingsViewModel = viewModel(factory = factory)
                        VisioProSettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenCategory = { category ->
                                navController.navigate(OasisRoute.VisioProListEditor.create(category.name))
                            },
                        )
                    }
                    composable(
                        OasisRoute.VisioProListEditor.route,
                        arguments = listOf(navArgument("category") { type = NavType.StringType }),
                    ) { entry ->
                        val categoryName = entry.arguments?.getString("category") ?: return@composable
                        val category = runCatching { VisioProCategory.valueOf(categoryName) }.getOrNull()
                            ?: return@composable
                        val vm: VisioProListEditorViewModel = viewModel(
                            factory = factory.visioProListEditorFactory(category),
                        )
                        VisioProListEditorScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(OasisRoute.VisioProHome.route) {
                        val vm: VisioProHomeViewModel = viewModel(factory = factory)
                        val counts by vm.counts.collectAsStateWithLifecycle()
                        val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
                        val mediaInstalled by vm.mediaInstalled.collectAsStateWithLifecycle()
                        VisioProHomeScreen(
                            categoryCounts = counts,
                            mediaInstalled = mediaInstalled,
                            syncMessage = syncMessage,
                            onDismissSyncMessage = vm::clearSyncMessage,
                            onOpenCategory = { category ->
                                navController.navigate(OasisRoute.VisioProCategory.create(category.name))
                            },
                            onOpenDesigner = {
                                navController.navigate(OasisRoute.VisioProDesignerHub.route)
                            },
                        )
                    }
                    composable(OasisRoute.VisioProDesignerHub.route) {
                        val hubVm: VisioProDesignerHubViewModel = viewModel(factory = factory)
                        VisioProDesignerHubScreen(
                            viewModel = hubVm,
                            onBack = { navController.popBackStack() },
                            onOpenPreset = { key ->
                                navController.navigate(OasisRoute.VisioProDesignerCanvas.create(key.name))
                            },
                        )
                    }
                    composable(
                        OasisRoute.VisioProDesignerCanvas.route,
                        arguments = listOf(navArgument("presetKey") { type = NavType.StringType }),
                    ) { entry ->
                        val presetKeyName = entry.arguments?.getString("presetKey") ?: return@composable
                        val presetKey = runCatching {
                            com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey.valueOf(presetKeyName)
                        }.getOrNull() ?: return@composable
                        val vm: VisioProDesignerViewModel = viewModel(
                            factory = factory.visioProDesignerFactory(presetKey),
                        )
                        VisioProDesignerCanvasScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        OasisRoute.VisioProCategory.route,
                        arguments = listOf(navArgument("category") { type = NavType.StringType }),
                    ) { entry ->
                        val categoryName = entry.arguments?.getString("category") ?: return@composable
                        val category = runCatching { VisioProCategory.valueOf(categoryName) }.getOrNull()
                            ?: return@composable
                        val vm: VisioProViewModel = viewModel(factory = factory)
                        VisioProCategoryScreen(
                            category = category,
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(OasisRoute.Design.route) {
                        val vm: DesignViewModel = viewModel(factory = factory)
                        DesignScreen(viewModel = vm)
                    }
                    composable(OasisRoute.PhoneSync.route) {
                        val vm: PhoneSyncViewModel = viewModel(factory = factory)
                        PhoneSyncScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "${OasisRoute.CheckShoot.route}?barcode={barcode}&startCapture={startCapture}&returnArticleId={returnArticleId}",
                        arguments = listOf(
                            navArgument("barcode") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("startCapture") {
                                type = NavType.StringType
                                defaultValue = "false"
                            },
                            navArgument("returnArticleId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        val vm: CheckShootViewModel = viewModel(factory = factory)
                        val barcode = entry.arguments?.getString("barcode").orEmpty()
                        val startCapture = entry.arguments?.getString("startCapture") == "true"
                        val returnArticleId = entry.arguments?.getString("returnArticleId")
                            ?.toLongOrNull()
                            ?.takeIf { it > 0L }
                        CheckShootScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            prefillBarcode = barcode,
                            startCapture = startCapture,
                            returnArticleId = returnArticleId,
                            onReturnToArticle = {
                                navController.popBackStack()
                            },
                            onOpenCameraBatch = { articleId ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(null, articleId))
                            },
                            onOpenSubBarcodeBatchShoot = { articleId, subBarcode ->
                                navController.navigate(
                                    OasisRoute.CameraBatchShoot.create(
                                        articleId = articleId,
                                        subBcAcquire = true,
                                        confirmedSubBarcode = subBarcode,
                                    ),
                                )
                            },
                        )
                    }
                    composable(
                        OasisRoute.BackgroundRemoval.route,
                        arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
                    ) { entry ->
                        val articleId = entry.arguments?.getLong("articleId") ?: 0L
                        val vm: BackgroundRemovalViewModel = viewModel(
                            factory = factory.backgroundRemovalViewModelFactory(articleId),
                        )
                        BackgroundRemovalScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(OasisRoute.ShareCart.route) {
                        CartRoute(
                            cartType = CartType.SHARE,
                            factory = factory,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                        )
                    }
                    composable(OasisRoute.PhotoshootCart.route) {
                        CartRoute(
                            cartType = CartType.PHOTOSHOOT,
                            factory = factory,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                            onOpenAgent = { navController.navigate(OasisRoute.CheckShoot.create()) },
                            onOpenCameraBatch = { articleId ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(null, articleId))
                            },
                        )
                    }
                    composable(OasisRoute.WorkHistory.route) {
                        val vm: WorkHistoryViewModel = viewModel(factory = factory)
                        WorkHistoryScreen(
                            viewModel = vm,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                        )
                    }
                    composable(OasisRoute.Report.route) {
                        val vm: ReportViewModel = viewModel(factory = factory)
                        ReportScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                        )
                    }
                    composable(OasisRoute.Import.route) {
                        val vm: ImportViewModel = viewModel(factory = factory)
                        ImportScreen(
                            vm,
                            onImportDetail = { navController.navigate(OasisRoute.ImportDetail.create(it)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        OasisRoute.ImportDetail.route,
                        arguments = listOf(navArgument("importId") { type = NavType.LongType }),
                    ) { entry ->
                        val importId = entry.arguments?.getLong("importId") ?: return@composable
                        val vm: ImportViewModel = viewModel(factory = factory)
                        ImportDetailScreen(
                            importId = importId,
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                        )
                    }
                    composable(OasisRoute.Catalog.route) {
                        val vm: CatalogViewModel = viewModel(factory = factory)
                        CatalogScreen(vm, onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) })
                    }
                    composable(
                        OasisRoute.ArticleDetail.route,
                        arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
                    ) { entry ->
                        val articleId = entry.arguments?.getLong("articleId") ?: return@composable
                        val vm: ArticleDetailViewModel = viewModel(factory = factory)
                        ArticleDetailScreen(
                            articleId = articleId,
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onRemoveBackground = { id ->
                                navController.navigate(OasisRoute.BackgroundRemoval.create(id))
                            },
                            onCreateAsset = { barcode ->
                                navController.navigate(
                                    OasisRoute.CheckShoot.create(
                                        barcode = barcode,
                                        startCapture = true,
                                        returnArticleId = articleId,
                                    ),
                                )
                            },
                            onOpenCameraBatch = { id ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(null, id))
                            },
                            onOpenSubBarcodeBatchShoot = { id ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(articleId = id, subBcAcquire = true))
                            },
                        )
                    }
                    composable(OasisRoute.Scanner.route) {
                        val vm: ScannerViewModel = viewModel(factory = factory)
                        ScannerScreen(
                            viewModel = vm,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                            onCreateAsset = { barcode ->
                                navController.navigate(OasisRoute.CheckShoot.create(barcode, startCapture = true))
                            },
                        )
                    }
                    composable(OasisRoute.BatchTxt.route) {
                        val vm: BatchTxtViewModel = viewModel(factory = factory)
                        BatchTxtScreen(
                            viewModel = vm,
                            onOpenCameraBatch = { queueItemId ->
                                navController.navigate(OasisRoute.CameraBatchShoot.create(queueItemId))
                            },
                            onOpenPhotoroomImport = { navController.navigate(OasisRoute.CameraBatchImport.route) },
                        )
                    }
                    composable(
                        route = OasisRoute.CameraBatchShoot.route,
                        arguments = listOf(
                            navArgument("queueItemId") {
                                type = NavType.StringType
                                defaultValue = ""
                                nullable = true
                            },
                            navArgument("articleId") {
                                type = NavType.StringType
                                defaultValue = ""
                                nullable = true
                            },
                            navArgument("subBcAcquire") {
                                type = NavType.StringType
                                defaultValue = "false"
                            },
                            navArgument("confirmedSubBarcode") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { backStackEntry ->
                        val queueItemId = backStackEntry.arguments
                            ?.getString("queueItemId")
                            ?.toLongOrNull()
                        val articleId = backStackEntry.arguments
                            ?.getString("articleId")
                            ?.toLongOrNull()
                        val subBcAcquire = backStackEntry.arguments
                            ?.getString("subBcAcquire") == "true"
                        val confirmedSubBarcode = backStackEntry.arguments
                            ?.getString("confirmedSubBarcode")
                            ?.takeIf { !it.isNullOrBlank() }
                        val vm: CameraBatchShootViewModel = viewModel(factory = factory)
                        CameraBatchShootScreen(
                            viewModel = vm,
                            queueItemId = queueItemId,
                            articleId = articleId,
                            subBcAcquire = subBcAcquire,
                            confirmedSubBarcode = confirmedSubBarcode,
                            onBack = { navController.popBackStack() },
                            onDoneShooting = { navController.navigate(OasisRoute.CameraBatchImport.route) },
                            onOpenPhotoroomImport = { navController.navigate(OasisRoute.CameraBatchImport.route) },
                        )
                    }
                    composable(OasisRoute.CameraBatchImport.route) {
                        val vm: CameraBatchImportViewModel = viewModel(factory = factory)
                        CameraBatchImportScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(OasisRoute.Preselection.route) {
                        val vm: PreselectionViewModel = viewModel(factory = factory)
                        PreselectionScreen(
                            vm,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                            onGoToPrint = { navController.navigate(OasisRoute.Print.route) },
                        )
                    }
                    composable(OasisRoute.Print.route) {
                        val vm: PrintViewModel = viewModel(factory = factory)
                        PrintScreen(vm)
                    }
                    composable(OasisRoute.PrintHistory.route) {
                        val vm: PrintHistoryViewModel = viewModel(factory = factory)
                        PrintHistoryScreen(vm, onBatchClick = { navController.navigate(OasisRoute.PrintBatchDetail.create(it)) })
                    }
                    composable(
                        OasisRoute.PrintBatchDetail.route,
                        arguments = listOf(navArgument("batchId") { type = NavType.LongType }),
                    ) { entry ->
                        val batchId = entry.arguments?.getLong("batchId") ?: return@composable
                        val vm: PrintHistoryViewModel = viewModel(factory = factory)
                        PrintBatchDetailScreen(batchId, vm, onBack = { navController.popBackStack() })
                    }
                    composable(OasisRoute.Promo.route) {
                        val vm: PromoViewModel = viewModel(factory = factory)
                        PromoScreen(vm)
                    }
                    composable(OasisRoute.Images.route) {
                        ImageManagerRoute(
                            factory,
                            onArticleClick = { navController.navigate(OasisRoute.ArticleDetail.create(it)) },
                        )
                    }
                }
            }
            CsvImportBanner(
                taskState = backgroundTasks,
                onClick = { navController.navigate(OasisRoute.Settings.route) },
                onDismiss = { factory.backgroundTaskManager.clearMessages() },
            )
        }
    }
}
