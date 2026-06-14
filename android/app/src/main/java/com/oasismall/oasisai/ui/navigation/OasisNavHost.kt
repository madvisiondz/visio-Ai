package com.oasismall.oasisai.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.ui.OasisViewModelFactory
import com.oasismall.oasisai.ui.screens.article.ArticleDetailScreen
import com.oasismall.oasisai.ui.screens.article.ArticleDetailViewModel
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
import com.oasismall.oasisai.ui.components.AgentNavIcon
import com.oasismall.oasisai.ui.screens.cart.CartRoute
import com.oasismall.oasisai.ui.screens.home.HomeScreen
import com.oasismall.oasisai.ui.screens.home.HomeViewModel
import com.oasismall.oasisai.ui.screens.images.ImageManagerRoute
import com.oasismall.oasisai.ui.screens.settings.GalleryLinkPlaceholderScreen
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.oasismall.oasisai.OasisApp
import com.oasismall.oasisai.ui.screens.parayhome.ParayHomeScreen
import com.oasismall.oasisai.ui.screens.parayhome.ParayHomeViewModel
import com.oasismall.oasisai.ui.screens.parayimport.ParayImportScreen
import com.oasismall.oasisai.ui.screens.parayimport.ParayImportViewModel
import com.oasismall.oasisai.ui.screens.phonesync.PhoneSyncScreen
import com.oasismall.oasisai.ui.screens.phonesync.PhoneSyncViewModel
import com.oasismall.oasisai.ui.screens.scanner.ScannerScreen
import com.oasismall.oasisai.ui.screens.scanner.ScannerViewModel

@Composable
fun OasisNavHost(factory: OasisViewModelFactory) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = bottomNavItems.any { item ->
        currentRoute == item.route || currentRoute?.startsWith("${item.route}?") == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route ||
                            (item.route == OasisRoute.CheckShoot.route &&
                                currentRoute?.startsWith(OasisRoute.CheckShoot.route) == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val dest = if (item.route == OasisRoute.CheckShoot.route) {
                                    OasisRoute.CheckShoot.create()
                                } else {
                                    item.route
                                }
                                navController.navigate(dest) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item.useAgentIcon) {
                                    AgentNavIcon(selected = selected)
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label, maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ),
                        )
                    }
                }
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
                )
            }
            composable(OasisRoute.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    viewModel = vm,
                    onNavigateImport = { navController.navigate(OasisRoute.Import.route) },
                    onNavigateImageManager = { navController.navigate(OasisRoute.Images.route) },
                    onNavigateScanner = { navController.navigate(OasisRoute.Scanner.route) },
                    onNavigateGalleryLink = { navController.navigate(OasisRoute.GalleryLink.route) },
                    onNavigateBackgroundRemoval = {
                        navController.navigate(OasisRoute.BackgroundRemoval.create(0L))
                    },
                    onNavigatePhoneSync = { navController.navigate(OasisRoute.PhoneSync.route) },
                    onNavigateHistory = { navController.navigate(OasisRoute.WorkHistory.route) },
                    onNavigateReport = { navController.navigate(OasisRoute.Report.route) },
                    onNavigateParayImport = { navController.navigate(OasisRoute.ParayImport.route) },
                    onNavigateParayHome = { navController.navigate(OasisRoute.ParayHome.route) },
                )
            }
            composable(OasisRoute.ParayHome.route) {
                val vm: ParayHomeViewModel = viewModel(factory = factory)
                val context = LocalContext.current
                val app = context.applicationContext as OasisApp
                val importPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        app.parayImportManager.enqueue(uri)
                        navController.navigate(OasisRoute.ParayImport.route)
                    }
                }
                ParayHomeScreen(
                    viewModel = vm,
                    onBackToOasis = { navController.popBackStack() },
                    onGoDesign = { navController.navigate(OasisRoute.Design.route) },
                    onGoScanShoot = { navController.navigate(OasisRoute.CheckShoot.create()) },
                    onImportFingerprints = {
                        importPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                )
            }
            composable(OasisRoute.ParayImport.route) {
                val vm: ParayImportViewModel = viewModel(factory = factory)
                ParayImportScreen(
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
            composable(OasisRoute.GalleryLink.route) {
                GalleryLinkPlaceholderScreen(onBack = { navController.popBackStack() })
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
                ImportDetailScreen(importId, vm, onBack = { navController.popBackStack() })
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
                BatchTxtScreen(viewModel = vm)
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
}
